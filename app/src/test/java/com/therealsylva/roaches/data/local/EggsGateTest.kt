package com.therealsylva.roaches.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime

class EggsGateTest {
    @Test
    fun acceptsCurrentAndPreviousMinuteInTwentyFourHourTime() {
        val now = LocalTime.of(14, 27)

        assertThat(EggsGate.accepts("dracula1427", now)).isTrue()
        assertThat(EggsGate.accepts("dracula1426", now)).isTrue()
        assertThat(EggsGate.accepts("dracula1425", now)).isFalse()
        assertThat(EggsGate.accepts("dracula0227", now)).isFalse()
        assertThat(EggsGate.accepts("dracula0227", LocalTime.of(2, 27))).isTrue()
    }

    @Test
    fun previousMinuteGraceWrapsAcrossMidnight() {
        assertThat(EggsGate.accepts("dracula0000", LocalTime.MIDNIGHT)).isTrue()
        assertThat(EggsGate.accepts("dracula2359", LocalTime.MIDNIGHT)).isTrue()
    }

    @Test
    fun wrongKeyEnablesOnlyTheDecoyState() {
        val now = LocalTime.of(14, 27)
        val access = EggsGate.accessFor("wrong", now)
        val unlocked = EggsGate.accessFor("dracula1427", now)

        assertThat(access.enabled).isTrue()
        assertThat(access.matureContentUnlocked).isFalse()
        assertThat(unlocked.enabled).isTrue()
        assertThat(unlocked.matureContentUnlocked).isTrue()
    }
}
