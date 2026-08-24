package com.therealsylva.roaches.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun releaseVersionComparisonHandlesTagsAndPatchVersions() {
        assertThat(isNewerVersion("0.2.8", "0.2.7")).isTrue()
        assertThat(isNewerVersion("v1.0.0", "0.9.9")).isTrue()
        assertThat(isNewerVersion("0.2.8", "0.2.8")).isFalse()
        assertThat(isNewerVersion("0.2.7", "0.2.8")).isFalse()
    }

    @Test
    fun releaseDigestAcceptsOnlySha256Values() {
        val digest = "a".repeat(64)

        assertThat(normalizeReleaseDigest("sha256:$digest")).isEqualTo(digest)
        assertThat(normalizeReleaseDigest(digest.uppercase())).isEqualTo(digest)
        assertThat(normalizeReleaseDigest("sha256:not-a-digest")).isNull()
        assertThat(normalizeReleaseDigest(null)).isNull()
    }
}
