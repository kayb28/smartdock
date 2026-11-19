# REGISTRO — diario operativo del fork

_Fork de [axel358/smartdock](https://github.com/axel358/smartdock). Todo cambio con fecha/hora UTC, motivo, build y estado._
_Estados: pendiente confirmar → confirmado → superado. La fuente de la verdad para CI y releases es GitHub._

## Convenios

- versionCode/versión **sin tocar** mientras el fork mantenga paridad con upstream: v1.15.0 (1150). Las releases del fork se distinguen por el **tag** (`v1.15.0-fork.N`), no por el versionCode.
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

## Cierre de la consolidación (completar tras push)

- [ ] main en `0eac1a4` + docs, CI verde en el push.
- [ ] La rama `bugfix/october-2026-bugs` sigue existiendo, apuntando a `0eac1a4` (histórico del tag).
