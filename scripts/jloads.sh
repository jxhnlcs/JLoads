#!/usr/bin/env sh
# Inicia o JLoads a partir da pasta do pacote baixado (macOS e Linux).
set -eu
cd "$(dirname "$0")"

JAVA_EXE="java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
fi

if ! command -v "$JAVA_EXE" >/dev/null 2>&1; then
  echo "[JLoads] Java 21 ou superior não foi encontrado."
  echo "         macOS:          brew install --cask temurin@21"
  echo "         Ubuntu/Debian:  sudo apt install openjdk-21-jre"
  exit 1
fi

JAR=$(ls jloads-*.jar 2>/dev/null | head -n 1 || true)
if [ -z "$JAR" ]; then
  echo "[JLoads] Arquivo jloads-*.jar não encontrado nesta pasta."
  exit 1
fi

echo "[JLoads] Iniciando. O navegador abre sozinho quando estiver pronto (Ctrl+C para encerrar)."
exec "$JAVA_EXE" -jar "$JAR" --app.open-browser=true "$@"
