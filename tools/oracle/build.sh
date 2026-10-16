#!/usr/bin/env bash
# Builds the test oracle: Chocolate Doom 3.1.1 + tools/oracle/chocolate-trace.patch.
#
#   tools/oracle/build.sh
#
# Environment:
#   ORACLE_WORK   work directory (default: <script dir>/work). It holds
#                   chocolate-doom/   the source clone (cloned from ORACLE_REPO if missing)
#                   build/            the CMake build tree; binary: build/src/chocolate-doom
#                   applied.patch     copy of the patch currently applied to the clone
#   ORACLE_PATCH  patch to apply (default: <script dir>/chocolate-trace.patch)
#   ORACLE_REPO   repository to clone (default: the official GitHub repository)
#   ORACLE_JOBS   parallel build jobs (default: number of CPUs)
#
# The patch is applied with patch(1), not git, so the clone can also be a plain source tree.
# Re-running is cheap and idempotent: when the patch changes, the previously applied copy
# (applied.patch) is reverted first and the new one is applied.
#
# Development workflow: edit the clone, then regenerate the patch from inside the clone with
#   git add -A && git diff --cached --no-color --no-ext-diff > <repo>/tools/oracle/chocolate-trace.patch
# and copy it to applied.patch (or simply re-run this script: it detects that the tree already
# contains the patch).
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
WORK=${ORACLE_WORK:-$SCRIPT_DIR/work}
PATCH=${ORACLE_PATCH:-$SCRIPT_DIR/chocolate-trace.patch}
REPO=${ORACLE_REPO:-https://github.com/chocolate-doom/chocolate-doom.git}
TAG=chocolate-doom-3.1.1
JOBS=${ORACLE_JOBS:-$( (sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4) | head -n 1)}

SRC=$WORK/chocolate-doom
BUILD=$WORK/build
STAMP=$WORK/applied.patch

log() { printf 'oracle-build: %s\n' "$*" >&2; }

[ -f "$PATCH" ] || { log "patch not found: $PATCH"; exit 1; }
mkdir -p "$WORK"

if [ ! -d "$SRC" ]; then
    log "cloning $REPO ($TAG) into $SRC"
    git clone --quiet --depth 1 --branch "$TAG" "$REPO" "$SRC"
    rm -f "$STAMP"
fi

apply_patch() {
    if patch -d "$SRC" -p1 --forward --dry-run --silent < "$PATCH" > /dev/null 2>&1; then
        patch -d "$SRC" -p1 --forward --silent < "$PATCH"
        log "applied $(basename "$PATCH")"
    elif patch -d "$SRC" -p1 -R --dry-run --silent < "$PATCH" > /dev/null 2>&1; then
        # The tree already contains exactly these changes (typical while developing in the clone).
        log "source tree already contains the patch"
    else
        log "the patch does not apply to $SRC (and is not already applied)."
        log "restore a pristine $TAG tree there (or delete it to re-clone) and run again."
        exit 1
    fi
    cp "$PATCH" "$STAMP"
}

if [ -f "$STAMP" ] && cmp -s "$STAMP" "$PATCH"; then
    log "patch is up to date"
else
    if [ -f "$STAMP" ]; then
        log "reverting the previously applied patch"
        if ! patch -d "$SRC" -p1 -R -E --silent < "$STAMP"; then
            log "could not revert $STAMP; restore a pristine $TAG tree in $SRC and run again"
            exit 1
        fi
        rm -f "$STAMP"
    fi
    apply_patch
fi

if [ ! -f "$BUILD/CMakeCache.txt" ]; then
    log "configuring $BUILD"
    # -fwrapv: signed overflow wraps as in the DOS executable (and in Java);
    # optional libraries are disabled so the build is the same on every machine.
    cmake -S "$SRC" -B "$BUILD" \
        -DCMAKE_BUILD_TYPE=RelWithDebInfo \
        -DCMAKE_C_FLAGS="-fwrapv" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DENABLE_SDL2_MIXER=OFF \
        -DENABLE_SDL2_NET=OFF \
        -DCMAKE_DISABLE_FIND_PACKAGE_PNG=ON \
        -DCMAKE_DISABLE_FIND_PACKAGE_SampleRate=ON \
        -DCMAKE_DISABLE_FIND_PACKAGE_FluidSynth=ON \
        > "$WORK/configure.log" 2>&1 || { cat "$WORK/configure.log" >&2; exit 1; }
fi

log "building chocolate-doom ($JOBS jobs)"
cmake --build "$BUILD" --target chocolate-doom -j "$JOBS" > "$WORK/build.log" 2>&1 \
    || { tail -n 60 "$WORK/build.log" >&2; exit 1; }

BIN=$BUILD/src/chocolate-doom
[ -x "$BIN" ] || { log "binary missing: $BIN"; exit 1; }
log "ok: $BIN"
