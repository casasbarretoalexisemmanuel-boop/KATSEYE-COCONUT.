# KATSEYE LIGHTSYNC 2.0 💜

Versión beta no oficial de Coconut para Android.

## Funciones
- Bluetooth BLE KATSEYE OLS (UUID y comandos **experimentales**).
- RGB, selector HSV, brillo, perfiles manuales de cinco canciones.
- Audio reactivo desde micrófono externo.
- Audio interno mediante MediaProjection y AudioPlaybackCapture, con consentimiento del usuario y sujeto a restricciones de la app reproductora.
- Sensibilidad configurable al ritmo y nueva interfaz violeta/rosa.

## Compilar e instalar
Abre Actions → Compilar APK Android, selecciona la rama `lightsync-v2-dual-audio` y ejecuta workflow_dispatch. Descarga el artefacto KATSEYE-LightSync-2-APK e instala app-debug.apk.

## Limitaciones
**No se ha validado la compilación ni el control con el lightstick real.** No identifica títulos de canciones automáticamente: los perfiles se eligen manualmente. No usa coreografías oficiales. La captura interna no funciona para apps que bloquean captura de reproducción. Mantén la app visible durante la captura; algunas versiones de Android exigen un servicio en primer plano para sesiones largas.

Proyecto de fans sin afiliación con KATSEYE ni su fabricante.
