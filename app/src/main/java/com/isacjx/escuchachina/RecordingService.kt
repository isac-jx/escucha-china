package com.isacjx.escuchachina

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servicio que graba en segundo plano: sigue aunque salgas a un juego o a Facebook
 * o apagues la pantalla. Reconoce el chino, lo traduce a español e inglés, y va
 * guardando todo. Al parar, guarda la conversación en el historial.
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"

        private const val CHANNEL_ID = "grabacion"
        private const val NOTIF_ID = 1001

        // --- Estado observable por la pantalla (sobrevive aunque salgas de la app) ---
        val running = MutableStateFlow(false)
        val speaking = MutableStateFlow(false)
        val elapsedSec = MutableStateFlow(0)
        val statusMsg = MutableStateFlow("")
        /** Frases de la conversación en curso, en vivo. */
        val liveSegments = MutableStateFlow<List<SegmentRecord>>(emptyList())
    }

    private val isRecording = AtomicBoolean(false)
    private var worker: Thread? = null

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }
            else -> startRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (isRecording.get()) return
        startForeground(NOTIF_ID, buildNotification())
        requestAudioFocus()       // silencia/pausa la otra app mientras grabas
        isRecording.set(true)
        running.value = true
        liveSegments.value = emptyList()
        elapsedSec.value = 0
        statusMsg.value = ""

        worker = Thread { recordLoop() }.apply { start() }
    }

    private fun recordLoop() {
        val sampleRate = AsrManager.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, sampleRate) // ~1 s de margen

        var source = MediaRecorder.AudioSource.VOICE_RECOGNITION
        var recorder = runCatching {
            AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        }.getOrNull()
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            source = MediaRecorder.AudioSource.MIC
            recorder = runCatching {
                AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            }.getOrNull()
        }
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            statusMsg.value = "No se pudo abrir el micrófono"
            stopSelfClean()
            return
        }

        val asr = try {
            AsrManager(applicationContext)
        } catch (e: Throwable) {
            statusMsg.value = "Error cargando el modelo de voz: ${e.message}"
            recorder.release()
            stopSelfClean()
            return
        }
        val translator = Translator()

        val startMillis = System.currentTimeMillis()
        val accumulated = ArrayList<SegmentRecord>()

        val readBuf = ShortArray(bufSize)
        val floatBuf = FloatArray(bufSize)

        recorder.startRecording()

        try {
            while (isRecording.get()) {
                val n = recorder.read(readBuf, 0, readBuf.size)
                if (n <= 0) continue

                for (i in 0 until n) floatBuf[i] = readBuf[i] / 32768.0f
                val chunk = floatBuf.copyOf(n)

                val segments = asr.process(chunk)
                speaking.value = asr.isSpeaking()
                elapsedSec.value = ((System.currentTimeMillis() - startMillis) / 1000).toInt()

                for (seg in segments) {
                    val es = translator.translate(seg.chinese, Translator.TARGET_ES)
                    val en = translator.translate(seg.chinese, Translator.TARGET_EN)
                    val rec = SegmentRecord(seg.startSec, seg.chinese, es, en)
                    accumulated.add(rec)
                    liveSegments.value = ArrayList(accumulated)
                }
            }

            // Al parar: procesar lo que quede en el buffer.
            val tail = asr.flush()
            for (seg in tail) {
                val es = translator.translate(seg.chinese, Translator.TARGET_ES)
                val en = translator.translate(seg.chinese, Translator.TARGET_EN)
                val rec = SegmentRecord(seg.startSec, seg.chinese, es, en)
                accumulated.add(rec)
                liveSegments.value = ArrayList(accumulated)
            }
        } catch (e: Throwable) {
            statusMsg.value = "Error durante la grabación: ${e.message}"
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            asr.release()
            translator.close()
        }

        // Guardar la conversación si tiene contenido.
        if (accumulated.isNotEmpty()) {
            val session = Session(
                id = startMillis,
                startedAtMillis = startMillis,
                durationSec = ((System.currentTimeMillis() - startMillis) / 1000).toInt(),
                segments = accumulated,
            )
            runCatching { SessionStore.save(applicationContext, session) }
        }

        stopSelfClean()
    }

    private fun stopRecording() {
        isRecording.set(false)
        // El hilo de grabación terminará, guardará y llamará stopSelfClean().
    }

    private fun stopSelfClean() {
        isRecording.set(false)
        running.value = false
        speaking.value = false
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Audio focus: pedir que las otras apps bajen/paren su sonido ---
    private fun requestAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(true)
                .build()
            focusRequest?.let { audioManager?.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    // --- Notificación mínima y silenciosa ---
    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Grabación", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_recording))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        isRecording.set(false)
        super.onDestroy()
    }
}
