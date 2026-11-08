package dev.showcode.ui.identity

import androidx.compose.runtime.saveable.SaverScope
import dev.showcode.data.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `InstalledApp` 的保存器。
 *
 * 存在的理由是一条**必现崩溃**：`IdentityEditScreen` 原来直接
 * `rememberSaveable { mutableStateOf<InstalledApp?>(null) }`，而 `InstalledApp`
 * 既不是 `Parcelable` 也不是 `Serializable`。首帧值是 null 所以注册时能过，
 * 用户选了 App 再转屏，`performSave` 里那句 `check(...)` 就抛 `IllegalStateException`。
 *
 * 这里测的是「存得下、恢复得回」，也就是崩溃的根因；不是测 Compose 自己的保存机制。
 */
class InstalledAppSaverTest {

    /** `SaverScope` 只用来判断能不能存，这两个用例不依赖它的内容。 */
    private val scope = SaverScope { true }

    /** 存一次，返回 `List<Any>` 形状的原始值（`Saver.save` 的静态类型是 `Any?`）。 */
    private fun save(value: InstalledApp?): List<Any> {
        val raw = with(InstalledAppSaver) { scope.save(value) }
        @Suppress("UNCHECKED_CAST")
        return raw as List<Any>
    }

    @Test
    fun `非空值能存成两个字符串`() {
        val app = InstalledApp(packageName = "com.cainiao.wireless", label = "菜鸟")
        assertEquals(listOf("com.cainiao.wireless", "菜鸟"), save(app))
    }

    @Test
    fun `非空值能原样恢复`() {
        val app = InstalledApp(packageName = "com.taobao.taobao", label = "淘宝")
        val restored = InstalledAppSaver.restore(save(app))
        assertEquals(app, restored)
    }

    /**
     * 「没选 App」这个初始态在转屏后仍然是「没选」。
     *
     * `listSaver` 对 null 值**不调 save**，直接存 null。
     * 恢复侧只要拿到的不是两个字段，就必须还原成 null——
     * 否则编辑屏会显示「已选：」，而其实什么都没选。
     */
    @Test
    fun `null 一路保持 null`() {
        assertNull(with(InstalledAppSaver) { scope.save(null) })
        assertNull(InstalledAppSaver.restore(emptyList<String>()))
    }

    /**
     * 存下去的东西必须是**框架能放进 Bundle 的类型**。
     *
     * 这一条是这次崩溃的直接判据：原来存的是 `InstalledApp` 本身，
     * `requireCanBeSaved` 会在保存那一刻拒绝它。现在存的是 `List<String>`。
     */
    @Test
    fun `存下去的元素都是 String`() {
        val saved = save(InstalledApp(packageName = "com.xingin.xhs", label = "小红书"))
        assertTrue(saved.all { it is String })
    }
}
