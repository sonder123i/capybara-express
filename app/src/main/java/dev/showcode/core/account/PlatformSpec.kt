package dev.showcode.core.account

/**
 * 三家平台的登录元数据与「什么算登录成功」的判据。
 *
 * 这一层刻意不碰 Android：登录页是 WebView，但判据只是几条 Cookie key，
 * 所以它可以在 JVM 单测里被钉死，而不是只能靠真机点一遍。
 *
 * ⚠️ 判据的 key 来自参考实现（Halo0sama/ExpressAssistant）的真机记录：
 * 京东 `pt_key`+`pt_pin`、淘宝 `cookie2`+(`unb`|`cookie1`)、拼多多 `PDDAccessToken`+`pdd_user_id`。
 * 这些 key 随平台改版会变，所以登录页失败时先查这里，而不是先怀疑 WebView。
 */
enum class Platform(
    val id: String,
    val label: String,
    val loginUrl: String,
    /** 登录成功后从哪个地址读整条 Cookie；也是 document-start 注入的主域。 */
    val cookieHost: String,
) {
    PDD(
        id = "pdd",
        label = "拼多多",
        loginUrl = "https://mobile.yangkeduo.com/login.html",
        cookieHost = "https://mobile.yangkeduo.com",
    ),
    TB(
        id = "tb",
        label = "淘宝",
        loginUrl = "https://login.m.taobao.com/login.htm",
        cookieHost = "https://h5.m.taobao.com",
    ),
    JD(
        id = "jd",
        label = "京东",
        loginUrl = "https://plogin.m.jd.com/login/login?appid=300" +
            "&returnurl=https%3A%2F%2Fhome.m.jd.com%2FmyJd%2Fhome.action",
        cookieHost = "https://www.jd.com",
    );

    companion object {
        fun of(id: String?): Platform? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
    }
}

/**
 * 一条 Cookie header 的解析结果。
 *
 * 值里可能含 `=`（token 常见），所以 split 必须 limit=2；空 key 直接丢，
 * 否则 `parse(";x=1")` 会造出一个空名 cookie，登录判据就会把它当成「有东西」。
 */
data class Session(val values: Map<String, String>) {

    operator fun get(name: String): String? = values[name]?.takeIf { it.isNotBlank() }

    fun has(platform: Platform): Boolean = when (platform) {
        Platform.PDD -> get("PDDAccessToken") != null &&
            (get("pdd_user_id") != null || get("pdduid") != null)

        Platform.TB -> get("cookie2") != null &&
            (get("unb") != null || get("cookie1") != null)

        Platform.JD -> get("pt_key") != null && get("pt_pin") != null
    }

    companion object {
        fun parse(header: String?): Session {
            val map = LinkedHashMap<String, String>()
            header.orEmpty().split(';').forEach { part ->
                val pieces = part.trim().split('=', limit = 2)
                if (pieces.size == 2 && pieces[0].isNotBlank()) {
                    map[pieces[0]] = pieces[1]
                }
            }
            return Session(map)
        }
    }
}
