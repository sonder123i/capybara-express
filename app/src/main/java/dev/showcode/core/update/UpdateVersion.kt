package dev.showcode.core.update

/**
 * 版本号的比较规则。
 *
 * 发布标签是 `v0.3.0` 这种形状；也接受没有 `v` 的 `0.3.0` 和 `0.3.0-beta.1`。
 * 只做一件小事：判断「服务器上的这个版本，是不是比我这台机器上的新」。
 * 解析不了的字符串一律算「不更新」——宁可不提示，也不要拿一个看不懂的标签去催用户。
 */
data class AppVersion(val numbers: List<Int>, val prerelease: String?)

fun parseVersion(raw: String): AppVersion? {
    val text = raw.trim().removePrefix("v").removePrefix("V")
    val match = Regex("^(\\d+(?:\\.\\d+)*)(?:[-+](.+))?$").find(text) ?: return null
    val parts = match.groupValues[1].split('.')
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    val pre = match.groupValues[2].trim().takeIf { it.isNotEmpty() }
    return AppVersion(numbers, pre)
}

/**
 * >0 = a 更新；<0 = b 更新；0 = 相同或无法比较。
 *
 * 数字段逐位比较，缺的位按 0 算；数字相同后，没有 prerelease 的那一个更新
 * （`1.0.0` > `1.0.0-beta.1`）。
 */
fun compareVersions(a: String, b: String): Int {
    val va = parseVersion(a) ?: return 0
    val vb = parseVersion(b) ?: return 0
    val size = maxOf(va.numbers.size, vb.numbers.size)
    for (i in 0 until size) {
        val x = va.numbers.getOrElse(i) { 0 }
        val y = vb.numbers.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    val pa = va.prerelease
    val pb = vb.prerelease
    return when {
        pa == null && pb == null -> 0
        pa == null -> 1
        pb == null -> -1
        else -> comparePrerelease(pa, pb)
    }
}

fun isNewerVersion(latest: String, current: String): Boolean =
    compareVersions(latest, current) > 0

/** prerelease 段按点拆开，数字段按数值比；数字段比文字段小（semver 的约定）。 */
private fun comparePrerelease(a: String, b: String): Int {
    val pa = a.split('.')
    val pb = b.split('.')
    for (i in 0 until minOf(pa.size, pb.size)) {
        val x = pa[i]
        val y = pb[i]
        val nx = x.toIntOrNull()
        val ny = y.toIntOrNull()
        val cmp = when {
            nx != null && ny != null -> nx.compareTo(ny)
            nx != null -> -1
            ny != null -> 1
            else -> x.compareTo(y)
        }
        if (cmp != 0) return cmp
    }
    return pa.size.compareTo(pb.size)
}
