package com.libreta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity:ComponentActivity(){private lateinit var voice:VoiceController;private fun hasMic()=ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ok->Controller.setPermissionDenied(!ok);if(ok&&::voice.isInitialized&&lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))voice.start()};override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);val prefs=getSharedPreferences("settings",MODE_PRIVATE);prefs.getString("keyword",null)?.let(Controller::setKeyword);voice=VoiceController(this);setContent{MaterialTheme(colorScheme=darkColorScheme()){Screen(onKeyword={Controller.setKeyword(it);prefs.edit().putString("keyword",Controller.state.value.keyword).apply();voice.reconfigure()},onTapMic=voice::trigger)}};if(!hasMic())permission.launch(Manifest.permission.RECORD_AUDIO)};override fun onResume(){super.onResume();if(hasMic()){Controller.setPermissionDenied(false);voice.start()}};override fun onPause(){voice.stop();super.onPause()};override fun onDestroy(){voice.release();super.onDestroy()}}
@Composable fun Screen(onKeyword:(String)->Unit,onTapMic:()->Unit){val s by Controller.state.collectAsState();val context=LocalContext.current;val focus=LocalFocusManager.current;Column(Modifier.fillMaxSize().background(Color(0xFF0E0E10)).systemBarsPadding().padding(20.dp),verticalArrangement=Arrangement.SpaceBetween){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){val(color,label)=when{ s.busy->Color(0xFFFFB300) to "Procesando…";s.mic==Mic.LISTENING->Color(0xFF43A047) to "Capturando ejercicio…";s.mic==Mic.WAITING->Color(0xFF607D8B) to "Esperando «${s.keyword}»";else->Color(0xFF424242) to "Micrófono en pausa"};Row(Modifier.clickable(onClick=onTapMic),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(12.dp).clip(CircleShape).background(color));Spacer(Modifier.width(10.dp));Text(label,fontSize=14.sp,color=Color.LightGray)};if(s.mic!=Mic.OFF)Text(if(s.wakeMode==WakeMode.PORCUPINE)"Detección local (Porcupine)" else "Detección por texto",fontSize=11.sp,color=Color.Gray);if(s.mic==Mic.LISTENING&&s.partial.isNotBlank())Text(s.partial,fontSize=12.sp,color=Color.Gray,maxLines=2);var text by remember(s.keyword){mutableStateOf(s.keyword)};OutlinedTextField(value=text,onValueChange={text=it},singleLine=true,label={Text("Palabra clave")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={onKeyword(text);focus.clearFocus()}),modifier=Modifier.fillMaxWidth())};Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF1A1A1D))){Column(Modifier.padding(20.dp)){val step=s.steps.getOrNull(s.index);when{ s.completed->{Text("Ejercicio completado",fontSize=20.sp,color=Color.White);Text("Di «nuevo ejercicio» o pulsa Nuevo.",fontSize=13.sp,color=Color.Gray)};step!=null->{Text("Paso ${s.index+1} de ${s.steps.size} · ${step.tipo}",fontSize=13.sp,color=Color.Gray);Spacer(Modifier.height(12.dp));Text("COPIAR EN LA LIBRETA:",fontSize=12.sp,color=Color(0xFF90CAF9));Text(step.copiar,fontSize=22.sp,fontFamily=FontFamily.Monospace,color=Color.White);if(step.aclaracion.isNotBlank()){Spacer(Modifier.height(14.dp));Text("EXPLICACIÓN:",fontSize=12.sp,color=Color(0xFF90CAF9));Text(step.aclaracion,fontSize=15.sp,color=Color.LightGray)}};s.busy->Text("Resolviendo…",color=Color.Gray);else->Text("Di «${s.keyword}» y dicta el ejercicio.",color=Color.Gray)}};s.error?.let{Text(it,fontSize=13.sp,color=Color(0xFFEF5350))};if(s.permissionDenied){Text("Falta permiso del micrófono.",color=Color(0xFFEF5350));TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",context.packageName,null)))}){Text("Abrir ajustes")}}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=Controller::reset,modifier=Modifier.weight(1f)){Text("Nuevo",maxLines=1)};OutlinedButton(onClick=Controller::simpler,modifier=Modifier.weight(1f),enabled=s.steps.isNotEmpty()&&!s.busy&&!s.completed){Text("Más simple",maxLines=1)};Button(onClick=Controller::next,modifier=Modifier.weight(1.2f),enabled=s.steps.isNotEmpty()&&!s.completed){Text(if(s.index==s.steps.lastIndex)"Terminar" else "Siguiente",maxLines=1)}}}}
