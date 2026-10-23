package com.francesco.myweather.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.graphics.Color
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.francesco.myweather.data.Place
import com.francesco.myweather.data.WindUnit

private const val TAG = "MapScreen"
private const val ASSET_HOST = "appassets.androidx.com"
private const val MAP_URL = "https://$ASSET_HOST/assets/map/map.html"
private const val DEFAULT_ZOOM = 6

/**
 * Full-screen map: a WebView running MapLibre GL JS (see assets/map/map.html).
 * Kotlin -> JS goes through evaluateJavascript; JS -> Kotlin through the `Android` interface.
 * The page shows its own readout when the map is tapped, so it needs the user's wind unit.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen(center: Place?, windUnit: WindUnit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.parseColor("#0F1420"))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onMapTap(lat: Double, lon: Double) {
                            Log.d(TAG, "Map tapped at $lat, $lon")
                        }
                    },
                    "Android",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = serveBundledAsset(context, request.url)

                    override fun onPageFinished(view: WebView, url: String) {
                        center?.let {
                            view.evaluateJavascript(
                                "setCenter(${it.latitude}, ${it.longitude}, $DEFAULT_ZOOM)",
                                null,
                            )
                        }
                    }
                }
                loadUrl(MAP_URL)
            }
        },
        // Runs when the unit changes. The page applies the last unit it was given once it has loaded.
        update = { it.evaluateJavascript("window.setWindUnit && setWindUnit('${windUnit.name}')", null) },
        onRelease = { it.destroy() },
    )
}

/**
 * Serves files from the app's assets at https://appassets.androidx.com/assets/..., giving the page a real
 * https origin (needed for fetch() and web workers). Everything else goes to the network as usual.
 */
private fun serveBundledAsset(context: Context, uri: Uri): WebResourceResponse? {
    if (uri.host != ASSET_HOST) return null
    val path = uri.path?.removePrefix("/assets/") ?: return null
    val mimeType = when (path.substringAfterLast('.')) {
        "html" -> "text/html"
        "js" -> "text/javascript"
        "css" -> "text/css"
        else -> "application/octet-stream"
    }
    return try {
        WebResourceResponse(mimeType, "utf-8", context.assets.open(path))
    } catch (e: java.io.IOException) {
        Log.w(TAG, "Missing bundled asset: $path", e)
        WebResourceResponse(mimeType, "utf-8", 404, "Not Found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
    }
}
