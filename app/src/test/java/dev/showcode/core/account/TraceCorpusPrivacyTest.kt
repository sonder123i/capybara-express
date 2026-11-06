package dev.showcode.core.account

import dev.showcode.core.Stage
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 回归语料的隐私自检。
 *
 * `app/src/test/resources/traces/` 是从真机轨迹**全量合成**出来的：真实运单号、订单号、
 * 手机号、地名、门店名、人名、商品标题都已替换成假值。这里只做结构性兜底——
 * 语料里只要还残留真实手机号形态、掩码手机号、10 位以上连续数字，就立刻失败阻塞。
 *
 * 真实地名/人名的一次性核对由生成脚本（不入库）负责：那份词表本身就是敏感数据，
 * 不能写进公开仓库。
 */
class TraceCorpusPrivacyTest {

    private companion object {
        const val FAKE_PHONE = "13800000000"
        val REAL_PHONE = Regex("1[3-9]\\d{9}")
        val MASKED_PHONE = Regex("1\\d{2}\\*\\*\\*\\*\\d{4}")
        val LONG_DIGITS = Regex("(?<![0-9])\\d{10,}(?![0-9])")
    }

    @Test
    fun `语料不含真实手机号或长数字`() {
        val files = corpusFiles()
        assertTrue("traces 目录为空", files.isNotEmpty())
        val violations = files.flatMap { file ->
            val body = file.readText().substringAfter("\n---\n", "")
            // 13800000000 是约定的假号，先摘掉再做检查
            val noFake = body.replace(FAKE_PHONE, "")
            buildList {
                if (REAL_PHONE.containsMatchIn(noFake)) add("${file.name}: phone")
                if (MASKED_PHONE.containsMatchIn(noFake)) add("${file.name}: masked-phone")
                if (LONG_DIGITS.containsMatchIn(noFake)) add("${file.name}: long-digits")
            }
        }
        assertTrue("语料隐私自检失败：$violations", violations.isEmpty())
    }

    @Test
    fun `语料文件结构完整`() {
        val files = corpusFiles()
        val stages = Stage.values().map { it.name }.toSet()
        files.forEach { file ->
            val text = file.readText()
            val header = text.substringBefore("\n---\n")
            val body = text.substringAfter("\n---\n", "")
            assertTrue("${file.name} 缺少正文", body.isNotBlank())
            assertTrue("${file.name} 缺少 id", header.lineSequence().any { it.startsWith("id: ") })
            assertTrue("${file.name} 缺少 platform", header.lineSequence().any { it.startsWith("platform: ") })
            val expected = header.lineSequence()
                .firstOrNull { it.startsWith("expected_stage: ") }
                ?.removePrefix("expected_stage: ")
                ?: error("${file.name} 缺少 expected_stage")
            assertTrue("${file.name} 期望阶段未知：$expected", expected in stages)
            assertTrue(
                "${file.name} 缺少 production_stage",
                header.lineSequence().any { it.startsWith("production_stage: ") },
            )
        }
    }

    private fun corpusFiles(): List<File> =
        File(javaClass.getResource("/traces/index.txt")!!.toURI())
            .parentFile!!
            .listFiles { f -> f.isFile && f.extension == "txt" && f.name != "index.txt" }
            ?.sortedBy { it.name }
            ?: emptyList()
}
