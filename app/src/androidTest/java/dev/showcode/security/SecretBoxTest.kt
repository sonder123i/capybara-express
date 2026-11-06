package dev.showcode.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Keystore 这条只能设备上验：JVM 里没有 AndroidKeyStore。
 * 两个判据：往返一致、密文里不出现明文、篡改后返回 null 而不是崩。
 */
@RunWith(AndroidJUnit4::class)
class SecretBoxTest {

    @Test
    fun roundTripAndNoPlaintextInCiphertext() {
        val plain = "PDDAccessToken=abc123; pdd_user_id=456"
        val sealed = SecretBox.encrypt(plain)
        assertTrue(sealed.contains(":"))
        assertFalse(sealed.contains("PDDAccessToken"))
        assertFalse(sealed.contains("pdd_user_id"))
        assertEquals(plain, SecretBox.decrypt(sealed))
    }

    @Test
    fun tamperedCiphertextReturnsNull() {
        val sealed = SecretBox.encrypt("hello")
        assertNull(SecretBox.decrypt(sealed + "AAAA"))
        assertNull(SecretBox.decrypt("not-a-sealed-secret"))
    }
}
