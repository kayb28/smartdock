# AGENTS.md — guía para agentes en este repositorio

_Fork de axel358/smartdock («Smart Dock fork»). Última actualización de esta guía: 2026-11-23._
_La fuente de la verdad para estado y CI es GitHub; el diario operativo está en `docs/REGISTRO.md`._

## 1. Qué es este proyecto

- Fork de **Smart Dock** (upstream [axel358/smartdock](https://github.com/axel358/smartdock)): launcher de modo escritorio para Android — dock, multi-ventana libreform, atajos de teclado, soporte tablet/escritorio. Kotlin + XML views (NO Compose), Gradle Groovy DSL, Views clásico con `AppCompatActivity`. Licencia **GPL-3.0** (cualquier derivado debe mantenerla).
- Identidad del fork: paquete `cu.axel.smartdock` (sin renombrar — paridad con upstream), versionCode estrictamente creciente (1156 / 1.15.6 en `v1.15.6-fork.10`). El fork se distingue por sus **tags de release** (`v1.15.x-fork.N`) y por su keystore debug propio.
- Estructura: `app/src/main/java/cu/axel/smartdock/` con `activities/` (launcher, ajustes), `services/DockService.kt` (el corazón: servicio de accesibilidad), `utils/` (DeviceUtils = displays/Shizuku/root, AppUtils = lanzamiento de apps y bounds), `receivers/`, `fragments/` (preferencias), `widgets/`, `models/`, `dialogs/`, `db/`, `components/`.
- Traducciones mantenidas por el fork: **de, es, pa, zh-rCN, zh-rHK, zh-rTW**. Regla dura: cada cadena nueva en `values/strings.xml` debe ir acompañada de su traducción en los seis, o lint tumba el build (`MissingTranslation` — falló así en `f5409c5`).

## 2. Cómo compilar y probar

- Requisitos: Android SDK + JDK 17 (zulu en CI), Gradle 8.11.2 vía wrapper `./gradlew`. `lintOptions` activos en el build: el target `./gradlew build` corre lint y **aborta** con errores de traducción.
- Comandos de CI: `./gradlew build` (compila + lint) y `./gradlew assembleDebug` (APK debug firmado con el keystore del repo).
- Firma: `signingConfigs.debug` apunta a `debug.keystore` (en el repo, contraseñas `android`). Toda build — local o CI — sale con el mismo cert `4680C231…54B41DB`: **las APKs del fork se actualizan entre sí sin desinstalar**; no pueden actualizarse encima de la oficial de F-Droid (certificado distinto).
- ⚠️ El sandbox del agente NO tiene Android SDK: `./gradlew` no funciona aquí. CI (GitHub Actions) decide si un commit compila. Los tres workflows: `build_project.yml` (push/PR), `build_debug_apk.yml` (dispatch manual, sube artefacto), `release_apk.yml` (tags `v*` + dispatch → GitHub Release con APK).
- Revisar estado de CI por API: `GET /repos/kayb28/smartdock/actions/runs` (token con scope repo en el remote del clone local).

## 3. Ramas

- `main` — punta consolidada del fork: upstream 1.15.0 (`517604a`) + los 10 commits de `bugfix/october-2026-bugs` (fast-forward del 2026-11-19) + documentación + bump v1.15.1 (versionCode 1151) + los 9 commits de `feature/november-2026-improvements` (v1.15.2-fork.6, versionCode 1152) + los 6 commits de `bugfix/november-2026-issues` (v1.15.3-fork.7, versionCode 1153) + 1 commit de `bugfix/wrapper-init-crash` (v1.15.4-fork.8, versionCode 1154) + los 7 commits de `bugfix/secondary-display-accessibility-regression` (v1.15.5-fork.9, versionCode 1155: routing de overlay al display secundario, accesibilidad listada-pero-no-bound, resilience de createViews) + 1 commit de cleanup post-fork.9 (`60fcd72`, plantillas de issues y notas de upstream — directo a main por descuido, ver lección abajo), todo absorbido por fast-forward. + 3 commits de features post-fork.9 (update checker `55ab8ac`, panel de diagnósticos `8f4f61b`, toggle de teclado blando `18fcc9c`) + 2 commits de seguimiento del updater (toggle de consentimiento `c0f3646`, estado del updater en diagnósticos `f25453b`) + 1 commit de tests del parser (`aca3df6`) + 5 commits de docs/chore (`60fcd72`, `030e948`, `b5d7143`, `b74ae5b`, `b0e3430`) + 2 commits de features de la sesión 10 (force-stop `b716860`, selector de pantalla del dock `0049dd0`; rama `feature/dock-placement-killtasks`), todo absorbido por fast-forward + el corte de fork.10: bump 1156/1.15.6/fork_release_tag en `chore/release-fork.10` (ff), tag `v1.15.6-fork.10`.
- `bugfix/october-2026-bugs` — la jornada de bugs 2026-11-07 (10 commits, `f5409c5`→`0eac1a4`). **Consolidada en main por fast-forward; se conserva como histórico** porque es donde vive el tag `v1.15.0-fork.4`. No pushear encima.
- Ramas nuevas: prefijos `bugfix/`, `feature/` o `chore/` (este último para cambios documentales o de proceso que no tocan código de la app), cortadas desde `main`. Convención del fork (heredada de WebLibre): el árbol de feature va siempre por delante de main y main **absorbe por fast-forward**, nunca se descarta trabajo ni se reescribe historial. **Incluso los commits puramente documentales van por una rama `chore/` y se absorben por fast-forward** — main no recibe commits directos (lección de `60fcd72`, que fue directo a main por descuido).
- upstream (`axel358/smartdock`) no está configurado como remote en el clone; para sincronizar: `git remote add upstream https://github.com/axel358/smartdock.git && git fetch upstream` y merge de `upstream/main` en `main` (repo GitHub del fork = fork real, la red «fork» ya está conectada).

## 4. Permisos, adb y Shizuku (lo que hace única a esta app)

- El dock necesita `WRITE_SECURE_SETTINGS` (no concedible a apps normales): concederlo por `adb shell pm grant cu.axel.smartdock android.permission.WRITE_SECURE_SETTINGS`, como app de sistema, o vía **Shizuku**. El README de upstream documenta las tres vías.
- Orden de intento del fork para settings globales (lección de `783e15f`): **permiso propio → Shizuku (`IShizukuService.newProcess`) → root**. Nunca root antes que Shizuku: en instalaciones Shizuku-only el switch de desktop-mode debe funcionar.
- `BootCompletedReceiver` re-engancha el servicio de accesibilidad 30 s tras un crash-reboot (issue #268) — necesita los mismos permisos; sin ellos muestra botón de recuperación en el escritorio y notificación.

## 5. Convenios del fork

- versionCode **estrictamente creciente en cada release** (Android rechaza updates con versionCode ≤ al instalado — lección de WebLibre); releases del fork por tag `v1.15.x-fork.N` y notas de release.
- Commits en inglés (un problema = un commit, con cuerpo explicando causa raíz — el estilo de la jornada de octubre es el estándar a imitar). Documentación interna en español.
- Cada fix de UI de strings: traducir a de/es/pa/zh-rCN/zh-rHK/zh-rTW en el mismo commit (no «luego»): la CI tumba el build si falta una.
- APKs publicadas como GitHub Release (workflow de tags), no como artefacto de Actions (requieren login y caducan).
- `fork_release_tag` (string `translatable="false"` en `values/strings.xml`) es la fuente de la verdad del tag de release del build; **bump obligatorio en cada release** junto a `versionCode`/`versionName` — `UpdateChecker` la compara contra `releases/latest`.
- **Próximo release (fork.11)** — patrón fijado en fork.10 (2026-11-23, el primero con `fork_release_tag` en juego): bump simultáneo en el mismo commit de `versionCode` (1156 → 1157), `versionName` (1.15.6 → 1.15.7) y `fork_release_tag` (`v1.15.6-fork.10` → `v1.15.7-fork.11`), siguiendo el patrón versionCode 115X ↔ versionName 1.15.X ↔ tag `v1.15.X-fork.N`. Tras el bump, verificar que el updater reconoce su propia release: `isNewer("v1.15.7-fork.11", "v1.15.6-fork.10")` debe ser `true` y una build publicada debe verse «up to date» (`isNewer` devuelve `false` ante tags iguales; cubierto por `UpdateCheckerTest`).

## 6. Lecciones (fallas ya pagadas — no repetir)

1. **lint MissingTranslation tumba `./gradlew build`**: cadena nueva sin traducir = build rota (falló en `f5409c5`, arreglada en `874fafb`). Regla del §5.
2. **AAPT y apóstrofes/escapes**: apóstrofe crudo en `values-pa` = error de compilación (`7bf1bf7`); `\u` mal formado en punjabi = `Invalid unicode escape sequence` (`62ee704`). Revisar escapes al tocar cadenas.
3. **Solo API pública**: `DISPLAY_CATEGORY_EXTERNAL` no es pública — el código que la usaba compila a medias y falla en CI (`Unresolved reference`, `7bf1bf7`). Usar `DisplayManager.DISPLAY_CATEGORY_PRESENTATION` (pública desde API 17; así está en `DeviceUtils.kt:321`).
4. **Keystore por runner = firma inestable**: sin keystore fijo, cada APK de CI tiene firma distinta y update exige desinstalar (perdiendo ajustes). Por eso `debug.keystore` vive en el repo (`43a3b24`).
5. **`GITHUB_TOKEN` nace read-only en repos nuevos**: workflow que crea Releases necesita `permissions: contents: write` explícito. Aquí ya estaba (la lección llegó de MihonFix y WidgetCraft).
6. **No presumir el display**: en OEMs la lista de displays incluye virtuales privados; solo la categoría *presentation* lista los displays donde una app normal puede lanzar. `getSecondaryDisplay()` devuelve null y los callers caen a la principal (`62ee704`).
7. **Main no recibe commits directos, ni siquiera docs**: la convención (§3) es rama `chore/` → fast-forward a main. `60fcd72` (cleanup post-fork.9) fue directo a main por descuido — no repetir; los commits puramente documentales también van por `chore/` y se absorben por fast-forward, igual que los de código.

## 7. Documentación interna (leer antes de tocar nada)

- `docs/REGISTRO.md` — diario operativo: todo cambio con fecha UTC, motivo, build, estado.
- `docs/HISTORIA.md` — narrativa consolidada y mapa de documentos/ramas.
- `FORK.md` — registro público del fork, build por build (estilo WebLibre).
- `CHANGELOG-FORK.md` — changelog formal por release del fork.
- `AGENTS.md` — este archivo.
