package dev.showcode.core.account

/** 绑定账号的状态。`BROKEN` 专指「密文解不开」，和「登录过期」不是同一句话。 */
enum class AccountState { ACTIVE, EXPIRED, BROKEN }

/**
 * 一条平台账号绑定。
 *
 * [secret] 是 [dev.showcode.security.SecretBox] 产出的密文，不是明文 Cookie。
 * 这一层不负责解密；需要 Cookie 的调用方拿 [secret] 去解，解不开就把状态置 BROKEN。
 */
data class BoundAccount(
    /** v1 一平台一条，id 直接用平台 id；以后多账号再换 UUID。 */
    val id: String,
    val platform: Platform,
    val label: String,
    val secret: String,
    val enabled: Boolean = true,
    val state: AccountState = AccountState.ACTIVE,
    val createdAt: Long,
    val lastSyncAt: Long? = null,
    val lastSyncResult: String? = null,
)
