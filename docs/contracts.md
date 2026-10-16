# Implementation contracts

This document is the **single source of truth** for everything that more than one module, agent or tool must agree on.
It complements the specification in `docs/spec/` (ADR-001…ADR-021) and records ADR-022, the implementation adjustments, plus a note on ADR-023.
Only the orchestrator edits this file. Agents that need a change ask for it in their final report.

Contents:

1. [Language and conventions](#1-language-and-conventions)
2. [Modules, packages and ownership](#2-modules-packages-and-ownership)
3. [Coding rules](#3-coding-rules)
4. [Build and workflow rules for agents](#4-build-and-workflow-rules-for-agents)
5. [Shared paths and environment](#5-shared-paths-and-environment)
6. [Per-tic trace format v1](#6-per-tic-trace-format-v1)
7. [Oracle (Chocolate Doom 3.1.1)](#7-oracle-chocolate-doom-311)
8. [Misc RNG call sites](#8-misc-rng-call-sites)
9. [Game-version conditionals and emulated overflows](#9-game-version-conditionals-and-emulated-overflows)
10. [Session-scoped state](#10-session-scoped-state)
11. [Cross-module API notes](#11-cross-module-api-notes)
12. [ADR-022 — Implementation adjustments](#12-adr-022--implementation-adjustments)
13. [Agent report template](#13-agent-report-template)

---

## 1. Language and conventions

- **Everything is written in English**: identifiers, comments, Javadoc, commit messages, documentation and README.
  This overrides spec 02 §10, which asked for Portuguese comments.
- Game texts are the original English texts (`d_englsh.h`), possibly replaced by DEHACKED/BEX strings.
- `@VanillaRef("file.c:Function")` on simulation methods that reproduce a specific original rule (mandatory in `doom.game`, optional elsewhere).
- `// VANILLA-BUG: <description>` where an original bug is preserved on purpose.
- `// DIVERGE: <description> (see docs/divergences.md D-xxx)` where we deviate on purpose.
- Fixed-point and BAM values may be annotated with the type annotations `@FixedPoint int` and `@Bam int` (ADR-022 §5).

## 2. Modules, packages and ownership

`(E)` marks packages exported from the JPMS module. Unmarked packages are internal.
"Owner" is who may edit the package during implementation. The orchestrator owns every `module-info.java`, every `pom.xml`
and this document.

| Maven module | JPMS module | Packages | Owner |
|---|---|---|---|
| `doom-math` | `doom.math` | `doom.math` (E) — `Fixed`, `Angle`, `Tables`, `Trig`, `Rng`, `BBox`; `doom.math.annotation` (E) — `VanillaRef`, `FixedPoint`, `Bam` | W1-B (API frozen by W0) |
| `doom-wad` | `doom.wad` | `doom.wad` (E) — `WadFile`, `LumpDirectory`, `Lump`, `LumpNamespace`, `WadFormatException`; `doom.wad.iwad` (E) — `GameMode`, `GameMission`, `GameVersion`, `IwadInfo`, `IwadLocator`; `doom.wad.map` (E) — map lump records; `doom.wad.texture` (E) — `TextureDefs`, `PatchNames`, `TextureDirectory`, `FlatDirectory`, `SpriteDirectory`; `doom.wad.demo` (E) — demo format; `doom.wad.text` (E) — ENDOOM | W1-A |
| | | `doom.wad.gfx` (E) — `Picture`, `Palettes`, `Colormaps`, `Flat` | W1-E |
| | | `doom.wad.audio` (E) — `DmxSound`, `MusicScore`, `MusConverter`, `MidiFile`, `GenMidi` | W1-D |
| `doom-game` | `doom.game` | `doom.game.api` (E), `doom.game.view` (E) | orchestrator (interfaces) |
| | | `doom.game.data`, `doom.game.data.dehacked` | W1-C |
| | | `doom.game.world` | W1-A, then W2-A |
| | | `doom.game.actor`, `doom.game.physics`, `doom.game.player`, `doom.game.impl` (api/view implementations), `doom.game.trace` | W2-A |
| | | `doom.game.ai` | W3-A |
| | | `doom.game.combat` | W3-B |
| | | `doom.game.mapspecial` | W3-C |
| | | `doom.game.session` | W2-A skeleton, then W3-D |
| | | `doom.game.persist` | W4-P |
| `doom-platform-api` | `doom.platform` | `doom.platform` (E), `doom.platform.input` (E) | orchestrator |
| `doom-render` | `doom.render` | `doom.render` (E), `doom.render.canvas` (E), `doom.render.image` (E) — 2D foundation, PNG | W1-E |
| | | `doom.render.scene`, `doom.render.texture`, `doom.render.wipe` (E) — 3D renderer, wipe | W2-B, then W4-R |
| `doom-sound` | `doom.sound` | `doom.sound.opl`, `doom.sound.music`, `doom.sound.mixer` | W1-D |
| | | `doom.sound` (E) — `SoundSystem` (channel logic) | W2-D |
| `doom-ui` | `doom.ui` | `doom.ui` (E) and internal `doom.ui.*` | W2-C |
| `doom-engine` | `doom.engine` | `doom.engine` (E), `doom.engine.config` (E), `doom.engine.cli` (E), internal `doom.engine.*` | W2-D, then W3-E / W4-A |
| `doom-platform-lwjgl` | `doom.platform.lwjgl` | `doom.platform.lwjgl` | W1-E |
| `doom-platform-javasound` | `doom.platform.javasound` | `doom.platform.javasound` | W1-D |
| `doom-app` | `doom.app` | `doom.app` (Main, logging) | W2-D |
| | | `doom.app.viewer` — development viewer (TITLEPIC, palettes, GL read-back) | W1-E |
| `doom-testkit` | `doom.testkit` | `doom.testkit` (E), `doom.testkit.trace` (E), `doom.testkit.oracle` (E), `doom.testkit.demo` (E) | W1-B |
| `doom-tools` | `doom.tools` | `doom.tools` (dispatcher, orchestrator) + one internal package per tool, owned by the tool's author | various |
| `doom-codegen` | `doom.codegen` | `doom.codegen.tables` (W1-B), `doom.codegen.defs` (W1-C) | — |
| `doom-lint` | `doom.lint` | `doom.lint` — javac plugin for `@Bam` | W1-B |
| `doom-it` | *(classpath, tests only)* | `doom.it.<area>` integration tests: `doom.it.wad` (W1-A), `doom.it.trace` (W1-B), `doom.it.audio` (W1-D), `doom.it.platform` (W1-E), others later | each area owns its sub-package; harness by W3-E |
| `doom-bench` | *(profile `bench`)* | JMH | W5 |

Non-Java areas:

| Path | Owner |
|---|---|
| `tools/oracle/` (build script, patch, runner, docs) | W0-OR |
| `tools/opl3-vectors/` (C harness for OPL3 reference vectors) | W1-D |
| `testdata/traces/` (oracle traces of IWAD demos), `testdata/demos/` (synthetic demos) | W0-OR / W1-B |
| `docs/spec/`, interim `README.md` | W1-F (translation), then orchestrator |
| `docs/divergences/<agent>.md` | each agent writes its own file; the orchestrator consolidates into `docs/divergences.md` |
| `docs/notes/<topic>.md` (e.g. `platform.md`, `audio-latency.md`) | the agent that owns the topic |
| `bin/viewer` | W1-E |
| `bin/doom-java`, `bin/doom-tools` | W2-D |

## 3. Coding rules

**Everywhere**

- Java 25, no preview or incubator APIs. Records, sealed types and pattern-matching `switch` are welcome.
- No mutable `static` state in any game module. `static final` constants and immutable tables are fine.
- Dependency injection by constructor. No frameworks.
- Logging only through `System.Logger` (`System.getLogger(...)`).
- No dependency outside the ADR-021 allowlist. The enforcer fails the build otherwise.
- Imports without wildcards. Formatting follows `.editorconfig` (4 spaces).
- Errors: fatal game errors throw `doom.math.DoomFatalException` (§11); malformed data throws `doom.wad.WadFormatException`
  with file, lump and offset. Never let a raw `ArrayIndexOutOfBoundsException` escape from bad PWAD data (spec 02 §9).
- Tests: `*Test` = unit (Surefire, no IWAD), `*IT` = integration (Failsafe, lives in `doom-it`, skipped when `DOOM_IWAD_DIR` is unset).
  Use JUnit 5 + AssertJ only. IWAD-dependent tests call `Assumptions.assumeTrue(...)` so they are **skipped, not failed**, when a WAD is missing.

**Hot paths (renderer, mixer, main loop, simulation tick)**

- Zero allocation in steady state: pre-allocated, growable arrays and pools.
- No `stream()`, capturing lambdas, boxing, `String.format`, collection iterators or `HashMap` iteration.

**Simulation (`doom.game` except `api`/`view` declarations)**

- No `float`/`double`, no `Math.*` except `Math.abs` on `int` (and `Math.floorMod` only where the rule needs it), no `StrictMath`.
- No clocks, no threads, no randomness outside `Rng`, no `HashMap`/`HashSet` iteration, no user configuration.
- 16.16 fixed point and BAM angles in `int`; unsigned angle comparisons through `Angle`; `>>>` for table indices.
- Integer division and modulo truncate toward zero, exactly like C. Do not "fix" them with `floorDiv`.
- Keep the original evaluation order of every expression that calls the RNG, including short-circuit evaluation.
  `P_Random() - P_Random()` is evaluated left to right (first call minus second call), as Chocolate Doom does.
- Presentation-only fields (`prevX` … for interpolation) are written by the simulation and **never read** by it.

## 4. Build and workflow rules for agents

- Work only inside the packages and paths you own (see §2). If you need a change elsewhere — a new export, a dependency,
  a contract change — put it in your final report under *Contract requests* and use a local stub meanwhile.
- Build only what you need from the reactor root: `./mvnw -o -q -pl <module>[,<module>…] -am verify`.
  Never run `mvn install` (it would leak your snapshot into `~/.m2` for other agents). Use `-o` (offline);
  the cache is warmed by Wave 0. If you truly need a new artifact, ask in the report.
- Never edit `pom.xml` files, `module-info.java` files or this document.
- Commit your work on your branch (you are in a git worktree). End each commit message with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Iterate: implement → test → fix, until your acceptance criteria pass. Report honestly what does not pass.
- Record every deliberate deviation from vanilla or from the spec in `docs/divergences/<your-agent-id>.md`
  as a table row: `| area | original behaviour | ours | reason | reference |`.
- When the orchestrator tells you that `main` has something you need, run `git merge main` in your worktree.

## 5. Shared paths and environment

Absolute paths (git worktrees do not contain ignored files, so always use these):

| Variable | Value | Content |
|---|---|---|
| `DOOM_HOME` | `/Users/luizaugustomarques/Documents/projetos/doom-java` | main working tree |
| `DOOM_IWAD_DIR` | `$DOOM_HOME/.iwads` (versioned since ADR-023, so a fresh checkout already holds the free IWADs) | `doom1.wad` (shareware 1.9, MD5 `f0cefca49926d00903cf57551d901abe`), `freedoom1.wad`, `freedoom2.wad`, `freedm.wad` (Freedoom 0.13.0). Commercial IWADs appear here if the user adds them. |
| `DOOM_ORACLE` | `$DOOM_HOME/tools/oracle/work/build/src/chocolate-doom` | patched Chocolate Doom 3.1.1 binary (after W0-OR) |
| `DOOM_ORACLE_RUN` | `$DOOM_HOME/tools/oracle/run.sh` | runner script with pinned configuration (after W0-OR) |
| `DOOM_ORACLE_CACHE` | `$DOOM_HOME/tools/oracle/work/cache` | generated reference frames, sidecars and synthetic-demo traces (git-ignored: frames are game content) |
| `CHOCOLATE_SRC` | `$DOOM_HOME/tools/oracle/work/chocolate-doom` | Chocolate Doom 3.1.1 source (reference for vanilla-exact behaviour, OPL music, DEHACKED) |
| `LINUXDOOM_SRC` | `/Users/luizaugustomarques/Documents/projetos/DOOM/linuxdoom-1.10` | original source: the rules reference |

Reference order when rules conflict: Chocolate Doom behaviour (DOS v1.9 exact) > linuxdoom-1.10 > Doom Wiki / Unofficial Doom Specs.

## 6. Per-tic trace format v1

The trace is the objective proof of simulation equivalence. The Java side (`doom.game.trace`, written by the engine on `-trace <file>`)
and the oracle patch (`tools/oracle/chocolate-trace.patch`) must produce **byte-identical lines** for the same input.

### 6.1 File

- UTF-8 text, LF line endings; gzip-compressed when the file name ends with `.gz`.
- Lines starting with `#` are comments. The first line is a header:
  `#doom-trace v1 iwad=<file name> demo=<name> version=<1.9|ultimate|final|final2>`.
- Then exactly **one line per call of `G_Ticker`** (Java: one per `GameSession.tick`), written right after the call returns.
  In Chocolate this is in `d_net.c RunTic()` after `G_Ticker()`; `D_DoAdvanceDemo()` (when pending) runs before it and belongs to the same tic.
- When the program ends inside `G_Ticker` (demo end marker under `-timedemo`/`-playdemo` quits through `I_Error`/`I_Quit`),
  that last call produces **no** line.

### 6.2 Line layout

Fields are `key=value` pairs separated by `;`. Values with several numbers use `,`. All numbers are signed 32-bit decimal
(angles and other unsigned values are printed as their `int` bit pattern), except hashes, which are 16 lowercase hex digits.

```
t=<tic>;gs=<gamestate>;r=<prndindex>;lt=<leveltime>;map=<episode>,<map>,<skill>;tot=<totalkills>,<totalitems>,<totalsecret>;p<N>=<player fields>;…;a=<count>,<hash>;s=<hash>;l=<hash>;d=<hash>;x=<count>,<hash>;m=<rndindex>
```

| Key | Meaning |
|---|---|
| `t` | 0-based count of `G_Ticker` calls since process start (equals `gametic` during the call) |
| `gs` | `gamestate` after the call: 0 LEVEL, 1 INTERMISSION, 2 FINALE, 3 DEMOSCREEN |
| `r` | game RNG index (`prndindex`) after the call |
| `lt` | `leveltime` |
| `map` | `gameepisode`, `gamemap`, `gameskill` (0-based skill, `sk_baby` = 0) |
| `tot` | `totalkills`, `totalitems`, `totalsecret` |
| `p<N>` | one entry per player `N` in 0..3 with `playeringame[N]`, in ascending order (fields below) |
| `a` | live map objects: count and hash (below) |
| `s`, `l`, `d` | hashes of sectors, lines and sides (below) |
| `x` | live non-mobj ("special") thinkers: count and hash (below) |
| `m` | misc RNG index (`rndindex`). **Informational only**: never gated, because it depends on rendering and sound settings |

All keys except `m` are **gated**: any difference fails the comparison. The comparer reports the first differing tic and key
(and, for `p<N>`, the field name).

### 6.3 Thinker index

References between objects are printed as a **thinker index**: the 0-based position of the referenced thinker among all
*live* thinkers (function ≠ −1, i.e. not marked for removal; in-stasis thinkers with a NULL function count as live),
in thinker-list order, mobjs and special thinkers counted together.
`-1` means a NULL pointer; `-2` means a pointer to something that is not a live thinker (for example a removed mobj).

### 6.4 Player fields (`p<N>`)

61 comma-separated values, in this order. When `player->mo` is NULL, fields 1–8 are 0.

| # | Field | # | Field |
|---|---|---|---|
| 1–3 | `mo->x`, `mo->y`, `mo->z` | 36 | `cheats` |
| 4 | `mo->angle` | 37 | `refire` |
| 5–7 | `mo->momx`, `momy`, `momz` | 38–40 | `killcount`, `itemcount`, `secretcount` |
| 8 | `mo->health` | 41–42 | `damagecount`, `bonuscount` |
| 9–12 | `viewz`, `viewheight`, `deltaviewheight`, `bob` | 43–45 | `extralight`, `fixedcolormap`, `colormap` |
| 13 | `playerstate` | 46 | `attacker` (thinker index) |
| 14 | `health` | 47–50 | weapon psprite: state index (−1 if NULL), `tics`, `sx`, `sy` |
| 15–16 | `armorpoints`, `armortype` | 51–54 | flash psprite: state index, `tics`, `sx`, `sy` |
| 17–18 | `readyweapon`, `pendingweapon` | 55–56 | `attackdown`, `usedown` |
| 19 | `weaponowned` as a bit mask (bit *i* = weapon *i*, 0..8) | 57–60 | `frags[0..3]` |
| 20–23 | `ammo[0..3]` | 61 | `didsecret` |
| 24–27 | `maxammo[0..3]` | | |
| 28 | `cards` as a bit mask (bit *i* = card *i*, 0..5) | | |
| 29 | `backpack` | | |
| 30–35 | `powers[0..5]` | | |

### 6.5 Hashes

All hashes are **FNV-1a 64-bit** (offset basis `0xcbf29ce484222325`, prime `0x100000001b3`) over the **little-endian bytes of
int32 values**, fed in the order listed. The hash of an empty sequence is the offset basis.

**`a` — map objects.** Iterate the thinker list from the head; include each thinker whose function is `P_MobjThinker`.
For each one, in order:
`type, x, y, z, angle, momx, momy, momz, floorz, ceilingz, radius, height, state index (−1 if NULL), tics, sprite, frame, flags,
health, movedir, movecount, reactiontime, threshold, lastlook, target (thinker index), tracer (thinker index),
player (player number 0..3, or −1)`. The count is the number of included mobjs.

**`s` — sectors**, in index order: `floorheight, ceilingheight, floorpic, ceilingpic, lightlevel, special, tag,
soundtraversed, soundtarget (thinker index), specialdata (thinker index)`.

**`l` — lines**, in index order: `flags & ~0x100` (ML_MAPPED is set by the renderer, so it is excluded), `special, tag`.

**`d` — sides**, in index order: `textureoffset, rowoffset, toptexture, bottomtexture, midtexture`.

**`x` — special thinkers.** Iterate the thinker list; include every live thinker that is not a mobj. For each, feed a kind code
followed by its fields:

| Code | Thinker (C function / struct) | Fields after the code |
|---|---|---|
| 1 | `T_VerticalDoor` / `vldoor_t` | `type, sector, topheight, speed, direction, topwait, topcountdown` |
| 2 | `T_MoveCeiling` / `ceiling_t` | `type, sector, bottomheight, topheight, speed, crush, direction, tag, olddirection` |
| 3 | `T_MoveFloor` / `floormove_t` | `type, crush, sector, direction, newspecial, texture, floordestheight, speed` |
| 4 | `T_PlatRaise` / `plat_t` | `type, sector, speed, low, high, wait, count, status, oldstatus, crush, tag` |
| 5 | `T_LightFlash` / `lightflash_t` | `sector, count, maxlight, minlight, maxtime, mintime` |
| 6 | `T_StrobeFlash` / `strobe_t` | `sector, count, minlight, maxlight, darktime, brighttime` |
| 7 | `T_Glow` / `glow_t` | `sector, minlight, maxlight, direction` |
| 8 | `T_FireFlicker` / `fireflicker_t` | `sector, count, maxlight, minlight` |
| 102 | ceiling in stasis (NULL function, found in `activeceilings[]`) | same fields as code 2 |
| 104 | platform in stasis (NULL function, found in `activeplats[]`) | same fields as code 4 |
| 0 | NULL function not found in either list | none |

`sector` fields are sector indices; booleans are 0/1.

### 6.6 Verbose trace

`-traceverbose <file>` (both sides) writes the normal line followed, for each tic in the optional range
`-tracefrom <tic> -traceto <tic>`, by one line per included object:
`  a<i>=<the 26 mobj fields>` and `  x<i>=<code>,<fields>`, so that a hash mismatch can be resolved to a single object.

### 6.7 Comparison

`doom-tools trace-diff <ref> <ours>` compares gated keys line by line. It prints the first differing tic, the key
(and the player field name), both values and the surrounding lines. Exit code 0 = identical, 1 = divergent, 2 = error.

## 7. Oracle (Chocolate Doom 3.1.1)

**Build** (`tools/oracle/build.sh`): CMake with `-DENABLE_SDL2_MIXER=OFF -DENABLE_SDL2_NET=OFF`, target `chocolate-doom`,
after applying `tools/oracle/chocolate-trace.patch` to the clone in `tools/oracle/work/chocolate-doom` (tag `chocolate-doom-3.1.1`).
CMake 4.x may need `-DCMAKE_POLICY_VERSION_MINIMUM=3.5`.

**Patch features** (all opt-in through command-line flags, so the unpatched behaviour is untouched):

| Flag | Effect |
|---|---|
| `-trace <file>` | Trace v1 (§6). Flushed and closed through `I_AtExit(…, true)` so it survives the `I_Error` exit of `-timedemo` |
| `-traceverbose <file>`, `-tracefrom <tic>`, `-traceto <tic>` | Verbose trace (§6.6) |
| `-dumpframe <tic>[,<tic>…] <dir>` | After the `D_Display()` that follows tic *N* (in `D_RunFrame`), write the 320×200 indexed `I_VideoBuffer` and a **scene sidecar** |
| `-dumpdefs <file>` | After DEHACKED is applied, dump the definition tables (states, mobjinfo, weaponinfo, ammo, sounds, misc values, cheats, strings) |
| `-headless` (fallback) | Skip SDL video initialization; keep `I_VideoBuffer`; make `I_FinishUpdate`/`I_SetPalette` no-ops. Only added if the SDL dummy driver does not work |

**Scene sidecar** — everything the 3D renderer needs to reproduce the view window without the simulation:
view (`viewx`, `viewy`, `viewz`, `viewangle`, `extralight`, `fixedcolormap`, display player, `viewwindowx/y`, `scaledviewwidth`,
`viewheight`, `detailshift`), palette index in use, `fuzzpos` at frame start, `skytexture`, `skyflatnum`, the
`texturetranslation[]` and `flattranslation[]` tables, per-sector `floorheight, ceilingheight, floorpic, ceilingpic, lightlevel`,
per-side `textureoffset, rowoffset, toptexture, bottomtexture, midtexture`, per-sector thing lists **in `snext` order** with
`x, y, z, angle, sprite, frame, flags` of each mobj, the view player's psprites (`sprite, frame, sx, sy` for weapon and flash) and
`powers[]`. The exact syntax of frames (`frame-<tic>.raw`), sidecars (`frame-<tic>.scene`) and `-dumpdefs` files is in
`tools/oracle/FORMAT.md` (authoritative). Sidecar `T` records carry a trailing `player` field. A frame requested for tic 0 is
satisfied by tic 1 (`requested` vs `tic` in the sidecar); wipe frames are never dumped.

**Zero-filled specials.** The oracle zero-fills every `PU_LEVSPEC` allocation (vanilla leaves heap garbage in unused
thinker fields, e.g. `plat_t.count`, which made traces non-deterministic). The Java simulation must create special thinkers
with all fields zero (Java defaults) before setting them, exactly as the original code then assigns them.

**Observed trace facts:** for `-timedemo` runs, lines equal the demo tic count (`t=0..N-1`, the end-marker call writes none);
line `t=0` already has `lt=1`. `s`, `l`, `d` hash the level currently in memory (empty before the first level).
Verbose `a<i>`/`x<i>` use the shared thinker index of §6.3.

**Running** (`tools/oracle/run.sh`):

- Fresh temporary `HOME` and config per run (parallel runs must not share files).
- Pinned config: `show_endoom 0`, `show_diskicon 0`, `screenblocks 10`, `detaillevel 0`, `fullscreen 0`, `sfx_volume 8`,
  `music_volume 8`, `force_software_renderer 1`, `smooth_pixel_scaling 0`, `usegamma 0`, `show_messages 1`.
- Environment `SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy`; always pass `-nogui`.
- Demos must be version **109** (Chocolate rejects 110). `-timedemo` ends with exit status **255**; that is success.
- Never use `-nodraw`: it skips `D_Display`, so wipes never start and the misc RNG sequence changes.
- The IWAD's own DEHACKED lump is applied automatically for Freedoom IWADs (`gamevariant == freedoom`); our engine does the same.
  `-nodeh` disables it on both sides.

**Game version picked by the oracle** (and by our auto-detection): `doom1.wad` → 1.9, `freedoom1.wad` → Ultimate,
`freedoom2.wad` → 1.9, `freedm.wad` → 1.9, TNT/Plutonia → Final.

**IWAD demos** (version 109): `doom1` DEMO1–3 (E1M5, E1M3, E1M7, single player); `freedoom1` DEMO1–4 (E1M6 single; E2M4, E3M9 and
E4M6 three-player coop); `freedoom2` DEMO1–4 (MAP03 single; MAP10 two-player coop; MAP22 single; MAP23 two-player coop);
`freedm` DEMO1–3 (deathmatch).

## 8. Misc RNG call sites

The misc RNG (`M_Random`, `rndindex`) has **exactly** these callers in DOS v1.9 / Chocolate. Golden frames depend on its sequence
(the status-bar face uses it), so the remake must call `MiscRandom.next()` at the same points, in the same order, the same number of times.

| Caller | When | Calls |
|---|---|---|
| `f_wipe.c wipe_initMelt` | start of every melt wipe | **320** (`y[0]` + 319 columns; `width` = SCREENWIDTH, verified on the oracle trace), independent of resolution |
| `s_sound.c S_StartSound` | after the link-volume and audibility checks pass (origin is NULL or the console player's mobj, or `S_AdjustSoundParams` returned non-zero) | 1, except for `sfx_itemup` and `sfx_tink` (0). Saw sounds use `& 15`, all others `& 31` |
| `st_stuff.c ST_Ticker` | every `G_Ticker` in GS_LEVEL (even while paused or with the menu open) | 1 |
| `wi_stuff.c WI_initAnimatedBack / WI_updateAnimatedBack` | Doom 1 animated intermission backgrounds | per animation, as in the original |

Both indices are reset only by `M_ClearRandom()`, called from `G_InitNew` (which demo playback also calls).
The quit message is picked from `gametic`, not from `M_Random`.
`S_StartSound` runs synchronously inside the tic (§11), also in headless runs, with the configured SFX volume (default 8).

## 9. Game-version conditionals and emulated overflows

Inventory taken from Chocolate Doom 3.1.1 (`src/doom/`). Each rule must be implemented with `version.isAtLeast(...)`-style checks
(never subclasses) and cross-checked by the area owner against the Chocolate source.

| Where | Condition | Effect |
|---|---|---|
| `g_game.c G_InitNew` | ≥ Ultimate: episode 0 becomes 4, else clamp 1–3; shareware forces E1; non-commercial caps map at 9 | start parameters |
| `g_game.c G_InitNew` | `-fast` / Nightmare halve state tics and change missile speeds **relative to the previous `gameskill`** | mutates the session's definition tables |
| `g_game.c G_InitNew / G_DoLoadLevel` | sky chosen in `G_InitNew` ("sky never changes"); Final2 sets it per level | sky texture |
| `g_game.c G_DoCompleted, G_WorldDone, G_SecretExitLevel` | next-map tables, E?M8 victory, secret exits, finales after maps 6/11/20/30, no MAP31 → no secret exit | game flow |
| `g_game.c G_DoCompleted` | par times; MAP33 reads bytes of the `GAMMALVL0` string; E4 reads past `pars` into `cpars` | intermission timing |
| `d_main.c D_DoAdvanceDemo` | Ultimate/Final: 7-step loop including DEMO4 (Final with TNT/Plutonia → error); commercial title page 11 s | title loop |
| `p_setup.c P_LoadThings` | non-commercial and a Doom II-only monster type: **stops loading all remaining things** | spawning |
| `p_mobj.c P_ZMovement` | ≥ Ultimate: lost-soul bounce | physics |
| `p_telept.c EV_Teleport` | Final: z is not reset to `floorz` | teleport |
| `p_enemy.c A_BossDeath` (and boss-end checks) | < Ultimate: any boss on map 8 (Baron only in E1); ≥ Ultimate: the episode's own boss; commercial: MAP07 | specials |
| `p_inter.c, p_user.c, p_pspr.c` | megasphere and super shotgun only in commercial; plasma/BFG not in shareware | pickups, weapon choice |
| `p_switch.c P_InitSwitchList` | shareware 1 / registered and **retail** 2 / commercial 3 (linuxdoom leaves out retail) | switch textures |
| `s_sound.c S_AdjustSoundParams` | `gamemap == 8` in any mode (MAP08 included): no distance cut-off | misc RNG draws |

| Emulated vanilla overflow / limit | Where (Chocolate) |
|---|---|
| more than 8 spechits: line address `0x01C09C98 + index × 0x3E` written into `tmbbox[0..3]`, `crushchange`, `nofit` | `p_map.c SpechitOverrun` |
| more than 128 intercepts: overwrites `lowfloor`, `openbottom`, `opentop`, `openrange`, `bulletslope`, `playerstarts`, `bmap*` | `p_maputl.c InterceptsOverrun` |
| short REJECT padded with `{((lines × 4 + 3) & ~3) + 24, 0, 50, 0x1D4A11}`, then zeros | `p_setup.c PadRejectArray` |
| donut without back sector: floor height 0, flat 0x16 | `p_spec.c DonutOverrun` |
| more than 20 adjoining sectors: the 21st overwrites `height`; 22 → error | `p_spec.c P_FindNextHighestFloor` |
| invalid back sidedef / missing back side | `p_setup.c P_LoadSegs`, `p_map.c PTR_ShootTraverse` |
| respawn fog with a negative angle index reads `finetangent` | `g_game.c G_CheckSpot` |
| NULL target replaced by a dummy mobj | `p_mobj.c P_SubstNullMobj` |
| thing type 0 ignored; skill bit = `1 << ((skill − 1) & 31)` | `p_mobj.c P_SpawnMapThing` |
| `MAXPLATS` 30, `MAXBUTTONS` 16, `MAXLINEANIMS` 64 → fatal error; `MAXCEILINGS` 30 → extra ceilings silently untracked | `p_plats.c`, `p_switch.c`, `p_spec.c`, `p_ceilng.c` |
| 20 lost souls limit; `A_Tracer` acts only when `gametic & 3 == 0`; `A_BrainSpit` keeps a static `easy` flag | `p_enemy.c` |

## 10. Session-scoped state

State that lives longer than a level, exactly as in the original (a "session" is one `GameSession` = one engine run):

| State | Lifetime / reset point |
|---|---|
| Definition tables (states, mobjinfo, weapons, sounds, strings) | copied once per session from the built-in definitions, then DEHACKED is applied; `G_InitNew` mutates them for `-fast`/Nightmare relative to the previous skill |
| Game and misc RNG indices | reset only in `G_InitNew` (`M_ClearRandom`) |
| `gametic` | process-global, +1 per `G_Ticker` call |
| `A_BrainSpit` `easy` flag | never reset |
| Sky texture | set in `G_InitNew` (per level only for Final2) |
| Body queue (`bodyque`, `bodyqueslot`) | as in the original |
| `consoleplayer`, `displayplayer`, `playeringame[]`, `deathmatch`, `netgame`, `respawnparm`, `fastparm`, `nomonsters` | session parameters (set by new game / demo header) |

## 11. Cross-module API notes

- **`doom.game.api` / `doom.game.view`** are the only exported packages of `doom.game`. Presentation reads state through the
  views (index-based, allocation-free) and acts only through `GameSession` commands. The skeleton interfaces are frozen in Wave 0;
  the simulation agents implement them; changes go through the orchestrator.
- **`SimEvents` callbacks are synchronous** and are invoked at the original call points: `startSound` where the original calls
  `S_StartSound`, `stopSound` where it calls `S_StopSound` (including `P_RemoveMobj`), `levelTick` where `G_Ticker` calls
  `ST_Ticker`/`AM_Ticker`/`HU_Ticker`, and so on. Implementations may read the views and may call `MiscRandom`; they must not
  call any other `GameSession` command from inside a callback.
- **Sound origins** are encoded as an `int`: −1 = no origin; an actor id (≥ 0, < `SoundOrigins.SECTOR_BASE`) = that map object;
  `SoundOrigins.SECTOR_BASE + sector` = the sector's sound origin (`sec->soundorg`).
- **Actor ids** are small non-negative integers handed out by the simulation; an id is reused only after `stopSound(id)` was
  emitted for the removed actor.
- **Demo playback and recording live in the session** (like `G_ReadDemoTiccmd` / `G_WriteDemoTiccmd` inside `G_Ticker`),
  because the title loop and deferred game actions start demos inside a tic. The engine's `TicCmdSource` provides local input only;
  its commands are ignored while a demo plays.
- **Intermission, finale and title-page logic live in the session** (they consume ticcmd buttons and change game flow).
  The UI draws them from `GameView.intermission()`, `finale()` and `pageName()`.
- **Fatal errors** in any game module throw `doom.math.DoomFatalException` (unchecked) with the original message.

## 12. ADR-022 — Implementation adjustments

**Status:** accepted. Amends ADR-004, ADR-014 and ADR-015 and the module table of spec 02 §2.

**Context.** Turning the specification into a parallel implementation plan exposed a few contradictions and missing pieces.

**Decisions.**

1. **Module graph.** Three modules are added:
   - `doom-codegen` (`gen-tables`, `gen-defs`): JDK only, run by hand, output committed. Generators cannot live in `doom-tools`, because `doom-tools` depends on `doom-game`.
   - `doom-lint`: the `@Bam` javac plugin (ADR-006), depending only on `jdk.compiler`.
   - `doom-it`: every `*IT`.

   `doom-testkit` depends only on `doom.wad`, and the synthetic `MapBuilder` lives in `doom-game`'s test sources.
   As specified, `doom-game` tests → `doom-testkit` → `doom-engine` → `doom-game` is a cycle that Maven rejects.
2. **Multiplayer demo playback in v1.** Five of the eight Freedoom demos are cooperative and FreeDM's demos are deathmatch.
   The simulation therefore supports four players, coop/deathmatch rules and starts from the beginning. There is no netcode (still a non-goal).
3. **Minimal DEHACKED in v1.** Freedoom IWADs embed a DEHACKED lump that Chocolate Doom applies automatically.
   It changes frame tics, fullbright flags, texts and level names. We support the subset that Chocolate Doom supports:
   Thing, Frame, Pointer, Sound, Ammo, Weapon, Misc, Text, Cheat, BEX `[STRINGS]`; plus `-deh`, `-nodeh`, and the IWAD lump rule.
4. **One `StateAction` enum** instead of method references in the definition DSL (ADR-014). The `states[]` table mixes monster and
   weapon actions and DEHACKED can swap them; method references from `data` to `ai` would also create a forbidden dependency cycle.
   The simulation dispatches the enum with a `switch` to per-area interfaces.
5. **Annotation names.** The type annotations are `@FixedPoint` and `@Bam`; `@VanillaRef` lives next to them in `doom.math.annotation`.
   The spec uses the name `Fixed` for both the utility class and the annotation, and the two clash.
6. **Demos live in the session**, not in engine `TicCmdSource` implementations (amends spec 08 §1.2): see §11.
7. **Language.** Comments and documentation are in English (amends spec 02 §10).
8. **Small corrections from Chocolate Doom:**
   - "registered" is detected by `E3M1`;
   - the map-8 sound rule also applies to Doom II's MAP08;
   - `P_InitSwitchList` includes retail;
   - JUnit stays on 5.x (5.14.4);
   - trig tables ship as a generated binary resource because a 10,240-element array literal exceeds the 64 KB `<clinit>` limit.

**Consequences.** The module table in spec 02 §2 gains three rows. ADR-015's test placement moves every `*IT` to `doom-it`.
Two backlog items move into v1: DEHACKED (subset) and multiplayer demo playback.

**ADR-023 note (free IWADs in git, Docker, bundled releases).** `.iwads/` now contains the four freely redistributable IWADs
(`doom1.wad` unmodified, Freedoom 1/2, FreeDM) and is committed; commercial IWADs stay git-ignored and forbidden.
`IwadLocator` also searches `<app dir>/iwads` and `/opt/doom-java/iwads`. Full text: `docs/spec/01-decisions.md`, ADR-023.

## 13. Agent report template

Every subagent ends with this report (it is the only thing the orchestrator sees):

```
## Report — <agent id>
Branch / last commit: <branch> <sha>
### Done
- …
### Tests
- command(s) run and results (counts of passed / failed / skipped)
### Acceptance criteria
- <criterion>: PASS | FAIL (details)
### Deviations
- … (also recorded in docs/divergences/<agent>.md)
### Contract requests
- … (exports, dependencies, interface changes you need from the orchestrator)
### Open issues / next steps
- …
```
