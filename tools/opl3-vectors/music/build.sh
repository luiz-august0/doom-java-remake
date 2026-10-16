#!/bin/sh
# Builds tools/opl3-vectors/music/musicref (the reference harness around Chocolate Doom's original OPL music code)
# into $OUT (default: <repo>/target/audio-refs/musicref) and prints the path.
set -e
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../../.." && pwd)
doom_home=${DOOM_HOME:-/Users/luizaugustomarques/Documents/projetos/doom-java}
choc=${CHOCOLATE_SRC:-$doom_home/tools/oracle/work/chocolate-doom}
sdl=${SDL2_INCLUDE:-/opt/homebrew/include/SDL2}
out=${OUT:-$repo/target/audio-refs/musicref}
mkdir -p "$(dirname "$out")"
clang -O1 -w -DHAVE_CONFIG_H \
  -I"$doom_home/tools/oracle/work/build" -I"$choc/src" -I"$choc/opl" -I"$sdl" \
  -o "$out" \
  "$here/musicref.c" "$here/opl_harness.c" \
  "$choc/src/i_oplmusic.c" "$choc/src/mus2mid.c" "$choc/src/midifile.c" "$choc/src/memio.c" \
  "$choc/opl/opl3.c" "$choc/opl/opl_queue.c"
echo "$out"
