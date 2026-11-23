#!/bin/bash
# Instala el pre-push hook en este clone (lección 8 de AGENTS.md).
# Uso: ./scripts/install-pre-push-hook.sh
set -e
cd "$(dirname "$0")/.."
cp scripts/pre-push-assembledebug.sh .git/hooks/pre-push
chmod +x .git/hooks/pre-push
echo "pre-push hook instalado: ./gradlew assembleDebug antes de cada push de código (salta pushes solo-.md; SMARTDOCK_SKIP_BUILD=1 lo desactiva una vez)."
