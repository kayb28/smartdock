# HISTORIA — narrativa consolidada del fork

_Cómo llegó el fork a su estado actual, y mapa de todos los documentos y ramas._

## El proyecto

**Smart Dock** (upstream: [axel358/smartdock](https://github.com/axel358/smartdock), GPL-3.0) es un launcher de modo escritorio para Android: convierte una tablet (o un teléfono conectado a un monitor) en un entorno de escritorio con dock, multi-ventana libreform, atajos de teclado y paneles de ajustes rápidos. El corazón es `DockService.kt`, un **servicio de accesibilidad** que dibuja las ventanas del dock, lanza apps en displays concretos y gestiona el teclado/ratón. Por eso mismo necesita `WRITE_SECURE_SETTINGS` — permiso que solo se concede por adb, app de sistema o **Shizuku**, y que explica la mitad de su magia y la mitad de sus bugs.

El fork (kayb28) nació de usarlo sobre un monitor USB-C: el soporte de display secundario estaba a medio construir, con crashes y estados muertos que dejaban el dock inerte. La jornada del 2026-11-07 («octubre de 2026 bugs», la rama se llamó así) arregló los problemas conocidos y completó el soporte multi-pantalla.

## Línea de tiempo

- **hasta 2026-05-21** — paridad con upstream: `main` del fork = `517604a`, punta de upstream («fix: Update donation address»), 840 commits de historia, versión 1.15.0 (versionCode 1150), publicado en F-Droid. El fork no tocó nada por debajo.
- **2026-11-07, 00:23–04:03 UTC** — **jornada de bugs de octubre 2026** en la rama `bugfix/october-2026-bugs`: 10 commits, 4 issues cerrados, 1 feature grande, 1 fix de infraestructura de builds, 1 workflow de releases. Publicada como `v1.15.0-fork.4` (el .4 no es un contador de releases previas: los números .1–.3 fueron builds locales previas a la cuarta; la primera en llegar a GitHub Release fue esta). Detalle commit por commit en `docs/REGISTRO.md` §2026-11-07.
- **2026-11-19** — **documentación y consolidación**: el trabajo de la rama nunca llegó a `main` (main quedó 10 commits atrás). Esta sesión consolida por fast-forward y deja el registro completo en docs. No hay cambios de código en esta fecha, solo documentación y la consolidación de rama.

## Qué cambió respecto a upstream (resumen en una pantalla)

1. **Estabilidad del servicio de accesibilidad**: crash en cambios de configuración tempranos (#270), dock muerto tras crash-reboot del SO (#268, boot receiver con re-enganche a los 30 s), atajos Meta que abrían el menú (#267), método de activación swipe que nunca funcionó en táctil (#264).
2. **Displays secundarios fail-safe (#263)**: selección correcta de display externo real (categoría presentation, no la lista cruda), null-checks, relanzamiento a pantalla principal cuando el sistema rechaza el lanzamiento, en lugar de tumbar el servicio.
3. **Multi-pantalla real**: tiling/resize calculados para el display donde vive la ventana (antes: métricas del display integrado, ventanas gigantes e inutilizables en el monitor), caso fullscreen que aplicaba un Rect vacío.
4. **Modo escritorio en el monitor** («Force desktop mode» automatizado): toggle en Ajustes avanzados que aplica `force_desktop_mode_on_external_displays` + `enable_freeform_support` por permiso propio → Shizuku → root (en ese orden), y «Send to screen» para mover tareas en ejecución entre pantallas.
5. **Infraestructura del fork**: keystore debug fijado en el repo (firmas estables → updates sin desinstalar, sin perder ajustes), workflow de releases automáticas en tags, traducciones de todas las cadenas nuevas a de/es/pa/zh-rCN/zh-rHK/zh-rTW.

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

- `main` — punta consolidada: `517604a` (upstream 1.15.0) + 10 commits de `bugfix/october-2026-bugs` (fast-forward) + commits `docs/` del 2026-11-19.
- `bugfix/october-2026-bugs` — histórico congelado de la jornada 2026-11-07; allí vive el tag `v1.15.0-fork.4`. No pushear encima.
- Tags: `v1.15.0-fork.4` → `0eac1a4` (Release «SmartDock fork v1.15.0-fork.4», APK `SmartDock-fork-debug-v1.15.0-fork.4.apk`).
- Próximas ramas: cortar de `main`, prefijos `bugfix/` (fixes) o `feature/` (funciones). Consolidar a main solo por fast-forward (o merge sin conflicto si upstream se cruza por el medio).

## Qué NO ha hecho el fork (límites actuales)

- No hay renombrado de paquete ni de aplicación: paridad total con upstream para poder absorber sus futuros cambios con merges baratos.
- versionCode sin tocar (1150): una futura absorción de upstream con versión nueva se resuelve en ese momento, subiendo el versionCode del fork por encima solo si upstream no la sube.
- El upstream no tiene PR abierto desde el fork: los fixes de la jornada de octubre son candidatos naturales a upstream (los commits están redactados en inglés con cuerpo de causa raíz para eso), pero no se ha propuesto nada — decisión pendiente del dueño.
