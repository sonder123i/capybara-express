package dev.showcode.core

import android.util.Log
import dev.showcode.BuildConfig

/**
 * 全 App 的日志入口。
 *
 * 规则（写清，免得下一个人又把单号拼回 release）：
 *
 * - [d] / [i] / [w]：**只在 debug 执行 lambda**。release 连字符串都不会拼。
 *   单号、取件码、点位名、商品标题、轨迹原文只能走这里。
 * - [mask]：debug 里也做遮罩——前 4 位 + 后 4 位，足够分辨是哪一单，
 *   又不至于把完整单号留在 logcat 里。手机号、姓名当前不打印；若要打印，同样中间打码。
 * - [safe] / [failure]：release 允许保留，但调用方**只准**传计数、错误码、平台标识。
 *   一旦把单号传进来，这条规则就破了，review 时应拒绝。
 *
 * 为什么不用 R8 把日志整段删掉：release 目前不开混淆（见 build.gradle.kts），
 * 门禁只能落在调用点上——所以敏感值必须永远不出现在 [safe] / [failure] 的参数里。
 */
object Slog {

    fun d(tag: String, block: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, block())
    }

    fun i(tag: String, block: () -> String) {
        if (BuildConfig.DEBUG) Log.i(tag, block())
    }

    fun w(tag: String, block: () -> String) {
        if (BuildConfig.DEBUG) Log.w(tag, block())
    }

    /** 异常只在 debug 打：`Throwable.message` 里可能带 URL/查询串。 */
    fun w(tag: String, throwable: Throwable, block: () -> String = { "failed" }) {
        if (BuildConfig.DEBUG) Log.w(tag, block(), throwable)
    }

    /** release 可见：只放计数/状态，不放任何业务标识。 */
    fun safe(tag: String, event: String) {
        Log.i(tag, event)
    }

    /** release 可见的失败摘要：code 只允许 AUTH/TRANSIENT 这类枚举，platform 是平台 id。 */
    fun failure(tag: String, code: String, platform: String) {
        Log.w(tag, "failure code=$code platform=$platform")
    }

    /** 单号/取件码/点位名的 debug 遮罩：保留首尾各 4 位，中间固定三颗星。 */
    fun mask(value: String?): String {
        val v = value.orEmpty()
        if (v.isEmpty()) return "<空>"
        if (v.length <= 8) return v.take(2) + "***"
        return v.take(4) + "***" + v.takeLast(4)
    }
}
