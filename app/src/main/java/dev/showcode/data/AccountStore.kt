package dev.showcode.data

import dev.showcode.core.Slog
import dev.showcode.core.account.AccountState
import dev.showcode.core.account.BoundAccount
import dev.showcode.core.account.Platform
import dev.showcode.security.SecretBox

/**
 * 账号绑定的唯一写入口。
 *
 * 它只做三件事：把 Cookie 加密后落库、把启停/状态改掉、把密文解开交还调用方。
 * 它**不做**同步、不碰 WebView、不判断登录是否有效——那些属于登录页与 SyncEngine。
 */
class AccountStore(private val db: ShowCodeDb) {

    /**
     * 一次绑定的结局。加密/落库都可能失败，**不能**让异常穿过登录页的 WebView 回调：
     * 那条线程上抛出去就是整个 App 崩掉。
     */
    sealed interface BindResult {
        data class Ok(val account: BoundAccount) : BindResult

        /** 没写进任何东西：既有账号、既有状态都原样保留，用户可以原地重试。 */
        data object Failed : BindResult
    }

    fun list(): List<BoundAccount> = db.accounts()

    fun account(platform: Platform): BoundAccount? = list().firstOrNull { it.platform == platform }

    /**
     * 绑定或更新一个平台账号。v1 一平台一条：再次登录会覆盖密文，
     * 但保住 `createdAt` 与用户关掉的启用开关——重新登录不该把「我暂时不用它」冲回「开」。
     */
    fun bind(platform: Platform, label: String?, cookie: String): BindResult {
        val existing = account(platform)
        val now = System.currentTimeMillis()
        /* Keystore 被系统重置、key 失效时 encrypt 会抛。先在内存里做完整条账号，
           再一次性写库：失败时库里连半行都不会出现。 */
        val secret = try {
            SecretBox.encrypt(cookie)
        } catch (t: Exception) {
            Slog.w(TAG, t) { "encrypt failed" }
            return BindResult.Failed
        }
        val account = BoundAccount(
            id = platform.id,
            platform = platform,
            label = label?.takeIf { it.isNotBlank() } ?: platform.label,
            secret = secret,
            enabled = existing?.enabled ?: true,
            state = AccountState.ACTIVE,
            createdAt = existing?.createdAt ?: now,
            lastSyncAt = existing?.lastSyncAt,
            lastSyncResult = null,
        )
        return try {
            db.upsertAccount(account)
            BindResult.Ok(account)
        } catch (t: Exception) {
            Slog.w(TAG, t) { "upsert failed" }
            BindResult.Failed
        }
    }

    /** 解不开就返回 null；调用方把账号置 BROKEN，而不是在这里抛。 */
    fun cookieOf(account: BoundAccount): String? = SecretBox.decrypt(account.secret)

    fun setEnabled(account: BoundAccount, enabled: Boolean) {
        db.updateAccountEnabled(account.id, enabled)
    }

    fun mark(account: BoundAccount, state: AccountState, result: String?, at: Long? = account.lastSyncAt) {
        db.updateAccountState(account.id, state, at, result)
    }

    fun remove(account: BoundAccount) {
        db.deleteAccount(account.id)
    }

    private companion object {
        const val TAG = "AccountStore"
    }
}
