package com.therealsylva.roaches.data.repository

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.MediaKind
import com.therealsylva.roaches.data.model.Shelf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import java.io.IOException

class RoachesRepositoryTest {
    @Test
    fun searchParserUsesPrimaryTitlesAndDropsRelatedVerticals() {
        val payload = JSONObject(
            """
            {
              "results": [
                {
                  "topicType": "SUBJECT",
                  "moreTabId": "",
                  "subjects": [
                    {
                      "subjectId": "hindi-dune",
                      "title": "Dune [Hindi]",
                      "language": "Hindi",
                      "subjectType": 1,
                      "releaseDate": "2021-10-22"
                    },
                    {
                      "subjectId": "english-dune",
                      "title": "Dune",
                      "language": "English",
                      "subjectType": 1,
                      "releaseDate": "2021-10-22"
                    }
                  ]
                },
                {
                  "topicType": "SUBJECT",
                  "moreTabId": "Sports",
                  "subjects": [
                    {
                      "subjectId": "fight",
                      "title": "Dune Fight Highlights",
                      "subjectType": 1,
                      "releaseDate": "2026-01-01"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val results = parseSearchResults(payload, "Dune")

        assertThat(results.map { it.id }).containsExactly("english-dune")
        assertThat(results.single().title).isEqualTo("Dune")
    }

    @Test
    fun searchParserKeepsProviderMatchesWithoutClientTitleFiltering() {
        val payload = JSONObject(
            """
            {
              "results": [
                {
                  "topicType": "SUBJECT",
                  "moreTabId": "",
                  "subjects": [
                    {
                      "subjectId": "one",
                      "title": "Arrakis: Part Two",
                      "subjectType": 1,
                      "releaseDate": "2024-03-01"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val results = parseSearchResults(payload, "Dune")

        assertThat(results.map { it.id }).containsExactly("one")
    }

    @Test
    fun catalogueParserRejectsIndianShortAndAdultRowsButKeepsMultilingualEnglishTitles() {
        val payload = JSONObject(
            """
            {
              "items": [
                {
                  "subjectId": "friends",
                  "title": "Friends",
                  "subjectType": 2,
                  "countryName": "United States",
                  "language": "English, Hindi, Spanish",
                  "genre": "Comedy, Romance"
                },
                {
                  "subjectId": "dub",
                  "title": "The Flash [Hindi]",
                  "subjectType": 2,
                  "countryName": "United States",
                  "genre": "Action"
                },
                {
                  "subjectId": "western-dub",
                  "title": "Avatar [Hindi]",
                  "subjectType": 1,
                  "countryName": "United States",
                  "language": "English",
                  "genre": "Action, Adventure"
                },
                {
                  "subjectId": "india",
                  "title": "Heart Beat",
                  "subjectType": 2,
                  "countryName": "India",
                  "genre": "Drama"
                },
                {
                  "subjectId": "short",
                  "title": "Fight clip",
                  "subjectType": 6,
                  "countryName": "United States"
                },
                {
                  "subjectId": "adult",
                  "title": "Explicit title",
                  "subjectType": 1,
                  "countryName": "United States",
                  "genre": "Adult, Romance"
                }
              ]
            }
            """.trimIndent(),
        )

        val results = parseCatalogueResults(payload, ContentRegion.GlobalEnglish)

        assertThat(results.map { it.id }).containsExactly("friends", "western-dub").inOrder()
        assertThat(results.last().title).isEqualTo("Avatar")
    }

    @Test
    fun legacyHomeRejectsRegionalShelvesAndUnsafeRows() {
        val payload = JSONObject(
            """
            {
              "items": [
                {
                  "title": "Latest Hindi releases",
                  "subjects": [
                    {"subjectId":"india-shelf","title":"Ignored","subjectType":1,"countryName":"United States"}
                  ]
                },
                {
                  "title": "Coming soon",
                  "subjects": [
                    {"subjectId":"clean","title":"Dune","subjectType":1,"countryName":"United States"},
                    {"subjectId":"india","title":"Heart Beat","subjectType":2,"countryName":"India"},
                    {"subjectId":"adult","title":"Explicit","subjectType":1,"countryName":"United States","genre":"Adult"},
                    {"subjectId":"short","title":"Clip","subjectType":6,"countryName":"United States"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val shelves = parseLegacyHome(payload, ContentRegion.GlobalEnglish)

        assertThat(shelves.map(Shelf::title)).containsExactly("Coming soon")
        assertThat(shelves.single().items.map(MediaItem::id)).containsExactly("clean")
    }

    @Test
    fun homeResolverUsesFreshRailsAndFillsMissingRailsFromCache() = runBlocking {
        val cached = listOf(Shelf("drama", "Drama", listOf(media("cached"))))
        var legacyCalled = false
        var persisted: List<Shelf> = emptyList()

        val result = resolveHome(
            cached = cached,
            fetchCatalogue = { rail ->
                if (rail.id == "popular") listOf(media("fresh")) else throw IOException("rate limited")
            },
            fetchLegacy = {
                legacyCalled = true
                emptyList()
            },
            persist = { persisted = it },
        )

        assertThat(result.map(Shelf::id)).containsExactly("popular", "drama").inOrder()
        assertThat(persisted).isEqualTo(result)
        assertThat(legacyCalled).isFalse()
    }

    @Test
    fun homeResolverFallsBackToLegacyThenCache() = runBlocking {
        val legacy = listOf(Shelf("legacy", "Discover", listOf(media("legacy"))))
        val cachedBeforeLegacy = listOf(Shelf("popular", "Popular now", listOf(media("older"))))
        val fromLegacy = resolveHome(
            cached = cachedBeforeLegacy,
            fetchCatalogue = { throw IOException("catalogue down") },
            fetchLegacy = { legacy },
        )
        val cache = listOf(Shelf("popular", "Popular now", listOf(media("cached"))))
        val fromCache = resolveHome(
            cached = cache,
            fetchCatalogue = { throw IOException("catalogue down") },
            fetchLegacy = { throw IOException("legacy down") },
        )

        assertThat(fromLegacy.map(Shelf::id)).containsExactly("popular", "legacy").inOrder()
        assertThat(fromCache).isEqualTo(cache)
    }

    @Test
    fun homeResolverPropagatesCancellationAndFailsWithoutAnySource() = runBlocking {
        val cancellation = runCatching {
            resolveHome(
                cached = emptyList(),
                fetchCatalogue = { throw CancellationException("cancelled") },
                fetchLegacy = { emptyList() },
            )
        }.exceptionOrNull()
        val unavailable = runCatching {
            resolveHome(
                cached = emptyList(),
                fetchCatalogue = { throw IOException("catalogue down") },
                fetchLegacy = { throw IOException("legacy down") },
            )
        }.exceptionOrNull()

        assertThat(cancellation).isInstanceOf(CancellationException::class.java)
        assertThat(unavailable).isInstanceOf(IOException::class.java)
    }

    private fun media(id: String) = MediaItem(id, id, MediaKind.Movie)
}
