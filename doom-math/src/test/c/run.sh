#!/bin/sh
# Builds and runs the doom-math C cross-check (crosscheck.c) against the Chocolate Doom 3.1.1 sources and writes
# the vector file replayed by doom.math.CrossCheckTest:
#   doom-math/src/test/resources/doom/math/crosscheck-vectors.txt
#
# usage: doom-math/src/test/c/run.sh [chocolate-doom source dir]
#   The source dir defaults to $CHOCOLATE_SRC, then to $DOOM_HOME/tools/oracle/work/chocolate-doom.
#   CC selects the compiler (default: clang).
#
# The program is built in four variants: -O0/-O2, each with and without -fwrapv. The vectors are taken from
# "-O0 -fwrapv" (two's-complement wrap-around, the semantics of the DOS original and of Java). The script then
# reports whether the other variants agree; a difference means the C code relies on signed-overflow behaviour that
# an optimizing compiler may change (a hazard for oracle builds, not for the Java code).
set -eu

here=$(cd "$(dirname "$0")" && pwd)
default_src="${DOOM_HOME:-$(cd "$here/../../../.." && pwd)}/tools/oracle/work/chocolate-doom"
src=${1:-${CHOCOLATE_SRC:-$default_src}}
out_file="$here/../resources/doom/math/crosscheck-vectors.txt"
cc=${CC:-clang}

for f in src/m_fixed.c src/tables.c src/doom/r_main.c src/doom/p_maputl.c; do
    if [ ! -f "$src/$f" ]; then
        echo "run.sh: $src/$f not found (pass the Chocolate Doom source dir)" >&2
        exit 2
    fi
done

build=$(mktemp -d "${TMPDIR:-/tmp}/doom-crosscheck.XXXXXX")
trap 'rm -rf "$build"' EXIT

# 1. Extract R_PointToAngle + R_PointToAngle2 and P_AproxDistance verbatim (the return type is on the line before).
{
    echo "/* Extracted verbatim from Chocolate Doom src/doom/r_main.c and src/doom/p_maputl.c by run.sh. */"
    awk '/^R_PointToAngle$/ { on = 1; print "angle_t" }
         /^R_PointToAngle2$/ { second = 1 }
         on { print }
         on && second && /^}/ { exit }' "$src/src/doom/r_main.c"
    awk '/^P_AproxDistance$/ { on = 1; print "fixed_t" }
         on { print }
         on && /^}/ { exit }' "$src/src/doom/p_maputl.c"
} > "$build/extracted.c"

for marker in "octant 0" "octant 5" "R_PointToAngle2" "viewx = x1;" "return dx+dy-(dy>>1);"; do
    if ! grep -q "$marker" "$build/extracted.c"; then
        echo "run.sh: extraction failed (missing '$marker')" >&2
        exit 1
    fi
done

# 2. Build and run every variant.
compiler=$("$cc" --version | head -n 1)
reference=""
for flags in "-O0 -fwrapv" "-O2 -fwrapv" "-O0" "-O2"; do
    name=$(echo "$flags" | tr -d ' -')
    # shellcheck disable=SC2086
    "$cc" $flags -w -DCOMPILER_DESCRIPTION="\"$compiler, $flags\"" \
        -I"$build" -I"$here/include" -I"$src/src" \
        "$here/crosscheck.c" "$src/src/m_fixed.c" "$src/src/tables.c" -o "$build/crosscheck-$name"
    "$build/crosscheck-$name" > "$build/out-$name.txt"
    grep -v '^# Compiler' "$build/out-$name.txt" > "$build/cmp-$name.txt"
    if [ -z "$reference" ]; then
        reference=$name
    elif cmp -s "$build/cmp-$reference.txt" "$build/cmp-$name.txt"; then
        echo "variant '$flags': identical to '-O0 -fwrapv'"
    else
        echo "variant '$flags': DIFFERS from '-O0 -fwrapv' in $(diff "$build/cmp-$reference.txt" "$build/cmp-$name.txt" | grep -c '^>') line(s):"
        diff "$build/cmp-$reference.txt" "$build/cmp-$name.txt" | grep '^[<>]' | head -n 20
    fi
done

mkdir -p "$(dirname "$out_file")"
cp "$build/out-$reference.txt" "$out_file"
echo "Wrote $out_file ($(grep -vc '^#' "$out_file") vectors and digests)"
