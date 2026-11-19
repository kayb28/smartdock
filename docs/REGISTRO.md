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
