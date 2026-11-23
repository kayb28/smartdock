#!/bin/bash
# Pre-push hook de Smart Dock: compila antes de pushear código.
# Instalable con scripts/install-pre-push-hook.sh (copia este fichero a
# .git/hooks/pre-push y le da permiso de ejecución). Se salta con
# SMARTDOCK_SKIP_BUILD=1 o cuando el push solo toca ficheros .md
# (la lección 8 de AGENTS.md nació de dos features seguidas pusheadas
# sin compilar: df4085d/9f0f96e/51be96a y e3a12bc/968b4e4).
set -e

if [ "${SMARTDOCK_SKIP_BUILD:-0}" = "1" ]; then
  echo "pre-push: build saltado por SMARTDOCK_SKIP_BUILD=1"
  exit 0
fi

# ¿El push toca algo fuera de *.md? Mirar los commits que salen (stdin del
# hook: <local-ref> <local-sha> <remote-ref> <remote-sha>); 0 000... = delete.
code_changed=0
zero=0000000000000000000000000000000000000000
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
