package com.domedav.mavjegy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.domedav.mavjegy.data.MavApi
import com.domedav.mavjegy.data.Purchase
import com.domedav.mavjegy.util.BarcodeGenerator
import com.domedav.mavjegy.util.TicketDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(api: MavApi, purchase: Purchase, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var ticketData by remember { mutableStateOf<com.domedav.mavjegy.data.TicketData?>(null) }
    var barcodeType by remember { mutableStateOf(BarcodeGenerator.Type.AZTEC) }
    var barcode by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(purchase.id) {
        try {
            val details = api.getTicketDetails(purchase.id)
            ticketData = details.ticketData
        } catch (_: Exception) {
            ticketData = null
        }
    }

    LaunchedEffect(ticketData?.serializedTicketData, barcodeType) {
        val serialized = ticketData?.serializedTicketData ?: return@LaunchedEffect
        try {
            val decoded = TicketDecoder.decodeSerialized(serialized)
            val content = decoded?.barcodeContent
            barcode = content?.let {
                BarcodeGenerator.generate(it, barcodeType, 800, 800)
            }
        } catch (_: Exception) {
            barcode = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Vissza")
                }
                Text(
                    "Jegy részletei",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ConfirmationNumber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            text = if (purchase.startStation == null) "Bérlet" else "Jegy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    InfoRow("Érvényesség kezdete", formatDate(purchase.validFrom))
                    InfoRow("Érvényesség vége", formatDate(purchase.validTo))
                    ticketData?.jegySorszam?.let { InfoRow("Jegysorszám", it) }
                    Text(
                        text = "%.0f %s".format(purchase.amount, purchase.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = barcodeType == BarcodeGenerator.Type.AZTEC,
                    onClick = { barcodeType = BarcodeGenerator.Type.AZTEC },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Aztec") }
                SegmentedButton(
                    selected = barcodeType == BarcodeGenerator.Type.CODE128,
                    onClick = { barcodeType = BarcodeGenerator.Type.CODE128 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Code128") }
            }

            val bmp = barcode
            if (bmp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // render at ~60% of the generated bitmap's natural size so it can be
                    // scrolled under an external reader device
                    val density = LocalDensity.current
                    val width = with(density) { bmp.width.toDp() } * 0.6f
                    Image(
                        bitmap = bmp,
                        contentDescription = "Vonalkód",
                        modifier = Modifier
                            .width(width)
                            .aspectRatio(bmp.width.toFloat() / bmp.height)
                    )
                }
            } else {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ConfirmationNumber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = ticketData?.jegySorszam
                                ?.let { "Vonalkód nem elérhető.\nJegysorszám: $it" }
                                ?: "Nincs megjeleníthető vonalkód.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value ?: "-", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
