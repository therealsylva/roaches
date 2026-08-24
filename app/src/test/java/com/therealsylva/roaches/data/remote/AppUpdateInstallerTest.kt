package com.therealsylva.roaches.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUpdateInstallerTest {
    @Test
    fun matchingKnownSignersAreAccepted() {
        assertThat(hasConflictingSigners(setOf("release"), setOf("release"))).isFalse()
    }

    @Test
    fun differentKnownSignersAreRejected() {
        assertThat(hasConflictingSigners(setOf("installed"), setOf("archive"))).isTrue()
    }

    @Test
    fun unavailableArchiveSignerMetadataDefersToAndroidInstaller() {
        assertThat(hasConflictingSigners(setOf("release"), emptySet())).isFalse()
    }

    @Test
    fun unavailableInstalledSignerMetadataDefersToAndroidInstaller() {
        assertThat(hasConflictingSigners(emptySet(), setOf("release"))).isFalse()
    }
}
