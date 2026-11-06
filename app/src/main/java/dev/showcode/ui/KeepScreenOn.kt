package dev.showcode.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * 出示期间不许熄屏，离开就原样还回去。
 *
 * 消费方是三种「手机举在别人（柜机相机 / 店员）面前」的屏：现场模式、平铺总览、身份码出示。
 * 站着对格口、等柜门弹开的那十几秒里，屏幕自己灭掉是比少显示一个字更狼狈的事——
 * 用户得重新解锁，后面可能还有人排队。
 *
 * ⚠️ 由**根路由**持有一个实例（`ShowCodeRoot` 里按 route 传 enabled），而不是每个屏各调一次：
 * 换屏走的是 Crossfade，新旧两屏会同时活着 200ms，各自 `addFlags/clearFlags` 同一个 Window flag
 * 的结果是「新屏点上、旧屏在动画结束时清掉」——平铺总览会莫名其妙地熄屏，且很难复查。
 * 一个 flag 只该有一个主人。
 *
 * 只负责常亮，不负责亮度：身份码那屏还要把屏幕抬到 0.7（相机测光需要），
 * 那件事由 `IdentityShowScreen` 自己的 effect 做——夜里在楼下取件时，
 * 无差别抬亮度是拿屏幕照人眼，不该被所有出示屏继承。
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = (view.context as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
