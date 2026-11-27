package com.libreta

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import android.content.Context
import java.text.Normalizer as JNormalizer

class PorcupineWake private constructor(private val manager:PorcupineManager){fun start()=try{manager.start();true}catch(_:PorcupineException){false};fun stop(){try{manager.stop()}catch(_:PorcupineException){}};fun release(){try{manager.delete()}catch(_:Exception){}}
    companion object{const val MODEL="porcupine_params_es.pv";fun keywordAsset(keyword:String):String{val s=JNormalizer.normalize(keyword.lowercase(),JNormalizer.Form.NFD).replace(Regex("\\p{Mn}+"),"").replace(Regex("[^a-z0-9]+"),"_").trim('_');return "${s}_android.ppn"};fun create(ctx:Context,keyword:String,onWake:()->Unit):PorcupineWake?{if(BuildConfig.PICOVOICE_KEY.isBlank())return null;val asset=keywordAsset(keyword);val list=ctx.assets.list("").orEmpty();if(asset !in list||MODEL !in list)return null;return try{PorcupineWake(PorcupineManager.Builder().setAccessKey(BuildConfig.PICOVOICE_KEY).setKeywordPath(asset).setModelPath(MODEL).setSensitivity(0.7f).build(ctx.applicationContext){onWake()})}catch(_:Exception){null}}}
}
