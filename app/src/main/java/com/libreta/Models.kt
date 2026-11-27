package com.libreta

const val DEFAULT_KEYWORD = "oye libreta"
data class ExerciseStep(val tipo: String, val copiar: String, val aclaracion: String)
enum class VoiceCommand { NEXT, REPEAT, SIMPLER, NEW }
enum class Mic { OFF, WAITING, LISTENING }
enum class WakeMode { PORCUPINE, TEXT }
data class AppUiState(
    val mic: Mic = Mic.OFF, val wakeMode: WakeMode = WakeMode.TEXT, val busy: Boolean = false,
    val partial: String = "", val keyword: String = DEFAULT_KEYWORD, val exercise: String = "",
    val steps: List<ExerciseStep> = emptyList(), val index: Int = 0, val completed: Boolean = false,
    val permissionDenied: Boolean = false, val error: String? = null
)
