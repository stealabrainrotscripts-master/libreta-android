package com.libreta

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** El modelo declaró que no puede resolver el ejercicio con certeza. */
class UnsolvableException(message: String) : Exception(message)

object Solver {
    private const val MODEL = "claude-sonnet-5-5"
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val TIPOS = setOf("datos", "planteo", "operacion", "procedimiento", "resultado", "otro")

    private val SOLVE_PROMPT = """
Eres un compañero que dicta qué anotar en una libreta. Resuelve el ejercicio en pasos cortos y ordenados
(Datos, Planteo/Fórmula, Sustitución, Desarrollo, Resultado cuando aplique; adapta el orden si es lógica, programación o texto).
Responde SOLO un JSON, sin markdown ni texto extra:
{"resoluble":true,"motivo":"","pasos":[{"tipo":"datos|planteo|operacion|procedimiento|resultado|otro","copiar":"...","aclaracion":"..."}]}
Reglas:
- Entre 3 y 8 pasos.
- "copiar": lo exacto y breve que se escribe a mano, en texto plano (usa ^, /, √). Solo operaciones o procedimiento, sin explicaciones.
- "aclaracion": la explicación, máximo una frase, tono natural. Nada de esto va en "copiar".
- Comprueba el resultado final antes de responder.
- El enunciado viene de dictado por voz y puede tener errores. Si es incompleto, ambiguo o no puedes resolverlo con certeza,
  responde {"resoluble":false,"motivo":"<qué falta, en una frase>","pasos":[]}. Nunca inventes datos ni resultados.
""".trimIndent()

    private val SIMPLIFY_PROMPT = """
Reexplica de forma más simple y con palabras cotidianas la aclaración de un paso de un ejercicio.
No cambies lo que se copia. Responde SOLO JSON: {"aclaracion":"..."} (máximo 2 frases).
""".trimIndent()

    suspend fun solve(raw: String, normalized: String): List<ExerciseStep> {
        val o = JSONObject(extractJson(call(SOLVE_PROMPT, "Dictado original: $raw\nNormalizado: $normalized")))
        if (!o.optBoolean("resoluble", true))
            throw UnsolvableException(o.optString("motivo").ifBlank { "No pude resolver este ejercicio." })
        val arr = o.optJSONArray("pasos") ?: JSONArray()
        val steps = (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val copiar = p.optString("copiar").trim()
            if (copiar.isEmpty()) null
            else ExerciseStep(
                tipo = p.optString("tipo").lowercase().takeIf { it in TIPOS } ?: "otro",
                copiar = copiar,
                aclaracion = p.optString("aclaracion").trim()
            )
        }.take(12)
        if (steps.isEmpty()) throw UnsolvableException("No obtuve pasos válidos. Intenta dictarlo de nuevo.")
        return steps
    }

    suspend fun simplify(exercise: String, step: ExerciseStep): String {
        val user = "Ejercicio: $exercise\nPaso (se copia): ${step.copiar}\nAclaración actual: ${step.aclaracion}"
        val o = JSONObject(extractJson(call(SIMPLIFY_PROMPT, user)))
        return o.optString("aclaracion").trim().ifEmpty { throw IOException("Respuesta vacía del modelo") }
    }

    private fun extractJson(s: String): String {
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        if (a < 0 || b < a) throw IOException("Respuesta del modelo no válida")
        return s.substring(a, b + 1)
    }

    /** Llamada cancelable: si la corrutina se cancela, se cancela también la petición HTTP. */
    private suspend fun call(system: String, user: String): String = suspendCancellableCoroutine { cont ->
        if (BuildConfig.ANTHROPIC_KEY.isBlank()) {
            cont.resumeWithException(IOException("Falta ANTHROPIC_KEY en local.properties"))
            return@suspendCancellableCoroutine
        }
        val body = JSONObject()
            .put("model", MODEL).put("max_tokens", 1500).put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        val req = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", BuildConfig.ANTHROPIC_KEY)   // MVP: en producción, proxy propio
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val c = http.newCall(req)
        cont.invokeOnCancellation { c.cancel() }
        c.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val msg = when (e) {
                    is UnknownHostException, is ConnectException, is SocketTimeoutException -> "Sin conexión o tiempo agotado."
                    else -> e.message ?: "Error de red"
                }
                if (cont.isActive) cont.resumeWithException(IOException(msg))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use { parse(it) }
                    if (cont.isActive) cont.resume(text)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(IOException(e.message ?: "Respuesta no válida"))
                }
            }
        })
    }

    private fun parse(r: Response): String {
        val txt = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw IOException(
            when (r.code) {
                401 -> "Clave de API inválida."
                429 -> "Límite de peticiones alcanzado. Espera un momento."
                else -> "Error del servicio (${r.code})."
            }
        )
        val content = JSONObject(txt).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val blk = content.getJSONObject(i)
            if (blk.optString("type") == "text") sb.append(blk.optString("text"))
        }
        return sb.toString()
    }
}
