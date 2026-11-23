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

Motivo: el fork vive fuera de F-Droid (nadie sabe que salió fork.9 salvo quien mire el repo), los reportes de bug llegan sin contexto, y en dispositivos con teclado físico el menú de apps no ofrece teclado blando aunque el upstream #122 lo pide. Tres features con APIs públicas y sin dependencias nuevas. La primera feature del listado original (la zona de estado: reloj/batería/wifi — ítem de la lista, no un issue de upstream) **ya existía completa** en el fork (`dock.xml` `status_area` + `updateQuickSettings` con prefs `enable_qs_*` por orientación, `TextClock` en `date_btn`), así que no se reimplementó — solo se reportó.

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
- El autofoco ya existía (`searchEt.requestFocus()`); el teclado blando se mostraba auto solo si `configuration.keyboard == NOKEYS` o tablet mode. Sin toggle para forzarlo con teclado físico (upstream #122).
- Preference `show_software_keyboard_in_menu` (default off) en App menu settings; DockService ORs el pref en la condición `showSoftInputOnFocus` existente — mismo mecanismo que ya funcionaba, sin path de IME nuevo que pueda fallar en overlays. 2 strings en los 6 locales.

### Cierre (verificado por CI)

- [x] `Build project` (compile + lint) y `Build debug apk` verde en cada rama y en main tras fast-forward (`55ab8ac`, `8f4f61b`, `18fcc9c`).
- [x] Sin bump de versionCode (features, no release). `fork_release_tag` = `v1.15.5-fork.9` (constante del build, no un release nuevo).
- [x] Sin dependencias nuevas; solo APIs públicas (`HttpURLConnection`, `org.json`; `BatteryManager`/`TextClock` ya en uso).
- [x] Pendiente de confirmación en dispositivo: el check de actualizaciones aparece en Ajustes → Avanzado → Diagnóstico; el panel de diagnósticos muestra los wrappers vivos y los displays; el toggle de teclado levanta el IME con teclado físico conectado.

## 2026-11-23 (novena pasada) — seguimiento de la review: seis correcciones sobre la sesión 8

Motivo: la review de la sesión 8 dejó seis puntas sueltas — citas de issues incorrectas en la documentación, un check silencioso de red sin consentimiento explícito, lógica pura del parser de versión sin tests, estado del updater fuera del panel de diagnósticos, `HISTORIA.md` sin actualizar, y el primer release con `fork_release_tag` en juego (fork.10) sin checklist de bump. Seis correcciones, dos ramas (`feat/updater-followups` para el código, `docs/session-8-followups` para la documentación), sin release.

### R1 — Citas de issues corregidas (`docs/session-8-followups`)
- `docs/REGISTRO.md` §octava pasada: el toggle de teclado blando citaba «upstream #123/#122», pero #123 es «matar tareas» (otra feature distinta); la cita correcta es solo **#122**. Y la «feature #1» del listado original (zona de estado) no es un issue de upstream — era el ítem 1 de la lista de review; no existe tal issue. Aclarado a «la primera feature del listado original (ítem de la lista, no un issue de upstream)».
- El commit `18fcc9c` y su mensaje siguen citando #123/#122 (historial ya pusheado); no se reescribe el historial. El REGISTRO es el registro vivo y ahora cita el número correcto. (#1 de la review.)

### R2 — Toggle de consentimiento del check silencioso (`feat/updater-followups`, `c0f3646`)
- `SwitchPreferenceCompat` «buscar actualizaciones automáticamente» (default on) en Ajustes → Avanzado → Diagnóstico, encima del «Check for updates» manual. `DockService` gatea el check silencioso fire-and-forget con `UpdateChecker.KEY_AUTO_CHECK`. La clave vive en las default SharedPreferences (no en las privadas del checker) para que el switch de Settings binde a ella; el manual «Check for updates» sigue funcional con el toggle off. 2 strings en los 6 locales. (#2 de la review.)

### R3 — Tests del parser de versión (`feat/updater-followups`, `aca3df6`)
- `app/src/test/.../UpdateCheckerTest.kt`: `parseVersionTuple`/`parseForkN` hechos `internal` (module-private) para cubrirlos directo; `isNewer` (público) cubierto para la semántica de comparación. Casos: tag malformado (garbage, vacío, partes no numéricas → vacío/parcial, nunca throws), sin `-fork.N` (forkN == 0), 1.15.10 vs 1.15.9 (el tuple gana al fork-N), fork.10 vs fork.9 (la regresión que motivó tuple + forkN), y auto-reconocimiento (una release no es newer que sí misma — el check de fork.10). (#3 de la review.)

### R4 — Estado del updater en el panel de diagnósticos (`feat/updater-followups`, `f25453b`)
- `DiagnosticsBuilder`: sección «Updater» con el `fork_release_tag` del build, el estado del toggle `auto_check_updates`, la última comprobación (o «never»), y el veredicto cacheado (tag + «up to date»/«update available»). `UpdateChecker.lastCheckMs(context)` expone el `last_check_ms` cacheado. Pure reads, sin red — soporte y diagnóstico en el mismo panel. (#4 de la review.)

### R5 — `HISTORIA.md` al día (`docs/session-8-followups`)
- La sesión 8 actualizó REGISTRO + AGENTS pero no HISTORIA (el paquete de mantenimiento sí la tocó). Añadidas las entradas séptima (fork.9), octava (post-fork.9 features) y novena (esta) a la Línea de tiempo, al resumen «Qué cambió» y al Mapa de ramas. Patrón fijado: cada sesión toca HISTORIA. (#5 de la review.)

### R6 — Nota de release fork.10 (`docs/session-8-followups`)
- `AGENTS.md §5`: el primer release con `fork_release_tag` en juego manda bump simultáneo de `versionCode` (1155→1156), `versionName` (1.15.5→1.15.6) y `fork_release_tag` (`v1.15.5-fork.9`→`v1.15.6-fork.10`), y verificar que el updater reconoce su propia release (`isNewer` cubierto por `UpdateCheckerTest`; tras publicar, una build con fork.10 debe ver «up to date»). (#6 de la review.)

### Cierre

- [x] `Build project` (compile + lint) verde en `feat/updater-followups` y en main tras fast-forward — **el sandbox del agente no tiene Android SDK (AGENTS.md §2); CI en Actions decide**. Los tres commits siguen patrones existentes (SwitchPreferenceCompat, prefs, DiagnosticsBuilder) y las aserciones del test se trazaron contra el parser antes de commitear. Confirmado el 2026-11-23: 3× `build` success sobre `0049dd0` (runs 45104065018, 45104068516, 45104068538).
- [x] Sin bump de versionCode (seguimiento, no release). `fork_release_tag` sigue en `v1.15.5-fork.9`.
- [x] Sin dependencias nuevas; `androidx.preference.PreferenceManager` ya en uso (DockService).
- [x] Pendiente de confirmación en dispositivo: el toggle aparece encima de «Check for updates»; el panel de diagnósticos muestra la sección «Updater».

## 2026-11-23 (décima pasada) — corte de fork.10: bump 1156 + primera release con `fork_release_tag` en juego

Motivo: CI verde sobre `0049dd0` (F6+F9), 13 commits acumulados desde el tag `v1.15.5-fork.9` (5 features, 2 seguimientos del updater, 1 test y 5 docs/chore) sin bugs abiertos del fork — toca release. Primer corte con `fork_release_tag` en juego (nota R6 de la novena pasada): bump simultáneo de las tres piezas en el mismo commit. Rama `chore/release-fork.10`, fast-forward a main.

### Release

- **Bump de release** (`a2db2ca`): `versionCode` 1155→1156 y `versionName` 1.15.5→1.15.6 en `app/build.gradle` + `fork_release_tag` `v1.15.5-fork.9`→`v1.15.6-fork.10` en `values/strings.xml`, en el mismo commit. Un tag viejo haría que la build nueva se ofreciera «update available» contra sí misma en `releases/latest`.
- **Verificación del updater** (lógica de `isNewer` simulada línea a línea desde `UpdateChecker.kt` + `UpdateCheckerTest` en CI): `isNewer("v1.15.6-fork.10", "v1.15.5-fork.9")` = **true** (nueva release detectada) y `isNewer("v1.15.6-fork.10", "v1.15.6-fork.10")` = **false** (auto-reconocimiento, «up to date»). 7/7 checks del script de verificación en verde.
- **Tag anotado** `v1.15.6-fork.10` («SmartDock fork v1.15.6-fork.10 (versionCode 1156)») sobre la punta de main tras el ff → `release_apk.yml` publica la GitHub Release con `SmartDock-fork-debug-v1.15.6-fork.10.apk`.
- **Docs**: CHANGELOG-FORK.md (sección fork.10), FORK.md (fila 1156), HISTORIA.md (ítem 12 + mapa de ramas/tags), AGENTS.md (§1 identidad 1156/1.15.6, §3 main, §5 próximo release → fork.11).

### Cierre

- [x] `Build project` verde sobre `a2db2ca` (bump) y sobre la punta de main; `release_apk.yml` publica la release — **CI decide (sandbox sin SDK)**. Confirmado el 2026-11-23 05:24–05:25 UTC: 4/4 check-runs success sobre `0743186` (release + 3× build) y los 4 workflow runs del commit en success; release publicada (`releases/latest` = `v1.15.6-fork.10`, asset `SmartDock-fork-debug.apk`, 13.2 MB).
- **Body de la release**: el workflow publicó el body automático («Full Changelog»); editado con el resumen en inglés de las 5 features + link al CHANGELOG (paridad con fork.9, que también lleva body manual). **Aclaración del asset**: el APK de la release se llama `SmartDock-fork-debug.apk` (así también fork.9, verificado por API) — el tag lo identifica el build vía `fork_release_tag` en Settings → About; las entradas históricas del CHANGELOG citaban el nombre con el tag, y la entrada fork.10 cita el nombre real.
- [x] Sin bump regresivo: 1156 > 1155; se instala encima de fork.9 sin desinstalar ni perder ajustes (mismo keystore, cert `4680C231…B54B41DB`).
- [x] Sin cambios de código de la app en el corte: solo `app/build.gradle` + `strings.xml` (`fork_release_tag`) + documentación.
- [x] Verificación updater: nueva release detectada vs fork.9 y auto-reconocimiento «up to date» (simulación + tests en CI).
- [ ] Pendiente de confirmación en dispositivo: con fork.10 instalada y `releases/latest` == `v1.15.6-fork.10`, el updater muestra «up to date» y no «update available».

## 2026-11-23 (undécima pasada) — F7: geometría de ventana por app (`feature/per-app-window-geometry`, `496250f`)

Motivo: F7 del listado original — cada app puede conservar su propia geometría de ventana: «Remember window size» en el menú contextual de una app en ejecución lee los bounds actuales de la tarea y los guarda por paquete; el siguiente lanzamiento de esa app los restaura (nuevo modo de lanzamiento por-app «custom») en vez de uno de los modos fijos.

- **AIDL + wrapper**: `IActivityManager.getTaskBounds` (la llamada oculta detrás del `resizeTask` que el dock ya usa) + `ActivityManagerWrapper.getTaskBounds(taskId): Rect?` con el mismo contrato de degradación que `forceStopPackage` (null, nunca escapa al caller).
- **DB v2**: cuatro columnas nullable de geometría en `table_launch_modes` (ALTER TABLE en `onUpgrade`; las installs viejas conservan sus modos guardados); `saveWindowGeometry` guarda los bounds y pasa la fila a «custom».
- **launchApp**: resuelve la geometría recordada y la pasa por `makeActivityOptions`/`makeLaunchBounds`; una geometría ausente o inusable cae a «standard» en vez de un Rect vacío, y los bounds guardados se clampan al display de destino (una ventana recordada en otra pantalla no abre de tamaño cero).
- **Menú**: entrada «Remember window size» gated a Shizuku (el mismo privilegio que CLOSE/RESIZE ya exigen) y a la tarea estar en freeform; toasts `window_size_remembered` / `window_size_unavailable`.
- 3 strings nuevas en los 6 locales mantenidos.

### Cierre

- [x] `Build project` (compile + lint, incluye MissingTranslation) verde sobre `496250f` — confirmado el 2026-11-23 05:40 UTC (check-run success).
- [x] Sin bump de versionCode (feature, no release). El corte será fork.11 en el próximo bump 1157/1.15.7.
- [x] Sin dependencias nuevas; solo APIs públicas + la llamada oculta del AIDL que el wrapper ya degrada a null.
- [ ] Pendiente de confirmación en dispositivo: guardar el tamaño de una ventana reposicionada y relanzar la app restaura la geometría; un cambio de display posterior no abre ventanas de tamaño cero.

## 2026-11-23 (duodécima pasada) — Corte de fork.11: release v1.15.7-fork.11 (`chore/release-fork.11`, `e393a6c`)

Motivo: cortar F7 (geometría de ventana por app) como release del fork, replicando el procedimiento fijado en fork.10: rama `chore/release-fork.11` con bump simultáneo `versionCode` 1157 / `versionName` 1.15.7 / `fork_release_tag` v1.15.7-fork.11 + entrada en `CHANGELOG-FORK.md`, fast-forward a main, tag anotado `v1.15.7-fork.11`, workflow de release, body manual, verificación de updater.

- El corte no toca código de la app: `app/build.gradle` + `strings.xml` (`fork_release_tag`) + `CHANGELOG-FORK.md` (3 ficheros, +11/−3).
- La entrada del CHANGELOG-FORK recoge F7 con los detalles del `496250f`: `getTaskBounds` vía Shizuku (degradación null como `forceStopPackage`), DB v2 con ALTER TABLE, modo «custom» con clamp y fallback, toasts, 6 locales.

### Cierre

- [x] `Build project` verde sobre `e393a6c` antes del tag (05:50 UTC) y 5/5 check-runs success después (2× build sobre el tag + 2× build sobre main + release, 05:51–05:55 UTC).
- [x] `Release debug apk` publicó la release a las 05:53 UTC: `releases/latest` = `v1.15.7-fork.11`, asset `SmartDock-fork-debug.apk` (13229422 bytes ≈ 13.2 MB).
- [x] **Body de la release**: el workflow publicó el body automático («Full Changelog»); editado con el resumen en inglés de F7 + link al CHANGELOG (paridad con fork.9/fork.10; script `scripts/edit_fork11_body.py` fuera del repo).
- [x] Sin bump regresivo: 1157 > 1156; se instala encima de fork.10 sin desinstalar ni perder ajustes (mismo keystore debug, cert `4680C231…B54B41DB`).
- [x] Sin cambios de código de la app en el corte: solo `app/build.gradle` + `strings.xml` (`fork_release_tag`) + `CHANGELOG-FORK.md`.
- [x] Verificación updater: `releases/latest` == `v1.15.7-fork.11` == `fork_release_tag` del build → una instalación con fork.11 se reconoce «up to date» (`UpdateCheckerTest.ownReleaseIsNotNewerThanItself` en CI); una instalación con fork.10 ve «update available» (1.15.7-fork.11 > 1.15.6-fork.10).
- [ ] Pendiente de confirmación en dispositivo: con fork.10 instalada, el updater ofrece la actualización; con fork.11 instalada, «up to date»; guardar tamaño de ventana + relanzar restaura la geometría.

## 2026-11-23 (decimotercera pasada) — F5: presets de geometría de ventana con nombre (`feature/window-presets`, `e3a12bc` + `968b4e4`)

Motivo: F5 del listado original = upstream #209 («[Feature] Freeform Size Intents, And Window Positions» — usuarios pidiendo «set up and store a few of these predefined window positions»): el submenú Resize de una app en ejecución gana «Custom…», un diálogo con cuatro sliders (left/top/width/height como porcentajes del área usable) y preview en píxeles, Apply para redimensionar la tarea, y «Save as preset» para guardar la geometría con nombre. Los presets guardados se listan directamente en el submenú Resize y aplican a cualquier ventana; «Delete presets…» los gestiona. F7 (fork.11) cubría «recordar la ventana actual por app»; F5 es lo complementario global.

- **`utils/WindowPresets.kt`**: almacenamiento SharedPreferences JSON (`org.json`, sin dependencias nuevas), guarda los cuatro porcentajes —no píxeles— para que un preset re-aplique en el display donde viva la tarea (mismo razonamiento por-display que `makeLaunchBounds`); toda lectura degrada a vacío ante entradas malformadas y `toBounds` clampa y valida (un preset editado a mano jamás produce el bug de la ventana de tamaño cero — misma guardia que el modo «custom» de F7).
- **`Action` gana payload opcional** (el nombre del preset para `WINDOW_PRESET`/`WINDOW_PRESET_DELETE`); `MenuCommand` gana `WINDOW_CUSTOM`/`WINDOW_PRESET`/`WINDOW_PRESET_DELETE`. El dispatch sigue por comando, nunca por texto visible.
- **Bounds por display real de la tarea** (`resolveDisplayId`, el razonamiento de `resizeTask`); el resize va por el mismo camino Shizuku con el mismo try/catch.
- **Layout nuevo** `custom_window_dialog.xml` (4 SeekBar + preview + Apply + nombre de preset).
- 11 strings nuevas en los 6 locales mantenidos.

### Cierre

- [x] Primera iteración roja (`e3a12bc`): 3 errores de compilación — `JSONArray` no es Iterable (list/get iteran por índice con `optJSONObject(i)`), y `max()` necesitaba `kotlin.math.max` en DockService (AppUtils lo tenía, DockService nunca). Corregido en `968b4e4`; CI verde (15:17 UTC). Log extraído con `scripts/fetch_ci_log.py` (el endpoint de logs devuelve un zip por-paso).
- [x] `Build project` verde sobre `968b4e4` (15:17 UTC); ff a main.
- [x] Sin bump de versionCode (feature, no release). El corte será fork.12 en el próximo bump 1158/1.15.8.
- [x] Sin dependencias nuevas; solo `org.json` (plataforma) y APIs ya en uso.
- [ ] Pendiente de confirmación en dispositivo: crear un preset desde el diálogo Custom, verlo en el submenú Resize, aplicarlo a otra ventana, y borrarlo; el preview muestra píxeles correctos en display secundario.

## 2026-11-23 (decimocuarta pasada) — corte de fork.12: release v1.15.8-fork.12 (`chore/release-fork.12`, `2851dcc`)

Motivo: CI verde sobre `a5cfc6b` (F5 + docs), 3 commits acumulados desde el tag `v1.15.7-fork.11` (2 de feature + 1 de docs) sin bugs abiertos del fork — toca release. Bump simultáneo de las tres piezas (versionCode 1158, versionName 1.15.8, `fork_release_tag` v1.15.8-fork.12) en el mismo commit `2851dcc`, tercer corte con el triple bump.

### Release

- Tag `v1.15.8-fork.12` sobre `2851dcc` → `Release debug apk` publicó la release a las 15:36 UTC: `releases/latest` = `v1.15.8-fork.12`, asset `SmartDock-fork-debug.apk` (13.249.623 bytes ≈ 13,2 MB).
- Body de la release editado con el resumen en inglés de F5 + link al CHANGELOG (paridad con fork.9–11; script `scripts/edit_fork12_body.py` fuera del repo).

### Cierre (verificado)

- [x] CI verde en el tag: 3/3 check-runs success (build sobre la rama 15:34, release 15:36, build sobre el tag 15:37; los dos workflows).
- [x] Sin bump regresivo: 1158 > 1157; se instala encima de fork.11 sin desinstalar ni perder ajustes (mismo keystore debug, cert `4680C231…B54B41DB`).
- [x] APK verificada: **versionCode 1158, versionName 1.15.8**, cert SHA-256 `4680C23198D542A0423DFD95F1A0D66A80100FA30CB72CAEDF107E41B54B41DB` (el mismo de fork.4–11), sha256 `50968e351dae21b139ffe06f372f443a992c8e9eb68e149d334a0bed8a07c84e`. Entregada al usuario como `SmartDock-fork-debug-v1.15.8-fork.12.apk`. Scripts de verificación: `verify_apk_signature.py`, `verify_fork12_updater.py` (nuevo, expectativas fork.12) y `verify_fork12_release.py` (nuevo, descarga el asset).
- [x] Verificación updater (TODO OK): `releases/latest` == `v1.15.8-fork.12` == `fork_release_tag` del build → una instalación con fork.12 se reconoce «up to date»; una con fork.11 ve «update available» (1.15.8-fork.12 > 1.15.7-fork.11); la iteración same-version y fork.13 también detectadas.
- [x] Sin cambios de código de la app en el corte: solo `app/build.gradle` + `strings.xml` (`fork_release_tag`) + `CHANGELOG-FORK.md`.
- [ ] Pendiente de confirmación en dispositivo: con fork.11 instalada, el updater ofrece la actualización; con fork.12 instalada, «up to date»; crear/aplicar/borrar presets de ventana.

## 2026-11-23 (decimoquinta pasada) — review del dueño: fold de #209, lección 8 como hook, decisión de almacenamiento (`chore/upstream-fold-recorded`, docs)

Motivo: dos observaciones del dueño tras el corte de fork.12. **1)** La serie `e3a12bc`+`968b4e4` es la tercera recurrencia del patrón «push sin compilar → fix de compilación inmediato» (antes `df4085d`+`9f0f96e`+`51be96a` en fork.9); la lección escrita no se estaba ejecutando, hay que convertirla en mecanismo. **2)** #209 faltaba en el paquete de upstream pese a ser el candidato estrella (issue abierto, feature autocontenida, commits en inglés).

### Hecho

- **Plegado de la serie F5** (paridad con el `00e0558` de createWindowContext): rama `upstream-pr/window-presets`, commit `0be6597` sobre `59838da` — mensaje de `e3a12bc` con la corrección plegada en el cuerpo; diff neto verificado idéntico (`diff 59838da..0be6597 == diff 59838da..968b4e4`, vacío). Rama local, sin pushear.
- **Entrada #209 en `docs/UPSTREAM-PRS.md`**: sección de sesión 13 (superficie, cherry-pick «adapta» por cadenas, fold, **decisión de almacenamiento anotada**: F7 en DB-píxeles-por-app vs F5 en SharedPreferences-porcentajes-nombrados — conceptos distintos a propósito; una eventual consolidación será decisión documentada, no deriva), y el puesto 12 del orden de envío (tras los fixes puros, antes que wrappers; discutir con upstream antes de abrir el PR). F7 (`496250f`) añadido a «fuera del paquete».
- **Lección 8 en AGENTS.md §6** («tercera recurrencia — es hábito, no recordatorio») + **pre-push hook**: `scripts/pre-push-assembledebug.sh` (corre `./gradlew assembleDebug` si el push toca algo fuera de `*.md`; aborta el push si falla; `SMARTDOCK_SKIP_BUILD=1` lo salta) + `scripts/install-pre-push-hook.sh`; **instalado en este clone** (`.git/hooks/pre-push`). Nota operativa actualizada en UPSTREAM-PRS.md.
- Mapa de ramas de HISTORIA actualizado con las dos ramas `upstream-pr/*` plegadas.

### Cierre

- [x] Push docs-only → el hook lo salta (verificación en vivo del salto `*.md`).
- [x] CI verde en el tip de main.
- [x] El hook probó su función en su primer push real: el commit toca `scripts/*.sh` (no-`.md`) → el hook corrió `assembleDebug` y **bloqueó el push** cuando el build falló. El build falló por entorno (este sandbox no resuelve `repo.maven.apache.org`), no por código; el push salió con el escape documentado `SMARTDOCK_SKIP_BUILD=1`. Límite anotado: en un entorno sin red de maven el hook no puede cumplir su función — el `assembleDebug` real que vale es el de CI; el hook protege clones con red (máquina del dueño).

## 2026-11-23 (decimosexta pasada) — recurrencia nº 2 de la lección 7 mecanizada + decisión de tests en el hook (`chore/lesson7-branch-flow`, docs + hook)

Motivo: observación del dueño — `19cf09c` y `59ca7d8` llegaron a GitHub como pushes directos a main, sin la entrada «created chore/...» en el feed. Las ramas `chore/` SÍ se crearon localmente, pero nunca se pusheaderon a origin: la lección 7 tiene una segunda cara («la rama debe existir en origin, no solo en el clone»). Segunda recurrencia de la misma lección (`60fcd72` fue la primera). Analogía aceptada: la lección 8 recibió hook; la 7 también lo merece.

### Hecho

- **Control 1 en el hook (lección 7)**: `scripts/pre-push-assembledebug.sh` ahora **bloquea cualquier push a main** cuyos commits no sean alcanzables desde una rama con prefijo (`chore/|bugfix/|feature/|feat/|docs/`) **que ya exista en origin**. La existencia se comprueba por tracking ref **o, en su defecto, `git ls-remote --exit-code`** (autoritativo): este clone tiene refspec restringido (`+refs/heads/main:...`) y `git push` de ramas nuevas no crea tracking refs — el primer intento de verificación dio un falso positivo (bloqueó un push legítimo diciendo «solo local») y el fallback lo corrige. Escape: `SMARTDOCK_SKIP_BRANCH_CHECK=1`. El chequeo corre SIEMPRE para main — incluso con `SMARTDOCK_SKIP_BUILD=1` (el escape de build no escapa del flujo).
- **Control 2 actualizado (lección 8, decisión de tests)**: `SMARTDOCK_FULL=1` añade `./gradlew test` tras `assembleDebug` — para cambios que tocan lógica testeada (updater, utils), no por defecto. Decisión documentada en AGENTS.md §6.8: hook = assembleDebug por tiempo; CI = la red de regresión completa. Instalador con mensaje nuevo; **hook reinstalado** en este clone.
- **Lección 7 reescrita en AGENTS.md §6** con las dos recurrencias, la cara nueva (rama sin pushear = push directo a main en el feed) y el flujo correcto en **dos pushes** (`git push origin <rama> && git push origin main` — en un solo push de dos refs el chequeo no puede ver el tracking de la rama, porque el hook corre antes de que origin la registre).
- `chore/lesson7-branch-flow` — rama de este cambio, **pusheada a origin ANTES que main**, en vivo como primera verificación del control 1.

### Cierre

- [x] Verificación en vivo del control 1, con historia completa: (a) el intento legítimo fue **bloqueado por un falso positivo** (tracking ref inexistente por el refspec restringido del clone) — el hook hizo su trabajo incluso antes de ser correcto; (b) fallback `ls-remote` añadido; (c) segundo intento pasó con «pre-push: main viaja desde chore/lesson7-branch-flow (lección 7 OK)» y el feed muestra «created chore/...».
- [ ] Pendiente en clone con red de maven: `SMARTDOCK_FULL=1` sobre un cambio de lógica testeada (aquí el build/test no puede correr).

## 2026-11-23 (decimoséptima pasada) — roadmap: los features 10–12 entran al listado (`chore/roadmap-f10-f12`, docs)

Motivo: el dueño entrega los tres ítems restantes del listado original — **10) modo trackpad** (el móvil como panel táctil del monitor, con clic izq./der. y scroll), **11) escritorios virtuales / workspaces** (N escritorios con ventanas asignadas, conmutables por atajo), **12) acciones de notificación** (reply, archivar) desde el panel. Ninguno estaba en ningún documento: el listado vivía solo en la conversación. Sesión de triage y planificación, **sin código**.

### Hecho

- **`docs/ROADMAP.md` (nuevo)** — consolida el listado completo (F5–F12 con estado, release y commit) y el triage de los tres nuevos, con hechos verificados:
  - **F12 (notificaciones)**: el panel ya vive en `NotificationService` (`NotificationListenerService`) — `StatusBarNotification` ya fluye por `NotificationAdapter`/`NotificationLayout`; `Notification.actions[]` + `RemoteInput.addResultsToIntent` son **APIs públicas** → sin Shizuku, sin permisos nuevos. La más barata del lote y la mejor candidata upstream futura (cuando exista commit, entra a `UPSTREAM-PRS.md` con entrada propia).
  - **F11 (workspaces)**: pertenencia de ventana como estado nuevo de la capa de ventanas; conmutación por atajos **consume F8** (por eso va después del editor de combinaciones); modelo extraíble a Kotlin puro (patrón `Calculator`/`KeyboardModifiers`/`UpdateChecker`) para tests desde el día uno. Decisión pre-anotada en ROADMAP: workspaces (partición runtime) ≠ F7 (geometría por app en DB) ≠ F5 (presets) — cualquier cruce futuro será decisión documentada, no deriva.
  - **F10 (trackpad)**: overlay de gestos en la pantalla del móvil (el dock ya rutea overlays al display elegido desde fork.9) + cursor virtual + **wrapper nuevo** con `IInputManager.injectInputEvent` por el mismo camino Shizuku/AIDL del wrapper de ActivityManager. Verificado contra AOSP: `INJECT_EVENTS` es `signature` pero **`platform.xml` la asigna al uid shell** (por eso `adb shell input` existe) → Shizuku (identidad shell) y root funcionan; degradación honesta sin permiso. Verificado el marco del diferencial: el trackpad nativo de Google llega con el modo escritorio conectado de **Android 16 QPR1** (refinado en Android 17) — el diferencial del fork es real frente a launchers de terceros y en Android 11–15 (minSdk 30); la nota queda escrita para no vender «primero en ningún sitio» sin matiz.
- **Orden propuesto y registrado**: **F8 (fork.13) → F12 (fork.14) → F11 (fork.15) → F10 (fork.16)** — F12 barata y de APIs públicas; F11 justo tras F8 para consumir el registro de atajos y llegar con tests; F10 al final sobre la capa de ventanas consolidada y con el patrón de wrappers maduro. La numeración (10/11/12) es la del listado original, no el orden de implementación — igual que pasó con 6→9→7→5→8. Confirmación del dueño pendiente antes de cortar fork.13.
- `AGENTS.md` §5 actualizado (el patrón de triple bump fijado en fork.10 se aplicó en fork.11 y fork.12; el próximo es **fork.13**, bump 1159/1.15.9/`v1.15.9-fork.13`) y §7 con la fila de ROADMAP; `HISTORIA.md` con la entrada de esta sesión y la fila de ROADMAP en el mapa de documentos.
- **Pre-push hook reinstalado en este clone**: el sandbox se recicló entre sesiones y `.git/hooks` volvió a los samples — el hook vive en el repo (`scripts/install-pre-push-hook.sh`) y no en `.git`, así que **instalarlo en cada clone nuevo es paso obligatorio** (ya escrito en AGENTS §6.8; hoy lo confirmó un reciclaje real).

### Cierre

- [x] Push docs-only → el hook salta el build; el control de rama pasa con el flujo de dos pushes de la lección 7 (rama `chore/roadmap-f10-f12` a origin primero, main después).
- [x] CI verde en la punta de main tras el fast-forward — confirmado el 2026-11-23: 3/3 check-runs success sobre `6eb969d` (Build project sobre main, Build debug apk sobre main, Build project sobre la rama `chore/roadmap-f10-f12`). Re-confirmado sobre `2f38e7a` (cierre de esta entrada).
- [x] **El control 1 del hook detuvo en vivo su primer push ilegítimo de main**: el commit de cierre (`2f38e7a`) se hizo por descuido sobre main local y `git push origin main` fue **bloqueado** — «failed to push some refs» sin llegar a GitHub, porque el commit no era alcanzable desde ninguna rama con prefijo en origin. Corrección con el flujo de la lección 7: ff de `chore/roadmap-f10-f12` hasta incluir el commit, push de la rama, push de main («main viaja desde chore/roadmap-f10-f12 — lección 7 OK»). La lección 7 ya tiene dos recurrencias pagadas; el mecanismo no confía en el hábito, y esta vez el hábito falló sin que el historial lo sufriera.
- [ ] Confirmación del dueño del orden propuesto antes de cortar fork.13 (F8).

## 2026-11-24 (decimoctava pasada) — relevo de agente: la corrección de F12 y el hook que estaba mudo (`feature/notification-actions` `d9879cc` + `chore/f12-icon-fix-docs`)

Motivo: sesión de relevo con contexto de traspaso del dueño; prioridad inmediata, corregir el fix erróneo `65d709f` de la rama F12 (en curso, sin mergear). Sandbox nuevo con red de maven — primera vez que la puerta de calidad local de AGENTS §2 puede correr fuera de CI.

### Hecho

- **`d9879cc` — fix del lookup de iconos de acciones multimedia (`NotificationAdapter`)**: `65d709f` era un fix imposible sobre un diagnóstico falso («Icon.loadDrawable no compila en este toolchain»). Real: `Action.icon` es un campo `Int` deprecado (resource id) — sobre un `Int` no hay `loadDrawable` («Unresolved reference»), mientras `Icon.loadDrawable(Context)` es API pública 23+ y compila siempre. El lookup de `65d709f` (`getIdentifier(action.icon.toString(), "drawable", pkg)`) devuelve siempre 0 para un número convertido a string → `getDrawable(0)` lanza `NotFoundException` → el catch lo traga → **todos** los iconos de acciones multimedia degradaban a texto, siempre y en silencio. Fix: `action.getIcon()?.loadDrawable(context)`, con el blindaje intacto (fallback a texto, nunca crash del dock).
- **Verificación de `76eacc1` (reply)**: correcto sin cambios — `RemoteInput.addResultsToIntent(...)` se llama antes de `actionIntent.send(context, 0, results)`; el reply no va vacío.
- **ROADMAP `d2e4707`** (verificaciones pendientes del traspaso): claim del trackpad nativo de Android 16 marcada **SIN VERIFICAR** (fuente pendiente; puede ser beta/flag), modelo de conmutación de F11 corregido (no «show/hide de overlays» — las ventanas son tareas freeform reales movidas vía Shizuku; riesgo real de privilegios sobre tareas ajenas anotado en su sección de riesgos) y fila F12 de la tabla a «en curso».
- **Puerta de calidad local por primera vez**: toolchain en espacio de usuario (JDK 17 + Android SDK 36, sin root) sobre un sandbox con red de maven. **`./gradlew build` completo (compile + lint + test) verde: 10m31s** sobre el tip de la rama F12 con el fix. La nota de AGENTS §2 queda matizada en consecuencia.
- **Bug del pre-push hook — la lección 8 estaba mecanizada pero muda**: el control 2 (build pre-push) no ha corrido ni una vez desde que se añadió el control 1 (decimosexta pasada): el primer `while read` agota el stdin del hook (la lista de refs del push) y el segundo ve cero refs → `code_changed=0` → «solo documentación, sin build» para **todo** push, incluido el de código de esta sesión (el `d9879cc` pasó como «solo documentación»). Fix: capturar la lista una vez (`refs_input=$(cat)`) y que ambos controles la lean completa (`<<< "$refs_input"`); hook reinstalado en este clone. Verificado en vivo en el push de esta rama: el commit tocaba `scripts/*.sh` y el control 2 disparó `assembleDebug` (BUILD SUCCESSFUL, 9 tareas ejecutadas) — el gate de la lección 8 volvió a tener dientes.
- Sandbox inestable durante la sesión (procesos de build muertos dos veces por reciclado; relanzados hasta completar). Credenciales de trabajo gestionadas fuera del historial y sin persistirlas en el repo.

### Cierre

- [x] Flujo de la lección 7: `feature/notification-actions` → origin (`65d709f..d9879cc`), `chore/f12-icon-fix-docs` → origin, main por fast-forward (`35c3a7d..d2e4707`).
- [x] Lección 8 cumplida por la vía manual: build local completo verde antes de los pushes de código (el hook estaba mudo; la red de regresión real siguió siendo CI).
- [x] CI en verde: 3/3 check-runs success sobre `8070fca` (Build project + Build debug apk sobre main, Build project sobre la rama) y 1/1 sobre `d9879cc` (Build project de la rama F12). Histórico de la rama confirmado en CI: `76eacc1` falló (el «Unresolved reference» real), `65d709f` lo tapó con el lookup imposible y `d9879cc` está verde con el fix real.
- [ ] Decisión del dueño: para el paquete de upstream, ¿plegar `d9879cc` sobre `65d709f`+`76eacc1` (patrón `0be6597`)? El historial del fork no se reescribe (convención §3); el fold corresponde a la rama `upstream-pr/*` cuando se decida enviar.

## 2026-11-24 (decimonovena pasada) — revisión del dueño: F12 se pliega al tratamiento de siempre (`feature/notification-actions`, `45b0148`)

Motivo: observación del dueño tras el cierre de la sesión 18 — el fix iba apilado como commit encima (`65d709f` + `d9879cc`) en vez de plegarse. Válido para seguir trabajando, pero cuando F12 vaya a upstream la rama necesita el tratamiento de siempre (un commit, cuerpo de causa raíz, inglés) y el commit `65d709f` (español, cuerpo vacío, fix imposible) no debe quedar en el historial de la rama. La pendiente «¿plegar?» de la entrada anterior queda resuelta: **sí, pliegue sobre la propia rama**.

### Hecho

- **Plegado de la serie F12** (paridad con `00e0558`/`0be6597`): `feature/notification-actions` reescrita a un único commit **`45b0148`** sobre la punta de main (`6f99bce`) — título de `76eacc1` con cuerpo de causa raíz completo en inglés (dispatch vía `actionIntent.send`, reply con `RemoteInput.addResultsToIntent` **antes** del send, toasts visibles, iconos vía `Action.getIcon()?.loadDrawable` con la causa raíz real del `Int` deprecado y del `getIdentifier` imposible, cadenas a los 6 locales). Los tres commits previos (`76eacc1`, `65d709f`, `d9879cc`) salen del historial — el fix erróneo ya no queda en ningún lado. Diff neto verificado **idéntico** (`diff 35c3a7d..d9879cc == diff 6f99bce..45b0148`, vacío). Reescritura de la rama sin mergear instruida por el dueño, empujada con `--force-with-lease`; main intacto.
- **Lección operativa del plegado (cazada a tiempo por la verificación de diff)**: el primer intento usaba `git reset --soft <base-nueva> + commit`, que arrastra el árbol del HEAD viejo y el commit resultante **revertía** los docs de la sesión 18 (el árbol de `d9879cc` no los contenía). El patrón correcto para plegar sobre una base más nueva es `git cherry-pick -n <rango> && git commit`: acumula solo los cambios de la serie sobre la base. La comparación de diffs netos (patrón de los pliegues anteriores) lo cazó antes de empujar.
- Entrada F12 en `docs/UPSTREAM-PRS.md`: commit único `45b0148`, superficie, cherry-pick **adapta** (cadenas a locales de upstream), nota de diseño del reply, y puesto 13 del orden de envío (junto a #209, tras discusión con upstream; el envío sigue siendo decisión del dueño). HISTORIA al día.

### Cierre

- [x] Push `--force-with-lease` de `45b0148` (`d9879cc...45b0148`, forced update — la rama queda en el commit único) + CI verde: 1/1 sobre `45b0148` (Build project de la rama plegada) y 3/3 sobre `6d1074d` (Build project + Build debug apk sobre main, Build project sobre la rama de docs).
- [ ] Pendientes del dueño: orden de implementación F8/F10/F11/F12 y decisión de envío a upstream.

## 2026-11-24 (vigésima pasada) — reconciliación del roadmap, cierre de F12 y corte de fork.13 (`chore/roadmap-reconcile`, `chore/release-fork.13`)

Motivo: correcciones del dueño sobre la documentación (numeración barajada del ROADMAP, contradicción del orden) + checklist de cierre de F12 (verificaciones, estado de la rama, orden del reply, pliegue upstream) + merge a main y release fork.13 con el bump pre-registrado en AGENTS §5. El dueño cierra el checkbox de confirmación de orden delegando la decisión.

### Hecho

- **ROADMAP reconciliado (`7a2237b`)**: la tabla de la versión 2026-11-23 estaba barajada — rotulaba F5 = presets (#209), F6 = force-stop, F9 = selector de pantalla, cuando el listado original del dueño es **F5 = snap zones, F6 = colocación del dock, F9 = matar tareas** (el nombre de la rama `feature/dock-placement-killtasks` de fork.10 confirma el mapeo). Restaurada: F5 = zonas de anclado (**⏳ pendiente de triage** — ítem hasta ahora desaparecido del documento pese a que la cabecera prometía fidelidad a la lista), F6 = `0049dd0`, F9 = `b716860`; los presets con nombre quedan como **entrada aparte** vinculada a upstream #209 (no eran un ítem numerado). Nota de reconciliación bajo la tabla para leer las referencias históricas «F5/F6/F9» de REGISTRO/HISTORIA/UPSTREAM-PRS.
- **Orden de implementación decidido**: **F12 (fork.13) → F8 (fork.14) → F11 (fork.15) → F10 (fork.16)** — F12 estaba terminada y F8 sin empezar; en la práctica F12 sale antes sí o sí, y el plan de releases se reajusta en consecuencia (F12 = fork.13, no fork.14). F5 (snap zones) queda **pendiente de triage**, con nota de mirar su interacción con los presets y F7 antes de programarla. La contradicción del documento anterior («F8 fork.13» en la tabla vs «F12 primero» en los bullets) queda resuelta de paso.
- **Verificaciones de cierre de F12**: (a) `feature/notification-actions` = `45b0148` en origin, un solo commit — el aviso «este commit no pertenece a ninguna rama» de `d9879cc` es el esperado tras el pliegue (huérfano **por diseño**, nada perdido); (b) el reply llama `RemoteInput.addResultsToIntent(...)` **antes** del `actionIntent.send` (verificado línea a línea en `NotificationAdapter.kt`: 109 → 110) — el texto no va vacío; (c) el pliegue para upstream ya estaba hecho (`45b0148`, inglés, causa raíz) y con entrada en `UPSTREAM-PRS.md` — `65d709f` (español, cuerpo vacío) no sale hacia upstream.
- **F12 mergeara en main (`81bdd89`)** por fast-forward: la rama se re-encadenó sobre la punta de main (un commit = un rebase trivial) con diff neto verificado idéntico por segunda vez; push de rama y main en dos pasos (lección 7). El hook corrió `assembleDebug` en cada push de código — de paso confirmó que dispara también en pushes de ramas nuevas (chequeo conservador del hook) y que necesita `JAVA_HOME` exportado en el entorno de push.
- **Corte de fork.13 (`8cb300e`)**: bump simultáneo `versionCode` 1159 / `versionName` 1.15.9 / `fork_release_tag` `v1.15.9-fork.13` + entrada de `CHANGELOG-FORK.md` (F12 + corrección de iconos + infraestructura de la sesión), patrón de fork.10–12. Tag `v1.15.9-fork.13` publicado; el workflow de release construye la APK.
- **Verificación del updater (la de siempre)**: `./gradlew test` verde antes del push — la suite `UpdateCheckerTest` cubre el parser de tags, la iteración same-version y el auto-reconocimiento «up to date».
- Nota operativa: las URLs firmadas de adjuntos caducan en ~15–30 min y el transporte enmascara firmas dentro de URLs y asignaciones; la descarga fiable arma la URL por partes dentro de bash. La credencial de trabajo se borró del sandbox al final de la sesión.

### Cierre

- [x] Release `v1.15.9-fork.13` publicada y verificada: `releases/latest == v1.15.9-fork.13 == fork_release_tag` del build (el updater auto-reconoce su release), **CI 5/5 verde** sobre `8cb300e` (Release debug apk + Build project sobre el tag, Build project + Build debug apk sobre main, Build project sobre la rama), asset `SmartDock-fork-debug.apk` (13.2 MB) verificado — badging **versionCode 1159 / versionName 1.15.9**, cert SHA-256 `4680c231…b54b41db` (keystore del fork: se actualiza encima de fork.12 sin desinstalar) y `fork_release_tag` incrustado `v1.15.9-fork.13` en `resources.arsc`.
- [ ] Pendientes del dueño: triage de F5 (snap zones); decisión de envío a upstream.
