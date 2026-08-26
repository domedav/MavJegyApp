package com.domedav.mavjegy.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

internal const val BUY_URL = "https://jegy.mav.hu"

fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BuyScreen(webViewState: MutableState<WebView?>) {
    var progress by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    var online by remember { mutableStateOf(isOnline(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Session-perzisztencia: kilépéskor / elhagyáskor elmentjük a WebView állapotát,
    // hogy app-újraindítás után is megmaradjon a bejelentkezés.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                webViewState.value?.let { WebViewSession.save(context, it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewState.value?.let { WebViewSession.save(context, it) }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (progress in 0f..1f) ProgressIndicatorDefaults.ProgressAnimationSpec else spring(
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "webProgress"
    )

    if (!online) {
        // Nincs internet – a WebView helyett értesítés + újrapróbálás
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = "Nincs internetkapcsolat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "A vásárláshoz internet szükséges",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(20.dp))
            Button(onClick = {
                online = isOnline(context)
                if (online) {
                    progress = 0f
                    webViewState.value?.loadUrl(BUY_URL)
                }
            }) {
                Text("Újrapróbálás")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (progress > 0f && progress < 1f) 1f else 0f)
        )
        AndroidView(
            factory = { ctx ->
                val existing = webViewState.value
                if (existing != null) {
                    // Újrahasználjuk a meglévő WebView-t (session + DOM megmarad)
                    existing
                } else {
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            var restored = false
                            override fun onPageFinished(view: WebView?, url: String?) {
                                cookies.flush()
                                WebViewSession.save(context, this@apply)
                                if (!restored && url?.contains("mav.hu") == true) {
                                    restored = true
                                    WebViewSession.restore(context, this@apply)
                                }
                                progress = 0f
                            }
                        }
                        loadUrl(BUY_URL)
                        webViewState.value = this
                        // Rendszeres mentés, hogy a session sose vesszen el
                        postDelayed(object : Runnable {
                            override fun run() {
                                WebViewSession.save(context, this@apply)
                                postDelayed(this, 20000)
                            }
                        }, 20000)
                    }
                }
            },
            update = { web ->
                web.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progress = newProgress / 100f
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
