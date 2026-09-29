package io.github.sbshrey.tambola.game.updates

import org.junit.Assert.*
import org.junit.Test

class UpdateFeedTest {
    private fun asset(version: Int = 41, digest: String = "sha256:" + "a".repeat(64), bytes: Long = 30_000_000,
        url: String = "https://github.com/sbshrey/tambola-caller/releases/download/test/tambola-beta-v$version.apk") =
        """{"name":"tambola-beta-v$version.apk","browser_download_url":"$url","size":$bytes,"digest":"$digest"}"""
    private fun feed(asset: String, draft: Boolean = false) = """[{"draft":$draft,"assets":[$asset]}]"""
    @Test fun selectsHighestOptedInVersion() {
        val result = UpdateFeed.latest(feed(listOf(asset(41), asset(43), asset(42)).joinToString(",")), 40)
        assertEquals(43, result!!.version)
    }
    @Test fun ignoresCurrentOldAndDraftReleases() {
        assertNull(UpdateFeed.latest(feed(asset(40)), 40))
        assertNull(UpdateFeed.latest(feed(asset(39)), 40))
        assertNull(UpdateFeed.latest(feed(asset(41), true), 40))
    }
    @Test fun ignoresLegacyAssetsAndEmptyFeed() {
        assertNull(UpdateFeed.latest("[]", 40))
        assertNull(UpdateFeed.latest(feed(asset().replace("tambola-beta-v41.apk", "other.apk")), 40))
    }
    @Test fun rejectsMissingOrInvalidDigest() {
        listOf("", "md5:" + "a".repeat(32), "sha256:" + "z".repeat(64)).forEach { invalid { UpdateFeed.latest(feed(asset(digest = it)), 40) } }
    }
    @Test fun rejectsEmptyAndOversizedDownloads() {
        listOf(0L, -1L, UpdateFeed.MAX_APK_BYTES + 1).forEach { invalid { UpdateFeed.latest(feed(asset(bytes = it)), 40) } }
    }
    @Test fun rejectsOtherRepositoriesAndInsecureOrigins() {
        val root = "https://github.com/sbshrey/tambola-caller/releases/download/test/tambola-beta-v41.apk"
        listOf(root.replace("https:", "http:"), root.replace("github.com", "evil.example"),
            root.replace("sbshrey", "other"), root + "?token=secret", root + "#fragment",
            root.replace("github.com", "name@github.com")).forEach { invalid { UpdateFeed.latest(feed(asset(url = it)), 40) } }
    }
    @Test fun rejectsMalformedFeed() { invalid { UpdateFeed.latest("{}", 40) } }
    @Test fun onlyMatchingNewerCompatibleSignedApkIsAccepted() {
        validateUpdateIdentity("beta", 41, 26, setOf("trusted"), "beta", 40, 30, setOf("trusted"), 41)
        invalid { validateUpdateIdentity("other", 41, 26, setOf("trusted"), "beta", 40, 30, setOf("trusted"), 41) }
        invalid { validateUpdateIdentity("beta", 40, 26, setOf("trusted"), "beta", 40, 30, setOf("trusted"), 41) }
        invalid { validateUpdateIdentity("beta", 42, 26, setOf("trusted"), "beta", 40, 30, setOf("trusted"), 41) }
        invalid { validateUpdateIdentity("beta", 41, 31, setOf("trusted"), "beta", 40, 30, setOf("trusted"), 41) }
        invalid { validateUpdateIdentity("beta", 41, 26, setOf("attacker"), "beta", 40, 30, setOf("trusted"), 41) }
        invalid { validateUpdateIdentity("beta", 41, 26, emptySet(), "beta", 40, 30, emptySet(), 41) }
    }
    private fun invalid(action: () -> Unit) { try { action(); fail("Accepted invalid update") } catch (_: IllegalArgumentException) {} }
}
