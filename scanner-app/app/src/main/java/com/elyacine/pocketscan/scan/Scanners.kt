package com.elyacine.pocketscan.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Returns a function that opens Google's on-device document scanner
 * (edge detection, crop, cleanup filters). It is free and needs no API key.
 */
@Composable
fun rememberDocumentScanner(
    pageLimit: Int,
    wantPdf: Boolean,
    onResult: (GmsDocumentScanningResult) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val activity = LocalContext.current.findActivity()
    val currentOnError = rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.let(onResult)
        }
    }
    return {
        val formats = if (wantPdf) {
            intArrayOf(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        } else {
            intArrayOf()
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, *formats)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener {
                currentOnError.value("Scanner not available: ${it.message ?: "please update Google Play services"}")
            }
    }
}

/** Reads all text in an image, on the phone. */
suspend fun recognizeText(context: Context, imageUri: Uri): String {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        recognizer.process(InputImage.fromFilePath(context, imageUri)).await().text
    } finally {
        recognizer.close()
    }
}

fun Context.findActivity(): Activity {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("No activity found")
}
