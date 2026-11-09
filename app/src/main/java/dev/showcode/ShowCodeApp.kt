package dev.showcode

import android.app.Application
import android.content.Context
import dev.showcode.data.PickupReminder
import dev.showcode.data.ParcelRepository
import dev.showcode.data.Prefs
import dev.showcode.data.ShowCodeDb
import dev.showcode.ui.glass.GlassRuntimeGuard
import dev.showcode.widget.PickupWidgetProvider

class ShowCodeApp : Application(), coil3.SingletonImageLoader.Factory {

    lateinit var repository: ParcelRepository
        private set

    /**
     * Coil 的全局配置。用 `SingletonImageLoader.Factory` 而不是每次请求传 ImageLoader：
     * 单例才能共享磁盘缓存——每个请求各建一个的话，磁盘缓存等于没有。
     *
     * 磁盘缓存**开着**（默认 2% 可用空间，上限 250MB）：商品图同一件货会反复看到，
     * 而自写的 ThumbnailLoader 只有内存缓存，冷启动后全部要重下。
     */
    override fun newImageLoader(context: android.content.Context): coil3.ImageLoader =
        coil3.ImageLoader.Builder(context)
            .components {
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory())
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        // 先于任何一次渲染：读上一次进程的退出原因，只留证据、不关玻璃（见 GlassRuntimeGuard）。
        val prefs = Prefs(this)
        GlassRuntimeGuard.initialize(this, prefs)
        /* 启动次数在 Application 里加，不在 Composable 里加：转屏、换深色都不会重开进程，
           而「第几次启动」问的是进程，不是组合。Star 弹窗靠它决定开不开口。 */
        prefs.launchCount = prefs.launchCount + 1
        repository = ParcelRepository(this, ShowCodeDb(this))
        // 组件是用户不打开 App 也会看到的表面：数据或阈值一变它就得跟着重绘
        repository.onDataChange = { PickupWidgetProvider.updateAll(this) }
        repository.start()
        // 渠道必须早于任何一次 notify：渠道没建好就发通知，在国产 ROM 上的症状是静默丢掉。
        PickupReminder.ensureChannel(this)
        // 每次启动重排一次。挂闹钟便宜且幂等，要不要响由 fire 那边判。
        PickupReminder.schedule(this)
    }
}

val Context.app: ShowCodeApp get() = applicationContext as ShowCodeApp
