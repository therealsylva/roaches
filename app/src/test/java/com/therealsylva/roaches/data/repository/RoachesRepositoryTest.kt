package com.therealsylva.roaches.data.repository

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.ContentRegion
import org.json.JSONObject
import org.junit.Test

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

        assertThat(results.map { it.id }).containsExactly("friends")
    }
}
