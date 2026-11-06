package dev.showcode.core

import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.MessageDomain
import dev.showcode.core.parse.MessageDomains
import dev.showcode.core.parse.ParsedParcel
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.UnrecognizedReason
import java.security.MessageDigest
import java.text.Normalizer

/**
 * 入库裁决——整条链路上唯一决定「这条消息该不该动库」的地方。
 *
 * 为什么它是纯函数：批 1 把裁决写在 `ParcelRepository.upsert` 和 `recordUnrecognized` 里，
 * 而 Repository 是 Android 类（Context + ContentResolver），一行都测不到。于是
 * 「已取的被刷新复活」「忽略过的又回来」这两个 bug 能在仓库里活很久——不是没人看代码，
 * 是这一层从来没有测试（`core/` 有 19 个测试文件，`data/` 一个都没有）。
 * 判断挪到这里之后，Repository 只剩取数和执行。
 */

/** 一条消息的稳定身份。 */
fun messageFingerprint(channel: Source, sender: String, body: String): String {
    val material = "${channel.name}\u0000${sender.trim()}\u0000${normalizeBody(body)}"
    return sha256Hex(material)
}

/** 同一条正文在 unrecognized 表里的身份。不含发件人：同一句话由号码和 App 各报一次是常态。 */
fun bodyFingerprint(body: String): String = sha256Hex(normalizeBody(body))

/**
 * NFKC 折全角/半角，再把空白压成单空格。
 * 归一化只到这里为止——正文是要回显给人看的原文，任何改写都会让用户对不上他手机里那条短信。
 */
private fun normalizeBody(body: String): String =
    Normalizer.normalize(body, Normalizer.Form.NFKC).replace(WhitespaceRun, " ").trim()

private val WhitespaceRun = Regex("\\s+")

private fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    val out = StringBuilder(digest.size * 2)
    for (b in digest) {
        out.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
    }
    return out.toString()
}

private const val HEX = "0123456789abcdef"

/**
 * 库里已存在的、和这条消息撞码撞点的行。
 *
 * **任意 picked 状态都要带回来。** 只看未取行（旧版 `findUnpicked` 的 `AND picked = 0`）
 * 正是「已取的被刷新复活」的根因：已取行对去重不可见，于是重放的旧短信插出一条新卡。
 */
data class ExistingParcel(
    val id: Long,
    val picked: Boolean,
    val pickedAt: Long?,
    val receivedAt: Long,
    /** 库里这一行的取件码 / 点 / 运单号，跨通道匹配时用。 */
    val code: String = "",
    val pointKey: String = "",
    val trackingNo: String? = null,
    val codeKind: CodeKind = CodeKind.PICKUP,
)

/** 用户在这条未识别消息上的表态。 */
enum class UnrecognizedState { NEW, DISMISSED, RESOLVED }

/**
 * 整条消息级的跳过原因。
 *
 * `MUTED_SENDER` 与 `NOTIFY_APP_OFF` 是两套语义，故意不合并：前者是用户对**一条没认出来的
 * 消息**的表态（可在收容页反悔），后者是对**一个来源**的配置（在设置页里改）。
 * 合并成一档，那一屏的「恢复」就会去动它根本不该动的东西。
 */
enum class MessageSkip { NONE, ALREADY_SEEN, MUTED_SENDER, NOTIFY_APP_OFF }

/** 对一件解析成功的包裹，库里该怎么动。 */
sealed interface ParcelAction {
    val parsed: ParsedParcel
    val pointKey: String

    data class Insert(override val parsed: ParsedParcel, override val pointKey: String) : ParcelAction

    /** 同一件的另一路报道（短信 + 通知双报、驿站二次提醒）：更新已有行，绝不碰 picked。 */
    data class Update(val id: Long, override val parsed: ParsedParcel, override val pointKey: String) : ParcelAction

    /** 这条消息说的件已经被取走了：一个字都不动。 */
    data class AlreadyPicked(val id: Long, override val parsed: ParsedParcel, override val pointKey: String) : ParcelAction
}

/** 对一条没认出来的消息，库里该怎么动。 */
sealed interface UnrecognizedAction {
    data class Insert(
        val sender: String,
        val body: String,
        val bodyKey: String,
        val reason: UnrecognizedReason,
        val domain: MessageDomain,
        val receivedAt: Long,
    ) : UnrecognizedAction

    /** 库里已经有这条正文（在看、已忽略、或已手动提取过）：不再重复插。 */
    data object Suppressed : UnrecognizedAction
}

data class IngestPlan(
    val skip: MessageSkip,
    val parcels: List<ParcelAction>,
    val unrecognized: UnrecognizedAction?,
    val fingerprint: String,
) {
    /**
     * 只给「真的写进了 parcel 表」的消息记账。
     *
     * ⚠️ 未识别**不记账**，这是用户自定义解析规则的前提。记了账，下一次刷新会在解析之前
     * 就被 [MessageSkip.ALREADY_SEEN] 整条跳过（见 [planIngest] 第一行），于是用户后来
     * 补的那条规则永远看不到这条消息——失败列表成了一条死路。
     * 不记账也不会重复插行：未识别自己按 `body_fp` 去重（库里已有这条正文即 Suppressed），
     * 代价只是每次刷新多跑几趟正则。
     *
     * 反过来，一旦某条消息被记了账，它就再也拿不到第二次机会，所以这条判据要往保守的一侧站。
     */
    val worthLedgering: Boolean
        get() = parcels.any { it is ParcelAction.Insert || it is ParcelAction.Update }
}

/**
 * 同一条事件经短信与通知各报一次时，两个时间戳的允许偏差。
 * 超出这个偏差就不该再当成同一条消息——那是「码被复用」和「旧消息重放」的分界线。
 */
private const val SAME_MESSAGE_CLOCK_SKEW_MS = 5 * 60 * 1000L

/**
 * 只有「后五位」可用时，两条报道要落在同一个时间窗里才算同一件。
 * 运单号是硬身份，不需要时间窗；后五位只是展示用的弱身份，必须有这道闸，
 * 否则同一点上相隔半年的两件末五位相同会被并成一件。
 */
private const val SAME_SUFFIX_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

/**
 * 一条消息进来，系统该做什么。全部判断在这，输入是已经取好的事实。
 *
 * 两道防线，缺一不可，因为它们各挡一类重复：
 *
 * 1. **[MessageSkip.ALREADY_SEEN]** —— 同一条消息被重放（每次刷新都会发生）。
 * 2. **[ParcelAction.AlreadyPicked]** —— 不同的消息报道同一件已取走的件。
 *    账本挡不住这一类：通知和短信指纹不同，驿站二次提醒更是全新的一条。
 *
 * 第 2 道判据故意往保守侧站：**拿不准就当成新件**。
 * 误插一张卡，用户点一下「已取」就纠正了；误吞一个真码，是他站在柜机前才发现。
 */
fun planIngest(
    message: IncomingMessage,
    outcome: ParseOutcome,
    fingerprint: String,
    alreadySeen: Boolean = false,
    senderMuted: Boolean = false,
    appMuted: Boolean = false,
    existingParcels: Map<Pair<String, String>, ExistingParcel> = emptyMap(),
    /** 同码同点之外的候选（同运单号、同点后五位）。用来接上短信行与账号行。 */
    candidates: List<ExistingParcel> = emptyList(),
    unrecognizedState: UnrecognizedState? = null,
    domain: MessageDomain = MessageDomains.classify(message.body),
): IngestPlan {
    // 消息级跳过先于解析结果：账本命中时连「它到底认没认出来」都不该再判断一次。
    if (alreadySeen) return IngestPlan(MessageSkip.ALREADY_SEEN, emptyList(), null, fingerprint)
    // 来源级的配置排在发件人级的表态之前：用户关掉了整个 App，那条消息连有没有被
    // 表态过都不必再查——否则收容页那句「恢复」会指向一条本来就不打算收的消息。
    if (appMuted) return IngestPlan(MessageSkip.NOTIFY_APP_OFF, emptyList(), null, fingerprint)
    if (senderMuted) return IngestPlan(MessageSkip.MUTED_SENDER, emptyList(), null, fingerprint)
    return when (outcome) {
        ParseOutcome.Ignored -> IngestPlan(MessageSkip.NONE, emptyList(), null, fingerprint)
        is ParseOutcome.Parsed -> IngestPlan(
            skip = MessageSkip.NONE,
            parcels = outcome.parcels.map { parcelAction(it, message, existingParcels, candidates) },
            unrecognized = null,
            fingerprint = fingerprint,
        )
        is ParseOutcome.Rejected -> IngestPlan(
            skip = MessageSkip.NONE,
            parcels = emptyList(),
            unrecognized = if (unrecognizedState == null) {
                UnrecognizedAction.Insert(
                    sender = message.sender,
                    body = message.body,
                    bodyKey = bodyFingerprint(message.body),
                    reason = outcome.reason,
                    domain = domain,
                    receivedAt = message.receivedAt,
                )
            } else {
                UnrecognizedAction.Suppressed
            },
            fingerprint = fingerprint,
        )
    }
}

/** 命中的是哪一类身份：完整运单号是硬身份，码/后五位是弱身份。 */
private data class FoundParcel(val row: ExistingParcel, val byTracking: Boolean)

private fun parcelAction(
    parsed: ParsedParcel,
    message: IncomingMessage,
    existingParcels: Map<Pair<String, String>, ExistingParcel>,
    candidates: List<ExistingParcel>,
): ParcelAction {
    val key = pointKey(parsed.pointName)
    val exact = existingParcels[parsed.code to key]
    val match = exact?.let { FoundParcel(it, byTracking = false) }
        ?: findCrossChannel(parsed, key, candidates, message.receivedAt)
        ?: return ParcelAction.Insert(parsed, key)
    val found = match.row
    return when {
        // 未取件：同一件的另一路报道。更新它，让补上的柜号/归属生效。
        !found.picked -> ParcelAction.Update(found.id, parsed, key)
        // 运单号相同的迟到提醒还是同一件；哪怕它晚到，也不能因为码复用就再插一张。
        match.byTracking -> ParcelAction.AlreadyPicked(found.id, parsed, key)
        // picked_at 才是「什么时候被取走」。用消息时间判新旧，正是已取后又冒出新卡的根因。
        found.pickedAt == null -> ParcelAction.AlreadyPicked(found.id, parsed, key)
        message.receivedAt > found.pickedAt + SAME_MESSAGE_CLOCK_SKEW_MS ->
            ParcelAction.Insert(parsed, key)
        else -> ParcelAction.AlreadyPicked(found.id, parsed, key)
    }
}

/**
 * 跨通道认同一件：短信行有真实取件码，账号行只有「后五位」或空码，
 * 旧的 `(code, point_key)` 键接不上。
 *
 * 优先级：
 * 1. 完整运单号相等 → 同一件（硬身份，不看时间窗）；
 * 2. 只有后五位可用时，必须同点 + 30 天窗口；
 * 3. 两个运单号都非空但不同 → 绝不合并。这里锁住「两件不同运单、同点同后五位仍是两行」。
 */
private fun findCrossChannel(
    parsed: ParsedParcel,
    key: String,
    candidates: List<ExistingParcel>,
    receivedAt: Long,
): FoundParcel? {
    val incoming = parsed.trackingNo.trim()
    if (incoming.isNotEmpty()) {
        candidates.firstOrNull { row ->
            row.trackingNo?.trim().orEmpty().let { it.isNotEmpty() && it == incoming }
        }?.let { return FoundParcel(it, byTracking = true) }
    }

    val suffix = when {
        incoming.isNotEmpty() -> incoming.takeLast(5)
        // 账号行才会给「后五位」；ParsedParcel 不带 codeKind，这里按形状识别（五位纯数字）。
        looksLikeSuffix(parsed.code) -> parsed.code
        else -> ""
    }
    if (suffix.length != 5) return null
    return candidates.firstOrNull { row ->
        if (row.pointKey != key) return@firstOrNull false
        val existingTracking = row.trackingNo?.trim().orEmpty()
        // 双方都有运单号却不同，是两件不同的货：后五位相同也不能并。
        if (existingTracking.isNotEmpty() && incoming.isNotEmpty() && existingTracking != incoming) {
            return@firstOrNull false
        }
        val rowSuffix = existingTracking.takeLast(5)
        val codeHit = row.code == suffix || (rowSuffix.isNotEmpty() && rowSuffix == suffix)
        codeHit && kotlin.math.abs(receivedAt - row.receivedAt) <= SAME_SUFFIX_WINDOW_MS
    }?.let { FoundParcel(it, byTracking = false) }
}

private fun looksLikeSuffix(code: String): Boolean = code.length == 5 && code.all { it.isDigit() }

/** 发件人屏蔽键。号码归一化到数字，App 名（通知通道）折成小写。 */
fun muteKey(sender: String): String {
    val trimmed = sender.trim()
    val digits = trimmed.filter { it.isDigit() }
    return if (digits.isNotEmpty()) digits else trimmed.lowercase()
}
