package com.isacjx.escuchachina

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una frase dentro de una conversación: tiempo, chino, y las dos traducciones. */
data class SegmentRecord(
    val startSec: Float,
    val chinese: String,
    val es: String,
    val en: String,
)

/** Una conversación grabada completa. */
data class Session(
    val id: Long,                 // epoch millis del inicio; sirve de nombre de archivo
    val startedAtMillis: Long,
    var durationSec: Int,
    val segments: MutableList<SegmentRecord> = mutableListOf(),
    var summary: String = "",     // se llenará en la Fase 2
) {
    fun title(): String {
        val fmt = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale("es", "MX"))
        return fmt.format(Date(startedAtMillis))
    }

    fun fullText(): String {
        val sb = StringBuilder()
        for (s in segments) {
            sb.append(s.chinese).append('\n')
            if (s.es.isNotBlank()) sb.append("ES: ").append(s.es).append('\n')
            if (s.en.isNotBlank()) sb.append("EN: ").append(s.en).append('\n')
            sb.append('\n')
        }
        return sb.toString().trim()
    }
}

object SessionStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "sessions").apply { mkdirs() }

    fun save(context: Context, session: Session) {
        val obj = JSONObject()
        obj.put("id", session.id)
        obj.put("startedAtMillis", session.startedAtMillis)
        obj.put("durationSec", session.durationSec)
        obj.put("summary", session.summary)
        val arr = JSONArray()
        for (s in session.segments) {
            val so = JSONObject()
            so.put("startSec", s.startSec.toDouble())
            so.put("zh", s.chinese)
            so.put("es", s.es)
            so.put("en", s.en)
            arr.put(so)
        }
        obj.put("segments", arr)
        File(dir(context), "${session.id}.json").writeText(obj.toString())
    }

    fun list(context: Context): List<Session> {
        val files = dir(context).listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { runCatching { parse(it.readText()) }.getOrNull() }
            .sortedByDescending { it.id }
    }

    fun load(context: Context, id: Long): Session? {
        val f = File(dir(context), "$id.json")
        if (!f.exists()) return null
        return runCatching { parse(f.readText()) }.getOrNull()
    }

    fun delete(context: Context, id: Long) {
        File(dir(context), "$id.json").delete()
    }

    private fun parse(text: String): Session {
        val obj = JSONObject(text)
        val session = Session(
            id = obj.getLong("id"),
            startedAtMillis = obj.getLong("startedAtMillis"),
            durationSec = obj.optInt("durationSec", 0),
            summary = obj.optString("summary", ""),
        )
        val arr = obj.optJSONArray("segments") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val so = arr.getJSONObject(i)
            session.segments.add(
                SegmentRecord(
                    startSec = so.optDouble("startSec", 0.0).toFloat(),
                    chinese = so.optString("zh", ""),
                    es = so.optString("es", ""),
                    en = so.optString("en", ""),
                )
            )
        }
        return session
    }
}
