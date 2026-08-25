package com.therealsylva.roaches.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CobaltChallengeView(
    siteKey: String,
    nonce: Int,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webView = remember(siteKey, nonce) {
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(
                TurnstileBridge(this, onToken, onError),
                BRIDGE_NAME,
            )
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !request.url.isAllowedChallengeUrl()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) onError("The connection check could not load.")
                }
            }
            loadDataWithBaseURL(
                COBALT_ORIGIN,
                turnstileDocument(siteKey),
                "text/html",
                Charsets.UTF_8.name(),
                null,
            )
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.stopLoading()
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

private class TurnstileBridge(
    private val webView: WebView,
    private val onToken: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    @JavascriptInterface
    fun token(value: String) {
        if (value.length !in 1..2_048) return
        webView.post { onToken(value) }
    }

    @JavascriptInterface
    fun error() {
        webView.post { onError("The connection check failed. Try again.") }
    }
}

private fun turnstileDocument(siteKey: String): String {
    val safeSiteKey = JSONObject.quote(siteKey)
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
          <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://challenges.cloudflare.com; frame-src https://challenges.cloudflare.com; connect-src https://challenges.cloudflare.com; style-src 'unsafe-inline'; img-src data: https://challenges.cloudflare.com">
          <style>
            html,body { margin:0; min-height:100%; background:transparent; color-scheme:dark; }
            body { display:flex; align-items:center; justify-content:center; overflow:hidden; }
          </style>
          <script>
            let sent = false;
            function renderChallenge() {
              window.turnstile.render('#challenge', {
                sitekey: $safeSiteKey,
                theme: 'dark',
                callback: function(value) {
                  if (!sent) { sent = true; window.$BRIDGE_NAME.token(value); }
                },
                'error-callback': function() { window.$BRIDGE_NAME.error(); },
                'expired-callback': function() { window.$BRIDGE_NAME.error(); }
              });
            }
          </script>
          <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?onload=renderChallenge&render=explicit" defer></script>
        </head>
        <body><div id="challenge"></div></body>
        </html>
    """.trimIndent()
}

private fun Uri.isAllowedChallengeUrl(): Boolean = when (scheme?.lowercase()) {
    "about" -> true
    "https" -> host in setOf("cobalt.tools", "challenges.cloudflare.com")
    else -> false
}

private const val BRIDGE_NAME = "RoachesTurnstile"
private const val COBALT_ORIGIN = "https://cobalt.tools/"
