package com.therealsylva.roaches.data.local

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class EggsAccess(val enabled: Boolean, val matureContentUnlocked: Boolean)

internal object EggsGate {
    private const val ACCESS_WORD = "dracula"
    private val timeFormat = DateTimeFormatter.ofPattern("HHmm", Locale.US)

    fun accepts(input: String, now: LocalTime = LocalTime.now()): Boolean {
        val candidate = input.trim()
        return sequenceOf(now, now.minusMinutes(1)).any { time ->
            candidate == ACCESS_WORD + time.format(timeFormat)
        }
    }

    fun accessFor(input: String, now: LocalTime = LocalTime.now()): EggsAccess = EggsAccess(
        enabled = true,
        matureContentUnlocked = accepts(input, now),
    )
}
