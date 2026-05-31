package com.nightreign.bosstool

import android.content.Context
import java.io.File

/**
 * ボスデータを外部テキストファイルから読み込むリポジトリ。
 *
 * データファイルは初回起動時に assets から
 *   端末の「Android/data/com.nightreign.bosstool/files/」配下にコピーされる。
 * 以降はそのファイルを読むので、ファイルマネージャで編集 → アプリの「再読み込み」で
 * 反映できる（再ビルド不要）。
 */
object BossRepository {
    private const val BOSSES_FILE = "bosses.txt"
    private const val LORDS_FILE = "nightlords.txt"

    var bosses: List<Boss> = emptyList()
        private set
    var nightlords: List<Nightlord> = emptyList()
        private set

    private fun dataDir(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    /** データファイルが無ければ assets からコピーして読み込む。 */
    fun ensureDataFiles(context: Context) {
        val dir = dataDir(context)
        if (!dir.exists()) dir.mkdirs()
        copyIfMissing(context, dir, BOSSES_FILE)
        copyIfMissing(context, dir, LORDS_FILE)
        reload(context)
    }

    /** assets の内容で上書きして読み込む（サンプルデータに戻す）。 */
    fun restoreDefaults(context: Context) {
        val dir = dataDir(context)
        copyFromAssets(context, dir, BOSSES_FILE)
        copyFromAssets(context, dir, LORDS_FILE)
        reload(context)
    }

    /** ファイルを読み直す。 */
    fun reload(context: Context) {
        val dir = dataDir(context)
        bosses = parseBosses(readOrEmpty(File(dir, BOSSES_FILE)))
        nightlords = parseNightlords(readOrEmpty(File(dir, LORDS_FILE)))
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

    private fun parseBosses(text: String): List<Boss> =
        cleanLines(text).mapNotNull { line ->
            val parts = line.split(",").map { it.trim() }
            val name = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val reading = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: name
            Boss(name, reading)
        }

    private fun parseNightlords(text: String): List<Nightlord> =
        cleanLines(text).mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            val name = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val n1 = parts.getOrNull(1)?.splitBosses() ?: emptySet()
            val n2 = parts.getOrNull(2)?.splitBosses() ?: emptySet()
            Nightlord(name, n1, n2)
        }

    private fun String.splitBosses(): Set<String> =
        split(";", "、", "/")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /**
     * 夜ボスの選択候補（1日目・2日目で選べるボス一覧）。
     * nightlords.txt に登場する夜ボスを集め、bosses.txt の読みを引いて返す。
     * （ファイルへの登場順を保つ）
     */
    fun nightBossChoices(): List<Boss> {
        val readingMap = bosses.associateBy({ it.name }, { it.reading })
        val names = LinkedHashSet<String>()
        for (lord in nightlords) {
            names += lord.night1
            names += lord.night2
        }
        return names.map { Boss(it, readingMap[it] ?: it) }
    }

    /**
     * 入力された夜ボスから3日目のボス候補を返す。
     *  - 1日目だけ／2日目だけ → 片方の条件で絞り込み
     *  - 両方 → 両条件を満たすものだけ（積集合で精度UP）
     */
    fun candidates(night1: String?, night2: String?): List<String> {
        if (night1.isNullOrEmpty() && night2.isNullOrEmpty()) return emptyList()
        var seq = nightlords.asSequence()
        if (!night1.isNullOrEmpty()) seq = seq.filter { night1 in it.night1 }
        if (!night2.isNullOrEmpty()) seq = seq.filter { night2 in it.night2 }
        return seq.map { it.name }.distinct().toList()
    }
}
