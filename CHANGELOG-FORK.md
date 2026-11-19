# Changelog del fork

_Changelog formal de las releases del fork de Smart Dock. Formato Keep a Changelog, en español._
_Upstream mantiene su propio changelog en `fastlane/metadata/android/en-US/changelogs/`; aquí solo el fork._

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
