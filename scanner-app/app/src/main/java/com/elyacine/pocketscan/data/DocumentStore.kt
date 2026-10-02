package com.elyacine.pocketscan.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A scanned PDF plus the JPEG of its first page, used as a thumbnail. */
data class ScannedDoc(val pdf: File, val thumbnail: File?) {
    val name: String get() = pdf.nameWithoutExtension
    val modified: Long get() = pdf.lastModified()
    val sizeBytes: Long get() = pdf.length()
}

/** Keeps scanned PDFs in the app's private storage. Nothing leaves the phone unless the user shares it. */
class DocumentStore(private val context: Context) {

    private val dir = File(context.filesDir, "documents").apply { mkdirs() }

    fun list(): List<ScannedDoc> =
        (dir.listFiles { f -> f.extension == "pdf" } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .map { ScannedDoc(it, thumbnailFor(it).takeIf(File::exists)) }

    /** Copies the scanner output into storage and returns the saved document. */
    fun save(pdfUri: Uri, firstPageUri: Uri?): ScannedDoc {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(Date())
        val pdf = uniqueFile("Scan $stamp")
        copy(pdfUri, pdf)
        firstPageUri?.let { copy(it, thumbnailFor(pdf)) }
        return ScannedDoc(pdf, thumbnailFor(pdf).takeIf(File::exists))
    }

    /** Returns false when the name is empty or already taken. */
    fun rename(doc: ScannedDoc, newName: String): Boolean {
        val clean = newName.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
        if (clean.isEmpty()) return false
        val target = File(dir, "$clean.pdf")
        if (target.exists()) return false
        val oldThumb = thumbnailFor(doc.pdf)
        if (!doc.pdf.renameTo(target)) return false
        if (oldThumb.exists()) oldThumb.renameTo(thumbnailFor(target))
        return true
    }

    fun delete(doc: ScannedDoc) {
        doc.pdf.delete()
        thumbnailFor(doc.pdf).delete()
    }

    private fun thumbnailFor(pdf: File) = File(dir, pdf.nameWithoutExtension + ".jpg")

    private fun uniqueFile(base: String): File {
        var file = File(dir, "$base.pdf")
        var n = 2
        while (file.exists()) file = File(dir, "$base ($n).pdf").also { n++ }
        return file
    }

    private fun copy(from: Uri, to: File) {
        context.contentResolver.openInputStream(from)!!.use { input ->
            to.outputStream().use { input.copyTo(it) }
        }
    }
}
