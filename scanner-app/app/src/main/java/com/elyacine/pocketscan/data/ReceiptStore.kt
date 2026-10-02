package com.elyacine.pocketscan.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

data class Receipt(
    val id: String = UUID.randomUUID().toString(),
    val merchant: String,
    val total: Double,
    /** yyyy-MM-dd */
    val date: String,
    val category: String,
    val imagePath: String?,
)

val receiptCategories = listOf(
    "Groceries", "Restaurant", "Transport", "Shopping", "Health", "Bills", "Other",
)

/** Stores receipts as a small JSON file in the app's private storage. */
class ReceiptStore(private val context: Context) {

    private val file = File(context.filesDir, "receipts.json")
    private val imageDir = File(context.filesDir, "receipts").apply { mkdirs() }

    fun list(): List<Receipt> {
        if (!file.exists()) return emptyList()
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Receipt(
                id = o.getString("id"),
                merchant = o.optString("merchant"),
                total = o.optDouble("total", 0.0),
                date = o.optString("date"),
                category = o.optString("category", "Other"),
                imagePath = o.optString("imagePath").ifEmpty { null },
            )
        }.sortedByDescending { it.date }
    }

    /** Adds the receipt, or replaces the one with the same id. */
    fun upsert(receipt: Receipt) = write(list().filterNot { it.id == receipt.id } + receipt)

    fun delete(receipt: Receipt) {
        receipt.imagePath?.let { File(it).delete() }
        write(list().filterNot { it.id == receipt.id })
    }

    /** Copies a scanned receipt photo into storage and returns its path. */
    fun saveImage(uri: Uri, id: String): String {
        val target = File(imageDir, "$id.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target.absolutePath
    }

    /** Writes all receipts to a CSV file that spreadsheet apps can open. */
    fun exportCsv(): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "receipts.csv")
        out.bufferedWriter().use { w ->
            w.write("date,merchant,category,total\n")
            list().sortedBy { it.date }.forEach { r ->
                w.write("${r.date},${csv(r.merchant)},${csv(r.category)},${String.format(Locale.ROOT, "%.2f", r.total)}\n")
            }
        }
        return out
    }

    private fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""

    private fun write(receipts: List<Receipt>) {
        val array = JSONArray()
        receipts.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("merchant", r.merchant)
                    .put("total", r.total)
                    .put("date", r.date)
                    .put("category", r.category)
                    .put("imagePath", r.imagePath ?: "")
            )
        }
        // Write then rename, so a crash mid-write never corrupts the list.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(file)
    }
}
