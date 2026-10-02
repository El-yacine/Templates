package com.elyacine.pocketscan.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elyacine.pocketscan.data.QrHistory
import com.elyacine.pocketscan.data.QrResult
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val history = remember { QrHistory(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var scans by remember { mutableStateOf(history.list()) }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder().enableAutoZoom().build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code ->
                val value = code.rawValue ?: return@addOnSuccessListener
                history.add(QrResult(value, kindOf(code), System.currentTimeMillis()))
                scans = history.list()
            }
            .addOnFailureListener {
                scope.launch { snackbar.showSnackbar("Scanner not available: ${it.message}") }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR & barcodes") },
                actions = {
                    IconButton(enabled = scans.isNotEmpty(), onClick = {
                        history.clear()
                        scans = emptyList()
                    }) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Button(onClick = ::scan, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Scan QR or barcode", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (scans.isEmpty()) {
                item {
                    Text(
                        "Scanned codes appear here. Works offline, no camera permission needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            items(scans, key = { it.time }) { result ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                result.kind + " · " + DateUtils.getRelativeTimeSpanString(result.time),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(result.value, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (result.kind == "Link") {
                            IconButton(onClick = { context.openLink(result.value) }) {
                                Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Open")
                            }
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(result.value))
                            scope.launch { snackbar.showSnackbar("Copied") }
                        }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy") }
                        IconButton(onClick = { context.shareText(result.value) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                    }
                }
            }
        }
    }
}

private fun kindOf(code: Barcode): String = when (code.valueType) {
    Barcode.TYPE_URL -> "Link"
    Barcode.TYPE_WIFI -> "Wi-Fi"
    Barcode.TYPE_PHONE -> "Phone"
    Barcode.TYPE_EMAIL -> "Email"
    Barcode.TYPE_CONTACT_INFO -> "Contact"
    Barcode.TYPE_PRODUCT, Barcode.TYPE_ISBN -> "Product"
    else -> "Text"
}
