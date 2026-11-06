package dev.showcode.core.account

import dev.showcode.core.Source
import dev.showcode.core.Stage
import dev.showcode.core.parse.IncomingMessage
import dev.showcode.core.parse.ParseOutcome
import dev.showcode.core.parse.PickupCodeParser
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.OffsetDateTime

/**
 * 用合成语料回归阶段判定（不是打印清单）。
 *
 * - PDD / JD：整页文本，时间戳在轨迹开头，走 [TraceParser.decideStage]；
 * - TB：库里只有拼接后的轨迹文本，时间戳在轨迹末尾，按末尾时间戳切分近似生产 `parseTraces`
 *   （生产仍走结构化轨迹）；
 * - SMS：走 `PickupCodeParser`，不经过 StageMachine。
 *
 * `tb-9017` 头部是「已签收」摘要、最新轨迹却是「已暂存请及时取件」，期望 DONE 本身存疑，
 * front matter 标了 `contested: true`，只打印不硬断言。
 * 期望值来自人工判断，不为了让测试变绿去改。
 */
class TraceCorpusAuditTest {

    @Test
    fun `语料逐条等于期望阶段`() {
        val failures = mutableListOf<String>()
        corpusFiles().forEach { file ->
            val record = readRecord(file)
            val decision = decide(record)
            println(
                "AUDIT|${record.id}|exp=${record.expected.name}|act=${decision.stage.name}|" +
                    "used=${decision.index ?: "-"}|reason=${decision.reason}|" +
                    "point=${record.parsedPoint.ifBlank { "-" }}|" +
                    "stored=${record.storedPoint.ifBlank { "-" }}|contested=${record.contested}",
            )
            if (record.contested) return@forEach
            if (decision.stage != record.expected) {
                failures += "${record.id}: expected=${record.expected.name} " +
                    "actual=${decision.stage.name} (${decision.reason})"
            }
        }
        if (failures.isNotEmpty()) {
            fail("语料阶段断言失败：\n" + failures.joinToString("\n"))
        }
    }

    /**
     * 点位名逐行对照库内值。
     *
     * 只守两条线：
     * - `pdd-9012` 的 raw 只剩「待取件」，解析不出点名，合并层保留库内点名；只有这一条允许空解析。
     * - 任何解析结果不得把两处点名后缀拼成一串（会裂卡的正是这种长串）。
     *
     * 其余行打印 `stored/parsed` 供逐行 diff；有些合成语料与库内值本来就有差异
     * （如 9004 的「菜鸟」字样），不属于本次改动，不能拿它当新回归。
     */
    @Test
    fun `点位名 - 9013 不裂卡 9012 不清空`() {
        val bad = mutableListOf<String>()
        corpusFiles().forEach { file ->
            val record = readRecord(file)
            if (record.platform !in PARSE_PLATFORMS) return@forEach
            val stored = record.storedPoint
            val parsed = record.parsedPoint
            println(
                "POINT|${record.id}|stored=${stored.ifBlank { "-" }}|" +
                    "parsed=${parsed.ifBlank { "-" }}|same=${parsed == stored}",
            )
            if (parsed.isBlank()) {
                if (stored.isNotBlank() && record.id != "pdd-9012") bad += "${record.id}: 空点名"
            } else if (POINT_SUFFIX.findAll(parsed).count() > 1) {
                bad += "${record.id}: 点名拼了多处后缀 -> $parsed"
            }
        }
        if (bad.isNotEmpty()) fail("点位名异常：\n" + bad.joinToString("\n"))
    }

    private data class CorpusRecord(
        val id: String,
        val platform: String,
        val expected: Stage,
        val body: String,
        val tracking: String,
        val storedPoint: String,
        val contested: Boolean,
        val at: Long,
        val parsedPoint: String,
    )

    private fun readRecord(file: File): CorpusRecord {
        val text = file.readText()
        val header = text.substringBefore("\n---\n")
        val body = text.substringAfter("\n---\n", "").trim()
        val meta = header.lineSequence()
            .filter { ": " in it }
            .associate { it.substringBefore(": ") to it.substringAfter(": ") }
        val platform = meta.getValue("platform")
        val at = OffsetDateTime.parse(meta.getValue("fetched_at")).toInstant().toEpochMilli()
        val tracking = meta.getValue("tracking").takeUnless { it == "-" }.orEmpty()
        val parsed = if (platform in PARSE_PLATFORMS) {
            TraceParser.parse(
                platform = Platform.valueOf(platform),
                accountId = platform.lowercase(),
                orderSn = null,
                trackingNo = tracking,
                text = body,
                at = at,
            )
        } else {
            null
        }
        return CorpusRecord(
            id = meta.getValue("id"),
            platform = platform,
            expected = Stage.valueOf(meta.getValue("expected_stage")),
            body = body,
            tracking = tracking,
            storedPoint = meta["point"].orEmpty().takeUnless { it == "-" }.orEmpty(),
            contested = meta["contested"] == "true",
            at = at,
            parsedPoint = parsed?.pointName.orEmpty(),
        )
    }

    private fun decide(record: CorpusRecord): StageMachine.StagePick = when (record.platform) {
        in PARSE_PLATFORMS -> TraceParser.decideStage(
            record.body,
            Platform.valueOf(record.platform),
        )
        "SMS" -> {
            val outcome = PickupCodeParser.parse(
                IncomingMessage(
                    sender = "示例",
                    body = record.body,
                    receivedAt = record.at,
                    channel = Source.SMS,
                ),
            )
            val parcels = (outcome as? ParseOutcome.Parsed)?.parcels.orEmpty()
            val stage = if (parcels.isNotEmpty()) Stage.AT_STATION else Stage.UNKNOWN
            StageMachine.StagePick(stage, reason = "SMS PickupCodeParser")
        }
        else -> StageMachine.StagePick(Stage.UNKNOWN, reason = "未知平台 ${record.platform}")
    }

    private fun corpusFiles(): List<File> =
        File(javaClass.getResource("/traces/index.txt")!!.toURI())
            .parentFile!!
            .listFiles { f -> f.isFile && f.extension == "txt" && f.name != "index.txt" }
            ?.sortedBy { it.name }
            ?: emptyList()

    private companion object {
        val PARSE_PLATFORMS = setOf("PDD", "TB", "JD")
        val POINT_SUFFIX = Regex("驿站|代收点|快递柜|自提柜|自提点|取件点|服务点|生活超市|便利店|超市")
    }
}
