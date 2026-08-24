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
        enableEdgeToEdge()
        val app = application as MavJegyApp
        val tokenStore = app.tokenStore
        val api = app.api
        setContent {
            MavJegyTheme {
                var loggedIn by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    loggedIn = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { tokenStore.hasToken() || tokenStore.hasCredentials() }
                            .getOrElse { false }
                    }
                }
                if (loggedIn) {
                    LaunchedEffect(Unit) {
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
fun AppRoot(api: MavApi) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var detailPurchase by remember { mutableStateOf<Purchase?>(null) }

    if (detailPurchase != null) {
        val purchase = detailPurchase!!
        TicketDetailScreen(
            api = api,
            purchase = purchase,
            onBack = { detailPurchase = null }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.ConfirmationNumber, contentDescription = "Jegyek") },
                    label = { Text("Jegyek") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.ShoppingCart, contentDescription = "Vásárlás") },
                    label = { Text("Vásárlás") }
                )
            }
        }
    ) { padding ->
        Crossfade(
            targetState = selectedTab,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
            modifier = Modifier.padding(padding),
            label = "rootSwitch"
        ) { tab ->
            when (tab) {
                1 -> BuyScreen()
                else -> TicketsScreen(api = api, onOpenDetail = { detailPurchase = it })
            }
        }
    }
}
