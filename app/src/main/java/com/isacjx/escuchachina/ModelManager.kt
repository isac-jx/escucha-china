package com.isacjx.escuchachina

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descarga y ubica los modelos que la app usa SIN internet una vez instalados.
 *
 * Se descargan una sola vez (en la primera ejecución, con wifi) a la carpeta
 * interna de la app. Después todo funciona offline.
 *
 *  - model.int8.onnx  -> reconocimiento de voz chino (SenseVoice), ~239 MB
 *  - tokens.txt       -> vocabulario del modelo, ~316 kB
 *  - silero_vad.onnx  -> detecta cuándo alguien habla y cuándo calla, ~2 MB
 */
object ModelManager {

    data class Asset(val name: String, val url: String, val approxBytes: Long)

    private val ASSETS = listOf(
        Asset(
            "model.int8.onnx",
            "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx",
            239_000_000L
        ),
        Asset(
            "tokens.txt",
            "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/tokens.txt",
            316_000L
        ),
        Asset(
            "silero_vad.onnx",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            2_300_000L
        )
    )

    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun modelPath(context: Context) = File(modelsDir(context), "model.int8.onnx").absolutePath
    fun tokensPath(context: Context) = File(modelsDir(context), "tokens.txt").absolutePath
    fun vadPath(context: Context) = File(modelsDir(context), "silero_vad.onnx").absolutePath

    /** True cuando los tres archivos ya están descargados y con tamaño razonable. */
    fun isReady(context: Context): Boolean {
        val dir = modelsDir(context)
        return ASSETS.all { a ->
            val f = File(dir, a.name)
            // Al menos el 80% del tamaño esperado => descarga completa.
            f.exists() && f.length() >= (a.approxBytes * 0.8).toLong()
        }
    }

    /** Progreso de descarga: 0.0 a 1.0 por archivo, más un mensaje de estado. */
    fun interface Progress {
        fun update(fileLabel: String, fraction: Float, overallIndex: Int, overallTotal: Int)
    }

    /**
     * Descarga lo que falte. Lanza excepción si algo falla (sin internet, etc.).
     * Se debe llamar fuera del hilo principal.
     */
    fun downloadMissing(context: Context, progress: Progress) {
        val dir = modelsDir(context)
        val total = ASSETS.size
        ASSETS.forEachIndexed { index, asset ->
            val dest = File(dir, asset.name)
            if (dest.exists() && dest.length() >= (asset.approxBytes * 0.8).toLong()) {
                progress.update(asset.name, 1f, index + 1, total)
                return@forEachIndexed
            }
            downloadOne(asset, dest) { frac ->
                progress.update(asset.name, frac, index + 1, total)
            }
        }
    }

    private fun downloadOne(asset: Asset, dest: File, onFraction: (Float) -> Unit) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(asset.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("Error ${conn.responseCode} al descargar ${asset.name}")
            }
            val length = conn.contentLengthLong.takeIf { it > 0 } ?: asset.approxBytes
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        onFraction((readTotal.toFloat() / length).coerceIn(0f, 1f))
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            onFraction(1f)
        } finally {
            conn?.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }
}
