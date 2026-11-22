# UPSTREAM-PRS — paquete de contribuciones hacia `axel358/smartdock`

_Estado: **preparado, no enviado**. Decisión de enviar (y de abrir PRs vs. mantener el fork) pendiente del dueño. Este documento lista, para cada issue abierto en upstream que el fork ya corrige, los commits del fork que lo resuelven, una nota de cherry-pick (¿aplican limpios sobre upstream o requieren adaptación?) y un orden de envío propuesto._

## Contexto

El fork mantiene paridad total con upstream (mismo paquete `cu.axel.smartdock`, sin renombrar, historial absorbible por merge). Los commits de fix están redactados en inglés con cuerpo de causa raíz precisamente para poderse proponer hacia upstream. Lo que sigue es el mapeo issue → commit, con la superficie que cada uno toca y si el cherry-pick es limpio.

Convención de este documento:
- **Limpio**: el commit aplica sobre la punta de upstream sin conflictos y sin tocar archivos que upstream no tenga (ni nuevos archivos, ni dependencias nuevas, ni cadenas nuevas sin traducir).
- **Adapta**: aplica conceptualmente pero requiere trabajo (split de un commit multi-concern, traducción de cadenas nuevas a los locales de upstream, o ajuste por un refactor reciente de upstream).
- **Política**: aplica limpio técnicamente pero añade superficie que upstream puede rechazar (dependencia nueva, archivo nuevo); necesita discusión previa.

## Mapeo issue → commit

### #167 — AltGr dispara atajos del dock
- Commit: `c5907bf` — _Pass AltGr chords through instead of firing Alt shortcuts_
- Superficie: `DockService.onKeyEvent` (~10 líneas).
- Cherry-pick: **limpio**. Puro causa raíz, sin cadenas ni archivos nuevos. Candidato a ir primero.

### #250 — «0 recents» no significa ilimitado
- Commit: `dd14aed` — _Treat a running-apps limit of 0 as unlimited_
- Superficie: `DockService.updateRunningTasks` (unas líneas).
- Cherry-pick: **limpio**. Puro causa raíz.

### #266 — La captura desde ajustes rápidos fotografiaba el panel
- Commit: `5a3beca` — _Take quick-settings screenshots after the panel is off screen_
- Superficie: `DockService.takeScreenshotClean` (hide dock + delay + restore).
- Cherry-pick: **limpio**. El delay 400 ms está inline en este commit; si se envía después del PR del task 4 (constante `Timers`), alinear con ese nombre.

### #270, #264, #267, #268 — crash en config changes tempranos, swipe en táctil, Meta abre menú, dock muerto tras crash-reboot
- Commit único: `f5409c5` — _Fix the crash on early configuration changes, the swipe activation method, Meta shortcuts and dead docks after a crash-reboot_
- Superficie: `DockService` (varios puntos) + nuevo `BootCompletedReceiver` (#268) + entrada en `AndroidManifest.xml`.
- Cherry-pick: **adapta**. El commit toca cuatro concerns distintos; para upstream conviene **split en 4 PRs** (uno por issue):
  - #270 (crash en config changes tempranos): la porción que endurece `onConfigurationChanged` / re-creación de vistas temprana.
  - #264 (swipe nunca funcionó en táctil): la porción del método de activación.
  - #267 (Meta+X abre menú al soltar Meta): la porción que trackea `metaLeftDown`/`metaUsedAsModifier`.
  - #268 (dock muerto tras crash-reboot): **trae un archivo nuevo** (`BootCompletedReceiver.kt`) + entrada de manifest. Es el más grande de los cuatro; revisar que upstream acepte el receiver de arranque (política de autostart).
- Nota: el split se hace sobre el árbol del fork creando commits nuevos a partir de los hunks de `f5409c5`; el commit original queda en el historial del fork.
- **Refinamiento de sesión 7**: el aspecto listed-but-not-bound de #268 ahora tiene un fix más directo en `a51c8b9` (ver sección de sesión 7 más abajo); el `BootCompletedReceiver` de este commit queda como complemento de arranque, no como fix principal.

### #259, #253 — arranques en blanco y binds obsoletos
- Commit único: `c1d02b2` — _Harden the dock against blank boot windows and stale adapter binds_
- Superficie: `DockService` (guards en `onAccessibilityEvent` / adaptadores).
- Cherry-pick: **adapta**. Commit multi-concern; **split en 2 PRs** (#259 ventanas en blanco, #253 binds obsoletos). Ambas porciones son causa raíz sin cadenas nuevas.
- **Refinamiento de sesión 7**: el aspecto de resilience del binding contra `Throwable` (no solo `Exception`) ahora tiene un fix más directo en `ff3f908` (ver sección de sesión 7); decidir si enviar ambos o solo `ff3f908` para #253.

### #200 — offset del handle desde el borde
- Commits: `f0edf9f` — _Add an edge-offset option for the activation handle_ + `dbba38d` — _Set the handle-offset slider range before its stored value_
- Superficie: `DockService` (handle) + `preferences_dock_elements.xml` + **cadenas nuevas** (title/summary) en `values/strings.xml` y los locales mantenidos.
- Cherry-pick: **adapta**. Las cadenas nuevas deben traducirse a los locales que upstream mantiene (upstream no mantiene `pa`/`zh-rHK`/`zh-rTW`; el fork sí — enviar solo los locales de upstream y dejar los del fork como seguimiento).

### #263 — soporte de display secundario fail-safe
- Commits: `62ee704` — _Make secondary display support fail-safe_ + `0c262fa` — _Use the public presentation display category in getSecondaryDisplay_ + `931b708` — _Fix window tiling and resizing on secondary displays_
- Superficie: `DeviceUtils` (display selection) + `AppUtils` (bounds por display) + `DockService` (lanzamiento con fallback). Feature-adjacente, no solo fix.
- Cherry-pick: **adapta**. Tres commits encadenados; idealmente un PR por issue si upstream los quiere separados, o un PR cohesivo «secondary display fail-safe».
- **Refinamiento de sesión 7**: el routing real del overlay al display elegido ahora tiene un fix directo en `df4085d`+`9f0f96e`+`51be96a` (ver sección de sesión 7); los tres commits de sesión 5 cubren la selección fail-safe, los de sesión 7 cubren el routing del `WindowManager`.
- El commit `98ebb9a` (_Add external display desktop mode and send running tasks to another screen_) es **feature pura** (modo escritorio externo + Send to screen) y queda fuera del paquete de fixes; candidato a discusión por separado.

### Crash de los wrappers Shizuku en frameworks nuevos (sin issue upstream)
- Commit: `f2f4727` — _Never let a Shizuku wrapper's hidden-API link failure kill the dock_
- Superficie: `App.kt`, `DockService.initWrappers`, los tres wrappers, `app/build.gradle` (añade dependencia `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`).
- Cherry-pick: **política**. Aplica limpio sobre upstream (upstream tiene los mismos wrappers), pero **añade una dependencia Maven nueva** y un unseal de hidden APIs global (`addHiddenApiExemptions("L")`). Upstream puede resistir ambas cosas; necesita discusión previa sobre si prefieren el unseal, una solución más estrecha, o aceptar que el wrapper degrada a muerto sin la dependencia.

## Sesión 7 (v1.15.5-fork.9) — añadidos y refinamientos

Los commits de la séptima sesión refinen los fixes de #263, #268 y #253 con causas raíz más directas que las de sesiones anteriores. **Para el paquete a upstream, los tres commits del fix de display secundario (`df4085d` + `9f0f96e` + `51be96a`) deben plegarse en un solo commit**: `9f0f96e` y `51be96a` son correcciones de compilación y lint sobre `df4085d` que solo existen porque el commit original no se compiló ni se linteó antes de pushear. El commit plegado lleva el mensaje de `df4085d` y un cuerpo que combina el razonamiento de los tres (el guard `SDK_INT >= S` y el fallback a `createDisplayContext` en API 30 son parte del fix, no correcciones posteriores). Forma de plegar: `git rebase -i` con `fixup` sobre `df4085d`, o re-crear el commit sobre upstream con el cuerpo combinado.

### #263 — routing de overlay al display secundario (refinamiento)
- Commits: `df4085d` + `9f0f96e` + `51be96a` — **plegar en uno solo**.
- Superficie: `Utils.getDisplayContext` (~10 líneas).
- Cherry-pick: **limpio** una vez plegado. Puro causa raíz, sin cadenas ni archivos nuevos. Complementa `62ee704` + `0c262fa` + `931b708` (routing real del overlay al display elegido, no solo selección fail-safe del display).

### #268 — accesibilidad listada pero no bound (refinamiento)
- Commit: `a51c8b9` — _Recognize accessibility as enabled when listed but not bound_
- Superficie: `DeviceUtils` (split de `isAccessibilityServiceEnabled` + nueva `isAccessibilityServiceBound`) + `MainActivity` (diálogo de permisos ofrece Reactivate) + **3 cadenas nuevas** en `values/strings.xml` y 6 locales.
- Cherry-pick: **adapta** (cadenas nuevas a traducir a los locales de upstream). Causa raíz directa para el aspecto listed-but-not-bound de #268; más directo que el split de `f5409c5`. Si se envía, reemplaza al split #268 de `f5409c5` como fix principal; el `BootCompletedReceiver` queda como complemento de arranque (misma grace: drop + re-add del servicio).

### #253 / #268 — resilience de `createViews` (refinamiento)
- Commit: `ff3f908` — _Survive a Throwable from createViews instead of killing the binding_
- Superficie: `DockService` (`onServiceConnected` y `restartUI`, ~15 líneas).
- Cherry-pick: **limpio**. Puro causa raíz: `catch (Exception)` → `catch (Throwable)` con fallback al display principal. Endurece el binding contra `Error` (no solo `Exception`), que es lo que mataba el servicio en #253 y #268. Complementa el hardening de adapters de `c1d02b2`.

## Orden de envío propuesto

Los fixes puros de causa raíz primero (rápidos de revisar y de alto valor para usuarios de upstream), los multi-concern que requieren split después, las features al final, y el endurecimiento de wrappers el último por su superficie de política. **Los refinamientos de sesión 7 van antes que sus originales de sesión 5** (son más directos); el commit plegado de display secundario va con los fixes puros.

1. **#167** AltGr — `c5907bf` (limpio, ~10 líneas).
2. **#250** 0 = ilimitado — `dd14aed` (limpio, mínimo).
3. **#266** captura limpia — `5a3beca` (limpio, pequeño).
4. **#253/#268 resilience** — `ff3f908` (limpio, `catch Throwable`).
5. **#268 listed-but-not-bound** — `a51c8b9` (adapta, cadenas). Reemplaza al split #268 de `f5409c5` como fix principal.
6. **#263 routing overlay** — `df4085d`+`9f0f96e`+`51be96a` **plegados en uno** (limpio). Complementa `62ee704`+`0c262fa`+`931b708`.
7. **#270 / #264 / #267** split de `f5409c5` — tres PRs pequeños, misma fuente.
8. **#259 / #253** split de `c1d02b2` — dos PRs de hardening (el aspecto #253 ahora también cubierto por `ff3f908` de sesión 7; decidir si enviar ambos o solo `ff3f908`).
9. **#268 BootCompletedReceiver** — split de `f5409c5`, trae archivo nuevo; revisar política de autostart con upstream.
10. **#200** offset del handle — `f0edf9f` + `dbba38d`, feature+fix con cadenas (traducir a locales de upstream).
11. **#263 fail-safe** — `62ee704` + `0c262fa` + `931b708` (si upstream los quiere separados del routing de sesión 7).
12. **Wrappers** (sin issue) — `f2f4727`, dependencia nueva + unseal global; **último**, tras discutir la política con upstream.

Fuera del paquete (features puras, candidatas a discusión separada, no a PR de fix): `98ebb9a` (modo escritorio externo + Send to screen), `027da00` (backup JSON completo), `c2d3bb8`/`2247850` (mejoras de UX del fork).

## Notas operativas

- Antes de cada PR, re-crear el commit sobre la punta actual de upstream (`git rebase --onto upstream/main <fork-base> <commit>`) para verificar que sigue limpio; este documento se escribió sobre `main` del fork en `fe08541` y los hashes son los del historial del fork.
- Para los splits (#270/#264/#267/#268 de `f5409c5`; #259/#253 de `c1d02b2`): usar `git checkout f5409c5 -- <archivo>` y re-commit por porción con un mensaje que cite solo el issue que cierra, o `git rebase -i` con `edit` + reset parcial. (El entorno del agente no soporta `rebase -i` interactivo; hacerlo con commits explícitos.)
- Cada PR debe referencear el issue que cierra (`Closes #NNN`) en el cuerpo.
- Las cadenas nuevas (#200, #263 si trae, #268 con `a51c8b9`) deben ir con `values/strings.xml` + los locales de upstream (que upstream mantenga). El fork mantiene `de, es, pa, zh-rCN, zh-rHK, zh-rTW`; upstream puede no querer todos.

### Nota operativa adicional (sesión 7)

**Compilar y lintar antes de pushear.** El commit `df4085d` se envió sin compilar; `9f0f96e` (la sobrecarga de `createWindowContext` a 2 argumentos no existe — el compilador resolvió la llamada a `(Int, Bundle)`) y `51be96a` (lint NewApi porque `createWindowContext(Display, type, options)` es API 31 y el minSdk del fork es 30) son correcciones posteriores que un `./gradlew assembleDebug lint` antes del push habría atrapado. Para los commits del paquete a upstream, **siempre** compilar y lintar localmente antes de abrir el PR; el historial del fork ya quedó con los tres commits, pero el PR upstream debe llevar el commit plegado. El detalle de degradar a `createDisplayContext` en API 30 (Android 11) está bien razonado — `createWindowContext` para overlays requiere API 31, y el fallback es lo correcto para mantener minSdk 30.
