package dev.showcode.widget

import android.content.Intent
import dev.showcode.core.TextIntake
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 外部入口 → App 内路由。走这条路的有桌面组件、也有巡检提醒那条通知。
 * 用 StateFlow 而不是在 Activity 里直读 intent：MainActivity 是 singleTask，
 * 外部点击可能走 onNewIntent，两条路都要能落到同一个导航状态上。
 */
object WidgetNav {

    const val EXTRA_ROUTE = "sc_route"
    const val EXTRA_POINT_KEY = "sc_point_key"

    /** 划词入口带进来的那段原文。只预填，不落库——落库仍然要用户按「保存」那一下。 */
    const val EXTRA_TEXT = "sc_text"

    const val ROUTE_HOME = "home"
    const val ROUTE_ONSITE = "onsite"
    const val ROUTE_DEMO = "demo"
    const val ROUTE_UNRECOGNIZED = "unrecognized"

    /** 系统「文本选择菜单」：长按选中一段 → 菜单里点「卡皮巴拉快递助手」→ 落手动导入，带着原文。 */
    const val ROUTE_MANUAL = "manual"

    /** 身份码那一格：预置的「去取」住在那儿，巡检提醒点开来就该落在这里。 */
    const val ROUTE_IDENTITY = "identity"

    val request = MutableStateFlow<Intent?>(null)

    fun routeOf(intent: Intent?): String? = intent?.getStringExtra(EXTRA_ROUTE)

    fun pointKeyOf(intent: Intent?): String? = intent?.getStringExtra(EXTRA_POINT_KEY)

    fun textOf(intent: Intent?): String? = intent?.getStringExtra(EXTRA_TEXT)

    /**
     * 把系统送进来的意图翻成 App 内的深链。
     *
     * 只认 [Intent.ACTION_PROCESS_TEXT]（长按选词 → 菜单里那一格「卡皮巴拉快递助手」）。
     * 别的意图**原样返回**：组件、巡检提醒那几条深链各有各的 extra，
     * 在这里顺手改写一次，将来加一条新的就会被无声吃掉。
     *
     * 拿不到文本时也原样返回。导航那一侧再挡一次空，是因为这条意图可能从冷启动来，
     * 那时 `routeOf` 仍然是 null，落回首页是唯一安全的默认。
     */
    fun intakeOf(intent: Intent?): Intent? {
        if (intent?.action != Intent.ACTION_PROCESS_TEXT) return intent
        val text = TextIntake.accept(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
            ?: return intent
        return Intent(intent)
            .putExtra(EXTRA_ROUTE, ROUTE_MANUAL)
            .putExtra(EXTRA_TEXT, text)
    }
}
