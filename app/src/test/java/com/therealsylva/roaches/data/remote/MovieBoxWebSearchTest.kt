package com.therealsylva.roaches.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MovieBoxWebSearchTest {
    @Test
    fun parsesIndexedNuxtSubjectsIntoProviderRows() {
        val html = """
            <html><body>
            <script type="application/json" id="__NUXT_DATA__" data-ssr="true">
            [
              {"subjectId":1,"subjectType":2,"title":3,"releaseDate":4,"genre":5,"countryName":6,"imdbRatingValue":7,"cover":8,"language":10},
              "6026412232966389904",
              1,
              "Spider-Man: Brand New Day [English]",
              "2026-07-31",
              "Action, Adventure, Fantasy",
              "United States",
              "7.8",
              {"url":9},
              "https://images.example/spider-man.webp",
              "English"
            ]
            </script>
            </body></html>
        """.trimIndent()

        val results = parseWebSearchHtml(html)
        val movie = results.getJSONObject(0)

        assertThat(results.length()).isEqualTo(1)
        assertThat(movie.getString("subjectId")).isEqualTo("6026412232966389904")
        assertThat(movie.getInt("subjectType")).isEqualTo(1)
        assertThat(movie.getString("title")).isEqualTo("Spider-Man: Brand New Day [English]")
        assertThat(movie.getString("releaseDate")).isEqualTo("2026-07-31")
        assertThat(movie.getString("genre")).contains("Adventure")
        assertThat(movie.getString("countryName")).isEqualTo("United States")
        assertThat(movie.getString("language")).isEqualTo("English")
        assertThat(movie.getString("poster")).isEqualTo("https://images.example/spider-man.webp")
    }

    @Test
    fun ignoresNoiseSentinelsAndDuplicateSubjects() {
        val html = """
            <script id='__NUXT_DATA__' type='application/json'>
            [
              {"unrelated":1},
              "noise",
              {"subjectId":3,"subjectType":4,"title":5},
              "same-id",
              1,
              "First copy",
              {"subjectId":3,"subjectType":4,"title":7},
              "Duplicate copy",
              {"subjectId":-1,"subjectType":4,"title":5}
            ]
            </script>
        """.trimIndent()

        val results = parseWebSearchHtml(html)

        assertThat(results.length()).isEqualTo(1)
        assertThat(results.getJSONObject(0).getString("title")).isEqualTo("First copy")
    }

    @Test
    fun missingOrMalformedNuxtDataReturnsNoResults() {
        assertThat(parseWebSearchHtml("<html></html>").length()).isEqualTo(0)
        assertThat(
            parseWebSearchHtml("<script id=\"__NUXT_DATA__\">not-json</script>").length(),
        ).isEqualTo(0)
    }
}
