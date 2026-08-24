package com.therealsylva.roaches.data.repository

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.ContentRegion
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.MediaKind
import com.therealsylva.roaches.data.model.PlaybackQuality
import com.therealsylva.roaches.data.model.PreferredAudio
import com.therealsylva.roaches.data.model.Shelf
import com.therealsylva.roaches.data.model.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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
    fun searchParserAcceptsProviderMovieGroupWithMoreTabId() {
        val payload = JSONObject(
            """
            {
              "results": [
                {
                  "topicType": "SUBJECT",
                  "moreTabId": "Sports",
                  "subjects": [
                    {
                      "subjectId": "sports",
                      "title": "Toy Story match highlights",
                      "language": "English",
                      "subjectType": 1,
                      "releaseDate": "2026-08-24"
                    }
                  ]
                },
                {
                  "topicType": "SUBJECT",
                  "moreTabId": "MovieTV",
                  "subjects": [
                    {
                      "subjectId": "toy-story-5",
                      "title": "Toy Story 5",
                      "language": "English",
                      "subjectType": 1,
                      "releaseDate": "2026-06-19"
                    },
                    {
                      "subjectId": "toy-story",
                      "title": "Toy Story",
                      "language": "English",
                      "subjectType": 1,
                      "releaseDate": "1995-11-22"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val results = parseSearchResults(payload, "toy")

        assertThat(results.map { it.id }).containsExactly("toy-story-5", "toy-story").inOrder()
    }

    @Test
    fun fallbackDecisionUsesPunctuationInsensitiveTitleMatching() {
        val toyResults = listOf(
            MediaItem("toy-story-5", "Toy Story 5", MediaKind.Movie, year = "2026"),
        )
        val incompleteSpiderManResults = listOf(
            MediaItem("brand-new-day", "Brand New Day", MediaKind.Movie, year = "2026"),
            MediaItem("homecoming", "Spider-Man: Homecoming", MediaKind.Movie, year = "2017"),
        )

        assertThat(toyResults.hasStrongTitleMatch("toy")).isTrue()
        assertThat(incompleteSpiderManResults.hasStrongTitleMatch("spiderman brand new day")).isFalse()
        assertThat(shouldUseWebSearchFallback(incompleteSpiderManResults, "spider-man brand new day", 1))
            .isTrue()
        assertThat(shouldUseWebSearchFallback(incompleteSpiderManResults, "spider-man brand new day", 2))
            .isFalse()
        assertThat(shouldUseWebSearchFallback(emptyList(), "sp", 1)).isFalse()
        assertThat(shouldUseWebSearchFallback(emptyList(), "---", 1)).isFalse()
        assertThat(
            shouldUseWebSearchFallback(
                listOf(MediaItem("adult", "Adult Content", MediaKind.Movie)),
                "adult content",
                page = 1,
                allowMature = true,
            ),
        ).isTrue()
        assertThat(
            shouldUseWebSearchFallback(emptyList(), "18+", page = 1, allowMature = true),
        ).isTrue()
        assertThat(shouldUseWebSearchFallback(toyResults, "toy", page = 1, allowMature = true))
            .isTrue()
        assertThat(shouldUseWebSearchFallback(emptyList(), "18+", page = 2, allowMature = true))
            .isFalse()
    }

    @Test
    fun matureQueryDetectionCoversExplicitFamiliesAndVariants() {
        listOf(
            "adult",
            "adults only",
            "pornography",
            "erotica",
            "NSFW movies",
            "18+",
            "hentai",
            "uncensored",
            "step-sister",
            "camgirls",
            "orgies",
            "sex videos",
            "naked",
            "nude actresses",
            "oral clips",
            "sex nude",
            "sex 2025",
            "naked women",
        ).forEach { query ->
            assertThat(isMatureSearchQuery(query)).isTrue()
        }
    }

    @Test
    fun matureQueryDetectionDoesNotBlockOrdinaryTitleWords() {
        listOf(
            "Toy Story",
            "Hot Fuzz",
            "Illicit Desire",
            "Virgin Territory",
            "Spider-Man Brand New Day",
            "The Naked Gun",
            "Sex Education",
            "Oral History",
        ).forEach { query ->
            assertThat(isMatureSearchQuery(query)).isFalse()
        }
    }

    @Test
    fun genericAnimationDiscoveryDetectionDoesNotHijackSpecificTitles() {
        listOf("anime", "Anime movies", "animated shows", "animation series").forEach { query ->
            assertThat(isGenericAnimationDiscoveryQuery(query)).isTrue()
        }
        listOf("Anime War", "Castlevania", "Toy Story", "animated Spider-Man").forEach { query ->
            assertThat(isGenericAnimationDiscoveryQuery(query)).isFalse()
        }
    }

    @Test
    fun recommendationsPreferSpecificGenreOverGenericAnimation() {
        assertThat(selectRecommendationGenre(listOf("Animation", "Action", "Adventure")))
            .isEqualTo("Action")
        assertThat(selectRecommendationGenre(listOf("Anime"))).isEqualTo("Animation")
        assertThat(selectRecommendationGenre(listOf("Hentai", "Fantasy"))).isEqualTo("Fantasy")
    }

    @Test
    fun websiteExactMatchRanksAheadOfMobileRelatedResultsAndDeduplicates() {
        val mobile = listOf(
            MediaItem("brand-new-day", "Brand New Day", MediaKind.Movie, year = "2026"),
            MediaItem("homecoming", "Spider-Man: Homecoming", MediaKind.Movie, year = "2017"),
            MediaItem("duplicate", "Spider-Man: Brand New Day", MediaKind.Movie, year = "2026"),
        )
        val website = listOf(
            MediaItem(
                "6026412232966389904",
                "Spider-Man: Brand New Day",
                MediaKind.Movie,
                year = "2026",
            ),
        )

        val merged = mergeSearchResults(mobile, website, "spiderman brand new day")

        assertThat(merged.map(MediaItem::id)).containsExactly(
            "6026412232966389904",
            "brand-new-day",
            "homecoming",
        ).inOrder()
    }

    @Test
    fun englishSearchRemovesBengaliDubAndKeepsEnglishTitle() {
        val payload = JSONObject(
            """
            {
              "results": [
                {
                  "topicType": "SUBJECT",
                  "moreTabId": "MovieTV",
                  "subjects": [
                    {
                      "subjectId": "bengali",
                      "title": "Toy Story 5 - Bengali Dub",
                      "language": "English, Bengali",
                      "countryName": "United States",
                      "subjectType": 1,
                      "releaseDate": "2026-06-19"
                    },
                    {
                      "subjectId": "bangla",
                      "title": "Toy Story 5 - Bangla Dub",
                      "language": "English, Bangla",
                      "countryName": "United States",
                      "subjectType": 1,
                      "releaseDate": "2026-06-19"
                    },
                    {
                      "subjectId": "english",
                      "title": "Toy Story 5",
                      "language": "English",
                      "countryName": "United States",
                      "subjectType": 1,
                      "releaseDate": "2026-06-19"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val results = parseSearchResults(
            payload,
            "Toy Story",
            PreferredAudio.English,
            ContentRegion.GlobalEnglish,
        )

        assertThat(results.map { it.id }).containsExactly("english")
    }

    @Test
    fun searchBlocksMatureResultsByDefaultEvenWithAnyAudio() {
        val payload = JSONObject(
            """
            {
              "items": [
                {"subjectId":"adult-genre","title":"Late title","subjectType":1,"genre":"Adult"},
                {"subjectId":"erotic-genre","title":"Another title","subjectType":1,"genre":"Erotic, Romance"},
                {"subjectId":"explicit-title","title":"Adult Content","subjectType":1,"genre":"Comedy"},
                {"subjectId":"plural-title","title":"Adults Only! S1-S3","subjectType":2,"genre":"Documentary"},
                {"subjectId":"adult-flag","title":"Generic title","subjectType":1,"isAdult":true},
                {"subjectId":"provider-kid-lock","title":"Weak provider row","subjectType":2,"restrictKid":1},
                {"subjectId":"adult-rating","title":"Weak metadata","subjectType":1,"contentRating":"TV-MA"},
                {"subjectId":"clean","title":"Family Adventure","subjectType":1,"genre":"Family, Adventure"}
              ]
            }
            """.trimIndent(),
        )

        val results = parseSearchResults(
            payload,
            "title",
            PreferredAudio.Any,
            ContentRegion.GlobalEnglish,
        )

        assertThat(results.map(MediaItem::id)).containsExactly("adult-rating", "clean").inOrder()
    }

    @Test
    fun searchCollapsesSeasonRowsThatReuseAProviderSubjectId() {
        val payload = JSONObject(
            """
            {
              "items": [
                {
                  "subjectId":"castlevania",
                  "title":"Castlevania S4",
                  "subjectType":2,
                  "language":"English",
                  "contentRating":"TV-MA",
                  "season":4
                },
                {
                  "subjectId":"castlevania",
                  "title":"Castlevania S3",
                  "subjectType":2,
                  "language":"English",
                  "contentRating":"TV-MA",
                  "season":3
                },
                {
                  "subjectId":"old-movie",
                  "title":"Castlevania",
                  "subjectType":1,
                  "releaseDate":"2009-01-01"
                }
              ]
            }
            """.trimIndent(),
        )

        val results = parseSearchResults(payload, "Castlevania")

        assertThat(results.map(MediaItem::id)).containsExactly("old-movie", "castlevania").inOrder()
        assertThat(results.count { it.id == "castlevania" }).isEqualTo(1)
    }

    @Test
    fun mainstreamMatureRatingsAreNotClassifiedAsExplicitContent() {
        val payload = JSONObject(
            """
            {
              "items": [
                {
                  "subjectId":"castlevania",
                  "title":"Castlevania",
                  "subjectType":2,
                  "genre":"Animation, Action",
                  "contentRating":"TV-MA"
                },
                {
                  "subjectId":"explicit",
                  "title":"Explicit listing",
                  "subjectType":1,
                  "genre":"Adult"
                }
              ]
            }
            """.trimIndent(),
        )

        val results = parseSearchResults(payload, "Castlevania")

        assertThat(results.map(MediaItem::id)).containsExactly("castlevania")
    }

    @Test
    fun unlockedSearchReturnsProviderMatureResults() {
        val payload = JSONObject(
            """
            {
              "items": [
                {"subjectId":"adult-genre","title":"Late title","subjectType":1,"genre":"Adult"},
                {"subjectId":"explicit-title","title":"Adult Content","subjectType":1,"genre":"Comedy"},
                {"subjectId":"clean","title":"Family Adventure","subjectType":1,"genre":"Family"}
              ]
            }
            """.trimIndent(),
        )

        val results = parseSearchResults(
            payload,
            "title",
            PreferredAudio.Any,
            ContentRegion.GlobalEnglish,
            allowMature = true,
        )

        assertThat(results.map(MediaItem::id)).containsExactly(
            "adult-genre",
            "explicit-title",
            "clean",
        )
    }

    @Test
    fun englishAudioAliasDoesNotMatchBengali() {
        assertThat(matchesAudioPreference("English / ENG", PreferredAudio.English)).isTrue()
        assertThat(matchesAudioPreference("Bengali", PreferredAudio.English)).isFalse()
    }

    @Test
    fun streamOrderingPrefersEnglishBeforeQualityAndKeepsTruthfulFallbackOrder() {
        val sources = listOf(
            StreamSource("bengali-1080", "https://media/bn", 1080, audio = "Bengali"),
            StreamSource("english-720", "https://media/en-720", 720, audio = "English"),
            StreamSource("blank-1080", "https://media/unknown", 1080),
            StreamSource("original-480", "https://media/original", 480, audio = "Original audio"),
            StreamSource("english-1080", "https://media/en-1080", 1080, audio = "ENG"),
        )

        val automatic = orderStreamSources(sources, PlaybackQuality.Auto, PreferredAudio.English)
        val capped = orderStreamSources(sources, PlaybackQuality.Hd, PreferredAudio.English)

        assertThat(automatic.map { it.resourceId }).containsExactly(
            "english-1080",
            "english-720",
            "original-480",
            "blank-1080",
            "bengali-1080",
        ).inOrder()
        assertThat(capped.take(2).map { it.resourceId })
            .containsExactly("english-720", "english-1080")
            .inOrder()
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

    @Test
    fun movieResourcesPaginatePastTheFirstQualityPage() = runBlocking {
        val initial = parseResourcePage(
            resourcePage(
                rows = """{"resourceId":"360","resourceLink":"https://media/360","resolution":360}""",
                hasMore = true,
                resolutions = listOf(1080, 720, 360),
            ),
        )
        val requestedPages = mutableListOf<Int>()

        val rows = collectMovieResourceRows(initial, maxPages = 3) { page ->
            requestedPages += page
            parseResourcePage(
                when (page) {
                    2 -> resourcePage(
                        """{"resourceId":"720","resourceLink":"https://media/720","resolution":720}""",
                        hasMore = true,
                    )
                    else -> resourcePage(
                        """{"resourceId":"1080","resourceLink":"https://media/1080","resolution":1080}""",
                        hasMore = false,
                    )
                },
            )
        }

        assertThat(requestedPages).containsExactly(2, 3).inOrder()
        assertThat(rows.map { it.getInt("resolution") }).containsExactly(360, 720, 1080).inOrder()
    }

    @Test
    fun episodeResourcesFetchEveryResolutionAndKeepOnlyTheTargetEpisode() = runBlocking {
        val initial = parseResourcePage(
            resourcePage(
                rows = """{"resourceId":"fallback","resourceLink":"https://media/360","resolution":360,"se":2,"ep":3}""",
                hasMore = true,
                resolutions = listOf(1080, 720),
            ),
        )
        val seasons = listOf(
            com.therealsylva.roaches.data.model.Season(
                1,
                (1..24).map { com.therealsylva.roaches.data.model.Episode(1, it) },
            ),
            com.therealsylva.roaches.data.model.Season(
                2,
                (1..10).map { com.therealsylva.roaches.data.model.Episode(2, it) },
            ),
        )

        val rows = collectEpisodeResourceRows(
            initial = initial,
            season = 2,
            episode = 3,
            startPage = estimatedEpisodePage(seasons, 2, 3),
            resolutions = initial.resolutions,
        ) { resolution, page ->
            assertThat(page).isEqualTo(2)
            parseResourcePage(
                resourcePage(
                    rows = """
                        {"resourceId":"wrong-$resolution","resourceLink":"https://media/wrong-$resolution","resolution":$resolution,"se":2,"ep":2},
                        {"resourceId":"target-$resolution","resourceLink":"https://media/target-$resolution","resolution":$resolution,"se":2,"ep":3}
                    """.trimIndent(),
                    hasMore = false,
                ),
            )
        }

        assertThat(rows.map { it.getString("resourceId") })
            .containsExactly("fallback", "target-1080", "target-720")
        assertThat(estimatedEpisodePage(seasons, 2, 3)).isEqualTo(2)
    }

    @Test
    fun episodeResourcesPreferTheScopedProviderQueryWithoutGenericScanning() = runBlocking {
        val scopedPages = mutableListOf<Int>()
        var genericCalls = 0

        val rows = resolveEpisodeResourceRows(
            season = 2,
            episode = 3,
            startPage = 2,
            fetchScopedPage = { page ->
                scopedPages += page
                parseResourcePage(
                    resourcePage(
                        rows = """{"resourceId":"target","resourceLink":"https://media/target","resolution":1080,"se":2,"ep":3}""",
                        hasMore = false,
                    ),
                )
            },
            fetchGenericPage = { _, _ ->
                genericCalls += 1
                error("generic scan should not run")
            },
        )

        assertThat(scopedPages).containsExactly(1)
        assertThat(genericCalls).isEqualTo(0)
        assertThat(rows.map { it.getString("resourceId") }).containsExactly("target")
        Unit
    }

    @Test
    fun episodeResourcesFallBackToResolutionScanningWhenScopedQueryIsUnavailable() = runBlocking {
        val genericRequests = mutableListOf<Pair<Int, Int>>()

        val rows = resolveEpisodeResourceRows(
            season = 2,
            episode = 3,
            startPage = 2,
            defaultResolutions = listOf(720),
            fetchScopedPage = {
                throw IOException("scoped query unavailable")
            },
            fetchGenericPage = { resolution, page ->
                genericRequests += resolution to page
                if (resolution == 0) {
                    parseResourcePage(
                        resourcePage(rows = "", hasMore = true, resolutions = listOf(720)),
                    )
                } else {
                    parseResourcePage(
                        resourcePage(
                            rows = """{"resourceId":"fallback","resourceLink":"https://media/fallback","resolution":720,"se":2,"ep":3}""",
                            hasMore = false,
                        ),
                    )
                }
            },
        )

        assertThat(genericRequests).containsExactly(0 to 1, 720 to 2).inOrder()
        assertThat(rows.map { it.getString("resourceId") }).containsExactly("fallback")
        Unit
    }

    @Test
    fun streamParserUsesProviderMetadataAndTruthfulAudioFallbacks() {
        val rows = listOf(
            JSONObject(
                """
                {
                  "resourceId":"english",
                  "resourceLink":"https://media/english",
                  "resolution":"1080p",
                  "codecName":"H.264",
                  "fileName":"Dune.2021.ENG.WEB-DL.mkv",
                  "size":"1048576.0",
                  "duration":"01:30:00",
                  "uploadBy":"Cinema source"
                }
                """.trimIndent(),
            ),
            JSONObject(
                """
                {
                  "resourceId":"spanish",
                  "resourceLink":"https://media/spanish",
                  "resolution":720,
                  "language":"Spanish",
                  "fileName":"Dune.mkv"
                }
                """.trimIndent(),
            ),
        )

        val sources = parseStreamSources(rows, "Original Audio")

        assertThat(sources[0].audio).isEqualTo("English")
        assertThat(sources[0].sizeBytes).isEqualTo(1_048_576L)
        assertThat(sources[0].durationSeconds).isEqualTo(5_400L)
        assertThat(sources[0].uploader).isEqualTo("Cinema source")
        assertThat(sources[0].filename).isEqualTo("Dune.2021.ENG.WEB-DL.mkv")
        assertThat(sources[1].audio).isEqualTo("Spanish")
    }

    @Test
    fun originalAudioIsResolvedOnlyWhenDetailsReportAConcreteLanguage() {
        val concrete = resolveAudioLanguage(
            "Original Audio",
            JSONObject("""{"language":"English"}"""),
            JSONObject(),
        )
        val unknown = resolveAudioLanguage(
            "Original Audio",
            JSONObject(),
            JSONObject(),
        )

        assertThat(concrete).isEqualTo("English")
        assertThat(unknown).isEqualTo("Original audio")
    }

    private fun resourcePage(
        rows: String,
        hasMore: Boolean,
        resolutions: List<Int> = emptyList(),
    ): JSONObject = JSONObject()
        .put("list", JSONArray("[$rows]"))
        .put("pager", JSONObject().put("hasMore", hasMore))
        .put(
            "collectionResolutions",
            JSONArray(resolutions.map { JSONObject().put("resolution", it) }),
        )

    private fun media(id: String) = MediaItem(id, id, MediaKind.Movie)
}
