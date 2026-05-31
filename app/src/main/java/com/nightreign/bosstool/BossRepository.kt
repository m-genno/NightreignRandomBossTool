package com.nightreign.bosstool

import android.content.Context
import com.nightreign.bosstool.Nightlord.Companion.WILDCARD
import java.io.File

/**
 * ボスデータを外部テキストファイルから読み込むリポジトリ。
 *
 * データの考え方:
 *   夜の王ごとに「1夜（1日目）に出る夜ボス」「2夜（2日目）に出る夜ボス」を持つ。
 *   1日目の夜ボスを選ぶ → それを1夜に持つ夜の王が候補になる
 *   2日目の夜ボスを選ぶ → それを2夜に持つ夜の王が候補になる
 *   両方を選ぶ           → 両方を満たす夜の王だけに絞り込まれる（積集合・精度UP）
 *
 * ファイルは初回起動時に assets から
 *   端末の「Android/data/com.nightreign.bosstool/files/」配下にコピーされる。
 * 以降はそのファイルを読むので、ファイルマネージャで編集 → アプリの「再読み込み」で
 * 反映できる（再ビルド不要）。
 */
object BossRepository {
    private const val BOSSES_FILE = "bosses.txt"        // 夜ボスの読み（検索用辞書）
    private const val NIGHTLORDS_FILE = "nightlords.txt" // 夜の王ごとの 1夜/2夜 ボス

    /** 夜ボス名 → よみがな */
    private var readings: Map<String, String> = emptyMap()

    /** 夜の王の一覧（ファイル記載順 = 表示順） */
    private var nightlords: List<Nightlord> = emptyList()

    private fun dataDir(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    /** データファイルが無ければ assets からコピーして読み込む。 */
    fun ensureDataFiles(context: Context) {
        val dir = dataDir(context)
        if (!dir.exists()) dir.mkdirs()
        copyIfMissing(context, dir, BOSSES_FILE)
        copyIfMissing(context, dir, NIGHTLORDS_FILE)
        reload(context)
    }

    /** assets の内容で上書きして読み込む（初期データに戻す）。 */
    fun restoreDefaults(context: Context) {
        val dir = dataDir(context)
        copyFromAssets(context, dir, BOSSES_FILE)
        copyFromAssets(context, dir, NIGHTLORDS_FILE)
        reload(context)
    }

    /** ファイルを読み直す。 */
    fun reload(context: Context) {
        val dir = dataDir(context)
        readings = parseReadings(readOrEmpty(File(dir, BOSSES_FILE)))
        nightlords = parseNightlords(readOrEmpty(File(dir, NIGHTLORDS_FILE)))
    }

    /** 編集すべきファイルの場所（説明表示用）。 */
    fun dataLocation(context: Context): String = dataDir(context).absolutePath

    private fun readOrEmpty(f: File): String =
        if (f.exists()) f.readText(Charsets.UTF_8) else ""

    private fun copyIfMissing(context: Context, dir: File, name: String) {
        if (!File(dir, name).exists()) copyFromAssets(context, dir, name)
    }

    private fun copyFromAssets(context: Context, dir: File, name: String) {
        context.assets.open(name).use { input ->
            File(dir, name).outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun cleanLines(text: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    private fun parseReadings(text: String): Map<String, String> =
        cleanLines(text).mapNotNull { line ->
            val parts = line.split(",").map { it.trim() }
            val name = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val reading = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: name
            name to reading
        }.toMap()

    private fun parseNightlords(text: String): List<Nightlord> =
        cleanLines(text).mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            val name = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Nightlord(
                name = name,
                night1 = parts.getOrNull(1).parseBossSet(),
                night2 = parts.getOrNull(2).parseBossSet(),
            )
        }

    /** "鈴玉狩り;夜の騎兵" → 集合。"*"や"全種類"はワイルドカードにする。 */
    private fun String?.parseBossSet(): Set<String> {
        val s = this?.trim().orEmpty()
        if (s.isEmpty()) return emptySet()
        if (s == "*" || s == "全" || s == "全種類") return setOf(WILDCARD)
        return s.split(";", "、", "/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun boss(name: String) = Boss(name, readings[name] ?: name)

    /** 1日目に選べる夜ボス一覧（読み順）。 */
    fun night1Choices(): List<Boss> = choicesFrom { it.night1 }

    /** 2日目に選べる夜ボス一覧（読み順）。 */
    fun night2Choices(): List<Boss> = choicesFrom { it.night2 }

    private fun choicesFrom(select: (Nightlord) -> Set<String>): List<Boss> {
        val names = LinkedHashSet<String>()
        for (lord in nightlords) {
            for (b in select(lord)) if (b != WILDCARD) names += b
        }
        return names.map { boss(it) }.sortedBy { it.reading }
    }

    /**
     * 入力された夜ボスから3日目のボス候補を返す。
     *  - 1日目だけ／2日目だけ → その条件を満たす夜の王
     *  - 両方            → 両方を満たす夜の王（絞り込み）
     */
    fun candidates(night1: String?, night2: String?): List<String> {
        if (night1.isNullOrEmpty() && night2.isNullOrEmpty()) return emptyList()
        return nightlords.filter { lord ->
            (night1.isNullOrEmpty() || lord.matchesNight1(night1)) &&
                (night2.isNullOrEmpty() || lord.matchesNight2(night2))
        }.map { it.name }
    }
}
