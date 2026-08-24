package com.therealsylva.roaches.data.repository

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class RoachesRepositoryTest {
    @Test
    fun searchParserKeepsNestedProviderResultsWithoutClientTitleFiltering() {
        val payload = JSONObject(
            """
            {
              "groups": [
                {
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
        assertThat(results.single().title).isEqualTo("Arrakis: Part Two")
    }
}
