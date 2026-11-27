# Libreta

App Android de guía por voz para resolver ejercicios paso a paso.

## Requisitos
- Android Studio/SDK con API 35 y JDK 17.
- Copia `local.properties.example` a `local.properties` y configura `ANTHROPIC_KEY`. `PICOVOICE_KEY` es opcional; déjalo vacío para usar reconocimiento de voz por texto.
- Nunca publiques claves. Las claves compiladas en una app cliente pueden extraerse; usa un backend propio antes de distribuirla.

## Compilar y probar
```bash
./gradlew testDebugUnitTest assembleDebug
```

La aplicación requiere permiso de micrófono. El modo opcional Porcupine necesita AccessKey y los archivos `oye_libreta_android.ppn` y `porcupine_params_es.pv` en `app/src/main/assets/`. Si faltan, se utiliza SpeechRecognizer en modo texto y consume más batería. Prueba reconocimiento offline, pausa/fin de dictado, cambio de palabra clave y recuperación de errores en un dispositivo real.
