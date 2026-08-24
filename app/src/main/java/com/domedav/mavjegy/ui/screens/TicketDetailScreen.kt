package com.domedav.mavjegy.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.domedav.mavjegy.data.MavApi
import com.domedav.mavjegy.data.Purchase
import com.domedav.mavjegy.data.TicketCache
import com.domedav.mavjegy.data.TicketDetails
import com.domedav.mavjegy.util.BarcodeGenerator
import com.domedav.mavjegy.util.TicketDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    api: MavApi,
    purchase: Purchase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var details by remember { mutableStateOf<TicketDetails?>(null) }
    var loadingDetails by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasCached by remember { mutableStateOf(false) }
    var fetchTrigger by remember { mutableStateOf(0) }

    var barcodeBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var generatingBarcode by remember { mutableStateOf(true) }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(purchase.id, fetchTrigger) {
        loadingDetails = true
        errorMessage = null
        val cached = withContext(Dispatchers.IO) {
            runCatching { TicketCache.load(context, purchase.id) }.getOrNull()
        }
        if (cached != null) {
            hasCached = true
            details = cached
        }
        try {
            val fetched = api.getTicketDetails(purchase.id)
            details = fetched
            withContext(Dispatchers.IO) {
                runCatching { TicketCache.save(context, purchase.id, fetched) }
            }
            errorMessage = null
        } catch (e: Exception) {
            if (!hasCached) errorMessage = e.message ?: e.javaClass.simpleName
        }
        loadingDetails = false
    }

    val serialized = details?.ticketData?.serializedTicketData

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(details, purchase)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
                actions = {
                    if (errorMessage != null) {
                        IconButton(onClick = { fetchTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Újrapróbálás")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val screenWpx = with(density) { maxWidth.toPx() }.toInt()
            val barcodeTargetPx = (screenWpx * 2).coerceAtLeast(256)

            val screenW = constraints.maxWidth.toFloat()
            val screenH = constraints.maxHeight.toFloat()

            // Barcode zone content inset 25% from each horizontal edge (~50% screen width glyph)
            val zoneHorizontalInset = screenW * 0.25f
            val zoneInnerW = screenW - zoneHorizontalInset * 2f
            val zoneH = (screenH - with(density) { 220.dp.toPx() })
                .coerceAtLeast(with(density) { 120.dp.toPx() })
            val displaySize = minOf(zoneInnerW, zoneH)

            LaunchedEffect(serialized, barcodeTargetPx, fetchTrigger) {
                generatingBarcode = true
                if (serialized.isNullOrBlank()) {
                    generatingBarcode = false
                    return@LaunchedEffect
                }
                val decoded = withContext(Dispatchers.Default) {
                    runCatching { TicketDecoder.decodeSerialized(serialized) }.getOrNull()
                }
                val content = decoded?.barcodeContent
                if (content.isNullOrBlank()) {
                    generatingBarcode = false
                    return@LaunchedEffect
                }
                barcodeBitmap = withContext(Dispatchers.Default) {
                    try {
                        BarcodeGenerator.generate(content, BarcodeGenerator.Type.AZTEC, barcodeTargetPx, barcodeTargetPx)
                    } catch (_: Exception) {
                        null
                    }
                }
                generatingBarcode = false
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (loadingDetails && details == null && errorMessage == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    return@Column
                }

                if (errorMessage != null && !hasCached && details == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "Ismeretlen hiba",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { fetchTrigger++ }) {
                                Text("Újrapróbálás")
                            }
                        }
                    }
                    return@Column
                }

                details?.let { d ->
                    val h = displaySize * scale
                    val maxPanX =
                        if (scale > 1f) ((displaySize * scale - zoneInnerW) / 2f).coerceAtLeast(0f) else 0f
                    val maxPanYUp = (0.10f * h).coerceAtLeast(0.10f * displaySize)
                    val maxPanYDown = (0.60f * h).coerceAtLeast(0.60f * displaySize)

                    fun clampOffsetY(y: Float): Float = y.coerceIn(-maxPanYUp, maxPanYDown)

                    // BARCODE ZONE (top, weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(40.dp))
                            .background(MaterialTheme.colorScheme.inverseSurface)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                )
                            }
                            .pointerInput(displaySize, zoneInnerW) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    val mpx =
                                        if (scale > 1f) {
                                            ((displaySize * scale - zoneInnerW) / 2f).coerceAtLeast(0f)
                                        } else 0f
                                    offsetX = if (scale > 1f) {
                                        (offsetX + pan.x).coerceIn(-mpx, mpx)
                                    } else 0f
                                    offsetY = clampOffsetY(offsetY + pan.y)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = with(density) { zoneHorizontalInset.toDp() }),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = barcodeBitmap
                            when {
                                bitmap != null -> Image(
                                    bitmap = bitmap,
                                    contentDescription = "Vonalkód",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .width(with(density) { displaySize.toDp() })
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offsetX
                                            translationY = offsetY
                                        }
                                )

                                generatingBarcode -> CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )

                                else -> Text(
                                    text = "Vonalkód nem elérhető",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // INFO CARD (below)
                    InfoAndValidityCard(details = d, purchase = purchase)
                }
            }
        }
    }
}

@Composable
private fun InfoAndValidityCard(details: TicketDetails, purchase: Purchase) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconValueRow(
                icon = Icons.Rounded.ConfirmationNumber,
                value = titleFor(details, purchase)
            )

            IconValueRow(
                icon = Icons.Rounded.Sell,
                value = buildString {
                    append("%.0f".format(Locale.US, purchase.amount))
                    if (purchase.currency.isNotBlank()) append(" ${purchase.currency}")
                }
            )

            val validityText = validityText(purchase)
            if (validityText != null) {
                IconValueRow(icon = Icons.Rounded.Schedule, value = validityText)
            }
        }
    }
}

@Composable
private fun IconValueRow(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp)
                )
                .padding(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.height(20.dp).width(20.dp)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private fun parseDate(iso: String?): LocalDateTime? {
    if (iso.isNullOrBlank()) return null
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"
    )
    for (f in formats) {
        try {
            return ZonedDateTime.parse(iso, DateTimeFormatter.ofPattern(f)).toLocalDateTime()
        } catch (_: Exception) {}
    }
    return try {
        LocalDate.parse(iso).atStartOfDay()
    } catch (_: Exception) { null }
}

private fun validityText(purchase: Purchase): String? {
    val to = parseDate(purchase.validTo) ?: return null
    val now = LocalDateTime.now()
    return if (!to.isBefore(now)) {
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), to.toLocalDate().plusDays(1)).coerceAtLeast(0)
        "még $days nap érvényes"
    } else {
        val from = parseDate(purchase.validFrom) ?: return null
        val f = DateTimeFormatter.ofPattern("yyyy.MM.dd.", Locale("hu"))
        "${from.format(f)} – ${to.format(f)}"
    }
}

private fun titleFor(details: TicketDetails?, purchase: Purchase): String =
    details?.ajanlatNev?.takeIf { it.isNotBlank() }
        ?: if (purchase.startStation == null) "Bérlet" else "Jegy"
