# OPL3 and music reference harnesses

C programs that link the original Chocolate Doom 3.1.1 sources (from `$DOOM_HOME/tools/oracle/work/chocolate-doom`,
see `tools/oracle/build.sh`) and produce reference data for the Java audio code. Needs `clang`; SDL2 headers
(`/opt/homebrew/include/SDL2`, override with `SDL2_INCLUDE`) only for the music harness.

| Path | Purpose | Consumer |
|---|---|---|
| `vectors.c`, `build.sh` | Drives the original `opl/opl3.c` through register sequences (2-op, 4-op, rhythm, OPL2/OPL3, envelopes, a write-queue overflow, fuzz; `OPL3_GenerateStream` at 48000/44100/49716 and raw `OPL3_Generate`) and writes the operations plus FNV-1a hashes of every 128-frame output block. `build.sh` regenerates `doom-sound/src/test/resources/opl3/vectors.txt.gz` (committed, 280 KB). | `doom.sound.opl.Opl3VectorsTest` |
| `music/musicref.c`, `music/opl_harness.c`, `music/build.sh` | Runs the original `i_oplmusic.c`, `mus2mid.c`, `midifile.c`, `opl3.c`, `opl_queue.c` on an OPL layer without SDL (copy of `opl_sdl.c` timing). Commands: `play` (register-write log with microsecond time plus raw PCM), `mus2mid` (size and hash of the MIDI file of every `D_*` lump), `mid` (writes one converted file). | `doom.it.audio.MusicDriverIT`, `Mus2MidIT` |

The music harness is built on demand by the ITs into `target/audio-refs/` (game content, never committed).
