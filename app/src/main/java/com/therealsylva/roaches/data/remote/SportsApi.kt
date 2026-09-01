package com.therealsylva.roaches.data.remote

import com.therealsylva.roaches.data.model.SportType
import com.therealsylva.roaches.data.model.SportsLeague
import com.therealsylva.roaches.data.model.SportsMatch
import com.therealsylva.roaches.data.model.SportsMatchStatus
import com.therealsylva.roaches.data.model.SportsTeam
import com.therealsylva.roaches.data.model.SportsVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal class SportsApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val baseUrl: String = "https://h5-sport-api.aoneroom.com",
) {
    suspend fun matches(
        sport: SportType,
        date: LocalDate = LocalDate.now(),
        liveOnly: Boolean = false,
    ): List<SportsLeague> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val url = "$baseUrl/wefeed-h5api-bff/live/match-list-v3".toHttpUrl().newBuilder()
            .addQueryParameter("status", if (liveOnly) "2" else "0")
            .addQueryParameter("matchType", sport.apiValue)
            .addQueryParameter("startTime", start.toString())
            .addQueryParameter("endTime", end.toString())
            .build()
        parseLeagues(request(url.toString()), sport)
    }

    suspend fun match(id: String, sport: SportType): SportsMatch = withContext(Dispatchers.IO) {
        val url = "$baseUrl/wefeed-h5api-bff/live/match-detail".toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .build()
        val root = request(url.toString())
        val data = root.optJSONObject("data") ?: throw IOException("Sports provider returned no match")
        parseMatch(data, sport, data.optJSONObject("league")?.optString("name").orEmpty())
    }

    private fun request(url: String): JSONObject {
        val call = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("callerSource", "node-frontend")
            .get()
            .build()
        client.newCall(call).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Sports provider returned ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())
            if (root.optInt("code", -1) != 0) {
                throw IOException(root.optString("message", "Sports provider rejected the request"))
            }
            return root
        }
    }

    companion object {
        internal fun parseLeagues(root: JSONObject, sport: SportType): List<SportsLeague> {
            val groups = root.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
            return buildList {
                for (index in 0 until groups.length()) {
                    val group = groups.optJSONObject(index) ?: continue
                    val league = group.optJSONObject("league")?.optString("name")
                        ?.takeIf(String::isNotBlank)
                        ?: group.optString("leagueName").takeIf(String::isNotBlank)
                        ?: "Other matches"
                    val items = group.optJSONArray("matchList") ?: JSONArray()
                    val matches = buildList {
                        for (matchIndex in 0 until items.length()) {
                            items.optJSONObject(matchIndex)?.let { add(parseMatch(it, sport, league)) }
                        }
                    }
                    if (matches.isNotEmpty()) add(SportsLeague(league, matches))
                }
            }
        }

        internal fun parseMatch(json: JSONObject, sport: SportType, fallbackLeague: String): SportsMatch {
            val homeJson = json.optJSONObject("team1") ?: JSONObject()
            val awayJson = json.optJSONObject("team2") ?: JSONObject()
            val homeScore = json.optJSONObject("teamMatchInfo1")?.opt("score")?.toString()
                ?: homeJson.opt("score")?.toString()
            val awayScore = json.optJSONObject("teamMatchInfo2")?.opt("score")?.toString()
                ?: awayJson.opt("score")?.toString()
            val status = when (json.optString("status")) {
                "MatchIng" -> SportsMatchStatus.Live
                "MatchEnded" -> SportsMatchStatus.Ended
                else -> SportsMatchStatus.Scheduled
            }
            val league = json.optJSONObject("league")?.optString("name")
                ?.takeIf(String::isNotBlank) ?: fallbackLeague
            return SportsMatch(
                id = json.opt("id")?.toString().orEmpty(),
                sport = sport,
                league = league,
                home = SportsTeam(homeJson.optString("name", "Home"), homeJson.optString("avatar").orNull(), homeScore.orNull()),
                away = SportsTeam(awayJson.optString("name", "Away"), awayJson.optString("avatar").orNull(), awayScore.orNull()),
                startTimeMs = json.optLong("startTime"),
                status = status,
                playUrl = json.optString("playPath").orNull(),
                alternativeStreams = parseVideos(json.optJSONArray("playSource")),
                replays = parseVideos(json.optJSONArray("replay")),
            )
        }

        private fun parseVideos(array: JSONArray?): List<SportsVideo> = buildList {
            val values = array ?: return@buildList
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val url = item.optString("path").orNull() ?: item.optString("url").orNull() ?: continue
                add(
                    SportsVideo(
                        title = item.optString("title").takeIf(String::isNotBlank) ?: "Stream ${index + 1}",
                        url = url,
                        durationSeconds = item.optLong("duration").takeIf { it > 0L },
                    ),
                )
            }
        }

        private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() && it != "null" }
    }
}
