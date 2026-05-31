package com.nightreign.bosstool

import android.content.Context
import java.io.File

/**
 * ボスデータを外部テキストファイルから読み込むリポジトリ。
 *
 * データの考え方:
 *   「ある夜ボスが出たら、3日目の夜の王はこの候補のどれか」という対応表を1つ持つ。
 *   1日目でも2日目でも、見えた夜ボスから同じ表で候補を引ける。
 *   1日目・2日目の両方が分かれば、両方の候補の積集合になり絞り込める（精度UP）。
 *
 * ファイルは初回起動時に assets から
 *   端末の「Android/data/com.nightreign.bosstool/files/」配下にコピーされる。
 * 以降はそのファイルを読むので、ファイルマネージャで編集 → アプリの「再読み込み」で
 * 反映できる（再ビルド不要）。
 */
object BossRepository {
    private const val BOSSES_FILE = "bosses.txt"     // 夜ボスの読み（検索用辞書）
    private const val TABLE_FILE = "boss_table.txt"  // 夜ボス → 夜の王候補 の対応表

    /** 夜ボス名 → よみがな */
    private var readings: Map<String, String> = emptyMap()

    /** 夜ボス名 → 候補の夜の王リスト（登場順を保持） */
    private var table: LinkedHashMap<String, List<String>> = LinkedHashMap()

    /** 夜の王の表示順（対応表での初出順） */
    private var lordOrder: List<String> = emptyList()

    private fun dataDir(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    /** データファイルが無ければ assets からコピーして読み込む。 */
    fun ensureDataFiles(context: Context) {
        val dir = dataDir(context)
        if (!dir.exists()) dir.mkdirs()
        copyIfMissing(context, dir, BOSSES_FILE)
        copyIfMissing(context, dir, TABLE_FILE)
        reload(context)
    }

    /** assets の内容で上書きして読み込む（初期データに戻す）。 */
    fun restoreDefaults(context: Context) {
        val dir = dataDir(context)
        copyFromAssets(context, dir, BOSSES_FILE)
        copyFromAssets(context, dir, TABLE_FILE)
        reload(context)
    }

    /** ファイルを読み直す。 */
    fun reload(context: Context) {
        val dir = dataDir(context)
        readings = parseReadings(readOrEmpty(File(dir, BOSSES_FILE)))
        table = parseTable(readOrEmpty(File(dir, TABLE_FILE)))
        lordOrder = table.values.flatten().distinct()
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

    private fun parseTable(text: String): LinkedHashMap<String, List<String>> {
        val map = LinkedHashMap<String, List<String>>()
        for (line in cleanLines(text)) {
            val parts = line.split("|").map { it.trim() }
            val boss = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: continue
            val lords = parts.getOrNull(1)
                ?.split(";", "、", "/")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            map[boss] = lords
        }
        return map
    }

    /** 夜ボスの選択候補（1日目・2日目で選べるボス一覧）。対応表のキーから生成。 */
    fun nightBossChoices(): List<Boss> =
        table.keys.map { Boss(it, readings[it] ?: it) }

    /**
     * 入力された夜ボスから3日目のボス候補を返す。
     *  - 1日目だけ／2日目だけ → その夜ボスの候補
     *  - 両方            → 両方の候補の積集合（絞り込み）
     */
    fun candidates(night1: String?, night2: String?): List<String> {
        val sets = ArrayList<List<String>>()
        if (!night1.isNullOrEmpty()) sets += (table[night1] ?: emptyList())
        if (!night2.isNullOrEmpty()) sets += (table[night2] ?: emptyList())
        if (sets.isEmpty()) return emptyList()

        var result = sets[0]
        for (i in 1 until sets.size) {
            val s = sets[i].toHashSet()
            result = result.filter { it in s }
        }

        val orderIndex = lordOrder.withIndex().associate { (i, v) -> v to i }
        return result.distinct().sortedBy { orderIndex[it] ?: Int.MAX_VALUE }
    }
}
