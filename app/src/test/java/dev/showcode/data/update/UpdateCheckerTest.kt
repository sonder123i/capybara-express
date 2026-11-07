package dev.showcode.data.update

import dev.showcode.core.update.isNewerVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val sha = "9f2c1a7b6d5e4c3b2a1908f7e6d5c4b3a2910f8e7d6c5b4a39281706f5e4d3c2"

    private val giteeJson = """
        [
          {
            "tag_name": "v0.3.0",
            "name": "v0.3.0",
            "body": "新增：检查更新\n修复：现场模式",
            "prerelease": false,
            "assets": [
              {
                "name": "showcode-0.3.0.apk",
                "browser_download_url": "https://gitee.com/sonder123i/capybara-express/releases/download/v0.3.0/showcode-0.3.0.apk",
                "size": 2400000
              },
              {
                "name": "SHA256SUMS.txt",
                "browser_download_url": "https://gitee.com/sonder123i/capybara-express/releases/download/v0.3.0/SHA256SUMS.txt",
                "size": 90
              }
            ]
          },
          {
            "tag_name": "v0.3.1-beta.1",
            "name": "beta",
            "body": "",
            "prerelease": true,
            "assets": [
              {
                "name": "showcode-0.3.1-beta.1.apk",
                "browser_download_url": "https://example.com/beta.apk"
              }
            ]
          }
        ]
    """.trimIndent()

    private val githubJson = """
        [
          {
            "tag_name": "v0.3.0",
            "name": "v0.3.0",
            "body": "",
            "prerelease": false,
            "assets": [
              {
                "name": "showcode-0.3.0.apk",
                "browser_download_url": "https://github.com/sonder123i/capybara-express/releases/download/v0.3.0/showcode-0.3.0.apk",
                "size": 2400000,
                "digest": "sha256:$sha"
              }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `Gitee 与 GitHub 的 assets 形状一致 - 都能挑出 APK`() {
        val gitee = UpdateChecker.parseReleases(giteeJson)
        val github = UpdateChecker.parseReleases(githubJson)
        assertEquals("v0.3.0", gitee.first().tag)
        assertEquals("showcode-0.3.0.apk", gitee.first().apkAsset()?.name)
        assertEquals("showcode-0.3.0.apk", github.first().apkAsset()?.name)
        assertEquals("sha256:$sha", github.first().apkAsset()?.digest)
    }

    @Test
    fun `有正式版就不拿更新的预发布版`() {
        val picked = UpdateChecker.pickRelease(
            UpdateChecker.parseReleases(giteeJson),
            UpdateSource.GITEE,
        )
        assertEquals("v0.3.0", picked?.release?.tag)
        assertFalse(picked?.release?.prerelease ?: true)
    }

    @Test
    fun `没有 APK 的发行版不算更新源`() {
        val json = """
            [{"tag_name":"v0.3.0","assets":[
              {"name":"source.zip","browser_download_url":"https://example.com/source.zip"}
            ]}]
        """.trimIndent()
        assertNull(UpdateChecker.pickRelease(UpdateChecker.parseReleases(json), UpdateSource.GITEE))
    }

    @Test
    fun `空响应是还没有发布 - 不是解析失败`() {
        assertTrue(UpdateChecker.parseReleases("[]").isEmpty())
        assertTrue(UpdateChecker.parseReleases("").isEmpty())
    }

    @Test
    fun `SHA256SUMS 里取对应 APK 的那一行`() {
        val text = "0000000000000000000000000000000000000000000000000000000000000000  other.apk\n" +
            "$sha  showcode-0.3.0.apk\n"
        assertEquals(sha, UpdateChecker.parseSha256Sums(text, "showcode-0.3.0.apk"))
    }

    @Test
    fun `SHA256SUMS 没有这一行 - 返回 null，不让无校验的包进安装流程`() {
        val text = "0000000000000000000000000000000000000000000000000000000000000000  other.apk\n"
        assertNull(UpdateChecker.parseSha256Sums(text, "showcode-0.3.0.apk"))
    }

    @Test
    fun `版本比较 - 更新源给的是不是真的更新`() {
        assertTrue(isNewerVersion("v0.3.0", "0.2.0"))
        assertFalse(isNewerVersion("v0.2.0", "0.2.0"))
    }
}
