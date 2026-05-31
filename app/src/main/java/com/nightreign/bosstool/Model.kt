package com.nightreign.bosstool

/** 検索対象になる夜ボス。reading は検索用の「よみがな」。 */
data class Boss(val name: String, val reading: String)

/**
 * ある夜（1夜 or 2夜）に出現する夜ボスの指定。
 *  - wildcard=true なら「全種類」。ただし exclude のボスは除く。
 *  - wildcard=false なら include に含まれるボスだけ。
 */
data class NightSpec(
    val wildcard: Boolean,
    val include: Set<String>,
    val exclude: Set<String>,
) {
    fun matches(boss: String): Boolean =
        if (wildcard) boss !in exclude else boss in include

    companion object {
        val EMPTY = NightSpec(false, emptySet(), emptySet())
    }
}

/**
 * 3日目のボス（夜の王）と、その王のときに出現する夜ボスの指定。
 *  - night1: 1日目（1夜）に出る夜ボス
 *  - night2: 2日目（2夜）に出る夜ボス
 */
data class Nightlord(
    val name: String,
    val night1: NightSpec,
    val night2: NightSpec,
) {
    fun matchesNight1(boss: String): Boolean = night1.matches(boss)
    fun matchesNight2(boss: String): Boolean = night2.matches(boss)
}

/**
 * 検索用に文字列を正規化する。
 *  - カタカナ → ひらがな（「ス」と「す」を同じ扱いにする）
 *  - 小書き文字 → 通常文字（「ぃ」「ィ」を「い」と同じ扱いにする）
 *  - 英字は小文字化
 * これにより「鈴」でも「す」でも「ス」でも、また「い」で「ティビア」の「ィ」にもマッチする。
 */
fun normalizeForSearch(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        var code = c.code
        // 全角カタカナ → ひらがな
        if (code in 0x30A1..0x30F6) code -= 0x60
        // 小書きひらがな → 通常ひらがな
        code = foldSmallKana(code)
        sb.append(code.toChar())
    }
    return sb.toString().lowercase()
}

private fun foldSmallKana(code: Int): Int = when (code) {
    0x3041 -> 0x3042 // ぁ→あ
    0x3043 -> 0x3044 // ぃ→い
    0x3045 -> 0x3046 // ぅ→う
    0x3047 -> 0x3048 // ぇ→え
    0x3049 -> 0x304A // ぉ→お
    0x3063 -> 0x3064 // っ→つ
    0x3083 -> 0x3084 // ゃ→や
    0x3085 -> 0x3086 // ゅ→ゆ
    0x3087 -> 0x3088 // ょ→よ
    0x308E -> 0x308F // ゎ→わ
    0x3095 -> 0x304B // ゕ→か
    0x3096 -> 0x3051 // ゖ→け
    else -> code
}

/** query が boss の表示名または読みの一部に含まれていれば true。 */
fun bossMatches(boss: Boss, query: String): Boolean {
    val q = normalizeForSearch(query.trim())
    if (q.isEmpty()) return true
    return normalizeForSearch(boss.name).contains(q) ||
        normalizeForSearch(boss.reading).contains(q)
}
