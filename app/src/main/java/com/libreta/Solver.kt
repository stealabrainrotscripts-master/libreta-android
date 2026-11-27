package com.libreta

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class UnsolvableException(message:String):Exception(message)
object Solver {
    private val http=OkHttpClient.Builder().callTimeout(60,TimeUnit.SECONDS).build()
    private val types=setOf("datos","planteo","operacion","procedimiento","resultado","otro")
    private val solvePrompt="""Resuelve el ejercicio en español en pasos ordenados y breves. Responde SOLO JSON: {"resoluble":true,"motivo":"","pasos":[{"tipo":"datos|planteo|operacion|procedimiento|resultado|otro","copiar":"...","aclaracion":"..."}]}. Entre 3 y 8 pasos. copiar contiene únicamente lo que se anota; aclaracion explica en una frase. Verifica el resultado. Si falta información o hay ambigüedad, responde resoluble:false y no inventes."""
    private val simplifyPrompt="""Simplifica la explicación sin alterar el contenido que se copia. SOLO JSON: {"aclaracion":"..."} (máximo dos frases)."""
    suspend fun solve(raw:String,normalized:String):List<ExerciseStep>{val o=parseObject(call(solvePrompt,"Dictado original: $raw\nNormalizado: $normalized"));if(!o.optBoolean("resoluble",true))throw UnsolvableException(o.optString("motivo").ifBlank{"No pude resolver este ejercicio."});val arr=o.optJSONArray("pasos")?:throw UnsolvableException("La respuesta no incluyó pasos.");val out=mutableListOf<ExerciseStep>();for(i in 0 until arr.length()){val p=arr.optJSONObject(i)?:continue;val copiar=p.optString("copiar").trim();if(copiar.isBlank())continue;val tipo=p.optString("tipo").lowercase().takeIf{it in types}?:"otro";out+=ExerciseStep(tipo,copiar,p.optString("aclaracion").trim())};if(out.size !in 3..12)throw UnsolvableException("No obtuve pasos válidos. Intenta dictarlo de nuevo.");return out}
    suspend fun simplify(exercise:String,step:ExerciseStep):String{val o=parseObject(call(simplifyPrompt,"Ejercicio: $exercise\nPaso: ${step.copiar}\nAclaración: ${step.aclaracion}"));return o.optString("aclaracion").trim().ifBlank{throw IOException("Respuesta de simplificación vacía")}}
    private fun parseObject(text:String):JSONObject{val start=text.indexOf('{');if(start<0)throw IOException("Respuesta JSON no válida");var depth=0;var quoted=false;var escaped=false;for(i in start until text.length){val c=text[i];if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'{'=>depth++;'}'->{depth--;if(depth==0)return try{JSONObject(text.substring(start,i+1))}catch(e:Exception){throw IOException("JSON del modelo no válido",e)}}}};throw IOException("JSON del modelo incompleto")}
    private suspend fun call(system:String,user:String):String=suspendCancellableCoroutine{cont->if(BuildConfig.ANTHROPIC_KEY.isBlank()){cont.resumeWithException(IOException("Falta ANTHROPIC_KEY en local.properties"));return@suspendCancellableCoroutine};val body=JSONObject().put("model",BuildConfig.ANTHROPIC_MODEL).put("max_tokens",1500).put("system",system).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",user)));val req=Request.Builder().url("https://api.anthropic.com/v1/messages").header("x-api-key",BuildConfig.ANTHROPIC_KEY).header("anthropic-version","2023-06-01").header("content-type","application/json").post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build();val call=http.newCall(req);cont.invokeOnCancellation{call.cancel()};call.enqueue(object:Callback{override fun onFailure(call:Call,e:IOException){if(cont.isActive)cont.resumeWithException(IOException(when(e){is UnknownHostException,is ConnectException,is SocketTimeoutException->"Sin conexión o tiempo agotado.";else->e.message?:"Error de red"},e))};override fun onResponse(call:Call,response:Response){response.use{r->try{val raw=r.body?.string().orEmpty();if(!r.isSuccessful){val msg=try{JSONObject(raw).getJSONObject("error").optString("message")}catch(_:Exception){""};throw IOException(when(r.code){401->"Clave de API inválida.";429->"Límite de peticiones alcanzado.";else->msg.ifBlank{"Error del servicio (${r.code})."}})};val content=JSONObject(raw).optJSONArray("content")?:throw IOException("Respuesta de API incompleta");val text=buildString{for(i in 0 until content.length()){val b=content.optJSONObject(i);if(b?.optString("type")=="text")append(b.optString("text"))}};if(cont.isActive)cont.resume(text)}catch(e:Exception){if(cont.isActive)cont.resumeWithException(if(e is IOException)e else IOException("Respuesta del servicio no válida",e))}}}})}
}
