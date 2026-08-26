package com.therealsylva.roaches.ui.components

import com.google.common.truth.Truth.assertThat
import com.therealsylva.roaches.data.model.CobaltSaveRequest
import com.therealsylva.roaches.data.model.LinkDownloadMode
import org.junit.Test

class CobaltChallengeViewTest {
    @Test
    fun challengeKeepsSessionAndMediaRequestInTheSameBrowserContext() {
        val document = turnstileDocument(
            siteKey = "site-key",
            request = CobaltSaveRequest(
                sourceUrl = "https://media.example/post",
                mode = LinkDownloadMode.Audio,
            ),
        )

        val sessionRequest = document.indexOf("fetch(apiOrigin + '/session'")
        val mediaRequest = document.indexOf("fetch(apiOrigin + '/'")

        assertThat(sessionRequest).isAtLeast(0)
        assertThat(mediaRequest).isGreaterThan(sessionRequest)
        assertThat(document).contains("'cf-turnstile-response': challengeResponse")
        assertThat(document).contains("'Authorization': 'Bearer ' + session.token")
        assertThat(document).contains("downloadMode")
        assertThat(document).doesNotContain("Roaches/")
    }
}
