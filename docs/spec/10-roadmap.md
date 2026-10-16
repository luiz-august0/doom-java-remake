# 10 — Roadmap

Milestones in dependency order. Each one ends with something **executable** and an objective acceptance criterion.
M5 (audio) can proceed in parallel with M3/M4 after the M1 spike.

```
M0 Foundation ─► M1 Window, pixels and sound ─► M2 Visible world ─► M3 Simulation ─► M4 Complete game ─► M6 Modern presentation ─► M7 Distribution v1.0
                          └──────────────────────► M5 Audio (parallel) ───────────────────┘
```

## M0 — Foundation

- Maven reactor with all modules empty and with their `module-info.java`; parent POM with versions, `enforcer` (JDK 25, Maven 3.9+, ADR-021 dependency list) and Maven Wrapper.
- `doom-math`: `Fixed`, `Angle`, tables (extracted by `doom-tools`), `Rng`, `BBox` + tests.
- `doom-wad`: WAD reading, directory, lookup by name, IWAD identification; `doom-tools wad-dump`.
- `doom-testkit`: IWAD locator (`DOOM_IWAD_DIR`), Freedoom download in CI, trace format.
- Oracle: `tools/oracle/build.sh` + trace patch compiling on macOS; first traces of the Freedoom demos generated.
- CI with `mvn verify` on the 3 OSes.

**Acceptance:** `mvn verify` green; `wad-dump` lists DOOM1.WAD and freedoom2.wad; reference traces generated.

## M1 — Window, pixels and sound

- `doom-platform-api` + `doom-platform-lwjgl`: GLFW window (windowed/borderless/fullscreen, HiDPI), `R8` + palette presentation, vsync, `-XstartOnFirstThread`.
- `doom-render`: `FrameBuffer`, `Canvas2D` (patch drawing), Picture decoding.
- `doom-platform-javasound`: `AudioSink` + **latency spike** (ADR-012) with a DMX sound triggered by a key.
- Display TITLEPIC and switch palettes with keys; FPS overlay.

**Acceptance:** correct TITLEPIC at 320×200 and at 2560×1080 with pillarbox; latency measured and audio decision recorded in ADR-012 (Java Sound kept or OpenAL fallback per OS).

## M2 — Visible world

- `doom-game/world`: complete `LevelLoader` (geometry, blockmap, reject); `doom.game.view`.
- `doom-render`: 3D renderer parameterized by `W×H`: walls, floors and ceilings, sky, static sprites, lighting, fuzz. Free camera (*noclip*) by keyboard and mouse.
- ADR-004 benchmark (*views* × direct access).
- Oracle patch with `-dumpframe`.

**Acceptance:** **pixel-identical** golden frames at tic 0 of all Freedoom demos (initial position, no animation); all Freedoom maps load and render without exceptions.

## M3 — Simulation

In the order of [03 §11](03-simulation.md#11-suggested-implementation-order):

- `data`: `doom-tools gen-defs` → definitions DSL + `DefsEquivalenceTest`.
- `actor`, `physics`, `player` → first demo without monsters syncs.
- `ai`, `combat` → demos with combat sync.
- `mapspecial` → complete demos.
- `game`: state flow (without graphical UI), `.lmp` demos, `-playdemo`/`-timedemo`/`-record` headless, `-trace`.
- Survey of the differences by `GameVersion` (inventory in Chocolate Doom) → `docs/divergences.md`.
- `doom-tools ref-coverage` with 100% of linuxdoom's simulation functions covered or justified.

**Acceptance:** all demos of the Freedoom corpus sync in CI; all `DEMOn` of the available commercial IWADs sync locally; golden frames at advanced tics of the demos pass.

## M4 — Complete game

- `doom-ui`: menus (original + extended), status bar, HUD, automap, intermission, finale, wipe, title loop, cheats.
- `doom-engine`: complete loop with real time, config (`doom-java.cfg`), CLI, input with *bindings*.
- Save/load ([08](08-persistence.md)).

**Acceptance:** play episode 1 of the shareware and one Freedoom episode from start to finish; golden frames of menu, intermission and automap pass; save/load test passes on all Freedoom maps.

## M5 — Audio (parallel to M3/M4)

- Channel logic, mixer, sound effects wired to the simulation and UI events.
- MUS/MIDI → `MusicScore`, sequencer, Nuked OPL3 port with test vectors, `GENMIDI` bank, OPL2/OPL3 modes.

**Acceptance:** bit-for-bit OPL3 vectors; music for all DOOM and DOOM II maps playing with a correct *loop*; zero *underruns* in 30 min of play on the 3 OSes.

## M6 — Modern presentation

- Arbitrary resolution, render scale, Hor+ widescreen up to 21:9 and 32:9, aspect correction and square-pixels mode.
- Precision fixes ([04 §3](04-rendering.md#3-precision-at-high-resolution)), light normalization, scaled fuzz.
- Interpolation and uncapped FPS, with per-frame turning of the local player.
- 2D in widescreen (status bar with sides, wide patches, minimal HUD).
- **Decision by benchmark:** framebuffer layout (row × transposed).

**Acceptance:** targets of [04 §9](04-rendering.md#9-performance); demos keep syncing with interpolation on (proof that the presentation does not touch the simulation); vanilla-mode golden frames remain pixel-identical.

## M7 — Distribution v1.0

- `maven-jlink-plugin` + `jpackage`: `.dmg` (arm64 and x64), `.msi`, `.deb`/`.tar.gz`; icon and metadata.
- Gamepad, finished options menu, friendly error messages (IWAD not found, etc.).
- User README (where to put the IWAD, how to get Freedoom), `NOTICE` with credits to id Software and Chocolate Doom.

**Acceptance:** a clean install on the 3 OSes opens the game with Freedoom and with a commercial IWAD; the full demo corpus syncs; `v1.0.0` *tag*.

## Post-v1 backlog

| Item | Notes |
|------|-------|
| DEHACKED beyond the Chocolate-compatible subset | **(ADR-022)** the subset itself (`-deh`, `DEHACKED` lump) is in v1; tables are mutable (ADR-014) |
| Multiplayer (co-op/deathmatch) | `NetTicSource` with lockstep over UDP; the `TicCmd` pipeline is already in place. **(ADR-022)** Multiplayer demo playback is already in v1 |
| Parallel rendering by strips | Platform pool (ADR-020) |
| 100% Java backend (`doom-platform-awt`) | Java2D + `javax.sound`, no natives |
| PC speaker effects (`DP*`) | Tone emulation |
| SoundFont music (Gervill) | Alternative `MusicSynth` |
| ENDOOM on exit | Render the DOS text screen |
| Structured Concurrency in loading | When it stops being *preview* |
| Post-processing (CRT, scanlines) | In the presentation shader |
