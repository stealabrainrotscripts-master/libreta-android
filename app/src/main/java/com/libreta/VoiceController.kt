package com.libreta

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.*

class VoiceController(context:Context){
    private val ctx=context.applicationContext;private val main=Handler(Looper.getMainLooper());private var recognizer:SpeechRecognizer?=null;private var wake:PorcupineWake?=null;private var wakeKeyword:String?=null;private var active=false;private var capturing=false;private var session=0;private var errors=0;private val buf=StringBuilder()
    private val speechIntent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-ES").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,2200L)
    private val startTask=Runnable{if(active&&needsRecognizer){try{recognizer?.startListening(speechIntent)}catch(_:RuntimeException){schedule(500)}}};private val timeout=Runnable{if(capturing)finish(session)};private val needsRecognizer get()=capturing||wake==null
    fun start(){if(active)return;if(!SpeechRecognizer.isRecognitionAvailable(ctx)){Controller.setError("Este dispositivo no tiene reconocimiento de voz");return};active=true;errors=0;recognizer=SpeechRecognizer.createSpeechRecognizer(ctx).also{it.setRecognitionListener(listener)};prepareWake();waiting()}
    fun stop(){active=false;capturing=false;session++;buf.clear();main.removeCallbacksAndMessages(null);try{wake?.stop()}catch(_:Exception){};try{recognizer?.cancel();recognizer?.destroy()}catch(_:Exception){};recognizer=null;Controller.setMic(Mic.OFF);Controller.setPartial("")}
    fun release(){stop();wake?.release();wake=null;wakeKeyword=null}
    fun reconfigure(){if(!active)return;main.removeCallbacksAndMessages(null);session++;capturing=false;buf.clear();wake?.stop();prepareWake();waiting()}
    fun trigger(){if(active&&!capturing){wake?.stop();beginCapture()}}
    private fun prepareWake(){val kw=Controller.state.value.keyword;if(wake!=null&&wakeKeyword==kw)return;wake?.release();wake=PorcupineWake.create(ctx,kw){main.post{if(active)onWake()}};wakeKeyword=kw;Controller.setWakeMode(if(wake==null)WakeMode.TEXT else WakeMode.PORCUPINE)}
    private fun waiting(){capturing=false;buf.clear();main.removeCallbacks(timeout);main.removeCallbacks(startTask);Controller.setMic(Mic.WAITING);Controller.setPartial("");try{recognizer?.cancel()}catch(_:Exception){};val w=wake;if(w==null)schedule(150)else if(!w.start()){w.release();wake=null;wakeKeyword=null;Controller.setWakeMode(WakeMode.TEXT);Controller.setError("Porcupine no arrancó; uso reconocimiento por texto");schedule(150)}}
    private fun onWake(){if(!active||capturing)return;wake?.stop();beginCapture()}
    private fun beginCapture(){capturing=true;session++;buf.clear();Controller.setMic(Mic.LISTENING);main.removeCallbacks(timeout);main.postDelayed(timeout,45_000);schedule(250)}
    private fun schedule(delay:Long){main.removeCallbacks(startTask);if(active&&needsRecognizer)main.postDelayed(startTask,delay)}
    private fun add(text:String){val piece=Normalizer.dropLeadingKeyword(text,Controller.state.value.keyword).trim();if(piece.isNotEmpty())buf.append(' ').append(piece)}
    private fun finish(expected:Int){if(!active||!capturing||expected!=session)return;val text=buf.toString().trim();waiting();if(text.isNotBlank())Controller.submit(text)}
    private fun waitingText(text:String){val normalized=Normalizer.plain(text);val kw=Normalizer.plain(Controller.state.value.keyword);val index=if(kw.isBlank())-1 else Regex("(?<![\\p{L}\\d])${Regex.escape(kw)}(?![\\p{L}\\d])").find(normalized)?.range?.first?:-1;if(index<0)return;capturing=true;session++;buf.clear();Controller.setMic(Mic.LISTENING);main.postDelayed(timeout,45_000);add(normalized.substring(index+kw.length));if(buf.isNotBlank())finish(session)}
    private fun first(b:Bundle?)=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().lowercase().trim()
    private fun listenerError(e:Int){when(e){SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->{if(capturing)finish(session)else schedule(200)};SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->{Controller.setPermissionDenied(true);stop()};SpeechRecognizer.ERROR_RECOGNIZER_BUSY,SpeechRecognizer.ERROR_CLIENT->schedule(500);else->{Controller.setError("Reconocimiento de voz: código $e");if(++errors>=6){stop();Controller.setError("Reconocimiento de voz no disponible (código $e)")}else schedule(1200)}}}
    private val listener=object:RecognitionListener{override fun onReadyForSpeech(params:Bundle?){errors=0};override fun onBeginningOfSpeech(){};override fun onRmsChanged(rmsdB:Float){};override fun onBufferReceived(buffer:ByteArray?){};override fun onEndOfSpeech(){};override fun onError(error:Int){listenerError(error)};override fun onResults(results:Bundle?){errors=0;val text=first(results);if(capturing){add(text);finish(session)}else if(wake==null&&text.isNotBlank())waitingText(text);if(active&&needsRecognizer)schedule(150)};override fun onPartialResults(partialResults:Bundle?){val text=first(partialResults);if(text.isBlank())return;if(capturing){Controller.setPartial(text);if(Normalizer.closes(text))try{recognizer?.stopListening()}catch(_:Exception){}}};override fun onEvent(eventType:Int,params:Bundle?) {}}
}
