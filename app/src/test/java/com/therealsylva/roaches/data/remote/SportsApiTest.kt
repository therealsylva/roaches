package com.therealsylva.roaches.data.remote

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.SportType
import com.therealsylva.roaches.data.model.SportsMatchStatus
import org.json.JSONObject
import org.junit.Test

class SportsApiTest {
    @Test
    fun `parses grouped live fixtures and direct stream`() {
        val payload = JSONObject(
            """
            {
              "code": 0,
              "data": {
                "list": [{
                  "league": {"name": "Premier League"},
                  "matchList": [{
                    "id": "match-1",
                    "status": "MatchIng",
                    "startTime": 1770000000000,
                    "playPath": "https://stream.example/live/playlist.m3u8?token=fresh",
                    "team1": {"name": "Arsenal", "avatar": "https://img.example/a.png"},
                    "team2": {"name": "Chelsea", "avatar": "https://img.example/c.png"},
                    "teamMatchInfo1": {"score": 2},
                    "teamMatchInfo2": {"score": 1}
                  }]
                }]
              }
            }
            """.trimIndent(),
        )

        val leagues = SportsApi.parseLeagues(payload, SportType.Football)

        assertThat(leagues).hasSize(1)
        assertThat(leagues.single().name).isEqualTo("Premier League")
        val match = leagues.single().matches.single()
        assertThat(match.id).isEqualTo("match-1")
        assertThat(match.title).isEqualTo("Arsenal vs Chelsea")
        assertThat(match.status).isEqualTo(SportsMatchStatus.Live)
        assertThat(match.home.score).isEqualTo("2")
        assertThat(match.away.score).isEqualTo("1")
        assertThat(match.playUrl).contains("playlist.m3u8")
        assertThat(match.isPlayable).isTrue()
    }

    @Test
    fun `uses replay when ended match has no live path`() {
        val match = SportsApi.parseMatch(
            JSONObject(
                """
                {
                  "id": "match-2",
                  "status": "MatchEnded",
                  "team1": {"name": "Home"},
                  "team2": {"name": "Away"},
                  "replay": [{"title": "Full match", "path": "https://stream.example/replay.mp4", "duration": 5400}]
                }
                """.trimIndent(),
            ),
            SportType.Football,
            "Cup",
        )

        assertThat(match.status).isEqualTo(SportsMatchStatus.Ended)
        assertThat(match.replays.single().title).isEqualTo("Full match")
        assertThat(match.replays.single().durationSeconds).isEqualTo(5400L)
        assertThat(match.isPlayable).isTrue()
    }
}
