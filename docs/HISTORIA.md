# HISTORIA — narrativa consolidada del fork

_Cómo llegó el fork a su estado actual, y mapa de todos los documentos y ramas._

## El proyecto

**Smart Dock** (upstream: [axel358/smartdock](https://github.com/axel358/smartdock), GPL-3.0) es un launcher de modo escritorio para Android: convierte una tablet (o un teléfono conectado a un monitor) en un entorno de escritorio con dock, multi-ventana libreform, atajos de teclado y paneles de ajustes rápidos. El corazón es `DockService.kt`, un **servicio de accesibilidad** que dibuja las ventanas del dock, lanza apps en displays concretos y gestiona el teclado/ratón. Por eso mismo necesita `WRITE_SECURE_SETTINGS` — permiso que solo se concede por adb, app de sistema o **Shizuku**, y que explica la mitad de su magia y la mitad de sus bugs.

El fork (kayb28) nació de usarlo sobre un monitor USB-C: el soporte de display secundario estaba a medio construir, con crashes y estados muertos que dejaban el dock inerte. La jornada del 2026-11-07 («octubre de 2026 bugs», la rama se llamó así) arregló los problemas conocidos y completó el soporte multi-pantalla.

## Línea de tiempo

- **hasta 2026-05-21** — paridad con upstream: `main` del fork = `517604a`, punta de upstream («fix: Update donation address»), 840 commits de historia, versión 1.15.0 (versionCode 1150), publicado en F-Droid. El fork no tocó nada por debajo.
- **2026-11-07, 00:23–04:03 UTC** — **jornada de bugs de octubre 2026** en la rama `bugfix/october-2026-bugs`: 10 commits, 4 issues cerrados, 1 feature grande, 1 fix de infraestructura de builds, 1 workflow de releases. Publicada como `v1.15.0-fork.4` (el .4 no es un contador de releases previas: los números .1–.3 fueron builds locales previas a la cuarta; la primera en llegar a GitHub Release fue esta). Detalle commit por commit en `docs/REGISTRO.md` §2026-11-07.
- **2026-11-19** — **documentación y consolidación**: el trabajo de la rama nunca llegó a `main` (main quedó 10 commits atrás). Esta sesión consolida por fast-forward y deja el registro completo en docs. No hay cambios de código en esta fecha, solo documentación y la consolidación de rama. Se publica `v1.15.1-fork.5` (bump de versionCode 1151: la build actualizable).
- **2026-11-19, tarde** — **jornada de las 12 mejoras** en la rama `feature/november-2026-improvements`: el menú completo de opciones de mejora del informe del 19-11 implementado en 9 commits (backup JSON completo, calculadora de expresión, motor de búsqueda configurable, atajos anclables, panel Wi-Fi propio, automatización, menús por comando, receivers, app activa real, DiffUtil). Publicada como `v1.15.2-fork.6` (versionCode 1152). Detalle en `docs/REGISTRO.md` §tercera pasada.
- **2026-11-19, tarde** — **jornada de cierre de issues reportados** en la rama `bugfix/november-2026-issues`: los 25 issues abiertos upstream se descargan con cuerpo y comentarios, se clasifican (11 bugs, 13 feature requests, 1 roadmap propio de upstream) y **todos los bugs que quedaban se corrigen** en 6 commits: AltGr (#167), «0 recents» = sin límite (#250), captura limpia desde ajustes rápidos (#266), offset del handle desde el borde (#200), y hardening contra arranques en blanco (#259) y binds obsoletos (#253). Publicada como `v1.15.3-fork.7` (versionCode 1153). Con esta release **no queda ningún bug reportado sin corregir en el fork**. Detalle en `docs/REGISTRO.md` §cuarta pasada.

## Qué cambió respecto a upstream (resumen en una pantalla)

1. **Estabilidad del servicio de accesibilidad**: crash en cambios de configuración tempranos (#270), dock muerto tras crash-reboot del SO (#268, boot receiver con re-enganche a los 30 s), atajos Meta que abrían el menú (#267), método de activación swipe que nunca funcionó en táctil (#264).
2. **Displays secundarios fail-safe (#263)**: selección correcta de display externo real (categoría presentation, no la lista cruda), null-checks, relanzamiento a pantalla principal cuando el sistema rechaza el lanzamiento, en lugar de tumbar el servicio.
3. **Multi-pantalla real**: tiling/resize calculados para el display donde vive la ventana (antes: métricas del display integrado, ventanas gigantes e inutilizables en el monitor), caso fullscreen que aplicaba un Rect vacío.
4. **Modo escritorio en el monitor** («Force desktop mode» automatizado): toggle en Ajustes avanzados que aplica `force_desktop_mode_on_external_displays` + `enable_freeform_support` por permiso propio → Shizuku → root (en ese orden), y «Send to screen» para mover tareas en ejecución entre pantallas.
5. **Infraestructura del fork**: keystore debug fijado en el repo (firmas estables → updates sin desinstalar, sin perder ajustes), workflow de releases automáticas en tags, traducciones de todas las cadenas nuevas a de/es/pa/zh-rCN/zh-rHK/zh-rTW.
6. **La jornada de las 12 mejoras (fork.6)**: backup de ajustes que ya no pierde Sets ni URIs, calculadora de expresión completa, motor de búsqueda configurable, atajos profundos anclables al dock, panel propio de redes Wi-Fi vía Shizuku, intents de automatización con permiso signature, menús contextuales despachados por comando estable (no por texto traducido), los 7 receivers desregistrados, app activa real por `TYPE_WINDOW_STATE_CHANGED` y DiffUtil en la franja de tareas.
7. **El cierre de los bugs reportados (fork.7)**: los 25 issues abiertos upstream clasificados y los 11 bugs de la lista corregidos (5 en fork.4 + 6 en fork.7). Quedan abiertos upstream solo feature requests (#260, #247, #243, #240, #235, #229, #209, #207, #191, #182, #165, #220, #258) y la propuesta de traducción al portugués de Brasil; ninguno es un defecto de funcionamiento.

## Mapa de documentos

| Documento | Qué contiene | Cuándo leerlo |
|---|---|---|
| `AGENTS.md` | Guía para agentes: compilar, ramas, permisos, convenios, lecciones | Antes de tocar nada |
| `docs/REGISTRO.md` | Diario operativo, commit por commit, con estado de CI | Para saber qué se hizo y por qué |
| `docs/HISTORIA.md` | Este archivo: narrativa y mapa | Para orientarse |
| `FORK.md` | Registro público del fork, build por build (estilo WebLibre) | Para el usuario del fork |
| `CHANGELOG-FORK.md` | Changelog formal por release | Para notas de versión |
| `README.md` / `CONTRIBUTING.md` / `HideNav.md` | Documentación de upstream (sin tocar salvo divergencia) | Referencia del proyecto |

## Mapa de ramas

- `main` — punta consolidada: `517604a` (upstream 1.15.0) + 10 commits de `bugfix/october-2026-bugs` + commits `docs/` del 2026-11-19 + 9 commits de `feature/november-2026-improvements` (fork.6) + 6 commits de `bugfix/november-2026-issues` (fork.7), todo absorbido por fast-forward.
- `bugfix/october-2026-bugs` — histórico congelado de la jornada 2026-11-07; allí vive el tag `v1.15.0-fork.4`. No pushear encima.
- `feature/november-2026-improvements` — histórico de la jornada de las 12 mejoras; allí vive el tag `v1.15.2-fork.6`. No pushear encima.
- `bugfix/november-2026-issues` — histórico de la jornada de cierre; allí vive el tag `v1.15.3-fork.7`. No pushear encima.
- Tags: `v1.15.0-fork.4` → `0eac1a4`; `v1.15.1-fork.5` → main consolidada + bump 1151; `v1.15.2-fork.6` → jornada de las 12 mejoras (bump 1152); `v1.15.3-fork.7` → cierre de issues reportados (bump 1153).
- Próximas ramas: cortar de `main`, prefijos `bugfix/` (fixes) o `feature/` (funciones). Consolidar a main solo por fast-forward (o merge sin conflicto si upstream se cruza por el medio).

## Qué NO ha hecho el fork (límites actuales)

- No hay renombrado de paquete ni de aplicación: paridad total con upstream para poder absorber sus futuros cambios con merges baratos.
- versionCode estrictamente creciente desde v1.15.1-fork.5 (1151): cada release del fork debe superar el versionCode instalado o Android la rechaza como reinstalación (lección de WebLibre). Si upstream sube su versión, el fork sigue por encima.
- El upstream no tiene PR abierto desde el fork: los fixes de las jornadas de octubre y de noviembre son candidatos naturales a upstream (los commits están redactados en inglés con cuerpo de causa raíz para eso), pero no se ha propuesto nada — decisión pendiente del dueño.
- Los feature requests abiertos upstream (widgets, ocultar barra de búsqueda, botón de bloqueo en el menú, layout vertical en apaisado, etc.) **no son bugs** y el fork no los ha implementado: quedan documentados como trabajo futuro. La traducción al portugués de Brasil (#258) es la más mecánica de la lista (los 7 locales del fork ya se mantienen manualmente).
