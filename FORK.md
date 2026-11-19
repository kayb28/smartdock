# Smart Dock fork — registro del fork (kayb28)

Fork de [axel358/smartdock](https://github.com/axel358/smartdock) con un objetivo: **que el modo escritorio funcione de verdad sobre un monitor externo**, sin crashes ni docks muertos. Este documento registra qué se cambió, build por build, para que ninguna función se pierda entre versiones.

## Versiones del fork

| Tag | versionCode | Contenido |
|---|---|---|
| v1.15.0-fork.1–3 | 1150 | Builds locales previas (sin release en GitHub). No publicadas |
| v1.15.0-fork.4 | 1150 | Publicada. Toda la jornada de bugs de oct-2026 (10 commits): 4 bugs viejos cerrados, display secundario fail-safe, modo escritorio en monitor, firma estable y releases automáticas |
| **v1.15.2-fork.6** | **1152** | Publicada. La «jornada de las 12 mejoras»: backup completo JSON, calculadora de expresión, motor de búsqueda configurable, atajos anclables al dock, panel Wi-Fi propio vía Shizuku, intents de automatización (permiso signature), menús por comando estable, receivers desregistrados, app activa real (TYPE_WINDOW_STATE_CHANGED), DiffUtil en la franja, Alt+F12 gobernado, código muerto fuera |
| **v1.15.1-fork.5** | **1151** | Publicada. Primer bump de versionCode del fork: hace la build **actualizable** encima de fork.4 (Android rechaza updates con versionCode igual o menor). Contenido = fork.4 + bump de versión; los fixes son los mismos |

El versionCode sube en cada release del fork: Android compara versionCodes para aceptar un update, así que un versionCode congelado haría que cada APK nueva se rechace como reinstalación (lección pagada en WebLibre, donde su fork.4 tuvo que saltar 423→483 para que Android aceptara el update). La numeración `fork.N` lleva su propio contador para no chocar con la de upstream.

## El trabajo de la jornada de octubre 2026 (rama `bugfix/october-2026-bugs`)

### Estabilidad del dock (los 4 bugs viejos)

- **Crash en cambios de configuración tempranos** (#270): conectar un monitor durante el arranque del servicio entregaba un `onConfigurationChanged` antes de que las vistas existieran (`lateinit` nacidos en `createViews()`). Ahora se salta la actualización hasta que existan; nada se pierde porque `createViews()` construye desde la configuración actual.
- **Dock muerto tras crash-reboot** (#268): tras un crash del sistema, Android dejaba el servicio de accesibilidad habilitado pero nunca lo re-enganchaba, y el único arreglo era tocar accesibilidad a mano. Nuevo `BootCompletedReceiver`: 30 s tras el boot detecta el estado y re-engancha el servicio como el toggle manual (vía WRITE_SECURE_SETTINGS); sin permiso, botón de recuperación de un toque en el escritorio + notificación.
- **Atajos Meta** (#267): cada Meta+X abría el menú de apps porque el key-up de Meta contaba como pulsación de menú. Meta ahora es modificador si otra tecla baja mientras está mantenida.
- **Activación por swipe** (#264): «deslizar desde abajo» nunca funcionó en táctil — `onSwipe` solo manejaba DOWN/LEFT (solo el hover del puntero revelaba el dock). Swipe hacia arriba en la franja de activación muestra el dock; en el escritorio, abre el menú de apps.

### Displays secundarios fail-safe (#263)

- `getSecondaryDisplay()` tomaba el último display de la lista completa: en OEMs que añaden displays virtuales privados (monitor virtual de Samsung One UI) elegía uno donde las apps normales no pueden lanzar → **cada lanzamiento fallaba**. Ahora usa la categoría *presentation* (API pública), prefiere displays externos reales y devuelve null para caer a la pantalla principal.
- `getDisplayBounds()` sin null-check mataba el dock si el display desaparecía entre medias.
- Un lanzamiento rechazado (`SecurityException`/`IllegalArgumentException`) tumbaría todo el servicio de accesibilidad («no pasa nada»). Ahora reinterta en la pantalla principal y avisa.

### Multi-pantalla real

- **Tiling y resize correctos**: los límites se calculaban con las métricas del display integrado sin importar dónde vivía la ventana — en el monitor, ventanas gigantes inutilizables. Ahora se computan para el display real de la tarea. El caso `fullscreen` faltaba en `makeLaunchBounds()` y aplicaba un `Rect(0,0,0,0)` (ventana de tamaño cero).
- **Modo escritorio en el monitor** («Force desktop mode» automatizado): toggle en Ajustes avanzados que aplica `force_desktop_mode_on_external_displays` + `enable_freeform_support` — orden de intento: **permiso propio → Shizuku → root**, con mensaje claro si no hay ninguno. Sin freeform en el monitor no hay ventanas arrastrables con ratón: este toggle es el que convierte el monitor en escritorio de verdad.
- **Send to screen**: mover tareas en ejecución entre tablet y monitor desde el menú contextual de la taskbar. Elección explícita de «Launch in» del menú de apps gana a «Prefer secondary displays».

### Infraestructura del fork

- **Firma estable** (`debug.keystore` en el repo): cada APK de CI salía con firma distinta y actualizar exigía desinstalar (perdiendo ajustes, apps fijadas, modos de lanzamiento). Ahora toda build firma igual: **los updates del fork se instalan encima sin desinstalar**.
- **Releases automáticas**: tags `v*` → GitHub Release pública con el APK adjunto (los artefactos de Actions requieren login y caducan; una release no).
- **Traducciones**: todas las cadenas nuevas del fork traducidas a de/es/pa/zh-rCN/zh-rHK/zh-rTW — el build aborta si falta una (`MissingTranslation`).

## Convenios del fork

- versionCode estrictamente creciente en cada release del fork (…1150 → 1151 → …); releases por tag `v1.15.x-fork.N`.
- main absorbe el trabajo de las ramas `bugfix/`/`feature/` por **fast-forward**; el historial nunca se descarta ni se reescribe.
- Commits en inglés, un problema por commit con la causa raíz en el cuerpo (candidatos a upstream); documentación interna en español (`docs/`).
- Cadena nueva → traducida en el mismo commit a los seis idiomas mantenidos.

## Instalación

APK de la release (build debug firmada con el keystore del fork). **Actualiza encima de otra build del fork** sin desinstalar. Si venía instalada la app oficial de F-Droid, desinstalarla primero (certificado distinto — no hay forma de actualizar encima).

Instalar como app de sistema, o conceder por adb/Shizuku:

```
adb shell pm grant cu.axel.smartdock android.permission.WRITE_SECURE_SETTINGS
```
