# ROADMAP — listado de features del fork

_Fuente de la verdad del listado de features y de su orden de implementación. La numeración es la del listado original del dueño, que vivía solo en la conversación; este documento la consolida (2026-11-23, tras el corte de fork.12) y la **reconcilia con la lista original el 2026-11-24** (la primera tabla estaba barajada — ver nota tras la tabla). El orden de implementación NO es el de la lista — ya pasó con F6+F9 → F7 → #209 → F12 antes que F8 — se decide por dependencias y riesgo en cada triage y queda registrado aquí._

## Estado del listado

| # | Feature | Estado | Dónde vive |
|---|---|---|---|
| F1–F4 | Ítems tempranos del listado (la zona de estado — F1 — ya existía completa en el fork y solo se reportó; REGISTRO §octava pasada) | sin pendientes conocidos | — |
| F5 | Zonas de anclado de ventanas (snap zones) | 🚧 **en curso** (triage = spec del handoff 2026-11-24: medio borde / cuarto esquina, umbral configurable, sin Shizuku deshabilitado con razón visible; detección de arrastre por polling de `getTaskBounds` — decisión documentada en REGISTRO) | rama `feature/snap-zones` |
| F6 | Colocación del dock (selector de pantalla del dock) | ✅ hecha | fork.10 (`0049dd0`) |
| F7 | Geometría de ventana por app | ✅ hecha | fork.11 (`496250f`) |
| F8 | Editor de combinaciones de teclado | ✅ **hecha** — entra en **fork.14** | rama `feature/shortcut-editor` (`b6ff68c`+`fe649ce`+`35471b1`) |
| F9 | Matar tareas (force-stop) | ✅ hecha | fork.10 (`b716860`) |
| F10 | Modo trackpad (el móvil como panel táctil del monitor) | 📋 triado (abajo) | — |
| F11 | Escritorios virtuales / workspaces | 📋 triado (abajo) | — |
| F12 | Acciones de notificación desde el panel | ✅ **hecha** — entra en **fork.13** | rama `feature/notification-actions` (`45b0148`, plegado) |
| — | Presets de geometría de ventana con nombre (upstream #209; sin número de lista) | ✅ hecha | fork.12 (`e3a12bc`+`968b4e4`; plegado upstream `0be6597`) |

**Reconciliación de numeración (2026-11-24)**: la primera versión de esta tabla (2026-11-23) estaba barajada: rotulaba F5 = presets (#209), F6 = force-stop y F9 = selector de pantalla, cuando el listado original del dueño era **F5 = zonas de anclado (snap zones), F6 = colocación del dock y F9 = matar tareas**; los presets con nombre no eran un ítem numerado de la lista — responden al issue upstream #209 y quedan como entrada aparte. La numeración queda restaurada aquí; las referencias históricas a «F5/F6/F9» en REGISTRO/HISTORIA/UPSTREAM-PRS se leen con esta tabla («F5» de esas entradas = presets; «F6» = force-stop = **F9** de la lista; «F9» = selector de pantalla = **F6** de la lista). El nombre de la rama `feature/dock-placement-killtasks` (fork.10) confirma el mapeo real: sus dos features son colocación del dock (F6) y matar tareas (F9).

## Orden de implementación (decidido 2026-11-24)

**F12 (fork.13) → F8 (fork.14) → F11 (fork.15) → F10 (fork.16)**

- **F12 primero (fork.13)**: la más barata del lote — el panel de notificaciones ya existe y todo lo que falta son APIs públicas; cierra la sensación de «sistema operativo» del dock sin tocar permisos ni la capa de ventanas. Decisión del 2026-11-24 (el checkbox de confirmación del dueño, abierto desde el 23, se cierra con delegación al agente): F12 estaba terminada y F8 sin empezar — en la práctica F12 sale antes que F8 sí o sí, y el plan de releases se reajusta en consecuencia (F12 = **fork.13**, no fork.14).
- **F11 justo después de F8**: la conmutación por atajos consume el registro de combinaciones que F8 construye, y su modelo de estado sale con tests desde el día uno.
- **F10 al final**: la más diferencial y la de mayor plumbing (wrapper nuevo de inyección + capa de gestos); conviene construirla sobre la capa de ventanas ya consolidada y con el patrón de wrappers maduro.
- **F5 (snap zones)**: implementada tras F8 (sesión del 2026-11-24, rama `feature/snap-zones`) — el handoff del dueño trajo su spec, cerrando el triage pendiente. Almacenamiento: ninguno (comportamiento runtime con toggle + umbral en Advanced); la geometría de zona es modelo puro testeado (`utils/SnapZones.kt`), separada de los presets de #209 y de la geometría por app de F7 (misma disciplina de ejes distintos). Detección de arrastre: el sistema arrastra las tareas freeform por su cuenta — el dock **no puede interceptar el gesto**; el watcher sondea `getTaskBounds` (camino Shizuku existente) y lee «arrastre» = bounds cambiando, «soltar» = ~400 ms estables. Sin Shizuku: la función queda inerte y el toggle lo declara (honestidad del contrato).
- El orden propuesto el 2026-11-23 queda **decidido** el 2026-11-24: F12 primero por estar terminada; el resto (F8 → F11 → F10) se mantiene por sus dependencias.

## Triage de los tres nuevos

### F12 — Acciones de notificación (reply, archivar) desde el panel

- **Mecanismo**: el panel ya vive en `NotificationService` (`NotificationListenerService`) y `StatusBarNotification` ya es el tipo que fluye por `NotificationAdapter`/`NotificationLayout`. `Notification.actions[]` es API pública: cada acción se ejecuta con `action.actionIntent.send(context, 0, fillInIntent)`; el **reply directo** rellena `RemoteInput.addResultsToIntent(action.remoteInputs, intent, resultados)` antes del send. Sin Shizuku, sin root, **sin permisos nuevos** (el acceso a notificaciones ya está concedido para el panel).
- **Alcance v1**: botones por acción estándar + reply para las notificaciones que traen `RemoteInput`. Apps con requisitos propios fuera del mecanismo degradan a «abrir la app» — comportamiento honesto, no fallo.
- **Riesgos**: bajos. Notificaciones redactadas con el bloqueo activo no exponen acciones; algunos `RemoteInput` de terceros exigen campos extra.
- **Valor upstream**: **alta** — autocontenida, APIs públicas, sin dependencias ni permisos de política; cuando se implemente, entra al paquete de `UPSTREAM-PRS.md` con entrada propia.

### F11 — Escritorios virtuales / workspaces

- **Mecanismo**: pertenencia de ventana a workspace como **estado nuevo de la capa de ventanas** (runtime del `DockService`) + **modelo propio de membresía y reordenado de tareas vía Shizuku** (`IActivityManager`); atajos de conmutación vía F8. Redacción corregida (2026-11-24): la versión anterior decía «conmutación = conjuntos show/hide de overlays», y es **incorrecta** — las ventanas de las apps son **tareas freeform reales** del sistema, no overlays del dock; conmutar exige mover/ocultar esas tareas con las llamadas privilegiadas del wrapper, no manipular ventanas propias. La persistencia del workspace activo sigue las decisiones de almacenamiento existentes.
- **Tests desde el día uno**: el modelo (asignar / mover / conmutar / persistir) se extrae a Kotlin puro — el patrón ya usado por `Calculator`/`KeyboardModifiers`/`UpdateChecker` (helpers JVM que `./gradlew test` corre); la visibilidad de overlays queda para instrumentados.
- **Decisión pre-anotada (misma disciplina que la de almacenamiento F5/F7)**: workspaces (partición runtime de ventanas) ≠ F7 (geometría por app en DB, píxeles) ≠ F5 (presets con nombre en SharedPreferences, porcentajes). Tres ejes distintos a propósito; si algún día se cruzan (p. ej. geometría por workspace), será decisión documentada, no deriva.
- **Riesgos**: foco global (una sola ventana enfocada a la vez), interacción con «mostrar todas las apps», multi-display (¿workspace global o por display? — a decidir en diseño), coste de conmutación con muchas ventanas, y **privilegios sobre tareas ajenas**: las tareas pertenecen al sistema y a otras apps, así que ocultarlas/reordenarlas entre workspaces exige llamadas privilegiadas (`IActivityManager` vía Shizuku/root) sobre tareas que no son del dock — riesgo real de interferir con la gestión de tareas del SO, con degradación obligatoria cuando el privilegio falte.
- **Valor upstream**: **bajo** — integración profunda en la capa de ventanas del fork; difícil de cherry-pick.

### F10 — Modo trackpad (el móvil como panel táctil del monitor)

- **Mecanismo**: overlay de gestos sobre la pantalla del móvil (el dock ya rutea overlays al display elegido desde fork.9) → cursor virtual en memoria → **inyección de eventos** vía un wrapper nuevo siguiendo el patrón existente (adquisición resiliente, degradación a null, nunca mata el servicio): `IInputManager.injectInputEvent` por el mismo camino Shizuku/AIDL que `activityManagerWrapper` ya usa para `getTaskBounds`/`resizeTask`.
- **Permisos**: `INJECT_EVENTS` es `signature`, pero **`platform.xml` de AOSP la asigna al uid shell** (por eso existe `adb shell input`) → funcionan **Shizuku (identidad shell)** y root; la app de sistema con firma de plataforma queda como camino futuro. Sin permiso: degradación honesta — entrada visible en el menú, deshabilitada y con explicación, nada silencioso (mismo contrato que CLOSE/RESIZE, ya gated a Shizuku).
- **Gestos v1**: un dedo mueve el cursor; tap = clic izquierdo; dos dedos = scroll (`ACTION_SCROLL` con ejes V/H); tap de dos dedos = clic derecho; v2: arrastre (doble-tap y mantener) y velocidad de puntero configurable en Ajustes.
- **Riesgos**: displayId correcto del monitor (resolución por categoría presentation — lección 6 de AGENTS.md), suavidad del cursor (inyección async + velocidad), batería (toggle explícito: el overlay solo vive en modo trackpad), generación correcta de multitouch.
- **Nota honesta (marco del diferencial)**: ⚠️ **SIN VERIFICAR** — se afirma que el modo escritorio nativo de Google para displays conectados ya convierte la pantalla del teléfono en trackpad (citado como **Android 16 QPR1**, «refinado en Android 17»), pero puede tratarse de una beta o de una flag: **fuente pendiente de confirmar** antes de dar el hecho por bueno. Si se confirma, el diferencial del fork es real frente a **launchers de terceros** y en **Android 11–15** (minSdk 30 del fork); no vender «primero en ningún sitio» sin ese matiz.
- **Valor upstream**: **política** — la función central depende de Shizuku/inyección; discusión previa con upstream antes de proponer nada.

## Reglas transversales (se aplican a los tres)

1. **Degradación honesta** en todo camino privilegiado: la función degrada con explicación, nunca muere ni se queda muda (contrato de los wrappers, AGENTS §4).
2. **Cada string nueva en los 6 locales** mantenidos (de, es, pa, zh-rCN, zh-rHK, zh-rTW) — `MissingTranslation` tumba el build (lección 1).
3. **Un feature = una rama `feature/` = entrada propia en REGISTRO**; el corte de release (fork.N) aplica el triple bump de AGENTS §5.
4. **Solo APIs públicas, u ocultas tras wrapper con degradación** (lección 3); nunca presumir el display (lección 6).
5. **Para upstream solo entra lo autocontenible** con commits en inglés y causa raíz — cada feature se evalúa al terminar (`UPSTREAM-PRS.md`).
