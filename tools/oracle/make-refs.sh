#!/usr/bin/env bash
# Regenerates every oracle reference data set.
#
#   tools/oracle/make-refs.sh [all | traces | defs] [iwad[:DEMOn] ...]
#
# Output:
#   testdata/traces/<iwad>-<demo>.trace.gz         trace v1 of every IWAD demo (committed)
#   $ORACLE_WORK/cache/frames/<iwad>-<demo>/       raw frames + scene sidecars (git-ignored)
#   $ORACLE_WORK/cache/defs/<iwad>.txt             -dumpdefs per IWAD (+ <iwad>-nodeh.txt)
#   $ORACLE_WORK/cache/summary.txt                 one line per demo: tics, trace lines, determinism, notes
#
# For every demo the oracle runs twice, in parallel with the other demos (ORACLE_JOBS, default 4):
# run 1 writes the trace, run 2 writes a second trace and the frame dump; the two traces must be
# byte-identical (this is the determinism check, under parallel load). The number of trace lines is
# compared with the tic count of the demo lump.
#
# Without arguments everything is regenerated for the four IWADs doom1, freedoom1, freedoom2, freedm.
# `iwad` or `iwad:DEMO2` arguments restrict the demo set.
set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=oracle-env.sh
. "$SCRIPT_DIR/oracle-env.sh"

UTIL="python3 -I $SCRIPT_DIR/oracle-util.py"
TRACES=${ORACLE_TRACES_DIR:-$REPO_ROOT/testdata/traces}
CACHE=$ORACLE_WORK/cache
JOBS=${ORACLE_JOBS:-4}
FRAMES=${ORACLE_FRAME_COUNT:-12}

# ---- one demo ------------------------------------------------------------------------------
if [ "${1:-}" = "--one" ]; then
    IWAD=$2
    DEMO=$3
    NAME=$IWAD-$(echo "$DEMO" | tr 'A-Z' 'a-z')
    WAD=$DOOM_IWAD_DIR/$IWAD.wad
    TMP=$(mktemp -d "${TMPDIR:-/tmp}/oracle-refs.XXXXXX")
    trap 'rm -rf "$TMP"' EXIT
    mkdir -p "$CACHE/results" "$CACHE/frames" "$TRACES"
    EXPECTED=$($UTIL demo-tics "$WAD" "$DEMO")
    NOTES=""

    ORACLE_LOG=$TMP/log1 "$SCRIPT_DIR/run.sh" "$WAD" "$DEMO" -trace "$TMP/t1.trace.gz" || NOTES="$NOTES run1-failed;"
    TICS=$($UTIL pick-tics "$TMP/t1.trace.gz" "$FRAMES")
    rm -rf "$CACHE/frames/$NAME"
    ORACLE_LOG=$TMP/log2 "$SCRIPT_DIR/run.sh" "$WAD" "$DEMO" -trace "$TMP/t2.trace.gz" \
        -dumpframe "$TICS" "$CACHE/frames/$NAME" || NOTES="$NOTES run2-failed;"

    DET=ok
    cmp -s "$TMP/t1.trace.gz" "$TMP/t2.trace.gz" || DET=DIFFERENT
    LINES=$(gzip -dc "$TMP/t1.trace.gz" 2>/dev/null | grep -vc '^#')
    if [ "$LINES" != "$EXPECTED" ]; then NOTES="$NOTES line-count-mismatch;"; fi
    if ! grep -q "^timed $EXPECTED gametics" "$TMP/log1" 2>/dev/null; then
        NOTES="$NOTES console: $(tail -n 1 "$TMP/log1" | tr -d '\n');"
    fi
    NFRAMES=$(ls "$CACHE/frames/$NAME" 2>/dev/null | grep -c '\.raw$')
    cp "$TMP/t1.trace.gz" "$TRACES/$NAME.trace.gz"
    printf '%-18s demo_tics=%-6s trace_lines=%-6s deterministic=%-9s frames=%-3s frame_tics=%s %s\n' \
        "$NAME" "$EXPECTED" "$LINES" "$DET" "$NFRAMES" "$TICS" "$NOTES" > "$CACHE/results/$NAME.txt"
    exit 0
fi

# ---- one defs dump -------------------------------------------------------------------------
if [ "${1:-}" = "--defs" ]; then
    IWAD=$2
    WAD=$DOOM_IWAD_DIR/$IWAD.wad
    FIRST=$($UTIL demos "$WAD" | head -n 1 | cut -d' ' -f1)
    mkdir -p "$CACHE/defs"
    "$SCRIPT_DIR/run.sh" "$WAD" "$FIRST" -dumpdefs "$CACHE/defs/$IWAD.txt" || echo "defs failed: $IWAD" >&2
    "$SCRIPT_DIR/run.sh" "$WAD" "$FIRST" -nodeh -dumpdefs "$CACHE/defs/$IWAD-nodeh.txt" \
        || echo "defs -nodeh failed: $IWAD" >&2
    exit 0
fi

# ---- driver --------------------------------------------------------------------------------
WHAT=all
case ${1:-} in all|traces|defs) WHAT=$1; shift ;; esac
SELECT=("$@")
[ ${#SELECT[@]} -gt 0 ] || SELECT=(doom1 freedoom1 freedoom2 freedm)

[ -x "$DOOM_ORACLE" ] || { echo "make-refs: oracle binary missing: $DOOM_ORACLE (run build.sh)" >&2; exit 2; }

TASKS=()
IWADS_USED=()
for sel in "${SELECT[@]}"; do
    IWAD=${sel%%:*}
    IWADS_USED+=("$IWAD")
    if [ "$sel" != "$IWAD" ]; then
        TASKS+=("$IWAD ${sel#*:}")
    else
        for d in $($UTIL demos "$DOOM_IWAD_DIR/$IWAD.wad" | cut -d' ' -f1); do
            TASKS+=("$IWAD $d")
        done
    fi
done

mkdir -p "$CACHE/results"
if [ "$WHAT" = all ] || [ "$WHAT" = traces ]; then
    printf '%s\n' "${TASKS[@]}" | xargs -P "$JOBS" -L 1 "$0" --one
fi
if [ "$WHAT" = all ] || [ "$WHAT" = defs ]; then
    printf '%s\n' "${IWADS_USED[@]}" | xargs -P "$JOBS" -L 1 "$0" --defs
fi

cat "$CACHE"/results/*.txt > "$CACHE/summary.txt" 2>/dev/null
cat "$CACHE/summary.txt"
if grep -qE 'DIFFERENT|failed|mismatch' "$CACHE/summary.txt"; then
    echo "make-refs: problems found (see above)" >&2
    exit 1
fi
