#!/usr/bin/env bash
# Sourced by the oracle scripts: resolves the shared locations.
#
#   ORACLE_DIR      this directory (tools/oracle)
#   MAIN_TREE       the main working tree (differs from the repository root inside a git worktree,
#                   where git-ignored files such as .iwads/ and tools/oracle/work/ do not exist)
#   ORACLE_WORK     build tree, clone and cache (default: first of <repo>/tools/oracle/work and
#                   $MAIN_TREE/tools/oracle/work that contains a built oracle, else the former)
#   DOOM_IWAD_DIR   IWAD directory (default: <repo>/.iwads, falling back to $MAIN_TREE/.iwads)
#   DOOM_ORACLE     oracle binary (default: $ORACLE_WORK/build/src/chocolate-doom)

ORACLE_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$ORACLE_DIR/../.." && pwd)
MAIN_TREE=$REPO_ROOT
if COMMON=$(git -C "$REPO_ROOT" rev-parse --git-common-dir 2>/dev/null); then
    case $COMMON in
        /*) ;;
        *) COMMON=$REPO_ROOT/$COMMON ;;
    esac
    if [ -d "$COMMON" ]; then
        MAIN_TREE=$(cd "$COMMON/.." && pwd)
    fi
fi

if [ -z "${ORACLE_WORK:-}" ]; then
    ORACLE_WORK=$ORACLE_DIR/work
    if [ ! -x "$ORACLE_WORK/build/src/chocolate-doom" ] \
       && [ -x "$MAIN_TREE/tools/oracle/work/build/src/chocolate-doom" ]; then
        ORACLE_WORK=$MAIN_TREE/tools/oracle/work
    fi
fi
if [ -z "${DOOM_IWAD_DIR:-}" ]; then
    DOOM_IWAD_DIR=$REPO_ROOT/.iwads
    [ -d "$DOOM_IWAD_DIR" ] || DOOM_IWAD_DIR=$MAIN_TREE/.iwads
fi
: "${DOOM_ORACLE:=$ORACLE_WORK/build/src/chocolate-doom}"
export ORACLE_DIR REPO_ROOT MAIN_TREE ORACLE_WORK DOOM_IWAD_DIR DOOM_ORACLE
