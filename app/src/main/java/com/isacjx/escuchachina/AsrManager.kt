package com.isacjx.escuchachina

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Motor de reconocimiento de voz chino, 100% en el teléfono.
 *
 * Cómo funciona: el VAD (silero) detecta cuándo alguien habla y cuándo se calla.
 * Cada vez que termina una frase (hay un silencio), ese pedazo de audio se pasa
 * al modelo SenseVoice, que devuelve el texto en chino. Por eso el texto aparece
 * 1-2 segundos después de cada frase, no letra por letra.
 */
class AsrManager(context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
    }

    data class Segment(val startSec: Float, val chinese: String)

    private val recognizer: OfflineRecognizer
    private val vad: Vad

    init {
        val modelConfig = OfflineModelConfig(
            senseVoice = OfflineSenseVoiceModelConfig(
                model = ModelManager.modelPath(context),
                language = "zh",                       // prioriza chino mandarín
                useInverseTextNormalization = true,    // "veinte" -> "20", etc.
            ),
            tokens = ModelManager.tokensPath(context),
            numThreads = 2,
            debug = false,
        )

        val recConfig = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = modelConfig,
        )
        recognizer = OfflineRecognizer(config = recConfig)

        val vadConfig = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = ModelManager.vadPath(context),
                threshold = 0.5f,
                minSilenceDuration = 0.3f,   // 0.3 s de silencio cierra la frase
                minSpeechDuration = 0.25f,
                maxSpeechDuration = 15f,     // corta frases muy largas a 15 s
                windowSize = 512,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
        )
        vad = Vad(config = vadConfig)
    }

    /** True mientras el VAD cree que alguien está hablando (para el indicador en pantalla). */
    fun isSpeaking(): Boolean = vad.isSpeechDetected()

    /**
     * Entra audio nuevo (en float -1..1). Devuelve las frases que se completaron
     * con este audio (puede ser lista vacía si la persona sigue hablando).
     */
    fun process(samples: FloatArray): List<Segment> {
        vad.acceptWaveform(samples)
        val out = ArrayList<Segment>()
        while (!vad.empty()) {
            val seg = vad.front()
            val stream = recognizer.createStream()
            stream.acceptWaveform(seg.samples, SAMPLE_RATE)
            recognizer.decode(stream)
            val text = recognizer.getResult(stream).text.trim()
            stream.release()
            vad.pop()
            if (text.isNotEmpty()) {
                out.add(Segment(startSec = seg.start.toFloat() / SAMPLE_RATE, chinese = text))
            }
        }
        return out
    }

    /** Al parar: procesa lo que quede en el buffer del VAD. */
    fun flush(): List<Segment> {
        vad.flush()
        val out = ArrayList<Segment>()
        while (!vad.empty()) {
            val seg = vad.front()
            val stream = recognizer.createStream()
            stream.acceptWaveform(seg.samples, SAMPLE_RATE)
            recognizer.decode(stream)
            val text = recognizer.getResult(stream).text.trim()
            stream.release()
            vad.pop()
            if (text.isNotEmpty()) {
                out.add(Segment(startSec = seg.start.toFloat() / SAMPLE_RATE, chinese = text))
            }
        }
        return out
    }

    fun release() {
        try { vad.release() } catch (_: Throwable) {}
        try { recognizer.release() } catch (_: Throwable) {}
    }
}
