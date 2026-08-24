package com.domedav.mavjegy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domedav.mavjegy.data.MavApi
import com.domedav.mavjegy.data.Purchase
import com.domedav.mavjegy.ui.screens.BuyScreen
import com.domedav.mavjegy.ui.screens.LoginScreen
import com.domedav.mavjegy.ui.screens.TicketDetailScreen
import com.domedav.mavjegy.ui.screens.TicketsScreen
import com.domedav.mavjegy.ui.theme.MavJegyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kötelezően álló képernyő
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        val app = application as MavJegyApp
        val tokenStore = app.tokenStore
        val api = app.api
        setContent {
            MavJegyTheme(dynamicColor = true) {
                // Ha mégsem álló a tájolás, szóljon az app, hogy forgatás
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    RotatePrompt()
                    return@MavJegyTheme
                }
                var loggedIn by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    loggedIn = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { tokenStore.hasToken() || tokenStore.hasCredentials() }
                            .getOrElse { false }
                    }
                }
                if (loggedIn) {
                    LaunchedEffect(Unit) {
                        if (tokenStore.isDemo()) return@LaunchedEffect
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { api.ensureSession() }.getOrElse { false }
                        }
                        if (!ok && !tokenStore.hasCredentials()) loggedIn = false
                    }
                    AppRoot(api)
                } else {
                    LoginScreen(
                        api = api,
                        onLoggedIn = { loggedIn = true }
                    )
                }
            }
        }
    }
}

@Composable
fun RotatePrompt() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.ConfirmationNumber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                "Fordítsd álló helyzetbe a készüléket!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AppRoot(api: MavApi) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var detailPurchase by remember { mutableStateOf<Purchase?>(null) }

    if (detailPurchase != null) {
        val purchase = detailPurchase!!
        // System back: visszatér a jegylistába, nem lép ki az appból
        androidx.activity.compose.BackHandler {
            detailPurchase = null
        }
        TicketDetailScreen(
            api = api,
            purchase = purchase,
            onBack = { detailPurchase = null }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = selectedTab,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
            modifier = Modifier.fillMaxSize(),
            label = "rootSwitch"
        ) { tab ->
            when (tab) {
                1 -> BuyScreen()
                else -> TicketsScreen(api = api, onOpenDetail = { detailPurchase = it })
            }
        }

        // M3 Expressive floating toolbar - jobboldali vertikális pill, alulra rögzítve,
        // húzható selection karika - húzás közben folytonos színátmenettel
        val itemSizePx = with(LocalDensity.current) { 58.dp.toPx() }
        val dragOffset = remember { Animatable(0f) } // -1..1 skálán a kettő ikon közt
        val scope = rememberCoroutineScope()

        LaunchedEffect(selectedTab) {
            dragOffset.animateTo(
                if (selectedTab == 0) 0f else itemSizePx,
                spring(dampingRatio = Spring.DampingRatioLowBouncy)
            )
        }

        // A selection karika MINDIG primary és MINDIG a kiválasztott elem alatt van:
        // csak a pozíciója csúszik (spring), a színek soha nem cserélnek át –
        // a kiválasztott ikon onPrimary, a másik onSurfaceVariant
        val circleColor = MaterialTheme.colorScheme.primary
        val onCircleColor = MaterialTheme.colorScheme.onPrimary
        val pillColor = MaterialTheme.colorScheme.surfaceContainerHighest

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = 8.dp)
                .shadow(6.dp, CircleShape),
            shape = CircleShape,
            color = pillColor,
            tonalElevation = 3.dp
        ) {
            Box(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
            ) {
                // húzható selection karika (draw over, iPhone-szerű)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(circleColor)
                )
                Column(
                    modifier = Modifier.pointerInput(itemSizePx) {
                        detectDragGestures(
                            onDragStart = { },
                            onDragEnd = {
                                // snap: amelyik ikonhoz közelebb ért
                                val target = if (dragOffset.value > itemSizePx / 2f) 1 else 0
                                selectedTab = target
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val new = (dragOffset.value + dragAmount.y)
                                .coerceIn(0f, itemSizePx)
                            scope.launch { dragOffset.snapTo(new) }
                        }
                    }
                ) {
                    listOf(
                        Icons.Rounded.ConfirmationNumber to "Jegyek",
                        Icons.Rounded.ShoppingCart to "Vásárlás"
                    ).forEachIndexed { index, (icon, label) ->
                        val selected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .clickable { selectedTab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (selected) onCircleColor
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
