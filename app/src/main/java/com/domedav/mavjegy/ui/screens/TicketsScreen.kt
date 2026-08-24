package com.domedav.mavjegy.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domedav.mavjegy.data.MavApi
import com.domedav.mavjegy.data.Purchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val huDate = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale("hu"))

private val isoFormats = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd"
)

internal fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    return try {
        var parsed: Date? = null
        for (fmt in isoFormats) {
            try {
                parsed = SimpleDateFormat(fmt, Locale.US).apply { isLenient = false }.parse(iso)
                break
            } catch (_: Exception) {}
        }
        parsed?.let { huDate.format(it) } ?: "-"
    } catch (_: Exception) { "-" }
}

private const val CACHE_FILE = "purchases_cache.json"

private fun readCache(context: Context): List<Purchase> = try {
    val raw = context.getFileStreamPath(CACHE_FILE).takeIf { it.exists() }
        ?.bufferedReader()?.use { it.readText() } ?: return emptyList()
    val arr = JSONArray(raw)
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        try {
            Purchase(
                id = o.getString("id"),
                validFrom = o.optString("validFrom", "").ifEmpty { null },
                validTo = o.optString("validTo", "").ifEmpty { null },
                startStation = o.optString("startStation", "").ifEmpty { null },
                endStation = o.optString("endStation", "").ifEmpty { null },
                status = o.optString("status", ""),
                takenOver = o.optBoolean("takenOver", false),
                amount = o.optDouble("amount", 0.0),
                currency = o.optString("currency", "HUF")
            )
        } catch (_: Exception) { null }
    }
} catch (_: Exception) { emptyList() }

private fun writeCache(context: Context, purchases: List<Purchase>) = try {
    val arr = JSONArray()
    purchases.forEach { p ->
        arr.put(
            org.json.JSONObject().apply {
                put("id", p.id)
                put("validFrom", p.validFrom ?: "")
                put("validTo", p.validTo ?: "")
                put("startStation", p.startStation ?: "")
                put("endStation", p.endStation ?: "")
                put("status", p.status)
                put("takenOver", p.takenOver)
                put("amount", p.amount)
                put("currency", p.currency)
            }
        )
    }
    context.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE).use { it.write(arr.toString().toByteArray()) }
} catch (_: Exception) {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(api: MavApi, onOpenDetail: (Purchase) -> Unit = {}) {
    var tab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var purchases by remember { mutableStateOf<List<Purchase>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        purchases = withContext(Dispatchers.IO) { readCache(context) }
        loading = true
        try {
            val fresh = withContext(Dispatchers.IO) { api.getPurchases() }
            purchases = fresh
            withContext(Dispatchers.IO) { writeCache(context, fresh) }
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    fun refresh() {
        loading = true
        scope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { api.getPurchases() }
                purchases = fresh
                withContext(Dispatchers.IO) { writeCache(context, fresh) }
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (loading) 360f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "refreshSpin"
    )

    val valid = purchases.filter { it.status.trim().equals("Ervenyes", ignoreCase = true) }
    val expired = purchases.filter { !it.status.trim().equals("Ervenyes", ignoreCase = true) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jegyek",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { refresh() }, enabled = !loading) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Frissítés",
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            androidx.compose.animation.AnimatedVisibility(loading) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Érvényes") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Lejárt") })
            }
            error?.let {
                Text(
                    text = "Hiba: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            val list = if (tab == 0) valid else expired
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 96.dp + WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(list, key = { it.id }) { purchase ->
                    PurchaseCard(purchase = purchase, onClick = { onOpenDetail(purchase) })
                }
            }
        }
    }
}

@Composable
private fun PurchaseCard(purchase: Purchase, onClick: () -> Unit) {
    val isValid = purchase.status.trim().equals("Ervenyes", ignoreCase = true)
    val isPass = purchase.startStation == null
    Card(
        shape = if (isValid) RoundedCornerShape(28.dp) else RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isValid)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        border = if (!isValid) androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline
        ) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPass)
                        Icons.Rounded.CalendarMonth
                    else
                        Icons.Rounded.Train,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isPass) "Bérlet" else "Jegy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!isPass) {
                    Text(
                        "${purchase.startStation} → ${purchase.endStation ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                if (purchase.validFrom != null || purchase.validTo != null) {
                    Text(
                        "${formatDate(purchase.validFrom)} – ${formatDate(purchase.validTo)}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = if (purchase.currency == "HUF")
                        "%.0f HUF".format(purchase.amount)
                    else
                        "%.0f %s".format(purchase.amount, purchase.currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
