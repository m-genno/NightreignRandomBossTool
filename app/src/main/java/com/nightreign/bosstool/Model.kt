package com.nightreign.bosstool

/** 検索対象になる夜ボス。reading は検索用の「よみがな」。 */
data class Boss(val name: String, val reading: String)

/**
 * 3日目のボス（夜の王）と、その王のときに出現する夜ボスの集合。
 *  - night1: 1日目（1夜）に出る夜ボス
 *  - night2: 2日目（2夜）に出る夜ボス
 * 集合に [WILDCARD] が含まれる場合は「全種類」を意味し、どの夜ボスにもマッチする。
 */
data class Nightlord(
    val name: String,
    val night1: Set<String>,
    val night2: Set<String>,
) {
    fun matchesNight1(boss: String): Boolean = WILDCARD in night1 || boss in night1
    fun matchesNight2(boss: String): Boolean = WILDCARD in night2 || boss in night2

    companion object {
        const val WILDCARD = "*"
    }
}

/**
 * 検索用に文字列を正規化する。
 *  - カタカナ → ひらがな（「ス」と「す」を同じ扱いにする）
 *  - 英字は小文字化
 * これにより「鈴」でも「す」でも「ス」でも同じようにマッチする。
 */
fun normalizeForSearch(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        val code = c.code
        if (code in 0x30A1..0x30F6) {
            // 全角カタカナ → ひらがな
            sb.append((code - 0x60).toChar())
        } else {
            sb.append(c)
        }
    }
    return sb.toString().lowercase()
}

/** query が boss の表示名または読みの一部に含まれていれば true。 */
fun bossMatches(boss: Boss, query: String): Boolean {
    val q = normalizeForSearch(query.trim())
    if (q.isEmpty()) return true
    return normalizeForSearch(boss.name).contains(q) ||
        normalizeForSearch(boss.reading).contains(q)
}
