package cl.lpa.agenda

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink

data class InkPt(val x: Float, val y: Float, val t: Long)

/** Convierte escritura a mano (espanol) en texto. El modelo se descarga una vez con internet. */
object InkRecognizer {
    private val model: DigitalInkRecognitionModel? by lazy {
        try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag("es")
                ?.let { DigitalInkRecognitionModel.builder(it).build() }
        } catch (e: Exception) {
            null
        }
    }

    private val recognizer: DigitalInkRecognizer? by lazy {
        model?.let { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build()) }
    }

    fun ensureModel(onDone: (Boolean) -> Unit = {}) {
        val m = model
        if (m == null) {
            onDone(false)
            return
        }
        RemoteModelManager.getInstance()
            .download(m, DownloadConditions.Builder().build())
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    fun recognize(strokes: List<List<InkPt>>, onResult: (String?) -> Unit) {
        val r = recognizer
        if (r == null) {
            onResult(null)
            return
        }
        val builder = Ink.builder()
        strokes.filter { it.isNotEmpty() }.forEach { s ->
            val sb = Ink.Stroke.builder()
            s.forEach { sb.addPoint(Ink.Point.create(it.x, it.y, it.t)) }
            builder.addStroke(sb.build())
        }
        r.recognize(builder.build())
            .addOnSuccessListener { res -> onResult(res.candidates.firstOrNull()?.text) }
            .addOnFailureListener { onResult(null) }
    }
}
