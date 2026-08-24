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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

@Composable
private fun ValiditySubtitle(purchase: Purchase, isPass: Boolean) {
    val now = LocalDateTime.now()
    val to = parseIso(purchase.validTo)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isPass) {
            // Bérlet: hátralévő napok
            if (to != null && !to.isBefore(now)) {
                val days = ChronoUnit.DAYS.between(now.toLocalDate(), to.toLocalDate().plusDays(1)).coerceAtLeast(0)
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "$days napig érvényes",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        } else {
            // Jegy: melyik napon/időpontban érvényes
            purchase.validFrom?.takeIf { it.isNotBlank() }?.let { vf ->
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    formatDate(vf),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}

private fun parseIso(iso: String?): LocalDateTime? {
    if (iso.isNullOrBlank()) return null
    for (fmt in isoFormats) {
        try {
            return SimpleDateFormat(fmt, Locale.US).apply { isLenient = false }.parse(iso)
                ?.let { java.time.Instant.ofEpochMilli(it.time).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }
        } catch (_: Exception) {}
    }
    return null
}

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
                currency = o.optString("currency", "HUF"),
                name = o.optString("name", "").ifEmpty { null }
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
                put("name", p.name ?: "")
            }
        )
    }
    context.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE).use { it.write(arr.toString().toByteArray()) }
} catch (_: Exception) {}

@Composable
fun TicketsScreen(api: MavApi, onOpenDetail: (Purchase) -> Unit = {}) {
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

    Scaffold(
        snackbarHost = {
            // Saját, eltűntethető snackbar: swipe balra/jobbra vagy lehúzás
            Box(modifier = Modifier.fillMaxWidth()) {
                error?.let { err ->
                    com.domedav.mavjegy.ui.components.DismissibleSnackbar(
                        message = "Hiba: $err",
                        onDismiss = { error = null },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                items(valid, key = { it.id }) { purchase ->
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
            containerColor = when {
                !isValid -> MaterialTheme.colorScheme.surfaceContainerHighest
                isPass -> MaterialTheme.colorScheme.primaryContainer      // bérlet: primary
                else -> MaterialTheme.colorScheme.tertiaryContainer       // jegy: tertiary
            }
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
                        color = if (isPass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
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
                    tint = if (isPass) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Pontos név – jegynél ha az API nem ad nevet, semmit nem írunk ki
                val titleText = if (isPass) "Bérlet" else purchase.name
                if (!titleText.isNullOrBlank()) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
                ValiditySubtitle(purchase = purchase, isPass = isPass)
            }
            Surface(
                shape = CircleShape,
                color = if (isPass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            ) {
                Text(
                    text = if (purchase.currency == "HUF")
                        "%.0f Ft".format(purchase.amount)
                    else
                        "%.0f %s".format(purchase.amount, purchase.currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isPass) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
