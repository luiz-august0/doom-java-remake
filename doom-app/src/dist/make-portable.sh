#!/usr/bin/env bash
# Builds the portable zips of the dist profile (called by exec-maven-plugin after jlink and jpackage; ADR-023):
#
#   target/dist/portable/DoomJava-<version>-<os>-portable.zip   jlink runtime + iwads/doom1.wad + launcher
#   target/dist/portable/DoomJava-<version>-<os>-jar.zip        fat jar + iwads/doom1.wad (needs a JDK 25)
#
# Usage: make-portable.sh <doom-app directory> <version>
# Needs bash, and `zip` on macOS / Linux (on Windows the JDK's `jar` tool writes the zip).
set -euo pipefail

APP="$(cd "$1" && pwd)"
VERSION="$2"
ROOT="$(cd "$APP/.." && pwd)"
DIST="$APP/target/dist"
RUNTIME="$DIST/runtime/classifiers/runtime"
OUT="$DIST/portable"

case "$(uname -s)" in
  Darwin) OS=macos ;;
  Linux) OS=linux ;;
  MINGW*|MSYS*|CYGWIN*) OS=windows ;;
  *) echo "make-portable: unsupported OS $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  arm64|aarch64) ARCH=arm64 ;;
  *) ARCH=x64 ;;
esac
if [[ "$OS" == windows && "${PROCESSOR_ARCHITECTURE:-}" == "ARM64" ]]; then ARCH=arm64; fi
LABEL="${DOOM_PORTABLE_LABEL:-$OS-$ARCH}"

[[ -d "$RUNTIME" ]] || { echo "make-portable: $RUNTIME missing (run the jlink step first)" >&2; exit 1; }
FAT_JAR="$(ls "$APP"/target/doom-app-*-all.jar | head -n 1)"
[[ -f "$ROOT/.iwads/doom1.wad" ]] || { echo "make-portable: $ROOT/.iwads/doom1.wad missing" >&2; exit 1; }

zip_dir() { # zip_dir <parent> <folder> <zip file>
  local parent="$1" folder="$2" zip_file="$3"
  rm -f "$zip_file"
  if command -v zip >/dev/null 2>&1; then
    (cd "$parent" && zip -q -r -y "$zip_file" "$folder")
  else
    # no zip tool (Windows): the JDK's jar writes a plain zip; Unix permissions are not needed there
    jar --create --no-manifest --file "$zip_file" -C "$parent" "$folder"
  fi
}

copy_common() { # copy_common <destination folder>
  local dest="$1"
  mkdir -p "$dest/iwads"
  cp "$ROOT/.iwads/doom1.wad" "$dest/iwads/doom1.wad"
  cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$dest/"
  cat >"$dest/iwads/README.txt" <<'TXT'
doom1.wad is the shareware episode of DOOM (v1.9) by id Software. It is redistributed unmodified under the
shareware terms: it may be copied freely as long as it is not altered and not sold on its own.

To play something else, put another IWAD (doom2.wad, freedoom1.wad, ...) in this folder, or start the game with
-iwad <file>. Free alternatives: Freedoom, https://freedoom.github.io/
TXT
}

rm -rf "$OUT"
mkdir -p "$OUT"

# ---- portable app: runtime image + launcher ----
NAME="DoomJava-$VERSION-$LABEL"
STAGE="$OUT/stage"
PKG="$STAGE/$NAME"
mkdir -p "$PKG"
cp -R "$RUNTIME" "$PKG/runtime"
copy_common "$PKG"

NATIVE_ACCESS="--enable-native-access=org.lwjgl,org.lwjgl.glfw,org.lwjgl.opengl"
case "$OS" in
  macos|linux)
    if [[ "$OS" == macos ]]; then LAUNCHER="doom-java.command"; else LAUNCHER="doom-java.sh"; fi
    {
      echo '#!/usr/bin/env bash'
      echo '# Starts DOOM Java. Extra arguments go to the game, for example: ./'"$LAUNCHER"' -iwad freedoom2.wad -warp 1'
      echo 'DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"'
      if [[ "$OS" == macos ]]; then
        echo '# Files downloaded with a browser are quarantined by macOS; this lets the bundled runtime start.'
        echo 'xattr -dr com.apple.quarantine "$DIR" 2>/dev/null || true'
        echo 'FIRST_THREAD=-XstartOnFirstThread'
      else
        echo 'FIRST_THREAD='
      fi
      echo 'export DOOM_APP_DIR="$DIR"'
      echo 'exec "$DIR/runtime/bin/java" $FIRST_THREAD '"$NATIVE_ACCESS"' -m doom.app/doom.app.Main "$@"'
    } >"$PKG/$LAUNCHER"
    chmod 755 "$PKG/$LAUNCHER"
    ;;
  windows)
    {
      printf '@echo off\r\n'
      printf 'rem Starts DOOM Java. Extra arguments go to the game, for example: doom-java.bat -iwad freedoom2.wad -warp 1\r\n'
      printf 'set "DOOM_APP_DIR=%%~dp0"\r\n'
      printf '"%%~dp0runtime\\bin\\java.exe" %s -m doom.app/doom.app.Main %%*\r\n' "$NATIVE_ACCESS"
      printf 'if errorlevel 1 pause\r\n'
    } >"$PKG/doom-java.bat"
    ;;
esac
zip_dir "$STAGE" "$NAME" "$OUT/$NAME-portable.zip"
rm -rf "$STAGE"

# ---- fat jar + doom1.wad ----
JAR_NAME="DoomJava-$VERSION-$LABEL-jar"
STAGE="$OUT/stage"
PKG="$STAGE/$JAR_NAME"
mkdir -p "$PKG"
cp "$FAT_JAR" "$PKG/doom-java.jar"
copy_common "$PKG"
cat >"$PKG/RUN.txt" <<TXT
Needs a JDK or JRE 25. This jar contains the LWJGL natives for $LABEL only.

  java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar doom-java.jar [options]
  macOS: add -XstartOnFirstThread before -jar

The bundled iwads/doom1.wad is found automatically.
TXT
zip_dir "$STAGE" "$JAR_NAME" "$OUT/$JAR_NAME.zip"
rm -rf "$STAGE"

echo "make-portable: wrote"
ls -l "$OUT"
