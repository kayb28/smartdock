#!/bin/bash
# Pre-push hook de Smart Dock: dos controles antes de pushear.
# Instalable con scripts/install-pre-push-hook.sh (copia este fichero a
# .git/hooks/pre-push y le da permiso de ejecución).
#
# Control 1 (lección 7 de AGENTS.md, recurrencia 2): un push a main solo
# pasa si sus commits nuevos son alcanzables desde UNA RAMA CON PREFIJO
# (chore/|bugfix/|feature/|feat/|docs/) QUE YA ESTÉ EN ORIGIN — empujar la
# rama primero, main después; así el feed de GitHub muestra «created
# chore/...» y main nunca recibe commits directos. Se salta (una vez, con
# aviso) con SMARTDOCK_SKIP_BRANCH_CHECK=1.
#
# Control 2 (lección 8 de AGENTS.md): si el push toca algo fuera de *.md,
# corre ./gradlew assembleDebug; con SMARTDOCK_FULL=1 añade ./gradlew test
# (decisión de sesión: assembleDebug por defecto por tiempo de build; la
# red de regresión completa la aporta CI en cada push). Se salta el build
# con SMARTDOCK_SKIP_BUILD=1 (entornos sin red de maven) o cuando el push
# solo toca ficheros .md.
set -e

zero=0000000000000000000000000000000000000000

# ---------- Control 1: flujo de ramas para pushes a main ----------
branch_ok=1
while read -r local_ref local_sha remote_ref remote_sha; do
  [ "$remote_ref" = "refs/heads/main" ] || continue
  [ "$local_sha" = "$zero" ] && continue           # delete de main: imposible, pero no bloquear
  prefixed=$(git branch --contains "$local_sha" \
             --format='%(refname:short)' 2>/dev/null \
             | grep -E '^(chore|bugfix|bug|feature|feat|docs)/' || true)
  carrier=""
  for b in $prefixed; do
    # ¿La rama está en origin? Tracking ref si existe (clones con refspec
    # completo); si no, ls-remote --exit-code (autoritativo — clones con
    # refspec restringido no crean tracking refs al pushear ramas nuevas).
    if git rev-parse -q --verify "origin/$b" >/dev/null 2>&1 \
       || git ls-remote --exit-code origin "refs/heads/$b" >/dev/null 2>&1; then
      carrier="$b"
      break
    fi
  done
  if [ -z "$carrier" ]; then
    branch_ok=0
    echo "pre-push: VIOLACIÓN de la lección 7 (AGENTS.md §6): push a main con commits" >&2
    echo "  no alcanzables desde ninguna rama con prefijo ya pusheada a origin." >&2
    echo "  Flujo correcto: crea la rama chore/|bugfix/|feature/, súbelo a origin (el feed" >&2
    echo "  muestra «created chore/...») y SOLO ENTONCES empuja main. La rama que contiene" >&2
    echo "  estos commits localmente:" >&2
    for b in $prefixed; do echo "    - $b (no vista en origin; púsheala primero)" >&2; done
    [ -z "$prefixed" ] && echo "    - (ninguna; el commit se hizo sobre main directamente)" >&2
    exit 1
  else
    echo "pre-push: main viaja desde $carrier (lección 7 OK)"
  fi
done

if [ "${SMARTDOCK_SKIP_BRANCH_CHECK:-0}" = "1" ]; then
  echo "pre-push: AVISO — chequeo de flujo de ramas saltado por SMARTDOCK_SKIP_BRANCH_CHECK=1 (lección 7)"
fi

# ---------- Control 2: build (y tests opcional) antes de pushear código ----------
if [ "${SMARTDOCK_SKIP_BUILD:-0}" = "1" ]; then
  echo "pre-push: build saltado por SMARTDOCK_SKIP_BUILD=1"
  exit 0
fi

code_changed=0
while read -r local_ref local_sha remote_ref remote_sha; do
  [ "$local_sha" = "$zero" ] && continue
  if git diff --name-only "$remote_sha" "$local_sha" 2>/dev/null | grep -qv '\.md$'; then
    code_changed=1
    break
  fi
  # remote_sha inexistente (rama nueva): comparar contra HEAD~ o asumir código
  if ! git cat-file -e "$remote_sha" 2>/dev/null; then
    code_changed=1
    break
  fi
done

if [ "$code_changed" = "0" ]; then
  echo "pre-push: solo documentación, sin build"
  exit 0
fi

echo "pre-push: ./gradlew assembleDebug (lección 8 de AGENTS.md)"
./gradlew assembleDebug
if [ "${SMARTDOCK_FULL:-0}" = "1" ]; then
  echo "pre-push: SMARTDOCK_FULL=1 → ./gradlew test (red de regresión completa)"
  ./gradlew test
fi
