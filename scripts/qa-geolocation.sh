#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
DIR=$(mktemp -d)
trap 'rm -rf "$DIR"' EXIT
javac -encoding UTF-8 -d "$DIR" \
  app/src/main/java/io/github/jok86/pokehabitwrapper/TrustedGeoOrigin.java \
  tests/TrustedGeoOriginTest.java
java -cp "$DIR" io.github.jok86.pokehabitwrapper.TrustedGeoOriginTest
python3 - <<'PY'
from pathlib import Path
main=Path('app/src/main/java/io/github/jok86/pokehabitwrapper/MainActivity.java').read_text()
manifest=Path('app/src/main/AndroidManifest.xml').read_text()
# Source-level wiring assertions; Gradle separately compiles and verifies Android classes.
assert 'TrustedGeoOrigin.accepts(origin,web==null?null:web.getUrl())' in main
assert 'ws.setGeolocationEnabled(true)' in main
assert 'geoSystemPermissionDialogActive=true' in main
assert 'geoSystemPermissionDialogActive=false' in main
assert 'if(geoSystemPermissionDialogActive)' in main
assert 'clearPendingGeolocation(allowed)' in main
assert 'ACCESS_COARSE_LOCATION' in manifest and 'ACCESS_FINE_LOCATION' in manifest
assert "!TRUSTED_ORIGIN.equals(origin)" not in main
print('PASS WebView location permission/source wiring')
PY
