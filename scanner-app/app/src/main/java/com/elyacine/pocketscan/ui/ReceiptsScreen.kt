package com.elyacine.pocketscan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elyacine.pocketscan.data.Receipt
import com.elyacine.pocketscan.data.ReceiptStore
import com.elyacine.pocketscan.data.receiptCategories
import com.elyacine.pocketscan.receipt.ReceiptParser
import com.elyacine.pocketscan.scan.recognizeText
import com.elyacine.pocketscan.scan.rememberDocumentScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

fun formatMoney(amount: Double): String = String.format(Locale.getDefault(), "%,.2f", amount)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen() {
    val context = LocalContext.current
    val store = remember { ReceiptStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var receipts by remember { mutableStateOf(store.list()) }
    var editing by remember { mutableStateOf<Receipt?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }

    val scan = rememberDocumentScanner(
        pageLimit = 1,
        wantPdf = false,
        onResult = { result ->
            val page = result.pages?.firstOrNull() ?: return@rememberDocumentScanner
            reading = true
            scope.launch {
                val id = UUID.randomUUID().toString()
                val draft = withContext(Dispatchers.IO) {
                    runCatching {
                        val path = store.saveImage(page.imageUri, id)
                        val parsed = ReceiptParser.parse(recognizeText(context, page.imageUri))
                        Receipt(
                            id = id,
                            merchant = parsed.merchant.orEmpty(),
                            total = parsed.total ?: 0.0,
                            date = parsed.date ?: LocalDate.now().toString(),
                            category = "Groceries",
                            imagePath = path,
                        )
                    }
                }
                reading = false
                draft.onSuccess {
                    isNew = true
                    editing = it
                }.onFailure {
                    snackbar.showSnackbar("Could not read the receipt. Try again with more light.")
                }
            }
        },
        onError = { scope.launch { snackbar.showSnackbar(it) } },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipts") },
                actions = {
                    IconButton(onClick = {
                        isNew = true
                        editing = Receipt(
                            merchant = "", total = 0.0, date = LocalDate.now().toString(),
                            category = "Other", imagePath = null,
                        )
                    }) { Icon(Icons.Outlined.Add, contentDescription = "Add manually") }
                    IconButton(
                        enabled = receipts.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                val csv = withContext(Dispatchers.IO) { store.exportCsv() }
                                context.shareFile(csv, "text/csv")
                            }
                        },
                    ) { Icon(Icons.Outlined.FileDownload, contentDescription = "Export CSV") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!reading) scan() },
                icon = { Icon(Icons.Outlined.ReceiptLong, contentDescription = null) },
                text = { Text("Scan receipt") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (receipts.isEmpty()) {
                EmptyState(
                    icon = { Icon(Icons.Outlined.ReceiptLong, null, Modifier.size(64.dp)) },
                    title = "Track spending in one photo",
                    body = "Scan a receipt and the amount, shop and date are filled in for you.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { MonthSummary(receipts) }
                    items(receipts, key = { it.id }) { r ->
                        ReceiptRow(r) {
                            isNew = false
                            editing = r
                        }
                    }
                }
            }
            if (reading) {
                Card(Modifier.align(Alignment.Center)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Reading receipt…")
                    }
                }
            }
        }
    }

    editing?.let { receipt ->
        ReceiptDialog(
            initial = receipt,
            isNew = isNew,
            onSave = {
                store.upsert(it)
                receipts = store.list()
                editing = null
            },
            onDelete = {
                store.delete(receipt)
                receipts = store.list()
                editing = null
            },
            onDismiss = {
                // A scanned receipt that was never saved: drop its photo.
                if (isNew) receipt.imagePath?.let { File(it).delete() }
                editing = null
            },
        )
    }
}

@Composable
private fun MonthSummary(receipts: List<Receipt>) {
    val month = LocalDate.now().toString().take(7) // yyyy-MM
    val thisMonth = receipts.filter { it.date.startsWith(month) }
    val byCategory = thisMonth.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.total } }
        .entries.sortedByDescending { it.value }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("This month", style = MaterialTheme.typography.labelLarge)
            Text(
                formatMoney(thisMonth.sumOf { it.total }),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("${thisMonth.size} receipt(s)", style = MaterialTheme.typography.bodySmall)
            byCategory.take(4).forEach { (category, total) ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(category, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(formatMoney(total), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(receipt: Receipt, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(receipt.imagePath?.let { File(it) }, Modifier.size(width = 40.dp, height = 52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    receipt.merchant.ifBlank { "Receipt" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${receipt.date} · ${receipt.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatMoney(receipt.total), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReceiptDialog(
    initial: Receipt,
    isNew: Boolean,
    onSave: (Receipt) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var merchant by remember { mutableStateOf(initial.merchant) }
    var amount by remember {
        mutableStateOf(if (initial.total == 0.0) "" else String.format(Locale.ROOT, "%.2f", initial.total))
    }
    var date by remember { mutableStateOf(initial.date) }
    var category by remember { mutableStateOf(initial.category) }

    val parsedAmount = amount.replace(',', '.').toDoubleOrNull()
    val dateValid = runCatching { LocalDate.parse(date) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Check receipt" else "Edit receipt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Total") },
                    isError = parsedAmount == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Shop") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    isError = !dateValid,
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    receiptCategories.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedAmount != null && dateValid,
                onClick = {
                    onSave(
                        initial.copy(
                            merchant = merchant.trim(),
                            total = parsedAmount ?: 0.0,
                            date = date,
                            category = category,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
