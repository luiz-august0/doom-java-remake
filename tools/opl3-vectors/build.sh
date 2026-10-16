#!/bin/sh
# Builds the C harness against the original Chocolate Doom opl3.c and (re)generates the gzipped reference vectors
# used by doom-sound's Opl3VectorsTest.
#
#   tools/opl3-vectors/build.sh            # regenerate doom-sound/src/test/resources/opl3/vectors.txt.gz
#
# Environment: DOOM_HOME (main tree, default /Users/luizaugustomarques/Documents/projetos/doom-java) must contain
# tools/oracle/work/chocolate-doom (Chocolate Doom 3.1.1 source, cloned by tools/oracle/build.sh).
set -e
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)
doom_home=${DOOM_HOME:-/Users/luizaugustomarques/Documents/projetos/doom-java}
choc=${CHOCOLATE_SRC:-$doom_home/tools/oracle/work/chocolate-doom}
work=${TMPDIR:-/tmp}/opl3-vectors.$$
mkdir -p "$work"
clang -O1 -Wall -Wno-parentheses -I"$choc/opl" -o "$work/vectors" "$here/vectors.c" "$choc/opl/opl3.c"
"$work/vectors" "$work/vectors.txt"
dest="$repo/doom-sound/src/test/resources/opl3"
mkdir -p "$dest"
gzip -9nc "$work/vectors.txt" > "$dest/vectors.txt.gz"
ls -l "$dest/vectors.txt.gz"
rm -rf "$work"
