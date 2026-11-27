package com.libreta

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object Controller {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var job:Job?=null;private var opId=0
    val state=MutableStateFlow(AppUiState())
    fun setMic(m:Mic)=state.update{it.copy(mic=m)}
    fun setPartial(p:String)=state.update{if(it.partial==p)it else it.copy(partial=p)}
    fun setError(e:String?)=state.update{it.copy(error=e)}
    fun setWakeMode(m:WakeMode)=state.update{it.copy(wakeMode=m)}
    fun setPermissionDenied(b:Boolean)=state.update{it.copy(permissionDenied=b)}
    fun setKeyword(k:String)=state.update{it.copy(keyword=k.trim().ifEmpty{DEFAULT_KEYWORD})}
    fun submit(raw:String){val base=Normalizer.base(raw);if(base.isBlank())return;val cmd=Normalizer.command(base);if(cmd!=null)run(cmd)else solve(base)}
    fun run(c:VoiceCommand){when(c){VoiceCommand.NEXT->next();VoiceCommand.REPEAT->repeat();VoiceCommand.SIMPLER->simpler();VoiceCommand.NEW->reset()}}
    fun next()=state.update{s->when{ s.steps.isEmpty()||s.completed->s;s.index<s.steps.lastIndex->s.copy(index=s.index+1);else->s.copy(completed=true)}}
    fun repeat()=state.update{s->if(s.steps.isEmpty())s else s.copy(completed=false,index=s.index.coerceIn(0,s.steps.lastIndex),error=null)}
    fun reset(){job?.cancel();job=null;opId++;state.update{it.copy(busy=false,partial="",exercise="",steps=emptyList(),index=0,completed=false,error=null)}}
    fun simpler(){val s=state.value;val idx=s.index;val step=s.steps.getOrNull(idx)?:return;if(s.busy||s.completed)return;val id=begin{it};job=scope.launch{try{val txt=Solver.simplify(s.exercise,step);if(id==opId)state.update{cur->if(idx in cur.steps.indices)cur.copy(steps=cur.steps.toMutableList().also{it[idx]=it[idx].copy(aclaracion=txt)})else cur}}catch(e:CancellationException){throw e}catch(e:Exception){if(id==opId)setError(e.message?:"No se pudo simplificar; conserva la explicación actual.")}finally{if(id==opId)state.update{it.copy(busy=false)}}}}
    private fun solve(base:String){val symbols=Normalizer.symbols(base);if(symbols.length<3){setError("No entendí el ejercicio. Intenta de nuevo.");return};val id=begin{it.copy(exercise=symbols,steps=emptyList(),index=0,completed=false)};job=scope.launch{try{val steps=Solver.solve(base,symbols);if(id==opId)state.update{it.copy(steps=steps,index=0,completed=false)}}catch(e:CancellationException){throw e}catch(e:Exception){if(id==opId)setError(e.message?:"Error inesperado")}finally{if(id==opId)state.update{it.copy(busy=false)}}}}
    private fun begin(change:(AppUiState)->AppUiState):Int{job?.cancel();opId++;state.update{change(it).copy(busy=true,error=null)};return opId}
}
