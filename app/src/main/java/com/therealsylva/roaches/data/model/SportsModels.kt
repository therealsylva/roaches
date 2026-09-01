package com.therealsylva.roaches.data.model

enum class SportType(val apiValue: String, val label: String) {
    Football("football", "Football"),
    Basketball("basketball", "Basketball"),
    Cricket("cricket", "Cricket"),
}

enum class SportsMatchStatus { Scheduled, Live, Ended }

data class SportsTeam(
    val name: String,
    val avatarUrl: String? = null,
    val score: String? = null,
)

data class SportsVideo(
    val title: String,
    val url: String,
    val durationSeconds: Long? = null,
)

data class SportsMatch(
    val id: String,
    val sport: SportType,
    val league: String,
    val home: SportsTeam,
    val away: SportsTeam,
    val startTimeMs: Long,
    val status: SportsMatchStatus,
    val playUrl: String? = null,
    val alternativeStreams: List<SportsVideo> = emptyList(),
    val replays: List<SportsVideo> = emptyList(),
) {
    val title: String get() = "${home.name} vs ${away.name}"
    val isPlayable: Boolean get() = !playUrl.isNullOrBlank() || replays.isNotEmpty()
}

data class SportsLeague(
    val name: String,
    val matches: List<SportsMatch>,
)
