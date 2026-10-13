package com.isacjx.escuchachina

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator as MlkitTranslator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Traducción offline con ML Kit (Google). Chino -> español / inglés.
 *
 * Los modelos de traducción se bajan una vez (con wifi) y luego funcionan
 * sin internet. Cada idioma pesa ~30 MB.
 */
class Translator {

    companion object {
        const val TARGET_ES = "es"
        const val TARGET_EN = "en"
    }

    private val clients = HashMap<String, MlkitTranslator>()

    private fun clientFor(target: String): MlkitTranslator {
        return clients.getOrPut(target) {
            val targetLang = when (target) {
                TARGET_EN -> TranslateLanguage.ENGLISH
                else -> TranslateLanguage.SPANISH
            }
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(targetLang)
                .build()
            Translation.getClient(options)
        }
    }

    /** Descarga el modelo del idioma si falta. Bloqueante; llamar en segundo plano. */
    fun ensureModel(target: String) {
        val conditions = DownloadConditions.Builder().build() // por defecto: solo con wifi
        Tasks.await(clientFor(target).downloadModelIfNeeded(conditions))
    }

    /** Traduce una frase. Bloqueante; llamar en segundo plano. Devuelve "" si falla. */
    fun translate(text: String, target: String): String {
        if (text.isBlank()) return ""
        return try {
            Tasks.await(clientFor(target).translate(text))
        } catch (e: Exception) {
            ""
        }
    }

    fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}
