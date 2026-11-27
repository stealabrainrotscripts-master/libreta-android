package com.libreta

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Dos fases separadas:
 *  ESPERA  -> Porcupine (local, ligero). Sin Porcupine configurado: modo texto, que repite SpeechRecognizer
 *             en bucle y busca la palabra clave en lo transcrito (funciona, pero gasta más batería).
 *  CAPTURA -> SpeechRecognizer una sola vez: termina con "listo", con una pausa o a los 45 s.
 * Los comandos solo se interpretan al terminar la captura (Controller.submit), nunca durante el dictado.
 * Solo vive con la Activity visible: start() en onResume, stop() en onPause, release() en onDestroy.
 * Todo corre en el hilo principal.
 */
class VoiceController(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var wake: PorcupineWake? = null
    private var wakeKeyword: String? = null
    private var active = false
    private var capturing = false
    private var errors = 0
    private val buf = StringBuilder()

    // Si el dispositivo no soporta es-ES, se prueban otras variantes del español.
    private val langs = listOf("es-ES", "es-419", "es-US", "es-MX", "es-VE", "es")
    private var langIdx = 0

    private fun buildIntent(lang: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)

    private var intent = buildIntent(langs[0])

    private val listenRunnable = Runnable { if (active) { rec?.cancel(); rec?.startListening(intent) } }
    private val watchdog = Runnable { if (capturing) finish() }
    private val needsRecognizer: Boolean get() = capturing || wake == null

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Controller.setError("Este dispositivo no tiene reconocimiento de voz"); return
        }
        active = true; errors = 0; langIdx = 0; intent = buildIntent(langs[0])
        Controller.setError(null)
        rec = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener) }
        prepareWake()
        waiting()
    }

    fun stop() {
        active = false; capturing = false; buf.clear()
        main.removeCallbacksAndMessages(null)
        wake?.stop()
        rec?.destroy(); rec = null
        Controller.setMic(Mic.OFF); Controller.setPartial("")
    }

    fun release() { stop(); wake?.release(); wake = null; wakeKeyword = null }

    /** Cambió la palabra clave: reconstruye el detector. */
    fun reconfigure() {
        if (!active) return
        main.removeCallbacksAndMessages(null)
        capturing = false; buf.clear()
        wake?.stop(); rec?.cancel()
        prepareWake()
        waiting()
    }

    /** Disparo manual (tocar el indicador) por si la palabra clave falla. */
    fun trigger() {
        if (!active || capturing) return
        wake?.stop()
        beginCapture(200)
    }

    private fun prepareWake() {
        val kw = Controller.state.value.keyword
        if (wake != null && wakeKeyword == kw) return
        wake?.release()
        wake = PorcupineWake.create(ctx, kw) { main.post { onWake() } }
        wakeKeyword = kw
        Controller.setWakeMode(if (wake != null) WakeMode.PORCUPINE else WakeMode.TEXT)
    }

    private fun waiting() {
        capturing = false; buf.clear()
        main.removeCallbacks(watchdog); main.removeCallbacks(listenRunnable)
        Controller.setMic(Mic.WAITING); Controller.setPartial("")
        rec?.cancel()
        val w = wake
        if (w == null) listen()
        else if (!w.start()) {
            w.release(); wake = null; wakeKeyword = null
            Controller.setWakeMode(WakeMode.TEXT)
            Controller.setError("Porcupine no arrancó; uso el modo texto (más batería)")
            listen()
        }
    }

    private fun onWake() {
        if (!active || capturing) return
        wake?.stop()                // libera el micrófono antes de abrir SpeechRecognizer
        beginCapture(200)
    }

    private fun beginCapture(delay: Long) {
        capturing = true; buf.clear()
        Controller.setMic(Mic.LISTENING)
        main.removeCallbacks(watchdog); main.postDelayed(watchdog, 45_000)
        listen(delay)
    }

    private fun listen(delay: Long = 0) {
        main.removeCallbacks(listenRunnable)     // evita sesiones duplicadas
        main.postDelayed(listenRunnable, delay)
    }

    private fun add(s: String) {
        val piece = Normalizer.dropLeadingKeyword(s, Controller.state.value.keyword).trim()
        if (piece.isNotEmpty()) buf.append(' ').append(piece)
    }

    private fun finish() {
        val text = buf.toString().trim()
        waiting()
        if (text.isNotBlank()) Controller.submit(text)
    }

    /** Solo modo texto: busca la palabra clave en lo transcrito; todo lo demás se descarta sin guardarse. */
    private fun waitingText(t: String) {
        val plain = Normalizer.plain(t)
        val kw = Normalizer.plain(Controller.state.value.keyword)
        val i = if (kw.isEmpty()) -1 else plain.indexOf(kw)
        if (i < 0) return
        capturing = true; buf.clear()
        Controller.setMic(Mic.LISTENING)
        main.removeCallbacks(watchdog); main.postDelayed(watchdog, 45_000)
        add(plain.substring(i + kw.length))
        if (buf.isNotBlank()) finish()           // palabra clave + ejercicio en la misma frase
    }

    private fun first(b: Bundle?) =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().lowercase().trim()

    private fun describe(e: Int) = when (e) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            "sin red: descarga el paquete de voz offline en español"
        SpeechRecognizer.ERROR_AUDIO -> "fallo de audio"
        12, 13 -> "idioma español no disponible: instala el paquete de español en Servicios de voz de Google"
        else -> "código $e"
    }

    private fun fail(e: Int) {
        Controller.setError("Voz: ${describe(e)}")
        if (++errors > 5) { stop(); Controller.setError("Reconocimiento de voz no disponible: ${describe(e)}") }
        else if (active && needsRecognizer) listen(1500)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(p: Bundle?) {
            val t = first(p)
            if (t.isEmpty()) return
            errors = 0
            if (capturing) {
                Controller.setPartial(t)
                if (Normalizer.closes(t)) rec?.stopListening()        // palabra de cierre
            } else if (wake == null && Normalizer.closes(t) &&
                Normalizer.plain(t).contains(Normalizer.plain(Controller.state.value.keyword))) {
                rec?.stopListening()
            }
        }

        override fun onResults(r: Bundle?) {
            errors = 0
            val t = first(r)
            if (capturing) { add(t); finish() }                       // pausa = fin del dictado
            else if (wake == null && t.isNotEmpty()) waitingText(t)
            if (active && needsRecognizer) listen()
        }

        override fun onError(e: Int) {
            when (e) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (capturing) finish()
                    if (active && needsRecognizer) listen()
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { Controller.setPermissionDenied(true); stop() }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    if (active && needsRecognizer) listen(400)
                }
                12, 13 -> {
                    if (langIdx < langs.lastIndex) {          // probar la siguiente variante del español
                        langIdx++; intent = buildIntent(langs[langIdx])
                        if (active && needsRecognizer) listen(300)
                    } else fail(e)
                }
                else -> fail(e)
            }
        }

        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }
}
