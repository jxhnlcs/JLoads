#!/usr/bin/env sh
# Gera o pacote distribuível: jar único (API + interface) + scripts de inicialização.
# Uso: scripts/build-release.sh 1.0.0
set -eu

VERSION="${1:?Uso: scripts/build-release.sh <versao, ex.: 1.0.0>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
BUNDLE="$DIST/jloads-$VERSION"

echo "==> Construindo a interface (Angular)"
cd "$ROOT/frontend"
npm ci --no-audit --no-fund
npx ng build --configuration production
[ -f "$ROOT/frontend/dist/jloads/browser/index.html" ] || { echo "Build do frontend não gerou index.html"; exit 1; }

echo "==> Construindo o jar com a interface embutida"
cd "$ROOT/backend"
sh mvnw -B -ntp -Pbundle "-Drevision=$VERSION" -DskipTests package

echo "==> Montando o pacote"
rm -rf "$BUNDLE" "$DIST/jloads-$VERSION.zip"
mkdir -p "$BUNDLE/config"
cp "$ROOT/backend/target/jloads-$VERSION.jar" "$BUNDLE/"
cp "$ROOT/backend/target/jloads-$VERSION.jar" "$DIST/"
cp "$ROOT/scripts/jloads.cmd" "$ROOT/scripts/jloads.sh" "$ROOT/README.md" "$ROOT/LICENSE" "$BUNDLE/"
cp "$ROOT/scripts/application.example.yml" "$BUNDLE/config/application.yml.example"
chmod +x "$BUNDLE/jloads.sh"

cd "$DIST"
zip -qr "jloads-$VERSION.zip" "jloads-$VERSION"
sha256sum "jloads-$VERSION.zip" "jloads-$VERSION.jar" > SHA256SUMS.txt

echo "Pacote gerado em $DIST"
