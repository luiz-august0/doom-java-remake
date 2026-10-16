# Test oracle: patched Chocolate Doom 3.1.1

The simulation of doom-java must be bit-identical to DOOM.EXE v1.9. The oracle is Chocolate Doom 3.1.1
(tag `chocolate-doom-3.1.1`, which reproduces DOS v1.9 exactly) plus `chocolate-trace.patch`, which adds opt-in
flags to dump a per-tic trace, rendered frames with a scene description, and the definition tables.
Contracts: `docs/contracts.md` sections 6 and 7. File formats: [`FORMAT.md`](FORMAT.md).

## Files

| File | Purpose |
|---|---|
| `build.sh` | clones Chocolate Doom (if needed), applies the patch, builds `work/build/src/chocolate-doom` (needs CMake and SDL2; SDL2_mixer/net are disabled) |
| `chocolate-trace.patch` | the patch (new files `src/doom/oracle*.c/h` plus small hooks) |
| `regen-patch.sh` | development helper: regenerates the patch from the edited clone in `work/chocolate-doom` |
| `run.sh` | runs the oracle on one demo with pinned configuration and a private temporary HOME |
| `make-refs.sh` | regenerates all reference data (traces, frames, definition dumps) |
| `oracle-util.py` | demo header/tic counts, raw frame to PNG, frame-tic picking, trace checks |
| `oracle-env.sh` | shared path resolution (main tree vs. git worktree) |

## Usage

```
tools/oracle/build.sh
tools/oracle/run.sh doom1 DEMO1 -trace /tmp/doom1-demo1.trace.gz
tools/oracle/run.sh freedoom2 DEMO3 -trace t.gz -dumpframe 0,35,700 /tmp/frames -dumpdefs /tmp/defs.txt
tools/oracle/make-refs.sh                      # all 14 IWAD demos + definition dumps
tools/oracle/make-refs.sh traces freedoom1:DEMO2
python3 tools/oracle/oracle-util.py png .iwads/doom1.wad frame-000035.raw out.png frame-000035.scene
```

Oracle flags (all opt-in): `-trace <file>`, `-traceverbose <file> [-tracefrom <tic> -traceto <tic>]`,
`-dumpframe <tic,tic,...> <dir>`, `-dumpdefs <file>`.

## Reference data

* `testdata/traces/<iwad>-<demo>.trace.gz` (committed): doom1 DEMO1-3, freedoom1 DEMO1-4, freedoom2 DEMO1-4, freedm DEMO1-3.
* `work/cache/frames/<iwad>-<demo>/` (git-ignored, game content): about 12 frames per demo, picked from the trace
  (tic 0 plus evenly spaced tics plus tics with firing, damage and monster activity).
* `work/cache/defs/<iwad>.txt` and `<iwad>-nodeh.txt` (git-ignored).
* `work/cache/summary.txt`: per demo the demo tic count, the trace line count, the determinism check and notes.

## Facts worth knowing

* **Version 109 demos only.** Chocolate rejects version 110 demos.
* **Exit status.** `-timedemo` leaves through `I_Error`; status 255 is success (`run.sh` returns 0 then).
* **SDL dummy drivers work** (`SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy`), so the planned `-headless` fallback was
  not needed. Never use `-nodraw` (it skips `D_Display`, no wipes, different misc RNG sequence).
* **`-nosound`** was tested: with SDL2_mixer disabled the oracle has no sound output module, and a run with `-nosound` is
  byte-identical to a run without it, including the informational misc RNG index `m`. `run.sh` does not pass it anyway.
* **Determinism.** Two runs, and runs in parallel, give byte-identical gzip files. Reaching this required zero-filling
  `PU_LEVSPEC` zone blocks (see FORMAT.md section 1: uninitialised thinker fields).
* The pinned configuration is written to a fresh temporary directory for every run and passed with `-config` and
  `-extraconfig`; nothing is read from or written to the real HOME. `ORACLE_KEEP=1` keeps the temporary directory,
  `ORACLE_LOG=<file>` keeps the console output.
* **Generated data lives in `work/`** of the main working tree (git-ignored). Inside a git worktree `oracle-env.sh`
  falls back to the main tree's `.iwads/` and `work/`; override with `ORACLE_WORK`, `DOOM_IWAD_DIR`, `DOOM_ORACLE`.

## Updating the patch

Edit the clone (`work/chocolate-doom`, a git checkout of the tag), then `regen-patch.sh` and `build.sh`.
The patch applies to a pristine clone with `patch -p1` (this is what `build.sh` does, and it re-applies cleanly
when the patch changes).
