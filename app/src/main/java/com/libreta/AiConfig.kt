package com.libreta

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Protocolo de la API: Anthropic nativo u "OpenAI-compatible" (OpenAI, Gemini, OpenRouter, Groq, Ollama, LM Studio…). */
enum class AiProvider { ANTHROPIC, OPENAI }

data class AiSettings(
    val provider: AiProvider,
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    fun endpoint(): String {
        val b = baseUrl.trim().trimEnd('/')
        return when (provider) {
            AiProvider.ANTHROPIC -> if (b.endsWith("/v1/messages")) b else "$b/v1/messages"
            AiProvider.OPENAI -> if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
        }
    }

    /** Devuelve el motivo por el que la configuración no sirve, o null si está completa. */
    fun problem(): String? = when {
        baseUrl.isBlank() -> "Falta la URL del servicio. Configúrala en «IA»."
        model.isBlank() -> "Falta el nombre del modelo. Configúralo en «IA»."
        provider == AiProvider.ANTHROPIC && apiKey.isBlank() -> "Falta la clave de API. Configúrala en «IA»."
        else -> null
    }
}

data class AiPreset(val name: String, val provider: AiProvider, val baseUrl: String, val model: String)

val AiPresets = listOf(
    AiPreset("Anthropic", AiProvider.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-5-5"),
    AiPreset("OpenAI", AiProvider.OPENAI, "https://api.openai.com/v1", ""),
    AiPreset("Gemini", AiProvider.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", ""),
    AiPreset("OpenRouter", AiProvider.OPENAI, "https://openrouter.ai/api/v1", ""),
    AiPreset("Groq", AiProvider.OPENAI, "https://api.groq.com/openai/v1", "")
)

object AiConfig {
    private const val PREFS = "p"

    /** Valores por defecto de compilación (local.properties / secretos de CI). */
    private fun fromBuild(): AiSettings {
        val provider = if (BuildConfig.AI_PROVIDER.trim().equals("openai", ignoreCase = true)) AiProvider.OPENAI else AiProvider.ANTHROPIC
        val key = BuildConfig.AI_API_KEY.ifBlank { BuildConfig.ANTHROPIC_KEY }
        val base = BuildConfig.AI_BASE_URL.ifBlank {
            if (provider == AiProvider.OPENAI) "https://api.openai.com/v1" else "https://api.anthropic.com"
        }
        val model = BuildConfig.AI_MODEL.ifBlank { if (provider == AiProvider.ANTHROPIC) "claude-sonnet-5-5" else "" }
        return AiSettings(provider, base, key, model)
    }

    val state = MutableStateFlow(fromBuild())

    /** Si el usuario guardó ajustes en la app, tienen prioridad sobre los de compilación. */
    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("ai_base_url")) return
        val provider = runCatching { AiProvider.valueOf(p.getString("ai_provider", "") ?: "") }.getOrDefault(AiProvider.ANTHROPIC)
        state.value = AiSettings(
            provider = provider,
            baseUrl = p.getString("ai_base_url", "") ?: "",
            apiKey = p.getString("ai_api_key", "") ?: "",
            model = p.getString("ai_model", "") ?: ""
        )
    }

    fun save(ctx: Context, s: AiSettings) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ai_provider", s.provider.name)
            .putString("ai_base_url", s.baseUrl)
            .putString("ai_api_key", s.apiKey)
            .putString("ai_model", s.model)
            .apply()
        state.value = s
    }
}
