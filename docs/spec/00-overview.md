# 00 — Overview

## 1. What we are building

A **remake** of DOOM in Java 25: code and architecture written from scratch, in idiomatic Java, with no line-by-line translation of the C.
The resulting game behaves **exactly** like the DOS v1.9 executable. "Exactly" has a precise, testable meaning here:

> Given the same IWAD and the same sequence of `ticcmd`s, the game state after every tic is **bit-for-bit identical**
> to that of vanilla DOOM. The proof is playing back the original `.lmp` demos without desyncing.

On top of that exact simulation, the **presentation** is modernized:

- arbitrary internal resolution (from 320×200 up to 4K or more, with a render scale);
- 4:3, 16:9, 16:10 and **21:9** aspect ratios (Hor+: the horizontal field of view grows, the vertical one is kept);
- **uncapped FPS**: the simulation stays at 35 Hz; the renderer interpolates between tics;
- vsync, frame limiter, windowed/borderless/fullscreen modes, HiDPI.

## 2. Goals

| ID | Goal | How we measure |
|----|------|----------------|
| G1 | Vanilla-exact simulation | Demos built into DOOM.WAD, DOOM2.WAD, TNT.WAD and PLUTONIA.WAD + a reference demo suite with no divergence in the per-tic trace (see [09](09-testing-quality.md)) |
| G2 | Faithful renderer | "Vanilla" mode (320×200, 4:3, no corrections) produces frames **pixel-identical** to Chocolate Doom's |
| G3 | High resolution and widescreen | 2560×1080 (21:9) and 3840×2160 playable, HUD/menus correct |
| G4 | Uncapped FPS | Smooth motion at 144 Hz+, without affecting the simulation or demo recording |
| G5 | Complete single-player game | DOOM (shareware, registered, Ultimate), DOOM II, Final DOOM (TNT/Plutonia) and Freedoom playable from start to finish: menus, save/load, automap, intermissions, endings, sound and music |
| G6 | Traceable behaviour | Simulation code that reproduces a specific rule of the original points to the reference (`@VanillaRef`), which makes investigating a desync cheap |
| G7 | Distributable | Native installers (macOS arm64/x64, Windows, Linux) with an embedded runtime |

## 3. Non-goals (v1)

- **Network multiplayer.** The architecture preserves the `ticcmd` pipeline (the way `d_net.c` does it) so that it can be added later without refactoring, but there is no netcode in v1. **(ADR-022)** Playback of multiplayer *demos* (coop/deathmatch, up to four players) is in v1.
- **Source port extensions**: Boom/MBF (generalized linedefs, `TRANSLUCENCY`, binary `ANIMATED`/`SWITCHES`), UDMF, ZDoom, scripting.
- **Gameplay changes**: vertical mouselook, jumping, crouching, free aim. (Mouselook may come in the future as a *visual-only* option; see ADR-011.)
- **GPU renderer** (polygons, filtered textures). The GPU only presents the indexed framebuffer.
- **Compatibility with DOS saves.** Own save format (ADR-013).
- **DEHACKED** stays in the post-v1 backlog, but the state and object tables are mutable from the start to allow it. **(ADR-022)** A minimal DEHACKED (the subset Chocolate Doom supports) moves into v1, because Freedoom IWADs embed a DEHACKED lump.

## 4. Principles

0. **A remake, not a translation.** The architecture, the names and the patterns are ours. The C code is the specification of the *rules*; how to implement them is free, as long as the result is identical (see ADR-007).
1. **Separate simulation from presentation.** Nothing the renderer, the audio, the UI or the interpolation do may change the simulation state. This boundary is enforced by the modules (ADR-004).
2. **In the simulation, free form and fixed content.** Classes and organization are ours, but the observable sequence of effects is the original's: order of RNG calls, 16.16 arithmetic, thinker update order, bugs included. An "improvement" that changes the result is a bug.
3. **Complete freedom outside the simulation.** The renderer, the UI, the mixer and the platform layer can and should diverge from the original (64-bit precision, dynamic limits, OpenGL) as long as they preserve the vanilla *visual/audible result*.
4. **Zero allocation in steady state.** The renderer, mixer and main loop allocate nothing per frame or per tic once the level has loaded (pre-allocated, growable pools). In the simulation, only discrete events allocate (spawning a mobj or a special thinker).
5. **No mutable static global state.** All state lives in instances (`DoomEngine`, `Level`, `Renderer`…). This allows running several headless simulations in parallel in tests.
6. **Testable headless.** The core runs without a window, without a GPU and without audio.

## 5. Glossary

| Term | Meaning |
|------|---------|
| **Tic** | Simulation step: 1/35 s. `gametic` counts the tics. |
| **ticcmd** | A player's input command for one tic (`forwardmove`, `sidemove`, `angleturn`, `buttons`…). The simulation's only input. |
| **Vanilla** | Behaviour of DOOM.EXE v1.9 for DOS (and the Ultimate/Final variants). |
| **IWAD / PWAD** | The game's main WAD / a modification WAD loaded on top. |
| **Lump** | Named entry (8 chars) inside a WAD. |
| **Fixed-point** | 16.16 number in an `int` (`FRACUNIT = 65536`). |
| **BAM** | *Binary Angle Measurement*: an angle as an unsigned 32-bit integer (0x40000000 = 90°). |
| **Thinker** | Object updated every tic (monsters, doors, lifts, lights…). Execution order is part of the determinism. |
| **Actor** | An actor on the map (player, monster, item, projectile); the original's `mobj_t`. |
| **Desync** | Divergence between the remake's simulation and vanilla's when playing back a demo. |
| **Hor+** | Widescreen that widens the horizontal FOV while keeping the vertical one. |
| **Render scale** | Factor between the renderer's internal resolution and the output resolution. |
