#!/usr/bin/env bash
# Regenerates tools/oracle/chocolate-trace.patch from the edited clone (development helper).
#
#   tools/oracle/regen-patch.sh
#
# The clone (ORACLE_WORK/chocolate-doom, a git checkout of the chocolate-doom-3.1.1 tag) is edited
# directly; this script stores `git diff` of the whole tree (new files included) as the patch.
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
WORK=${ORACLE_WORK:-$SCRIPT_DIR/work}
SRC=$WORK/chocolate-doom
git -C "$SRC" add -A
git -C "$SRC" diff --cached --no-color --no-ext-diff > "$SCRIPT_DIR/chocolate-trace.patch"
cp "$SCRIPT_DIR/chocolate-trace.patch" "$WORK/applied.patch"
echo "wrote $SCRIPT_DIR/chocolate-trace.patch ($(wc -l < "$SCRIPT_DIR/chocolate-trace.patch") lines)"
