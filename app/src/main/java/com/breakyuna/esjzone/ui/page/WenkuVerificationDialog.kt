package com.breakyuna.esjzone.ui.page

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.EsjzoneClient

@Composable
internal fun WenkuVerificationDialog(url: String, onVerified: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val browser = remember(url) { WebView(context) }
    DisposableEffect(browser) {
        onDispose { browser.stopLoading(); browser.destroy() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wenku_verification_title)) },
        text = {
            AndroidView(
                factory = {
                    browser.apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = EsjzoneClient.wenkuUserAgent()
                            allowFileAccess = false
                            allowContentAccess = false
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.isForMainFrame &&
                                    (request.url.scheme != "https" || request.url.host != "www.wenku8.net")

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val host = request.url.host.orEmpty()
                                if (request.url.scheme == "https" && host in setOf(
                                        "www.wenku8.net", "challenges.cloudflare.com", "www.cloudflare.com")) return null
                                return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                            }

                            override fun onPageFinished(view: WebView, finishedUrl: String) {
                                val cookie = CookieManager.getInstance().getCookie(url)
                                if (cookie?.split(';')?.any {
                                        it.trim().startsWith("cf_clearance=") && it.substringAfter('=').isNotBlank()
                                    } == true) {
                                    CookieManager.getInstance().flush()
                                    EsjzoneClient.importWenkuBrowserCookies(cookie)
                                    onVerified()
                                }
                            }
                        }
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(420.dp)
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
