package dev.showcode.core

/**
 * 系统「文本选择菜单」送进来的那段文本，够不够格开一张手动导入。
 *
 * 判据只有一条：**非空白**。这一条和 [ClipboardIntake.autoPrefill] 的保守判据是刻意相反的，
 * 理由和 [ClipboardIntake.explicitPaste] 一样——用户先在别的 App 里长按选中一段，
 * 再从系统菜单里点了「卡皮巴拉快递助手」，这两下都在表达意图。这时候还替他判断「像不像取件」，
 * 就是把「不打扰」做成了「不响应」。
 *
 * 也正因为判据这么松，它**只能**接显式入口：本函数不读剪贴板、不接通知、不接短信。
 * 那三条通道各自的取舍不一样（自动那条要保守，通知那条要过滤来源），
 * 混进来会互相松动。
 */
object TextIntake {

    /**
     * 收下这段文本；空白返回 null。
     *
     * 收 null 时**什么都不发生**：不导航、不弹「没读到」。用户选了一段空白本身没有信息量，
     * 解释它只是噪音。
     *
     * 原文只修剪首尾空白，中间一个字都不动——这段要回显给他核对，
     * 任何改写都会让他对不上自己刚才选的那一句。
     */
    fun accept(raw: CharSequence?): String? {
        val text = raw?.toString()?.trim().orEmpty()
        return text.ifEmpty { null }
    }
}
