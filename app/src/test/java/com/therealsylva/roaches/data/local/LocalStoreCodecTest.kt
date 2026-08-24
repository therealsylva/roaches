package com.therealsylva.roaches.data.local

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.MediaItem
import com.therealsylva.roaches.data.model.MediaKind
import com.therealsylva.roaches.data.model.Shelf
import org.junit.Test

class LocalStoreCodecTest {
    private val item = MediaItem(
        id = "dune",
        title = "Dune",
        kind = MediaKind.Movie,
        year = "2021",
        posterUrl = "https://images.example/dune.jpg",
    )

    @Test
    fun homeCacheRoundTripsAndDeduplicatesBoundedContent() {
        val raw = encodeHomeCache(
            listOf(
                Shelf("popular", "Popular now", listOf(item, item)),
                Shelf("popular", "Duplicate shelf", listOf(item)),
            ),
        )

        val decoded = decodeHomeCache(raw)

        assertThat(decoded).containsExactly(Shelf("popular", "Popular now", listOf(item)))
    }

    @Test
    fun corruptOrUnknownCacheIsIgnored() {
        assertThat(decodeHomeCache("not-json")).isEmpty()
        assertThat(decodeHomeCache("{\"version\":2,\"shelves\":[]}")).isEmpty()
        assertThat(decodeHomeCache(encodeHomeCache(emptyList()))).isEmpty()
    }
}
