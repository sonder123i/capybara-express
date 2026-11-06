package dev.showcode.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 账号 Cookie 的本地加密盒。
 *
 * 为什么不用 EncryptedSharedPreferences：那要额外拖进 `androidx.security`，
 * 而这个仓库的取舍是「少依赖」。Keystore 的 AES/GCM 是系统自带能力，手写这一层
 * 只多一个文件，不多一条供应链。
 *
 * 密文格式 `base64(iv):base64(ciphertext)`，GCM 自带完整性校验：
 * 被篡改或被换了密钥都会在解密时抛，[decrypt] 返回 null 而不是崩。
 *
 * ⚠️ 换机还原 / 恢复出厂后 Keystore 里的 key 会失效。这不是数据损坏，
 * 而是「这台机器已经不认识这把钥匙了」——调用方必须把账号置 BROKEN、要求重新登录。
 */
object SecretBox {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "showcode.account.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    /** 解不开（key 失效、密文损坏、格式不对）一律返回 null，不抛。 */
    fun decrypt(sealed: String): String? = runCatching {
        val parts = sealed.split(':', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
