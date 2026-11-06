package dev.showcode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 划词入口的判据。它只回答一件事：这段被显式选中的文本要不要带进手动导入。
 *
 * 重心是「只拒空」：多一道「像不像取件」的闸，就会把用户刚选中的那一句挡在门外——
 * 而他选它，就是为了不用再抄一遍。语义该由下一屏的解析器去判，不该在这一层拦住。
 */
class TextIntakeTest {

    private val pickupBlock =
        "取件出示快递单号后五位 76416\n菜鸟驿站 | 示例大学示例校区菜鸟驿站"

    @Test
    fun `选中的取件文本原样收下`() {
        assertEquals(pickupBlock, TextIntake.accept(pickupBlock))
    }

    @Test
    fun `只有一个单号也收下`() {
        // 光一串数字不像取件短信，但它是用户选完再点的菜单——意图已经表达过了。
        assertEquals("465700000076416", TextIntake.accept("465700000076416"))
    }

    @Test
    fun `首尾空白被修剪，中间的换行一个字不动`() {
        // 屏上要回显的是他刚才选中的样子，改一个空格就核对不上了。
        assertEquals(pickupBlock, TextIntake.accept("  \n$pickupBlock\t "))
    }

    @Test
    fun `空与纯空白不收`() {
        assertNull(TextIntake.accept(null))
        assertNull(TextIntake.accept(""))
        assertNull(TextIntake.accept("  \n\t "))
    }

    @Test
    fun `验证码那一类也收，拒绝它的是下一屏的解析`() {
        // 这一层不判语义：判错了用户还能改；判没了，他站在驿站门口什么也没有。
        assertEquals("您的验证码是 610920", TextIntake.accept("您的验证码是 610920"))
    }
}
