#!/usr/bin/env bash
# Runs the patched Chocolate Doom oracle on a demo with a pinned configuration.
#
#   tools/oracle/run.sh <iwad> <demo lump | file.lmp> [extra oracle/Chocolate arguments...]
#
#   <iwad>   path to an IWAD, or a bare name such as doom1 / freedoom1.wad (searched in
#            $DOOM_IWAD_DIR, default <repo>/.iwads when run from the main tree)
#   <demo>   a lump name (DEMO1) or the path of a .lmp file (version 109 demos only)
#
# Examples:
#   tools/oracle/run.sh doom1 DEMO1 -trace /tmp/doom1-demo1.trace.gz
#   tools/oracle/run.sh freedoom2 DEMO3 -trace t.gz -dumpframe 0,35,70 /tmp/frames -dumpdefs /tmp/defs.txt
#
# Behaviour:
#   * a fresh temporary HOME and configuration per run (parallel runs never share files);
#   * pinned configuration (docs/contracts.md section 7), SDL dummy video and audio drivers, -nogui;
#   * runs with -timedemo: the run ends through I_Error and exit status 255 is SUCCESS here.
#     run.sh itself exits 0 in that case, and with the real status (or 1) otherwise;
#   * sound is NOT disabled (-nosound would skip the S_StartSound code path and change the
#     misc RNG sequence); the dummy audio driver is used instead;
#   * -nodraw is never used (it would skip D_Display and the wipes);
#   * -nodeh is passed through when given (the Freedoom DEHACKED lump is otherwise applied).
#
# Environment:
#   DOOM_ORACLE     oracle binary (default: see oracle-env.sh: work/build/src/chocolate-doom)
#   DOOM_IWAD_DIR   IWAD directory for bare names (default: see oracle-env.sh)
#   ORACLE_KEEP=1   keep the temporary HOME (its path is printed to stderr)
#   ORACLE_LOG=file write the oracle's console output there (default: discarded unless it fails)
set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=oracle-env.sh
. "$SCRIPT_DIR/oracle-env.sh"
ORACLE=$DOOM_ORACLE

if [ $# -lt 2 ]; then
    sed -n '2,/^set -uo/p' "${BASH_SOURCE[0]}" | sed '$d' | sed 's/^# \{0,1\}//' >&2
    exit 2
fi
IWAD=$1
DEMO=$2
shift 2

[ -x "$ORACLE" ] || { echo "run.sh: oracle binary not found: $ORACLE (run tools/oracle/build.sh)" >&2; exit 2; }

if [ ! -f "$IWAD" ]; then
    DIR=$DOOM_IWAD_DIR
    case $IWAD in
        *.wad|*.WAD) CAND=$DIR/$IWAD ;;
        *) CAND=$DIR/$IWAD.wad ;;
    esac
    [ -f "$CAND" ] || { echo "run.sh: IWAD not found: $IWAD (also tried $CAND)" >&2; exit 2; }
    IWAD=$CAND
fi
IWAD=$(cd "$(dirname "$IWAD")" && pwd)/$(basename "$IWAD")

# A demo file path must be absolute because the process runs in a temporary directory.
case $DEMO in
    *.lmp|*.LMP)
        [ -f "$DEMO" ] || { echo "run.sh: demo file not found: $DEMO" >&2; exit 2; }
        DEMO=$(cd "$(dirname "$DEMO")" && pwd)/$(basename "$DEMO") ;;
esac

# Relative output paths in the extra arguments must survive the cd into the temporary directory:
# make the values of the file-writing flags absolute.
ARGS=()
while [ $# -gt 0 ]; do
    case $1 in
        -trace|-traceverbose|-dumpdefs)
            ARGS+=("$1")
            shift
            if [ $# -gt 0 ]; then
                case $1 in /*) ARGS+=("$1") ;; *) ARGS+=("$PWD/$1") ;; esac
                shift
            fi ;;
        -dumpframe)
            ARGS+=("$1")
            shift
            if [ $# -ge 2 ]; then
                ARGS+=("$1")
                shift
                mkdir -p "$1"
                ABS=$(cd "$1" && pwd)
                ARGS+=("$ABS")
                shift
            fi ;;
        *) ARGS+=("$1"); shift ;;
    esac
done

TMP=$(mktemp -d "${TMPDIR:-/tmp}/oracle-run.XXXXXX")
cleanup() { [ "${ORACLE_KEEP:-0}" = 1 ] && echo "run.sh: kept $TMP" >&2 || rm -rf "$TMP"; }
trap cleanup EXIT

mkdir -p "$TMP/home" "$TMP/save"
cat > "$TMP/pinned.cfg" <<EOF
show_endoom 0
show_diskicon 0
screenblocks 10
detaillevel 0
fullscreen 0
sfx_volume 8
music_volume 8
force_software_renderer 1
smooth_pixel_scaling 0
usegamma 0
show_messages 1
autoadjust_video_settings 0
window_width 640
window_height 480
vanilla_keyboard_mapping 0
grabmouse 0
EOF

LOG=${ORACLE_LOG:-$TMP/console.log}
(
    cd "$TMP" || exit 99
    HOME="$TMP/home" XDG_DATA_HOME="$TMP/home/.local/share" XDG_CONFIG_HOME="$TMP/home/.config" \
    SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy \
    "$ORACLE" -iwad "$IWAD" -config "$TMP/pinned.cfg" -extraconfig "$TMP/pinned.cfg" \
        -savedir "$TMP/save" -nogui -timedemo "$DEMO" "${ARGS[@]+"${ARGS[@]}"}" > "$LOG" 2>&1
)
STATUS=$?

if [ "$STATUS" -eq 255 ]; then
    exit 0
fi
echo "run.sh: oracle exited with status $STATUS" >&2
tail -n 20 "$LOG" >&2
[ "$STATUS" -eq 0 ] && exit 1
exit "$STATUS"
