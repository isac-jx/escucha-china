# Escucha China

App de Android **solo para uso personal** que:

- Graba conversaciones en chino (incluso en segundo plano, con otra app abierta o la pantalla apagada).
- Reconoce el chino hablado **sin internet** (modelo SenseVoice, bueno con acentos de mandarín).
- Traduce en vivo a **español** o **inglés** (botón ES / EN), también sin internet.
- Guarda cada conversación para leerla después, con hora de cada frase.
- Permite compartir/exportar el texto.

> **Fase 1** (esta versión): grabar + reconocer + traducir + guardar + historial.
> **Fase 2** (siguiente): resumen automático de la conversación y exportar a PDF.

## Importante

- La **primera vez** hay que descargar los modelos (una sola vez, con wifi, ~300 MB).
  Después todo funciona **sin internet**.
- Android muestra un **punto verde** de micrófono mientras graba y una notificación
  silenciosa; eso **no se puede quitar** en una app normal (es protección del sistema).
- Grabar conversaciones en las que tú participas es legal en México. Grabar a terceros
  donde tú no estás presente es otra cosa; úsala con criterio.

## Cómo sale el APK (sin instalar nada en la PC)

Este repositorio se **compila solo** en GitHub:

1. Sube estos archivos a un repositorio en tu cuenta de GitHub.
2. Ve a la pestaña **Actions** y abre el flujo **"Construir APK"** (o espera a que corra solo).
3. Cuando termine (unos minutos), entra a esa ejecución y descarga el **artifact**
   llamado `EscuchaChina-APK`. Dentro está `app-debug.apk`.
4. Pasa ese `.apk` a tu teléfono e instálalo (permitir "fuente desconocida").

El propio GitHub descarga la librería de voz y compila; no necesitas Android Studio.

## Detalles técnicos

- Lenguaje: Kotlin. `minSdk 26`, `targetSdk 34`.
- Voz: **sherpa-onnx 1.12.21** (SenseVoice + silero VAD), 100% en el dispositivo.
- Traducción: **ML Kit Translation** (modelos offline de Google).
- Historial: archivos JSON en el almacenamiento interno de la app.
