package ae.sayyara.app.platform

import ae.sayyara.core.ocr.OdometerParser
import ae.sayyara.core.ocr.ReceiptFields
import ae.sayyara.core.ocr.ReceiptParser
import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * On-device OCR (ML Kit Text Recognition v2, Latin script) for fuel receipts and odometer photos.
 * The raw text goes to the core parsers. Arabic script is not recognised by the Latin model, but the
 * digits and English labels on ADNOC/ENOC/Emarat receipts are; a server fallback is a later phase.
 * Create one per screen and [close] it when done.
 */
class ReceiptScanner(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    data class ReceiptScan(val rawText: String, val fields: ReceiptFields)

    /** Throws IOException if the Uri cannot be read, or the ML Kit exception if recognition fails. */
    suspend fun scanReceipt(uri: Uri): ReceiptScan {
        val text = recognize(uri)
        return ReceiptScan(text, ReceiptParser.parse(text))
    }

    /** Best odometer candidate in km, using [lastOdometerKm] to reject implausible readings. */
    suspend fun scanOdometer(uri: Uri, lastOdometerKm: Double?): Double? =
        OdometerParser.parse(recognize(uri), lastOdometerKm)

    suspend fun recognize(uri: Uri): String {
        // fromFilePath decodes and downsamples the bitmap: do it off the main thread.
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(appContext, uri) }
        return process(image).text
    }

    private suspend fun process(image: InputImage): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
            .addOnCanceledListener { cont.cancel() }
    }

    override fun close() = recognizer.close()
}
