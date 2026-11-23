#!/bin/bash
# Instala el pre-push hook en este clone (lección 8 de AGENTS.md).
# Uso: ./scripts/install-pre-push-hook.sh
set -e
cd "$(dirname "$0")/.."
cp scripts/pre-push-assembledebug.sh .git/hooks/pre-push
chmod +x .git/hooks/pre-push
echo "pre-push hook instalado: (1) push a main exige rama con prefijo ya en origin (lección 7); (2) assembleDebug antes de push de código (lección 8; SMARTDOCK_FULL=1 añade test; SMARTDOCK_SKIP_BUILD=1 salta el build en entornos sin red de maven)."
