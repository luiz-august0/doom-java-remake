#!/usr/bin/env bash
# Container entrypoint. Arguments are passed to the game (e.g. -iwad freedoom2.wad -warp 1).
#
# Environment:
#   DOOM_MODE       browser (default) | x11
#   DOOM_GEOMETRY   browser mode: virtual screen and game window size (default 1280x800)
#   DOOM_SOUND      browser mode: 1 starts the game with its audio enabled (there is no audio device by default,
#                   the game then falls back to silence); default 0 passes -nosound
#   DOOM_JAVA_OPTS  extra JVM options
#
# browser: Xvfb (virtual X server) + x11vnc + noVNC (websockify) on port 6080; OpenGL is Mesa's software rasteriser.
# x11:     uses the host's X server (DISPLAY, /tmp/.X11-unix mounted by the caller), /dev/dri for GL acceleration when
#          present, and the host's PulseAudio (PULSE_SERVER, bridged from ALSA through ~/.asoundrc).
#
# When the game exits the container exits.
set -euo pipefail

JAR=/opt/doom-java/doom-java.jar
MODE="${DOOM_MODE:-browser}"
mkdir -p "$HOME" 2>/dev/null || true
# Mesa and friends warn when XDG_RUNTIME_DIR is missing.
if [[ -z "${XDG_RUNTIME_DIR:-}" ]]; then
  export XDG_RUNTIME_DIR="/tmp/runtime-$(id -u)"
  mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"
fi

java_cmd=(java
  --enable-native-access=ALL-UNNAMED
  --sun-misc-unsafe-memory-access=allow
  ${DOOM_JAVA_OPTS:-}
  -jar "$JAR")

wait_for_x() {
  local display_num="$1" i
  for i in $(seq 1 100); do
    [[ -S "/tmp/.X11-unix/X${display_num}" ]] && return 0
    sleep 0.1
  done
  echo "doom-entrypoint: X server :${display_num} did not start" >&2
  return 1
}

case "$MODE" in
  browser)
    GEOMETRY="${DOOM_GEOMETRY:-1280x800}"
    if ! [[ "$GEOMETRY" =~ ^([0-9]+)x([0-9]+)$ ]]; then
      echo "doom-entrypoint: DOOM_GEOMETRY must look like 1280x800, got '$GEOMETRY'" >&2
      exit 2
    fi
    WIDTH="${BASH_REMATCH[1]}"
    HEIGHT="${BASH_REMATCH[2]}"

    export DISPLAY=:99
    export LIBGL_ALWAYS_SOFTWARE=1
    export GALLIUM_DRIVER=llvmpipe

    Xvfb :99 -screen 0 "${GEOMETRY}x24" +extension GLX +render -noreset -nolisten tcp >/tmp/xvfb.log 2>&1 &
    wait_for_x 99

    x11vnc -display :99 -nopw -forever -shared -noxdamage -rfbport 5900 -localhost -quiet >/tmp/x11vnc.log 2>&1 &
    websockify --web /usr/share/novnc 6080 localhost:5900 >/tmp/websockify.log 2>&1 &

    echo "doom-java: open http://localhost:6080 in a browser"

    sound_args=(-nosound)
    [[ "${DOOM_SOUND:-0}" == "1" ]] && sound_args=()
    exec "${java_cmd[@]}" -window -width "$WIDTH" -height "$HEIGHT" "${sound_args[@]}" "$@"
    ;;

  x11)
    if [[ -z "${DISPLAY:-}" ]]; then
      echo "doom-entrypoint: DOOM_MODE=x11 needs DISPLAY (and the X11 socket mounted); see bin/doom-docker --x11" >&2
      exit 2
    fi
    # Use the GPU when the host exposes one (docker run --device /dev/dri); otherwise Mesa falls back to software.
    if [[ ! -e /dev/dri ]]; then
      export LIBGL_ALWAYS_SOFTWARE=1
    fi
    # ALSA -> PulseAudio bridge for the JDK's Java Sound (libasound2-plugins).
    if [[ -n "${PULSE_SERVER:-}" ]]; then
      cat >"$HOME/.asoundrc" <<'ASOUND'
pcm.!default { type pulse }
ctl.!default { type pulse }
ASOUND
    fi
    exec "${java_cmd[@]}" "$@"
    ;;

  *)
    echo "doom-entrypoint: unknown DOOM_MODE '$MODE' (use browser or x11)" >&2
    exit 2
    ;;
esac
