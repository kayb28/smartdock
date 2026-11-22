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

### #259, #253 — arranques en blanco y binds obsoletos
- Commit único: `c1d02b2` — _Harden the dock against blank boot windows and stale adapter binds_
- Superficie: `DockService` (guards en `onAccessibilityEvent` / adaptadores).
- Cherry-pick: **adapta**. Commit multi-concern; **split en 2 PRs** (#259 ventanas en blanco, #253 binds obsoletos). Ambas porciones son causa raíz sin cadenas nuevas.

### #200 — offset del handle desde el borde
- Commits: `f0edf9f` — _Add an edge-offset option for the activation handle_ + `dbba38d` — _Set the handle-offset slider range before its stored value_
- Superficie: `DockService` (handle) + `preferences_dock_elements.xml` + **cadenas nuevas** (title/summary) en `values/strings.xml` y los locales mantenidos.
- Cherry-pick: **adapta**. Las cadenas nuevas deben traducirse a los locales que upstream mantiene (upstream no mantiene `pa`/`zh-rHK`/`zh-rTW`; el fork sí — enviar solo los locales de upstream y dejar los del fork como seguimiento).

### #263 — soporte de display secundario fail-safe
- Commits: `62ee704` — _Make secondary display support fail-safe_ + `0c262fa` — _Use the public presentation display category in getSecondaryDisplay_ + `931b708` — _Fix window tiling and resizing on secondary displays_
- Superficie: `DeviceUtils` (display selection) + `AppUtils` (bounds por display) + `DockService` (lanzamiento con fallback). Feature-adjacente, no solo fix.
- Cherry-pick: **adapta**. Tres commits encadenados; idealmente un PR por issue si upstream los quiere separados, o un PR cohesivo «secondary display fail-safe». El commit `98ebb9a` (_Add external display desktop mode and send running tasks to another screen_) es **feature pura** (modo escritorio externo + Send to screen) y queda fuera del paquete de fixes; candidato a discusión por separado.

### Crash de los wrappers Shizuku en frameworks nuevos (sin issue upstream)
- Commit: `f2f4727` — _Never let a Shizuku wrapper's hidden-API link failure kill the dock_
- Superficie: `App.kt`, `DockService.initWrappers`, los tres wrappers, `app/build.gradle` (añade dependencia `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`).
- Cherry-pick: **política**. Aplica limpio sobre upstream (upstream tiene los mismos wrappers), pero **añade una dependencia Maven nueva** y un unseal de hidden APIs global (`addHiddenApiExemptions("L")`). Upstream puede resistir ambas cosas; necesita discusión previa sobre si prefieren el unseal, una solución más estrecha, o aceptar que el wrapper degrada a muerto sin la dependencia.

## Orden de envío propuesto

Los fixes puros de causa raíz primero (rápidos de revisar y de alto valor para usuarios de upstream), los multi-concern que requieren split después, las features al final, y el endurecimiento de wrappers el último por su superficie de política.

1. **#167** AltGr — `c5907bf` (limpio, ~10 líneas).
2. **#250** 0 = ilimitado — `dd14aed` (limpio, mínimo).
3. **#266** captura limpia — `5a3beca` (limpio, pequeño).
4. **#270 / #264 / #267** split de `f5409c5` — tres PRs pequeños, misma fuente.
5. **#259 / #253** split de `c1d02b2` — dos PRs de hardening causa raíz.
6. **#268** dock muerto tras crash-reboot — split de `f5409c5`, trae archivo nuevo (`BootCompletedReceiver`); revisar política de autostart con upstream.
7. **#200** offset del handle — `f0edf9f` + `dbba38d`, feature+fix con cadenas (traducir a locales de upstream).
8. **#263** display secundario fail-safe — `62ee704` + `0c262fa` + `931b708`, feature-adjacente, superficie mayor.
9. **Wrappers** (sin issue) — `f2f4727`, dependencia nueva + unseal global; **último**, tras discutir la política con upstream.

Fuera del paquete (features puras, candidatas a discusión separada, no a PR de fix): `98ebb9a` (modo escritorio externo + Send to screen), `027da00` (backup JSON completo), `c2d3bb8`/`2247850` (mejoras de UX del fork).

## Notas operativas

- Antes de cada PR, re-crear el commit sobre la punta actual de upstream (`git rebase --onto upstream/main <fork-base> <commit>`) para verificar que sigue limpio; este documento se escribió sobre `main` del fork en `fe08541` y los hashes son los del historial del fork.
- Para los splits (#270/#264/#267/#268 de `f5409c5`; #259/#253 de `c1d02b2`): usar `git checkout f5409c5 -- <archivo>` y re-commit por porción con un mensaje que cite solo el issue que cierra, o `git rebase -i` con `edit` + reset parcial. (El entorno del agente no soporta `rebase -i` interactivo; hacerlo con commits explícitos.)
- Cada PR debe referencear el issue que cierra (`Closes #NNN`) en el cuerpo.
- Las cadenas nuevas (#200, #263 si trae) deben ir con `values/strings.xml` + los locales de upstream (que upstream mantenga). El fork mantiene `de, es, pa, zh-rCN, zh-rHK, zh-rTW`; upstream puede no querer todos.
