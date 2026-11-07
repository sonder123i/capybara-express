package dev.showcode.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun `新版本比旧版本大`() {
        assertTrue(isNewerVersion("v0.3.0", "0.2.0"))
        assertTrue(isNewerVersion("0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("1.0", "0.9.9"))
    }

    @Test
    fun `相同版本不算更新 - 带不带 v 都一样`() {
        assertFalse(isNewerVersion("v0.2.0", "0.2.0"))
        assertEquals(0, compareVersions("0.2", "0.2.0"))
    }

    @Test
    fun `正式版比同号的预发布版新`() {
        assertTrue(isNewerVersion("1.0.0", "1.0.0-beta.1"))
        assertFalse(isNewerVersion("1.0.0-beta.2", "1.0.0"))
    }

    @Test
    fun `预发布之间按段比较`() {
        assertTrue(isNewerVersion("1.0.0-beta.2", "1.0.0-beta.1"))
        assertTrue(isNewerVersion("1.0.0-beta.10", "1.0.0-beta.9"))
    }

    @Test
    fun `看不懂的标签不提示更新`() {
        assertFalse(isNewerVersion("nightly", "0.2.0"))
        assertFalse(isNewerVersion("v0.3.0", "not-a-version"))
        assertEquals(0, compareVersions("", "0.2.0"))
    }
}
