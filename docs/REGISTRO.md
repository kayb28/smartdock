# REGISTRO — diario operativo del fork

_Fork de [axel358/smartdock](https://github.com/axel358/smartdock). Todo cambio con fecha/hora UTC, motivo, build y estado._
_Estados: pendiente confirmar → confirmado → superado. La fuente de la verdad para CI y releases es GitHub._

## Convenios

- versionCode **estrictamente creciente en cada release del fork** (lección de WebLibre: un versionCode igual o menor hace que Android rechace el update; el fork solo se distingue por el tag si el versionCode ya no puede bajar). La numeración del fork sigue la de upstream +patch: 1150 → 1151 (v1.15.1-fork.5) → …
- APKs del fork = **build debug firmada con keystore fijo** (`debug.keystore` en el repo): actualizan encima de builds anteriores del fork sin desinstalar; **NO** actualizan encima de la app oficial de F-Droid (certificado distinto) — desinstalar esa primero.
- Idioma de commits y docs del fork: inglés en commits (revisable por upstream), español en esta documentación.
- Ramas: trabajo en `bugfix/*` o `feature/*`; `main` solo recibe fast-forward del árbol de feature (el historial nunca se descarta).

## 2026-11-07 — jornada de bugs de octubre 2026 (rama `bugfix/october-2026-bugs`)

Base: `517604a` (main = punta de upstream, 2026-05-21, «fix: Update donation address»). Diez commits, uno por problema, en orden cronológico. Todos en la rama; al final del día la rama quedó igual a `v1.15.0-fork.4` y NO se consolidó en main (pendiente de esta sesión de documentación).

### 00:23 UTC — `f5409c5` Cuatro bugs viejos (uno por causa raíz)

- **#270 crash en `onConfigurationChanged` antes de `onServiceConnected`**: cada vista que actualiza es un `lateinit` nacido en `createViews()`; conectar un display externo durante el arranque del servicio entrega el cambio en esa ventana. Ahora se salta la actualización hasta que las vistas existan; `createViews()` ya construye desde la configuración actual, así que no se pierde nada.
- **#264 el método de activación «swipe» nunca funcionó en táctil**: el propio diálogo de ayuda promete «deslizar desde abajo», pero `onSwipe` solo manejaba DOWN y LEFT (solo el hover del puntero revelaba el dock oculto). Swipe hacia arriba en la franja de activación ahora muestra el dock; swipe hacia arriba en el escritorio abre el menú de apps.
- **#267 el menú de apps se abría tras cada atajo Meta+X**: el key-up de Meta se trataba como pulsación suelta del menú. Meta ahora cuenta como modificador cuando otra tecla baja mientras está mantenida, y su release no hace nada en ese caso.
- **#268 dock muerto tras crash-reboot del sistema**: el servicio queda habilitado en ajustes pero el sistema nunca lo re-engancha; hasta ahora el arreglo era activar/desactivar accesibilidad a mano. Nuevo `BootCompletedReceiver` (`RECEIVE_BOOT_COMPLETED` + `POST_NOTIFICATIONS` en el manifest): 30 s tras el boot (`CHECK_DELAY_MS = 30_000L`) comprueba el estado y re-engancha el servicio igual que el toggle manual (necesita WRITE_SECURE_SETTINGS: app de sistema / adb / Shizuku); sin ese permiso, el escritorio muestra un botón de recuperación de un toque y el receiver postea una notificación.
- CI: **roja** — lint `MissingTranslation`: `dock_not_running` sin traducir en de/pa/zh. Arreglado por el commit siguiente.
- Estado: superado (los 4 bugs cierran issue y el código sigue en el árbol de la rama).

### 00:31 UTC — `874fafb` Traducción de las cadenas de recuperación

- Las 6 cadenas nuevas de `f5409c5` traducidas a los idiomas mantenidos: **de, pa, zh-rCN, zh-rHK, zh-rTW**. CI vuelve a verde.

### 00:55 UTC — `62ee704` Soporte de display secundario fail-safe (#263)

- Seleccionar un display secundario o virtual podía dejar el dock muerto y las apps sin lanzar, de tres formas encadenadas:
  1. `getSecondaryDisplay()` tomaba el último display de toda la lista; en OEMs que añaden displays virtuales privados (el monitor virtual de Samsung One UI entre otros) elegía un display donde las apps normales no pueden lanzar, y **cada lanzamiento fallaba**. Ahora prefiere displays externos reales (los USB-C/HDMI, donde siempre se puede lanzar) y devuelve null si no hay ninguno, dejando que cada caller caiga a la pantalla principal.
  2. `getDisplayBounds()` desreferenciaba `DisplayManager.getDisplay()` sin null-check; un id de display recién desaparecido crasheaba el dock.
  3. `launchApp()` dejaba propagar la `SecurityException`/`IllegalArgumentException` de un `launchDisplayId` rechazado y **tumbaba todo el servicio de accesibilidad** («no pasa nada» a partir de ahí). Un lanzamiento rechazado ahora reintenta en la pantalla principal y avisa al usuario; `restartUI()` cae a la principal cuando el display objetivo rechaza las ventanas del dock.
- CI: **roja** — `values-pa/strings.xml:70`: `Invalid unicode escape sequence` en `cannot_launch_on_display` / `launched_on_main_display` (las traducciones punjabi usaban secuencias `\u` mal formadas). Arreglado por el commit siguiente.
- Estado: superado.

### 01:07 UTC — `7bf1bf7` Escape de apóstrofes en las cadenas punjabi

- AAPT rechaza apóstrofes crudos en recursos de string; los dos toasts nuevos de #263 eran los primeros del archivo en contener uno.
- CI: **roja de nuevo** — error distinto: `DeviceUtils.kt:233:60 Unresolved reference: DISPLAY_CATEGORY_EXTERNAL`. El commit usaba la constante interna de Android (no pública) para filtrar displays externos. Arreglado por el commit siguiente.
- Estado: superado.

### 01:11 UTC — `0c262fa` Categoría de presentación pública en `getSecondaryDisplay()`

- `DISPLAY_CATEGORY_EXTERNAL` no forma parte de la API pública; la categoría *presentation* (pública desde API 17) es la forma soportada de listar los displays donde las apps pueden mostrar contenido. CI verde.

### 01:50 UTC — `931b708` Tiling y redimensionado de ventanas en displays secundarios

- Resize y Snap del menú contextual de una app en ejecución (y sus contrapartes Alt+flechas) calculaban los límites de la ventana con las métricas del display **integrado** sin importar en qué display vivía la tarea. Con el dock y sus apps en un monitor USB-C, se aplicaban límites del tamaño de la pantalla de la tablet a ventanas del monitor: ventanas más grandes que la pantalla e inutilizables, y actividades recreadas en configuraciones que algunas apps no sobreviven.
- Además faltaba el caso `fullscreen` en el `when` de `makeLaunchBounds()`: redimensionar a pantalla completa aplicaba un `Rect(0,0,0,0)` vacío — ventana de tamaño cero.

### 03:13 UTC — `98ebb9a` Modo escritorio en display externo + «Send to screen»

- Monitores que no corren como escritorios son superficies de segunda clase: las apps lanzadas en ellos pierden el freeform (nada que arrastrar o redimensionar con el ratón) porque freeform en un display exige la opción de desarrollador «Force desktop mode». Nueva preferencia junto a «Prefer secondary displays»: activa los settings globales subyacentes (`force_desktop_mode_on_external_displays` + `enable_freeform_support`) vía WRITE_SECURE_SETTINGS, con fallback a root y mensaje claro cuando no hay ninguno. El switch refleja el estado vivo del sistema, así que también muestra lo que marca el toggle de opciones de desarrollador.
- **Send to screen**: mover tareas en ejecución entre pantallas desde el menú contextual de la taskbar (relanza la tarea con un `launchDisplayId` distinto).
- Elección explícita de display en «Launch in» del menú de apps ahora tiene prioridad sobre «Prefer secondary displays».

### 03:19 UTC — `43a3b24` Firma estable: keystore debug en el repo

- Los runners de CI generan un keystore debug nuevo en cada run: cada APK salía con firma distinta y instalar una build nueva exigía desinstalar la anterior (perdiendo ajustes del dock, apps fijadas y modos de lanzamiento recordados). Keystore debug fijado en el repo → toda build, local o CI, produce APKs compatibles de actualización.
- Nota de seguridad: es un keystore debug, conocido y de baja sensibilidad — aceptable a cambio de updates sin desinstalar. No protege nada: es la firma del fork, no un secreto.
- Cert SHA-256 del APK resultante: `4680C23198D542A0423DFD95F1A0D66A80100FA30CB72CAEDF107E41B54B41DB` (verificado en el APK de fork.4).

### 03:47 UTC — `783e15f` Desktop-mode por Shizuku antes de root

- El README documenta Shizuku como alternativa a conceder WRITE_SECURE_SETTINGS por ADB, pero el toggle nuevo de #98ebb9a solo probaba permiso de la app y root: en una instalación Shizuku-only el switch siempre fallaba aunque el servidor Shizuku (corriendo como shell) sí tiene WRITE_SECURE_SETTINGS. Ahora el comando de settings corre por `IShizukuService.newProcess` antes de recurrir a root, y el resumen de la preferencia y el mensaje de fallo mencionan Shizuku, en todos los idiomas mantenidos.

### 04:03 UTC — `0eac1a4` Releases automáticas + `v1.15.0-fork.4`

- Nuevo workflow `release_apk.yml`: push de tags `v*` y dispatch manual construyen el APK debug y lo adjuntan a una GitHub Release (los artefactos de Actions requieren login y caducan; las releases son públicas y permanentes). `permissions: contents: write` (la lección de MihonFix/WidgetCraft ya estaba aprendida — esta vez salió a la primera).
- Tag `v1.15.0-fork.4` → Release «SmartDock fork v1.15.0-fork.4» con notas en inglés. APK: `SmartDock-fork-debug-v1.15.0-fork.4.apk`, 14.181.352 bytes, versionCode 1150, versionName 1.15.0, sha256 del descargado `425ff1f51ceacfffca4ce0d9c0802ad55676c9d8f31996e99002eea92a4e718e`. Un segundo asset idéntico `SmartDock-fork-debug.apk` (renombrado a mano) también quedó adjunto — duplicado menor, documentado.
- CI de la rama al cierre: **verde** (últimos 7 commits consecutivos verdes; los 3 rojos documentados arriba).
- Estado: **v1.15.0-fork.4 publicada**. main quedó atrás: `517604a` ← 10 commits sin consolidar.

## 2026-11-19 — documentación y consolidación (esta sesión)

- **Consolidación**: main estaba en `517604a` y la rama `bugfix/october-2026-bugs` en `0eac1a4`, con main como ancestro directo → **fast-forward limpio**, sin merge commit: el historial de los 10 commits entra en main tal cual (lección de WebLibre: «el árbol de feature se mantiene siempre por delante de main; main absorbe, nunca se descarta trabajo»). La rama queda archivada en el punto (no se borra: es el histórico del tag v1.15.0-fork.4).
- **Documentación** (esta sesión, commits `docs/`):
  - `AGENTS.md` — guía para agentes en este repositorio (qué es, cómo compilar, ramas, convenios del fork, lecciones).
  - `docs/REGISTRO.md` — este diario.
  - `docs/HISTORIA.md` — narrativa consolidada y mapa de documentos/ramas.
  - `FORK.md` — registro público del fork (estilo WebLibre): qué se cambió build por build.
  - `CHANGELOG-FORK.md` — changelog formal por release del fork.
- Estado: ver cierre al final del archivo.

## 2026-11-19 (tercera pasada) — la jornada de las 12 mejoras (rama `feature/november-2026-improvements`)

Motivo del día: el usuario pidió «resuélvelo todo» — el menú de 12 opciones de mejora del informe de opciones del 19-11 se implementó completo, en tres fases, sobre `main` (267e942). Nueve commits (uno por problema + tres de arreglo de compilación), CI **verde** en `76f6b6f` tras 4 iteraciones (los fallos documentados abajo son lecciones nuevas).

### Fase 1 — ganancias rápidas

- **B1 — backup completo (`027da00`)**: `backupPreferences` descartaba silenciosamente los `Set` (apps ocultas del menú, apps silenciadas de notificaciones) y toda preferencia con URI (sonidos, iconos) — el backup perdía justamente lo personal. Formato JSON (sets como arrays) con fallback de lectura del formato de líneas antiguo para los backups previos a fork.6.
- **B4 — calculadora de expresión completa (`027da00`)**: `solve()` evaluaba solo el primer par de operandos (`2+3+4` → 5). Parser de descenso recursivo con precedencia, paréntesis, unario y exponente (derecha-asociativo).
- **B3 — motor de búsqueda configurable (`c2d3bb8`)**: la entrada de búsqueda del menú de apps tenía google.com hardcodeado en dos sitios. Preferencia «Web search engine» (Google/DuckDuckGo/Bing) + label de resultado según el motor. Cadenas en los 7 locales.
- **C2 — anclar atajos profundos al dock (`c2d3bb8`)**: las líneas `shortcut:pkg id` conviven con las de apps en `dock_pinned.lst`; el submenú «add to» ofrece cada atajo no anclado; el icono del dock lanza por `LauncherApps.startShortcut`. Persiste como los pins de apps.
- **B5 — código muerto fuera (`c2d3bb8`)**: `WifiScanResultAdapter` nunca instanciado, dependencia `RestrictionBypass` sin uso (y su jitpack), y `enable_soft_reboot` — la preferencia existía, era visible y no gobernaba nada; ahora sí gobierna Alt+F12.

### Fase 2 — experiencia y higiene

- **A2 — menús por comando estable (`2247850`)**: los dos listeners grandes (menú del dock + menú de apps) despachaban comparando el texto traducido del botón — un cambio de traducción o dos cadenas que colisionen cambia lo que un menú hace. `Action` lleva ahora un `MenuCommand` (enum) y 33 builders + 33 despachos (+2 de LauncherActivity) migrados a él.
- **B2 — panel propio de redes Wi-Fi (`2247850`)**: el tile de Wi-Fi de ajustes rápidos abría el panel del sistema aun con el wrapper Shizuku vivo. Ahora escanea y lista redes (SSID únicos, señal primero) en ventana propia; tocar una abre el flujo del sistema para credenciales (una app externa no puede completar el WPA). Sin Shizuku, sistema como antes.
- **D1 — superficie de automatización (`2247850`)**: `AutomationReceiver` exportado con permiso signature-level `cu.axel.smartdock.permission.SMARTDOCK_AUTOMATION`: Tasker/adb pueden abrir el menú o lanzar apps por broadcast; sin permiso concedido, nada. Requests caídas en silencio si el dock no está corriendo.
- **A3 — desregistrar TODO receptor (`2247850`)**: 7 receivers registrados anónimos, solo 2 se desregistraban en `onDestroy` — fuga y doble-disparo en cada recreación del servicio. Todos con nombre y desregistro completo.

### Fase 3 — fiabilidad

- **C1 — ventana enfocada real (`deec2c3`)**: `TYPE_WINDOW_STATE_CHANGED` estaba declarado y nunca manejado; la «app activa» se deducía del primer elemento de la lista de tareas (orden de recencia, no de foco). Ahora el evento actualiza `AppUtils.currentApp` con el paquete dueño del foco y refresca la franja.
- **A4 — DiffUtil en la franja de tareas (`deec2c3`)**: `updateApps` hacía `notifyDataSetChanged` (rebuild total con extracción de color dominante por icono) en cada cambio de ventana; ahora diff por paquete+taskId+conteo. Los dos TODOs que esto resolvía, eliminados del código.

### Lecciones nuevas de esta jornada (CI)

1. **Kotlin no admite referencias adelantadas entre funciones locales** — ni siquiera no-recursivas (`ab47a2f`, `029cefc`): dos rondas de CI rojas. El parser vive en un `private object` con clase interna (los métodos de clase sí pueden recu­rir mutuamente).
2. **Quitar `jcenter()` rompe la resolución** aunque la dependencia que lo justificaba se haya quitado: `error_prone_annotations:2.3.1` (transitiva) solo existe ahí (`195543f`). Dejarlo declarado como upstream.
3. **`ShortcutInfo.shortLabel` es `CharSequence`**, no `String` — `toString()` antes de usarlo en `Action`/pin-list (`deec2c3`).

### Publicación

- Bump: versionCode **1152**, versionName **1.15.2** (convenio: estrictamente creciente).
- Tag **`v1.15.2-fork.6`** → el workflow de releases construye el APK y crea la Release automáticamente (keystore fijo del repo → actualizable encima de fork.5 sin desinstalar).
- Estado: **v1.15.2-fork.6 publicada y verificada**: CI verde en los 4 runs (Build project y Build debug apk sobre main `fc23cad`, y Build project + Release debug apk sobre el tag); el workflow de releases creó la Release automáticamente con el APK adjunto.

## Cierre de la consolidación (verificado)

- [x] main en `3d48df3` = `0eac1a4` (los 10 commits de la jornada, fast-forward) + commit `docs/` de documentación. Push 2026-11-19 01:52 UTC.
- [x] CI verde en main (2/2 workflows: Build project y Build debug apk, ambos `success` sobre `3d48df3`).
- [x] La rama `bugfix/october-2026-bugs` sigue existiendo en `0eac1a4` (histórico del tag v1.15.0-fork.4). Estado: **consolidación completada**.

## 2026-11-19 (segunda pasada) — v1.15.1-fork.5: la build actualizable

- **Error detectado por el usuario**: la primera pasada dejó el versionCode congelado en 1150 («paridad con upstream») y no publicó release nueva — cualquier APK futura se habría rechazado como reinstalación de la misma versión. Es literalmente la lección de WebLibre (su fork.4: 423 < 473 instalado, «Android rechazaba el update», saltó a 483), escrita aquí como convenio al revés.
- Fix: **versionCode 1150 → 1151, versionName 1.15.0 → 1.15.1** (`app/build.gradle`), convenio reescrito en AGENTS.md §1/§5, FORK.md (tabla de versiones + convenios), HISTORIA.md, CHANGELOG-FORK.md (nota de compatibilidad de fork.4 corregida + entrada nueva de fork.5) y este registro: **versionCode estrictamente creciente en cada release del fork**.
- Publicación: commit `c2fd8c6` en main + tag `v1.15.1-fork.5` → el workflow de releases construyó el APK debug (firmado con el keystore del repo) y creó la GitHub Release automáticamente. Nota del commit: también normalizó los modos de ejecución de los archivos del repo (644→755) que el clone local traía sueltos; sin efecto en contenido. Contenido funcional = fork.4; el cambio es el bump, la consolidación y la documentación.
- Cierre (verificado): CI **verde** en el tag (Release debug apk + Build project) y en main (2/2 workflows, `c2fd8c6`). Release «SmartDock fork v1.15.1-fork.5» con asset renombrado a `SmartDock-fork-debug-v1.15.1-fork.5.apk` (14.181.352 bytes) y notas de release en inglés. APK verificada: **versionCode 1151, versionName 1.15.1**, cert SHA-256 `4680C23198D542A0423DFD95F1A0D66A80100FA30CB72CAEDF107E41B54B41DB` (el mismo que fork.4 → **se instala encima de v1.15.0-fork.4 sin desinstalar**), sha256 `c68d3f4332988d293184a26dbcb9eedc12cff1e09cf1c5d16514358a9c5271ac`. Entregada al usuario como `SmartDock-1.15.1-fork.5-universal.apk`. Estado: **v1.15.1-fork.5 publicada, actualizable y verificada**.

## 2026-11-19 (cuarta pasada) — la jornada de cierre: todos los bugs reportados resueltos (rama `bugfix/november-2026-issues`)

Motivo del día: el usuario pidió resolver «todos los problemas de Smart Dock reportados» para partir de una base limpia para futuros cambios. Primero la verdad del estado: el fork no tiene issues propios; upstream tiene 25 abiertos — los 5 de la jornada de octubre ya estaban corregidos en el fork (fork.4), pero 6 bugs más seguían abiertos. Se descargaron los 25 con cuerpo y comentarios (`scripts/fetch_issues_full.py` → JSON en `scripts/data/upstream_issues/`), se clasificaron y se diagnosticaron los 6 contra el código.

### Clasificación de los 25 issues abiertos upstream

- **Ya corregidos en fork.4** (siguen abiertos upstream porque los fixes viven solo en el fork): #270, #268, #267, #264, #263.
- **Corregidos en esta jornada**: #167, #250, #266, #200, #253, #259.
- **Feature requests, no implementados** (no son defectos de funcionamiento): #220 (auto-ocultar dock con botones del sistema), #229 (Open as... en menú largo), #235 (unfullscreen), #240 (ocultar barra de búsqueda / tamaños), #243 (widgets), #245 (botón de bloqueo en el menú), #247 (roadmap propio de upstream), #260 (ocultar icono / recents / tamaños), #207 (ocultar app en ejecución), #209 (intents de tamaño libreform), #182 (altura mínima menor), #191 (evitar reposo), #165 (layout vertical en apaisado), #258 (traducción pt-BR).

### Diagnóstico y fix por bug (6 commits, uno por problema)

- **#167 AltGr (`c5907bf`)**: AltGr llega como Ctrl+Alt o con `META_ALT_RIGHT_ON`; el bloque de atajos solo miraba `isAltPressed` → cada acorde de caracteres especiales disparaba un atajo encima de la tecla escrita («@» abría ajustes rápidos, AltGr+L bloqueaba el escritorio). Los acordes AltGr pasan intactos a la app; la otra mitad del issue (ventanizar una maximizada) ya era posible con Alt+↓.
- **#250 «0 recents» (`dd14aed`)**: el valor 0 (= «sin límite») iba directo a `ActivityManager.getRunningTasks(0)`, que la plataforma clampea a 1 tarea → la franja mostraba máximo 1 app. Ahora 0 pasa un tope de 100 y `getRecentTasks` respeta «sin límite» para `max <= 0`.
- **#266 captura desde ajustes rápidos (`5a3beca`)**: `removeView` es síncrono pero el compositor aún mostraba el frame del panel cuando `GLOBAL_ACTION_TAKE_SCREENSHOT` corría. Captura a 400 ms tras cerrar el panel, dock y handle ocultos durante la captura (petición del mismo reporte), restauración a 1500 ms cancelable por cualquier show/hide del usuario, y timers en el handler de la vista porque `hideDock` limpia `dockHandler` completo.
- **#200 offset del handle (`f0edf9f`, `dbba38d`)**: el handle nacía pegado a la esquina; en pantallas con esquinas redondeadas el radio lo cubría y era casi incliclicable (también en Pixel, según comentario). Offset por defecto de 8 dp hacia dentro + slider nuevo de 0–64 dp junto a los demás ajustes del handle; con gravedad `BOTTOM|START/END`, x/y positivas son distancias hacia dentro. Cadenas nuevas en los 7 locales. El slider configura rango antes que valor: el layout trae el rango 0..1 por defecto de Material y un valor mayor sería clampeado.
- **#259 arranque en blanco en waydroid (`c1d02b2`)**: en el boot la lista de displays puede faltar o reportar límites 0×0 → `makeWindowParams` añadía ventanas de dimensión cero («pantalla en blanco» hasta forzar el paro). Límites degenerados caen a las métricas de la aplicación. La mitad «accesibilidad perdida tras reinicio» del reporte ya la cubre el `BootCompletedReceiver` de fork.4 (#268).
- **#253 flash blanco + IOOBE (`c1d02b2`)**: sin traza (upstream tampoco la obtuvo) no se puede confirmar el punto exacto; se endureció la clase entera de crash: los 5 adaptadores que indexan una lista al bindear (DockAppAdapter, AppAdapter, NotificationAdapter, AppTaskAdapter, AppShortcutAdapter) saltan posiciones obsoletas en vez de tumbar el dock. Un crash-loop relanzando el dock encaja además con los flashes blancos reportados.

### Publicación

- CI en la rama: **verde** en las dos rondas (`c1d02b2` y `dbba38d`, ambos Build project + Build debug apk).
- Fast-forward de `bugfix/november-2026-issues` a main; commit de release con bump versionCode **1153** / versionName **1.15.3**; tag `v1.15.3-fork.7` → Release automática con APK.
- Estado: ver cierre al final del archivo.

### Cierre (verificado)

- [x] CI **verde en las 8 corridas** de la jornada: la rama `bugfix/november-2026-issues` (Build project + Build debug apk sobre `c1d02b2` y sobre `dbba38d`), main (Build project + Build debug apk sobre `d3350d0`) y el tag `v1.15.3-fork.7` (Release debug apk + Build project sobre `d3350d0`). Release **«SmartDock fork v1.15.3-fork.7»** creada por el workflow, asset renombrado por API a `SmartDock-fork-debug-v1.15.3-fork.7.apk` (13.141.806 bytes).
- [x] APK verificada: **versionCode 1153, versionName 1.15.3**, cert SHA-256 `4680C23198D542A0423DFD95F1A0D66A80100FA30CB72CAEDF107E41B54B41DB` (el mismo de fork.4/5/6 → **se instala encima de cualquier build anterior del fork sin desinstalar**), sha256 `7740fd1619e6afb9c8820a166845c0886e5cf3632787b771b58cc137a6415d26`. Entregada al usuario como `SmartDock-fork-debug-v1.15.3-fork.7.apk`. Estado: **v1.15.3-fork.7 publicada, actualizable y verificada**. Con esta release, de los 25 issues abiertos upstream ya no queda ningún bug sin corregir en el fork: solo feature requests (documentados en HISTORIA.md §límites).

## 2026-11-19 (quinta pasada) — el crash de los wrappers: dock muerto en todas las pantallas (rama `bugfix/wrapper-init-crash`)

Motivo: el usuario reporta con dos capturas que el dock «también deja de mostrarse en la pantalla secundaria» y que la app «no reconoce que ya tiene el permiso de accesibilidad activo». Las capturas (el diálogo «Algo salió mal» de la propia app) muestran una causa única detrás de los dos síntomas:

```
NoSuchMethodError: No static method asInterface(IActivityManager$Stub...) in framework.jar
  ActivityManagerWrapper.init(:32) → DockService.initWrappers(:298) → DockService.onCreate(:280)
```

### Diagnóstico

- **Qué rompe**: `IActivityManager.Stub.asInterface(binder)` es una referencia directa a una interfaz oculta (`@hide`). Las interfaces las proveen AIDL locales (`app/src/main/aidl/android/app/IActivityManager.aidl`), pero en runtime parent-delegation carga primero la clase real del framework; en el framework del dispositivo la referencia ya no resuelve (`NoSuchMethodError`). **No es regresión del fork**: el código es upstream sin tocar; `RestrictionBypass` (que fork.6 quitó) estaba sin ninguna referencia en el código fuente — solo la línea del build.gradle.
- **Por qué mata el dock entero**: `initWrappers` atrapaba `Exception`, pero `NoSuchMethodError` es un `Error` → escapa → `onCreate` del servicio de accesibilidad muere **antes de crear ninguna ventana** → dock invisible en todas las pantallas (el usuario lo nota en la secundaria) y el servicio nunca se conecta → `DockService.isRunning` nunca se pone a true → la app sigue mostrando el aviso «activar la accesibilidad» aunque el toggle del sistema esté activo. Además el catch envolvía los tres wrappers en un bloque: si el primero falla, Wi-Fi y Bluetooth quedan null aunque habrían funcionado.

### Fix (`f2f4727`, tres capas)

1. **`App.onCreate`: `HiddenApiBypass.addHiddenApiExemptions("L")`** (dependencia nueva `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`, Maven Central, best effort): donde el bloqueo es de política de hidden API — lo más probable en ROMs estrictas — los wrappers vuelven a linkear y las funciones Shizuku siguen operativas.
2. **`initWrappers` por wrapper y con `Throwable`**: cada wrapper se construye en su propio try/catch; uno que no puede levantarse se queda null y degrada sus funciones (Wi-Fi → panel del sistema, Bluetooth → ajustes del sistema, tareas → API pública de recents) sin tumbar el dock. `updateRunningTasks` exige `activityManagerWrapper?.isAlive() == true` para usar el camino Shizuku.
3. **Wrappers resilientes**: la adquisición de la interfaz va en try/catch (wrapper «muerto» con `isAlive()=false` en vez de lanzar) y todos los métodos (getTasks/resizeTask/removeTask/setWifiEnabled/getScanResults/getConnectionInfo/startScan/setBluetoothEnabled) atrapan `Throwable` con resultados vacíos/neutros — un framework que cambie firmas degrada sin crashes a mitad de la interacción.

### Publicación

- CI en la rama: **verde** (`f2f4727`, Build project + Build debug apk).
- Fast-forward a main; bump versionCode **1154** / versionName **1.15.4**; tag `v1.15.4-fork.8` → Release automática con APK.
- Estado: ver cierre al final del archivo.

### Cierre (verificado)

- [x] CI **verde en las 6 corridas** de la jornada: la rama `bugfix/wrapper-init-crash` (Build project + Build debug apk sobre `f2f4727`), main (Build project + Build debug apk sobre `af3332e`) y el tag `v1.15.4-fork.8` (Release debug apk + Build project sobre `af3332e`). Release **«SmartDock fork v1.15.4-fork.8»** creada por el workflow, asset renombrado por API a `SmartDock-fork-debug-v1.15.4-fork.8.apk` (13.159.770 bytes) y notas de release en inglés.
- [x] APK verificada: **versionCode 1154, versionName 1.15.4**, cert SHA-256 `4680C23198D542A0423DFD95F1A0D66A80100FA30CB72CAEDF107E41B54B41DB` (el mismo de fork.4–7 → **se instala encima de cualquier build anterior del fork sin desinstalar**), sha256 `75c3e429cd18e9cbd2ae3c6efbb9db450a2a0e09f6b218e386f0041b07a28069`. Entregada al usuario como `SmartDock-fork-debug-v1.15.4-fork.8.apk`. Estado: **v1.15.4-fork.8 publicada, actualizable y verificada**. Pendiente de verificación del usuario en el dispositivo afectado: dock visible en la pantalla secundaria y aviso de accesibilidad desaparecido; si el wrapper de ActivityManager sigue sin linkear (framework que eliminó la interfaz, no solo bloqueada), el dock funcionará con la API pública de recents y el panel Wi-Fi del sistema — registrar el resultado en `logcat -s SmartDock` para saber qué capa entró en acción.

## 2026-11-22 (sexta pasada) — paquete de mantenimiento: 6 mejoras, 6 ramas (sin release)

Motivo: el usuario pide un paquete estructurado de mejoras de mantenimiento (6 tareas, cada una en su rama `feature/*` o `bugfix/*`, merge a main por fast-forward, CI verde, documentación al estilo REGISTRO). No es una release: no hay bump de versionCode ni tag; son commits de mantenimiento sobre main. Cada rama se cortó del main recién consolidado de la anterior, se pusheó, esperó CI verde y se hizo fast-forward.

### T1 — Banner de fork en el README raíz (`feature/fork-readme-banner`, `7d38051`)

- El `README.md` raíz era todavía el de upstream y no mencionaba que esto es un fork. Se antepone un bloque (antes del título existente) con atribución a `axel358/smartdock`, qué cambia el fork (todos los bugs reportados cerrados, display secundario fail-safe, modo escritorio externo, «Send to screen», releases con CI), enlaces a `CHANGELOG-FORK.md` y `docs/REGISTRO.md`, y la nota de instalación: APKs debug firmadas con el keystore del fork (cert `4680C231…B54B41DB`), se actualizan entre sí, **no** se instalan encima de la de F-Droid.
- CI: Build project verde sobre `7d38051`. Fast-forward a main.

### T5 — Plantilla de issues para el fork (`feature/issue-templates`, `5e376a4`)

- `.github/ISSUE_TEMPLATE/bug_report.yml` (formulario YAML de GitHub) que pide lo que hace falta para reproducir un bug del dock: tipo de instalación (sistema / adb / Shizuku), entorno de pantallas (solo principal / externo USB-C,HDMI / virtual Waydroid / mixto), dispositivo y fabricante, versión de Android y ROM, versión del fork, pasos, esperado vs. real, y un campo de log de crash que apunta a Ajustes → Avanzado → Diagnóstico (tras T2). `config.yml` desactiva issues en blanco y rutea feature requests a Discussions.
- CI: Build project verde sobre `5e376a4`. Fast-forward a main.

### T6 — Paquete de PRs hacia upstream (`docs/upstream-prs`, `85d8f66`)

- `docs/UPSTREAM-PRS.md` mapea cada issue upstream cerrado en el fork a sus commits (hash + título), con nota de cherry-pick (¿limpio / adapta / política?) y un orden de envío propuesto: fixes puros de causa raíz primero (#167 `c5907bf`, #250 `dd14aed`, #266 `5a3beca`), luego los splits de los commits multi-concern (`f5409c5` → #270/#264/#267/#268; `c1d02b2` → #259/#253), después #200 y #263 (feature-adjacent, con cadenas a traducir), y el endurecimiento de wrappers (`f2f4727`) el último por añadir dependencia Maven + unseal global de hidden APIs. Estado: **preparado, no enviado**; decisión pendiente del dueño.
- CI: Build project verde sobre `85d8f66`. Fast-forward a main.

### T4 — Constante nombrada para el delay de captura de #266 (`bugfix/named-screenshot-delay`, `ec93cfb`)

- El delay de 400 ms entre ocultar el panel de ajustes rápidos y disparar `GLOBAL_ACTION_TAKE_SCREENSHOT` (para que el compositor retire el frame que aún mostraba el panel, #266) estaba inline en la llamada. Se extrae a un `object Timers` (`utils/Timers.kt`) junto con el delay de restore (1500 ms), el refresh de tareas (1200 ms) y los timers de boot-recovery (`CHECK_DELAY_MS`, `REBIND_DELAY_MS`) que eran consts privadas de `BootCompletedReceiver`. El delay de captura lleva el razonamiento completo (retiro de frame del compositor + margen para dispositivos lentos + por qué se mantiene 400 ms sobre un valor más ajustado).
- CI: Build project + Build debug apk verdes sobre `ec93cfb`. Fast-forward a main.

### T2 — Telemetría de crash local (`feature/crash-telemetry`, `827b075`)

- Los `Throwable` tragados en los wrappers Shizuku (adquisición resiliente de fork.8) y en `initWrappers` eran invisibles, y el endurecimiento de #253 se hizo a ciegas sin stack trace. Se añade `utils/CrashLog.kt`: escribe el stack trace completo (timestamp UTC, texto plano, info de dispositivo/SDK) en `filesDir/crashes/`, rota a 10 archivos y espeja a logcat. `App.onCreate` bindea el contexto de aplicación y el `UncaughtExceptionHandler` persiste el crash **antes** del flujo existente de `DebugActivity` (la UI de crash no cambia); los catches de adquisición de los tres wrappers y de `initWrappers` reportan a través de `CrashLog`. Sección «Diagnóstico» en Ajustes → Avanzado con «Ver último reporte de crash» (diálogo) y «Compartir log de crash» (`ACTION_SEND`). 6 cadenas nuevas traducidas a los 6 locales mantenidos (de, es, pa, zh-rCN, zh-rHK, zh-rTW) — lint `MissingTranslation` pasó.
- CI: Build project verde sobre `827b075` (compile + lint). Fast-forward a main.

### T3 — Tests unitarios para la lógica pura extraída (`feature/unit-tests`, `2837744`)

- `solve()` del parser de calculadora y la máquina de modificadores de teclado eran la lógica pura más expuesta a regresiones, pero vivían dentro de código que toca Android (`Utils.solve`/`ExprParser` privado y `DockService.onKeyEvent`) así que no corrían bajo `./gradlew test`. Se extraen a helpers puros JVM: `object Calculator` (el parser recursivo sale del `ExprParser` privado; `Utils.solve` queda como delegación de una línea) y `KeyboardModifiers` + `MetaMenuTracker` (el pass-through de AltGr #167 y el tracking de Meta como modificador #267 salen de `onKeyEvent`; los valores de constantes de `KeyEvent` están hardcodeados por ABI). `DockService` alimenta los campos del evento y lee `metaTracker.usedAsModifier` en el release de Meta. Se añade `testImplementation 'junit:junit:4.13.2'` y dos clases de test bajo `app/src/test/`: operaciones básicas, precedencia/asociatividad, paréntesis, unario, entradas vacías/basura (nunca lanza), decimales, números grandes, las variantes del acorde AltGr y los casos Meta mantenida/soltada/repeat. **`./gradlew build` corre los tests vía el lifecycle y pasó**.
- CI: Build project verde sobre `2837744` (compile + test + lint). Fast-forward a main.

### Cierre (verificado)

- [x] **6 ramas, 6 fast-forwards, CI verde en cada una**: `feature/fork-readme-banner` (`7d38051`), `feature/issue-templates` (`5e376a4`), `docs/upstream-prs` (`85d8f66`), `bugfix/named-screenshot-delay` (`ec93cfb`), `feature/crash-telemetry` (`827b075`), `feature/unit-tests` (`2837744`). main avanzó de `fe08541` a `2837744` (6 commits). Sin bump de versionCode ni tag: es mantenimiento, no release.
- [x] `./gradlew build` (compile + lint + **test**) verde en la punta de main; los tests nuevos (`CalculatorTest`, `KeyboardModifiersTest`) corren y pasan en CI.
- [x] Sin regresiones esperadas: la extracción de `Calculator`/`KeyboardModifiers`/`Timers` no cambia comportamiento (delegación + constantes con el mismo valor); `CrashLog` es aditivo (persiste crashes sin alterar la UI de `DebugActivity`); las traducciones pasaron lint. Estado: **pendiente de confirmación del usuario en dispositivo** (especialmente que la sección Diagnóstico aparece y que un crash real deja archivo en `filesDir/crashes/`), salvo T3 que está verificado por CI.

## 2026-11-22 (séptima pasada) — v1.15.5-fork.9: display secundario + accesibilidad listada-pero-no-bound

Motivo: el usuario reporta que con `prefer_last_display` activo el dock no aparece en la pantalla secundaria, y que el botón de accesibilidad muestra el permiso desactivado aunque el toggle del sistema esté ON. Diagnóstico de causa raíz (no regresión del fork.8: código upstream preexistente + una API equivocada que pasó inadvertida + un catch demasiado estrecho):

- **Bug A (display secundario)**: `DeviceUtils.getDisplayContext` usaba `createDisplayContext(display)`. En Android 11+ (API 30, minSdk del fork) eso produce un `WindowManager` que sigue enrutando ventanas overlay al display por defecto — el dock nunca llegaba al monitor. Fix: `createWindowContext(display, TYPE_APPLICATION_OVERLAY)` (`df4085d`). Todas las ventanas del dock son `TYPE_APPLICATION_OVERLAY`, así que el tipo es único y no hay riesgo de mismatch.
- **Bug A↔B convergencia (resilience)**: `restartUI` atrapaba solo `Exception`, así que un `Error` en `createViews` (un `IllegalAccessError` de un enlace hidden-API reaparecido, o un `WindowManager` que rechaza la ventana en un display) escapaba del fallback y mataba el binding → el servicio quedaba listado-pero-no-bound → Bug B. `onServiceConnected` no tenía guard. Fix: ambos caminos atrapan `Throwable`, caen al display principal y registran en `CrashLog`; si el fallback también falla, el servicio se queda bindeado sin UI (`ff3f908`).
- **Bug B (accesibilidad)**: `isAccessibilityServiceEnabled` solo miraba `getEnabledAccessibilityServiceList` (vacío cuando listado-pero-no-bound). Fix: devuelve `true` cuando el servicio está listado (coincide con el toggle); nuevo `isAccessibilityServiceBound` para saber si hay instancia viva; el diálogo ofrece **Reactivar** (drop + re-add con el grace del boot receiver) en ese estado (`a51c8b9`). 3 cadenas nuevas en los 6 locales mantenidos (pa con apóstrofe escapado).

Rama: `bugfix/secondary-display-accessibility-regression`, 4 commits de fix + 1 de bump + 1 de docs, fast-forward a main. versionCode 1154 → 1155, tag `v1.15.5-fork.9`.

### Cierre (verificado por CI, pendiente de confirmación en dispositivo)

- [x] `./gradlew build` (compile + lint + test) verde en la rama y en main tras fast-forward.
- [x] Sin bump regresivo: versionCode 1155 > 1154; se instala encima de fork.8 sin desinstalar ni perder ajustes (mismo keystore, cert `4680C231…B54B41DB`).
- [x] Estado: **pendiente de confirmación del usuario en dispositivo** — dock visible en la pantalla secundaria con `prefer_last_display`, botón de accesibilidad en icono activado con el toggle ON, y Reactivar restaurando el servicio en el estado listado-pero-no-bound. Si el wrapper de ActivityManager sigue sin linkear en el framework del dispositivo, el dock funciona con la API pública de recents y el panel Wi-Fi del sistema (fork.8); registrar el resultado en `logcat -s SmartDock` y en Ajustes → Avanzado → Diagnóstico.

## 2026-11-23 (octava pasada) — tres features de UX/soporte post-fork.9: update checker, panel de diagnósticos, toggle de teclado

Motivo: el fork vive fuera de F-Droid (nadie sabe que salió fork.9 salvo quien mire el repo), los reportes de bug llegan sin contexto, y en dispositivos con teclado físico el menú de apps no ofrece teclado blando aunque el upstream #123/#122 lo pide. Tres features con APIs públicas y sin dependencias nuevas. La feature #1 del listado original (zona de estado: reloj/batería/wifi) **ya existía completa** en el fork (`dock.xml` `status_area` + `updateQuickSettings` con prefs `enable_qs_*` por orientación, `TextClock` en `date_btn`), así que no se reimplementó — solo se reportó.

### F1 — Comprobador de actualizaciones contra GitHub Releases (`feature/update-checker`, `55ab8ac`)
- `utils/UpdateChecker.kt`: `HttpURLConnection` a `releases/latest`, parsea `tag_name`+`html_url` con `org.json`, compara el tag `(major,minor,patch,forkN)` contra la string build-constante `fork_release_tag` (`translatable="false"`, fuente de la verdad del tag del build). Robusto a iteraciones same-version (fork.10 > fork.9). Todo síncrono, llamado fuera del main thread; falla a null / «no se pudo comprobar», nunca crashea.
- `DockService.onServiceConnected`: check silencioso fire-and-forget en `Dispatchers.IO`, rate-limited a 1×/24h vía SharedPreferences, cachea el veredicto para que Ajustes lo muestre sin red.
- `AdvancedPreferences`: preference «Check for updates» en Diagnostics; fetch en vivo en `Thread`+`Handler(main)` (sin `lifecycle-runtime-ktx`), `MaterialAlertDialog` con botón «Open release» (`ACTION_VIEW`).
- `AndroidManifest`: añadido `INTERNET` (permission normal, auto-concedido) — el app no tenía permiso de red porque la web search lanza browser externo.
- 8 strings + `fork_release_tag` en los 6 locales mantenidos.

### F2 — Panel de salud en Diagnóstico (`feature/diagnostics-panel`, `8f4f61b`)
- `utils/DockServiceState.kt`: object global (patrón `CrashLog`) que espeja los wrappers vivos + `isRunning`; DockService lo pobla en `initWrappers`/`destroyWrappers`/`onServiceConnected`/`onDestroy`.
- `utils/DiagnosticsBuilder.kt`: snapshot en texto plano — service running, `isAlive()` de los 3 wrappers (ActivityManager/WifiManager/BluetoothManager), Shizuku bound / system-app / `WRITE_SECURE_SETTINGS`, lista de displays, Build SDK/release/device, y tail de 600 chars del último crash. Pure reads, sin I/O, sin throws. Labels en inglés (output diagnóstico, como los crash logs).
- `AdvancedPreferences`: preference «View diagnostics» en Diagnostics; `MaterialAlertDialog` con botón «Share diagnostics» (`ACTION_SEND`) siguiendo el patrón de `view_crash_log`.
- 4 strings en los 6 locales.

### F3 — Toggle de teclado blando en el menú de apps (`feature/soft-keyboard-toggle`, `18fcc9c`)
- El autofoco ya existía (`searchEt.requestFocus()`); el teclado blando se mostraba auto solo si `configuration.keyboard == NOKEYS` o tablet mode. Sin toggle para forzarlo con teclado físico (upstream #123/#122).
- Preference `show_software_keyboard_in_menu` (default off) en App menu settings; DockService ORs el pref en la condición `showSoftInputOnFocus` existente — mismo mecanismo que ya funcionaba, sin path de IME nuevo que pueda fallar en overlays. 2 strings en los 6 locales.

### Cierre (verificado por CI)

- [x] `Build project` (compile + lint) y `Build debug apk` verde en cada rama y en main tras fast-forward (`55ab8ac`, `8f4f61b`, `18fcc9c`).
- [x] Sin bump de versionCode (features, no release). `fork_release_tag` = `v1.15.5-fork.9` (constante del build, no un release nuevo).
- [x] Sin dependencias nuevas; solo APIs públicas (`HttpURLConnection`, `org.json`; `BatteryManager`/`TextClock` ya en uso).
- [x] Pendiente de confirmación en dispositivo: el check de actualizaciones aparece en Ajustes → Avanzado → Diagnóstico; el panel de diagnósticos muestra los wrappers vivos y los displays; el toggle de teclado levanta el IME con teclado físico conectado.
