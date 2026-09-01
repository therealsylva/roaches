package com.therealsylva.roaches.data.repository

import com.therealsylva.roaches.data.model.SportType
import com.therealsylva.roaches.data.model.SportsLeague
import com.therealsylva.roaches.data.model.SportsMatch
import com.therealsylva.roaches.data.remote.SportsApi
import java.time.LocalDate

class SportsRepository internal constructor(private val api: SportsApi = SportsApi()) {
    suspend fun matches(sport: SportType, date: LocalDate): List<SportsLeague> =
        api.matches(sport, date)

    suspend fun match(id: String, sport: SportType): SportsMatch = api.match(id, sport)
}
