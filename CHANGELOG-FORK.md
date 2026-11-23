# Changelog del fork

_Changelog formal de las releases del fork de Smart Dock. Formato Keep a Changelog, en español._
_Upstream mantiene su propio changelog en `fastlane/metadata/android/en-US/changelogs/`; aquí solo el fork._

## [v1.15.9-fork.13] — 2026-11-24

Publicada como GitHub Release (APK `SmartDock-fork-debug.apk`, el nombre real del asset en todas las releases del fork; el tag lo identifica el build vía `fork_release_tag` en Settings → About). Base: `v1.15.8-fork.12` + 1 commit de feature plegado (rama `feature/notification-actions`, `45b0148`, fast-forward) + documentación de las sesiones 18–20. versionCode 1159. El updater compara el tag del build contra `releases/latest` de GitHub (`UpdateCheckerTest.ownReleaseIsNotNewerThanItself` sigue garantizando el auto-reconocimiento «up to date»).

### Añadido

- **Acciones de notificación desde el panel** (F12, `45b0148` — serie `76eacc1`+`65d709f`+`d9879cc` plegada a un commit al tratamiento de siempre): los botones de `Notification.Action` se despachan vía `actionIntent.send(context, 0, fillInIntent)`; las acciones con `RemoteInput` abren un campo de respuesta inline — el reply rellena `RemoteInput.addResultsToIntent(...)` **antes** del send (sin ese orden el texto llegaba vacío); los fallos de dispatch se comunican con toast visible, nunca en silencio; y los iconos de las acciones multimedia se resuelven con `Action.getIcon()?.loadDrawable` (API pública 23+). Sin permisos nuevos, sin Shizuku. 4 cadenas nuevas traducidas a los 6 locales mantenidos.

### Corregido

- **Iconos de acciones multimedia** (corregido dentro de `45b0148`): el lookup de `65d709f` (`Resources.getIdentifier(action.icon.toString(), "drawable", pkg)`) devolvía siempre 0 para un resource id convertido a string — `getDrawable(0)` lanzaba `NotFoundException` y **todos** los iconos de acciones degradaban a texto, en silencio. Causa raíz real: `Action.icon` es un campo `Int` deprecado y el `loadDrawable` correcto vive en `Action.getIcon()`; el «no compila en este toolchain» del commit intermedio era un diagnóstico falso.

### Infraestructura

- **Pre-push hook**: el control de build (lección 8) estaba mudo desde que se añadió el control de ramas (el primer `while read` agotaba stdin y el segundo veía cero refs); arreglado (`25823dc`) y verificado en vivo.
- **ROADMAP reconciliado** con la numeración de la lista original del dueño (F5 = snap zones pendientes, F6 = colocación del dock, F9 = matar tareas; los presets quedan como entrada aparte de upstream #209) y orden de implementación decidido: **F12 (fork.13) → F8 (fork.14) → F11 (fork.15) → F10 (fork.16)**.

## [v1.15.8-fork.12] — 2026-11-23

Publicada como GitHub Release (APK `SmartDock-fork-debug.apk`, el nombre real del asset en todas las releases del fork; el tag lo identifica el build vía `fork_release_tag` en Settings → About). Base: `v1.15.7-fork.11` + 2 commits de feature + 1 commit de docs (rama `feature/window-presets` + `chore/f5-docs`, fast-forward). versionCode 1158. El updater compara el tag del build contra `releases/latest` de GitHub (`UpdateCheckerTest.ownReleaseIsNotNewerThanItself` sigue garantizando el auto-reconocimiento «up to date»).

### Añadido

- **Presets de geometría de ventana con nombre** (F5, upstream #209, `e3a12bc`+`968b4e4`): el submenú Resize de una app en ejecución gana **«Custom…»** — diálogo con cuatro sliders (left/top/width/height como porcentajes del área usable del display) y preview en píxeles, Apply para redimensionar la tarea, «Save as preset» para guardar la geometría con nombre — y los presets guardados se listan directamente en el submenú y aplican a cualquier ventana («Delete presets…» los gestiona). El almacenamiento (`utils/WindowPresets.kt`) es SharedPreferences JSON con `org.json`: guarda **porcentajes, no píxeles**, para que un preset re-aplique en el display donde viva la tarea (mismo razonamiento por-display que `makeLaunchBounds`); toda lectura degrada a vacío ante entradas malformadas y `toBounds` clampa y valida (un preset editado a mano jamás produce la ventana de tamaño cero — misma guardia que el modo «custom» de fork.11). `Action` gana payload opcional (el nombre del preset) y `MenuCommand` tres comandos nuevos — el dispatch sigue por comando, nunca por texto visible. Bounds calculados contra el display real de la tarea (`resolveDisplayId`); resize por el mismo camino Shizuku con el mismo try/catch. 11 strings nuevas traducidas a los 6 locales mantenidos. Completa F5 del listado original: upstream #209 pedía «set up and store a few of these predefined window positions».

## [v1.15.7-fork.11] — 2026-11-23

Publicada como GitHub Release (APK `SmartDock-fork-debug.apk`, el nombre real del asset en todas las releases del fork; el tag lo identifica el build vía `fork_release_tag` en Settings → About). Base: `v1.15.6-fork.10` + 1 commit de feature + 2 commits de docs (rama `feature/per-app-window-geometry` + `chore/f7-docs`, fast-forward). versionCode 1157. El updater compara el tag del build contra `releases/latest` de GitHub (`UpdateCheckerTest.ownReleaseIsNotNewerThanItself` sigue garantizando el auto-reconocimiento «up to date»).

### Añadido

- **Geometría de ventana por app** (F7, `496250f`): nueva entrada «Remember window size» en el menú contextual de una app en ejecución (freeform, gated a Shizuku — el mismo privilegio que Close/Resize ya exigen): lee los bounds actuales de la tarea vía `IActivityManager.getTaskBounds` (la llamada oculta detrás del `resizeTask` que el dock ya usa, mismo contrato de degradación que `forceStopPackage`: null, nunca escapa al caller) y los guarda por paquete en cuatro columnas nuevas de la DB (v2, ALTER TABLE en upgrade — las installs viejas conservan sus modos guardados). El siguiente lanzamiento de esa app restaura esos bounds como nuevo modo de lanzamiento por-app **«custom»**, en vez de uno de los modos fijos (standard/maximized/portrait/tiled/fullscreen). Defensas: los bounds guardados se clampan al display de destino (una ventana recordada en otra pantalla o contra un tamaño de pantalla caduco no abre de tamaño cero — la misma clase de bug del Rect vacío corregido en fork.10), y una geometría ausente o inusable cae a «standard» en vez de pasar bounds rotos. Toasts `window_size_remembered` / `window_size_unavailable`: nunca un no-op silencioso. 3 strings nuevas traducidas a los 6 locales mantenidos.

## [v1.15.6-fork.10] — 2026-11-23

Publicada como GitHub Release (APK `SmartDock-fork-debug.apk`, el nombre real del asset en todas las releases del fork; el tag lo identifica el build vía `fork_release_tag` en Settings → About). Base: `v1.15.5-fork.9` + 13 commits de las sesiones 8–9 (5 features, 2 seguimientos del updater, 1 test, 5 docs/chore) + 2 commits de release (rama `chore/release-fork.10`, fast-forward). versionCode 1156. Primera release con `fork_release_tag` en juego: el updater compara el tag del build contra `releases/latest` de GitHub, y `UpdateCheckerTest.ownReleaseIsNotNewerThanItself` garantiza que una build se reconozca a sí misma como «up to date».

### Añadido

- **Comprobador de actualizaciones contra GitHub Releases** (`55ab8ac`, `c0f3646`, `aca3df6`): el fork vive fuera de F-Droid, así que hasta ahora nadie sabía que salió una release salvo mirando el repo. `UpdateChecker` consulta `releases/latest` con `HttpURLConnection` (sin dependencias nuevas), compara el tag `(major, minor, patch, forkN)` contra la string del build `fork_release_tag` — robusto a iteraciones same-version (fork.10 sobre 1.15.6 > fork.9 sobre 1.15.5) — y degrada a «no se pudo comprobar» sin crashear jamás. Check silencioso 1×/24h en `onServiceConnected`, gated por el toggle «buscar actualizaciones automáticamente» (default on) que añade `c0f3646`, más comprobación manual en Ajustes → Avanzado → Diagnóstico con botón «Open release». Tests unitarios del parser de versión (`aca3df6`).
- **Panel de diagnósticos** (`8f4f61b`, `f25453b`): Ajustes → Avanzado → Diagnóstico → «View diagnostics»: servicio vivo, `isAlive()` de los tres wrappers Shizuku, permisos (Shizuku / app de sistema / WRITE_SECURE_SETTINGS), lista de displays, Build SDK/release/device, tail de 600 chars del último crash y sección «Updater» (tag del build, auto-check, última comprobación, veredicto cacheado). Botón «Share diagnostics» (ACTION_SEND) para adjuntarlo al bug report — los reportes llegan con contexto en vez de una captura.
- **Force-stop de apps desde el menú contextual del dock** (#123, `b716860`): nuevo `MenuCommand.FORCE_STOP` bajo Close, gated a permiso Shizuku (el mismo que Close ya exige): `IActivityManager.forceStopPackage` vía el wrapper del fork termina el proceso de verdad — `removeTask` (Close) solo lo quita de recents. Toasts `force_stopped` / `force_stop_unavailable`: nunca un no-op silencioso.
- **Selector explícito de pantalla del dock** (`0049dd0`): el «a qué pantalla va el dock» deja de ser un toggle oculto (`prefer_last_display` en Avanzado) y es ahora un `ListPreference` `dock_display_placement` (Primaria / Externa). Los cinco puntos de lectura y el listener de prefs migran a la nueva clave; el default «primaria» conserva el comportamiento anterior. «Ambos» (dock en dos pantallas a la vez) queda aplazado: exige una segunda ventana overlay.
- **Toggle de teclado blando en el menú de apps** (#122, `18fcc9c`): `show_software_keyboard_in_menu` (default off) fuerza el IME en el buscador del menú con teclado físico conectado (p. ej. teclado Bluetooth en un móvil); sin el toggle, el teclado solo salía auto en dispositivos sin teclas.

### Notas

- Primera versión del fork con permiso **INTERNET** (normal, se concede solo): hasta fork.9 la app no tenía ningún permiso de red — el updater lo añade. Toggle de consentimiento para el check silencioso incluido (default on).

## [v1.15.5-fork.9] — 2026-11-22

Publicada como GitHub Release (APK `SmartDock-fork-debug-v1.15.5-fork.9.apk`). Base: `main` 2837744 + 4 commits (rama `bugfix/secondary-display-accessibility-regression`, fast-forward). versionCode 1155.

### Corregido

- **El dock no aparecía en la pantalla secundaria** con `prefer_last_display` activo: `getDisplayContext` construía el contexto del display con `createDisplayContext`, que en Android 11+ (API 30, el minSdk del fork) produce un `WindowManager` que sigue enrutando las ventanas overlay al display por defecto. Sustituido por `createWindowContext(display, TYPE_APPLICATION_OVERLAY)`, que vincula el `WindowManager` al display destino para ese tipo de ventana. Todas las ventanas del dock son `TYPE_APPLICATION_OVERLAY` (la rama `accessibilityWindow` de `makeWindowParams` nunca se usa), así que el tipo es único y seguro (`df4085d`).
- **El permiso de accesibilidad se mostraba desactivado aunque el toggle del sistema estuviera ON**: `isAccessibilityServiceEnabled` solo consultaba `getEnabledAccessibilityServiceList`, que devuelve vacío cuando el servicio está listado en `enabled_accessibility_services` pero sin instancia viva — el estado tras un crash-reboot del SO (#268), un `Throwable` de un wrapper que fork.8 contiene sin dejar que el servicio bindee, o cualquier `Throwable` en `onServiceConnected`/`createViews` antes de que el servicio se publique. Ahora devuelve `true` cuando el servicio está listado (coincide con el toggle que ve el usuario); un nuevo `isAccessibilityServiceBound` dice si hay instancia viva, y el diálogo de accesibilidad ofrece un botón **Reactivar** (drop + re-add con el mismo grace del boot receiver) en el estado listado-pero-no-bound, en vez del Enable que no hacía nada (`a51c8b9`).
- **`createViews` mataba el servicio ante un `Error`**: `restartUI` solo atrapaba `Exception`, así que un `Error` (p. ej. `IllegalAccessError` de un enlace hidden-API que reapareció, o un error de `WindowManager` en un display que rechaza la ventana) escapaba del fallback y mataba el binding — dock fuera de todas las pantallas y aviso falso de accesibilidad desactivada. `onServiceConnected` no tenía guard ninguno, así que el mismo `Error` en la primera conexión mataba el binding antes de publicar el servicio. Ambos caminos ahora atrapan `Throwable`, caen al display principal y registran en `CrashLog`; si el fallback también falla, el servicio se queda bindeado sin UI para que el usuario pueda Reactivar (`ff3f908`).

### Notas

- Nota de instalación: APKs debug firmadas con el keystore del fork (cert `4680C231…B54B41DB`), se actualizan encima de cualquier build anterior del fork (incluida fork.8) sin desinstalar ni perder ajustes; **no** se instalan encima de la build oficial de F-Droid (certificado distinto).
- Estado: **pendiente de confirmación del usuario en dispositivo** — dock visible en la pantalla secundaria con `prefer_last_display` activo, botón de accesibilidad mostrando el icono de activado cuando el toggle del sistema está ON, y Reactivar restaurando el servicio en el estado listado-pero-no-bound.

## [v1.15.4-fork.8] — 2026-11-19

Publicada como GitHub Release (APK `SmartDock-fork-debug-v1.15.4-fork.8.apk`). Base: `main` d69ef2f + 1 commit (rama `bugfix/wrapper-init-crash`, fast-forward). versionCode 1154.

### Corregido

- **Crash del servicio en frameworks donde los wrappers Shizuku ya no linkean** (reportado por el usuario con capturas del diálogo «Algo salió mal»; *no* es una regresión del fork: es código upstream sin tocar): `ActivityManagerWrapper()` lanzaba `NoSuchMethodError` — `IActivityManager$Stub.asInterface` ya no resuelve contra el framework del dispositivo (parent delegation carga primero la clase real y las interfaces ocultas cambiaron o dejaron de ser accesibles). Como el catch de `initWrappers` solo atrapaba `Exception` (el crash es un `Error`) y envolvía los tres wrappers en un bloque, el servicio de accesibilidad moría en `onCreate` **antes de dibujar nada**: el dock desaparecía de todas las pantallas —la secundaria incluida— y, al no conectar nunca el servicio, la app seguía mostrando el aviso de «activar la accesibilidad» aunque el toggle estuviera activo. Tres capas (`f2f4727`):
  1. **Unseal de hidden APIs al arrancar** (`HiddenApiBypass.addHiddenApiExemptions("L")` en `App.onCreate`, best effort): donde el bloqueo era de política (lo más probable), los wrappers vuelven a linkear y sus funciones siguen operativas.
  2. **`initWrappers` construye cada wrapper por separado y atrapa `Throwable`**: un wrapper que no puede levantarse degrada sus propias funciones (Wi-Fi → panel del sistema, Bluetooth → ajustes, tareas → API pública de recents) en vez de tumbar el dock.
  3. **Adquisición resiliente dentro de cada wrapper** (`isAlive()=false` en vez de lanzar) + todo método protegido con `try/catch(Throwable)`: un framework que cambie firmas degrada a resultados vacíos, sin crashes a mitad de la interacción. `updateRunningTasks` cae al camino público de recents cuando el wrapper está muerto.

## [v1.15.3-fork.7] — 2026-11-19

Publicada como GitHub Release (APK `SmartDock-fork-debug-v1.15.3-fork.7.apk`). Base: `main` 5e58df9 + 6 commits de la jornada de cierre de issues reportados (rama `bugfix/november-2026-issues`, fast-forward). versionCode 1153. Con esta release, **todos los bugs reportados por usuarios en upstream están corregidos en el fork** (los 5 de octubre + los 6 que quedaban); lo que sigue abierto upstream son feature requests, no bugs.

### Corregido

- **#167 — AltGr disparaba atajos del dock**: AltGr llega como Ctrl+Alt (o con `META_ALT_RIGHT_ON`); el manejador de atajos solo miraba `isAltPressed`, así que cada acorde de caracteres especiales disparaba un atajo encima de la tecla escrita: AltGr+Q («@» en teclado español/alemán) abría ajustes rápidos y AltGr+L bloqueaba el escritorio. Los acordes AltGr pasan ahora intactos a la app; la mitad «ventanizar una app maximizada» ya era posible (Alt+↓). (`c5907bf`)
- **#250 — «0 recents» limitaba la franja a 1 app**: el límite 0 (= «sin límite») iba directo a `ActivityManager.getRunningTasks(0)`, que la plataforma clampea a una tarea. Un límite de 0 pasa ahora un tope de 100 y `getRecentTasks` respeta «sin límite». (`dd14aed`)
- **#266 — la captura de ajustes rápidos fotografiaba el propio panel**: la ventana se quitaba de forma síncrona pero el compositor aún mostraba el frame anterior cuando `GLOBAL_ACTION_TAKE_SCREENSHOT` corría. La captura corre 400 ms después de cerrar el panel, el dock (y el handle, si es el control de activación visible) se ocultan durante la captura —petición del mismo reporte— y todo se restaura tras el flash del sistema; una acción del usuario cancela la restauración. Los timers corren en el handler de la vista para que `hideDock` no se los coma. (`5a3beca`)
- **#259 — dock en blanco al arrancar (waydroid)**: en el boot la lista de displays puede faltar o reportar límites de tamaño cero, y `makeWindowParams` añadía ventanas de dimensión cero (pantalla en blanco hasta forzar el paro). Límites degenerados caen a las métricas de la aplicación; el dock siempre tiene dimensiones reales donde dibujarse. Mitigación adicional: el re-enganche de accesibilidad a los 30 s de `BootCompletedReceiver` (fork.4, #268) cubre la mitad «accesibilidad perdida tras reinicio» de este reporte. (`c1d02b2`)
- **#253 — flash blanco + crash con IndexOutOfBoundsException**: sin traza no se puede confirmar el punto exacto (upstream tampoco la consiguió), así que se endureció la clase entera de crash: los cinco adaptadores que indexan una lista al bindear (franja de tareas, menú de apps, notificaciones, menús contextuales) saltan las posiciones obsoletas en vez de tumbar el dock; un dock en crash-loop relanzándose encaja además con los «flashes» reportados. (`c1d02b2`)

### Añadido

- **#200 — opción de offset del handle desde el borde**: el handle de activación quedaba pegado a la esquina de la pantalla; en móviles con esquinas redondeadas el radio cubría parte del handle y lo hacía casi incliclicable (también en Pixel, según un comentario). Ahora nace con un offset de 8 dp hacia dentro y un slider nuevo (0–64 dp, junto a los demás ajustes del handle) permite empujarlo más; x/y positivas con gravedad `BOTTOM|START/END` son distancias hacia dentro desde la esquina. Cadenas nuevas en los 7 locales. (`f0edf9f`, `dbba38d`)

## [v1.15.2-fork.6] — 2026-11-19

Publicada como GitHub Release (APK `SmartDock-fork-debug-v1.15.2-fork.6.apk`). Base: `main` 267e942 + 9 commits de la jornada de las 12 mejoras (rama `feature/november-2026-improvements`, fast-forward). versionCode 1152.

### Añadido

- **Backup de ajustes completo**: formato JSON que conserva Sets (apps ocultas, notificaciones silenciadas) y URIs (sonidos, icon packs) que el formato antiguo descartaba; los backups antiguos siguen restaurándose. (`027da00`)
- **Calculadora de expresión completa**: precedencia, paréntesis, unario y exponente — antes `2+3+4` devolvía 5. (`027da00`)
- **Motor de búsqueda web configurable**: Google / DuckDuckGo / Bing (antes google.com fijo). (`c2d3bb8`)
- **Anclar atajos profundos al dock**: «Add to» → submenú por atajo; el icono del dock lanza el atajo (persistido como las apps). (`c2d3bb8`)
- **Panel propio de redes Wi-Fi**: lista escaneada vía Shizuku (SSID únicos, señal primero); credenciales siguen yendo al flujo del sistema. (`2247850`)
- **Superficie de automatización**: `AutomationReceiver` exportado con permiso signature-level — abrir menú / lanzar apps desde Tasker o adb. (`2247850`)

### Corregido

- **Menús contextuales por comando estable**: 33+2 sitios despachaban por texto traducido; ahora por enum (`MenuCommand`). (`2247850`)
- **Receptores desregistrados**: los 7 receivers de `DockService` se desregistran en `onDestroy` (antes 2 de 7 → fuga). (`2247850`)
- **App activa real**: `TYPE_WINDOW_STATE_CHANGED` (declarado, nunca manejado) actualiza el indicador con el foco real, no con el orden de recencia de tareas. (`deec2c3`)
- **DiffUtil en la franja de tareas**: diff por paquete+taskId+conteo en vez de rebuild total por cada ventana. (`deec2c3`)
- **Alt+F12 ahora obedece a su switch**: `enable_soft_reboot` era una preferencia visible muerta. (`c2d3bb8`)

### Eliminado

- `WifiScanResultAdapter` muerto, dependencia `RestrictionBypass` sin uso y el repo jitpack que solo ella justificaba (jcenter se mantiene: una transitiva lo necesita). (`c2d3bb8`)

## [v1.15.0-fork.4] — 2026-11-07

Publicada como GitHub Release («SmartDock fork v1.15.0-fork.4», APK `SmartDock-fork-debug-v1.15.0-fork.4.apk`, 14.181.352 bytes, sha256 `425ff1f51ceacfffca4ce0d9c0802ad55676c9d8f31996e99002eea92a4e718e`). Base: upstream 1.15.0 (`517604a`). Rama: `bugfix/october-2026-bugs` (10 commits).

### Corregido

- **#270 — crash en cambios de configuración tempranos**: conectar un display durante el arranque del servicio de accesibilidad entregaba `onConfigurationChanged` antes de que sus vistas (`lateinit`) existieran. La actualización se salta hasta que existan; `createViews()` ya construye desde la configuración actual, así que no se pierde nada. (`f5409c5`)
- **#264 — la activación por swipe nunca funcionó en táctil**: `onSwipe` solo manejaba DOWN y LEFT; solo el hover del puntero revelaba el dock oculto. Swipe hacia arriba en la franja de activación muestra el dock; swipe hacia arriba en el escritorio abre el menú de apps. (`f5409c5`)
- **#267 — el menú de apps se abría tras cada atajo Meta+X**: el key-up de Meta se trataba como pulsación suelta del menú. Meta cuenta como modificador cuando otra tecla baja mientras está mantenida, y su release no hace nada en ese caso. (`f5409c5`)
- **#268 — dock muerto tras crash-reboot del sistema**: el servicio quedaba habilitado pero el sistema nunca lo re-enganchaba; el arreglo manual era tocar accesibilidad. Nuevo `BootCompletedReceiver` (con permisos `RECEIVE_BOOT_COMPLETED` y `POST_NOTIFICATIONS`): 30 s tras el boot comprueba el estado y re-engancha el servicio como el toggle manual (necesita WRITE_SECURE_SETTINGS); sin ese permiso, botón de recuperación de un toque en el escritorio y notificación. (`f5409c5`)
- **#263 — soporte de display secundario fail-safe**: tres fallos encadenados podían dejar el dock muerto y las apps sin lanzar: (1) `getSecondaryDisplay()` tomaba el último display de la lista completa — en OEMs con displays virtuales privados (Samsung One UI) elegía uno donde las apps normales no pueden lanzar y cada lanzamiento fallaba; ahora prefiere displays externos reales y devuelve null para caer a la principal. (2) `getDisplayBounds()` desreferenciaba sin null-check un display recién desaparecido. (3) `launchApp()` propagaba la excepción de un `launchDisplayId` rechazado y tumbaba todo el servicio de accesibilidad; ahora reintenta en la pantalla principal y avisa. (`62ee704`)
- **Tiling y redimensionado en displays secundarios**: Resize/Snap (y Alt+flechas) calculaban los límites con las métricas del display integrado aunque la ventana viviera en el monitor — ventanas más grandes que la pantalla e inutilizables. Ahora se computan para el display de la tarea. El caso `fullscreen` faltaba en `makeLaunchBounds()` y aplicaba un `Rect(0,0,0,0)` (ventana de tamaño cero). (`931b708`)
- **`getSecondaryDisplay()` con API pública**: `DISPLAY_CATEGORY_EXTERNAL` no es pública (fallaba la compilación en CI); se usa `DisplayManager.DISPLAY_CATEGORY_PRESENTATION` (pública desde API 17). (`0c262fa`, `7bf1bf7`)
- **Cadenas**: traducción de las cadenas de recuperación del dock a de/pa/zh-rCN/zh-rHK/zh-rTW (`874fafb`) y escape de apóstrofes en punjabi que AAPT rechaza (`7bf1bf7`).

### Añadido

- **Modo escritorio en display externo**: toggle «External display desktop mode» en Ajustes avanzados que aplica los settings globales `force_desktop_mode_on_external_displays` + `enable_freeform_support` (como el «Force desktop mode» de opciones de desarrollador), para que las apps del monitor conserven ventanas libreform arrastrables con ratón. Orden de intento: permiso propio → Shizuku (`IShizukuService.newProcess`) → root, con mensaje claro cuando no hay ninguno; el switch refleja el estado vivo del sistema. (`98ebb9a`, `783e15f`)
- **«Send to screen»**: mover tareas en ejecución entre pantallas desde el menú contextual de la taskbar. La elección explícita de «Launch in» del menú de apps ahora tiene prioridad sobre «Prefer secondary displays». (`98ebb9a`)
- **Firma estable del fork**: keystore debug en el repo (`debug.keystore`, contraseñas estándar `android`); toda build — local o CI — firma igual, y los updates del fork se instalan encima sin desinstalar (antes cada APK de CI firmaba distinto y había que desinstalar, perdiendo ajustes, apps fijadas y modos de lanzamiento). Cert SHA-256: `4680C23198D542A0423DFD95F1A0D66A80100FA30CB72CAEDF107E41B54B41DB`. (`43a3b24`)
- **Releases automáticas**: workflow `release_apk.yml` — push de tags `v*` y dispatch manual construyen el APK y lo adjuntan a una GitHub Release pública. (`0eac1a4`)

### Notas de compatibilidad

- La build del fork **actualiza encima** de builds anteriores del fork (keystore fijo); no puede actualizarse encima de la app oficial de F-Droid (certificado distinto): desinstalar esa primero.
- versionCode 1150 (= upstream 1.15.0): instalable encima de las builds locales fork.1–3, pero cualquier release posterior del fork **debe subir el versionCode** para que Android acepte el update (corregido en fork.5).

## [v1.15.1-fork.5] — 2026-11-19

Publicada como GitHub Release desde la main consolidada (workflow automático de tags). Base: `v1.15.0-fork.4` + documentación del fork + bump de versión. Primer release del fork con versionCode propio por encima de upstream.

### Corregido

- **La build ahora es actualizable**: versionCode 1150 → **1151**, versionName 1.15.0 → **1.15.1**. Con el versionCode congelado, cualquier APK nueva se rechazaba como reinstalación de la misma versión: Android exige versionCode estrictamente creciente para aceptar un update (lección de WebLibre, donde su fork.4 tuvo que saltar 423→483). Esta build **se instala encima de v1.15.0-fork.4 sin desinstalar** (misma firma del keystore del fork) y conserva ajustes, apps fijadas y modos de lanzamiento.

### Notas de compatibilidad

- Actualiza encima de v1.15.0-fork.4 (versionCode 1151 > 1150, misma firma). Sigue sin poder actualizarse encima de la app oficial de F-Droid (certificado distinto): desinstalar esa primero.
- Contenido funcional = fork.4 (mismos 10 commits de la jornada de octubre); el cambio de esta release es el bump de versión, la consolidación a main y la documentación del fork (AGENTS.md, docs/, FORK.md, CHANGELOG-FORK.md).

## [v1.15.0-fork.1–3] — anteriores a la primera release pública

Builds locales de trabajo durante la jornada de 2026-11-07; nunca llegaron a GitHub Release. Solo existen como pasos intermedios del historial de la rama; no hay artefactos que conservar.
