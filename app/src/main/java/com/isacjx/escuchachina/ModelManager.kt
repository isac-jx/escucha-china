package com.isacjx.escuchachina

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelManager {

    data class Asset(
        val name: String,
        val url: String,
        val approxBytes: Long,
        val minBytes: Long,
    )

    private val ASSETS = listOf(
        Asset(
            "model.int8.onnx",
            "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/model.int8.onnx",
            239_000_000L,
            150_000_000L
        ),
        Asset(
            "tokens.txt",
            "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main/tokens.txt",
            316_000L,
            100_000L
        ),
        Asset(
            "silero_vad.onnx",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            2_300_000L,
            1_000_000L
        )
    )

    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun modelPath(context: Context) = File(modelsDir(context), "model.int8.onnx").absolutePath
    fun tokensPath(context: Context) = File(modelsDir(context), "tokens.txt").absolutePath
    fun vadPath(context: Context) = File(modelsDir(context), "silero_vad.onnx").absolutePath

    fun isReady(context: Context): Boolean {
        val dir = modelsDir(context)
        return ASSETS.all { a ->
            val f = File(dir, a.name)
            f.exists() && f.length() >= a.minBytes
        }
    }

    fun interface Progress {
        fun update(fileLabel: String, fraction: Float, overallIndex: Int, overallTotal: Int)
    }

    fun downloadMissing(context: Context, progress: Progress) {
        val dir = modelsDir(context)
        val total = ASSETS.size
        ASSETS.forEachIndexed { index, asset ->
            val dest = File(dir, asset.name)
            if (dest.exists() && dest.length() >= asset.minBytes) {
                progress.update(asset.name, 1f, index + 1, total)
                return@forEachIndexed
            }
            var lastError: Throwable? = null
            for (attempt in 1..3) {
                try {
                    downloadOne(asset, dest) { frac ->
                        progress.update(asset.name, frac, index + 1, total)
                    }
                    lastError = null
                    break
                } catch (e: Throwable) {
                    lastError = e
                }
            }
            if (lastError != null) throw lastError
        }
    }

    private fun downloadOne(asset: Asset, dest: File, onFraction: (Float) -> Unit) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        if (tmp.exists()) tmp.delete()

        var currentUrl = asset.url
        var conn: HttpURLConnection? = null
        var redirects = 0
        try {
            while (true) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "EscuchaChina/1.0 (Android)")
                    setRequestProperty("Accept", "*/*")
                }
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val loc = conn.getHeaderField("Location")
                        ?: throw RuntimeException("Reenvio sin destino al bajar ${asset.name}")
                    conn.disconnect()
                    currentUrl = if (loc.startsWith("http")) loc
                                 else URL(URL(currentUrl), loc).toString()
                    redirects++
                    if (redirects > 8) throw RuntimeException("Demasiados reenvios al bajar ${asset.name}")
                    continue
                }
                if (code !in 200..299) {
                    throw RuntimeException("Error $code al descargar ${asset.name}")
                }
                break
            }

            val length = conn!!.contentLengthLong.takeIf { it > 0 } ?: asset.approxBytes
            var readTotal = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        onFraction((readTotal.toFloat() / length).coerceIn(0f, 1f))
                    }
                }
            }

            if (readTotal < asset.minBytes) {
                tmp.delete()
                throw RuntimeException(
                    "${asset.name} bajo incompleto (${readTotal / 1_000_000} MB). Revisa tu internet e intenta otra vez."
                )
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
