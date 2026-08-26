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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.therealsylva.roaches.data.model.CobaltSaveRequest
import com.therealsylva.roaches.data.remote.COBALT_PUBLIC_API_ORIGIN
import com.therealsylva.roaches.data.remote.cobaltRequestJson
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CobaltChallengeView(
    siteKey: String,
    request: CobaltSaveRequest,
    nonce: Int,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val currentOnError = rememberUpdatedState(onError)
    val webView = remember(siteKey, request, nonce) {
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
                TurnstileBridge(
                    webView = this,
                    onResult = { currentOnResult.value(it) },
                    onError = { currentOnError.value(it) },
                ),
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
                COBALT_PAGE_ORIGIN,
                turnstileDocument(siteKey, request),
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
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    @JavascriptInterface
    fun result(value: String) {
        if (value.length !in 1..MAX_BROWSER_RESULT_LENGTH) {
            webView.post { onError("Cobalt returned an invalid response.") }
            return
        }
        webView.post { onResult(value) }
    }

    @JavascriptInterface
    fun error(value: String) {
        val message = value.take(160).ifBlank { "The connection check failed. Try again." }
        webView.post { onError(message) }
    }
}

internal fun turnstileDocument(siteKey: String, request: CobaltSaveRequest): String {
    val safeSiteKey = JSONObject.quote(siteKey)
    val safeApiOrigin = JSONObject.quote(COBALT_PUBLIC_API_ORIGIN)
    val safeRequest = JSONObject.quote(cobaltRequestJson(request))
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
          <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://challenges.cloudflare.com; frame-src https://challenges.cloudflare.com; connect-src https://challenges.cloudflare.com $COBALT_PUBLIC_API_ORIGIN; style-src 'unsafe-inline'; img-src data: https://challenges.cloudflare.com; base-uri 'none'; form-action 'none'">
          <style>
            html,body { margin:0; min-height:100%; background:transparent; color-scheme:dark; }
            body { display:flex; align-items:center; justify-content:center; overflow:hidden; }
          </style>
          <script>
            const apiOrigin = $safeApiOrigin;
            const cobaltRequest = JSON.parse($safeRequest);
            let submitting = false;

            function reportError(message) {
              const safeMessage = String(message || 'The connection check failed. Try again.').slice(0, 160);
              window.$BRIDGE_NAME.error(safeMessage);
            }

            async function readJson(response, phase) {
              const value = await response.text();
              try {
                return JSON.parse(value);
              } catch (_) {
                throw new Error('Cobalt ' + phase + ' returned ' + response.status + '.');
              }
            }

            async function prepareLink(challengeResponse) {
              if (submitting) return;
              submitting = true;
              try {
                const sessionResponse = await fetch(apiOrigin + '/session', {
                  method: 'POST',
                  redirect: 'manual',
                  cache: 'no-store',
                  headers: {
                    'Accept': 'application/json',
                    'cf-turnstile-response': challengeResponse
                  }
                });
                const session = await readJson(sessionResponse, 'session');
                if (!sessionResponse.ok || session.status === 'error') {
                  window.$BRIDGE_NAME.result(JSON.stringify({ session: session }));
                  return;
                }
                if (!session.token) throw new Error('Cobalt did not open a valid session.');

                const mediaResponse = await fetch(apiOrigin + '/', {
                  method: 'POST',
                  redirect: 'manual',
                  cache: 'no-store',
                  headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + session.token
                  },
                  body: JSON.stringify(cobaltRequest)
                });
                const response = await readJson(mediaResponse, 'request');
                window.$BRIDGE_NAME.result(JSON.stringify({ session: session, response: response }));
              } catch (error) {
                reportError(error && error.message);
              }
            }

            function renderChallenge() {
              window.turnstile.render('#challenge', {
                sitekey: $safeSiteKey,
                theme: 'dark',
                retry: 'auto',
                'retry-interval': 800,
                'refresh-expired': 'never',
                callback: prepareLink,
                'error-callback': function() {
                  reportError('The connection check failed. Try again.');
                  return true;
                },
                'expired-callback': function() { reportError('The connection check expired. Try again.'); },
                'timeout-callback': function() { reportError('The connection check timed out. Try again.'); },
                'unsupported-callback': function() { reportError('Android WebView cannot run the connection check.'); }
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
    "https" -> host in setOf("cobalt.tools", "api.cobalt.tools", "challenges.cloudflare.com")
    else -> false
}

private const val BRIDGE_NAME = "RoachesTurnstile"
private const val COBALT_PAGE_ORIGIN = "https://cobalt.tools/"
private const val MAX_BROWSER_RESULT_LENGTH = 1_048_576
