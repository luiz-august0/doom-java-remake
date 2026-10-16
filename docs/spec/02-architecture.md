# 02 — Architecture

This is a **remake**: the architecture below is ours and does not mirror the file split of the C code.
The original serves as the **rules reference**. The table in §4 says where to look for each rule in linuxdoom-1.10,
not which class "corresponds" to which file.

## 1. Layered view

```
┌──────────────────────────────────────────────────────────────────────────┐
│ doom-app      Main, CLI, config paths, logging, wiring                   │
├──────────────────────────────────────────────────────────────────────────┤
│ doom-platform-lwjgl (GLFW + OpenGL) · doom-platform-javasound (audio)    │
├────────────────────────────────── implements ────────────────────────────┤
│ game modules (pure Java, JDK only)                                       │
│                                                                          │
│  PRESENTATION (complete freedom)                                         │
│   engine ─ loop, ticcmd sources, config, CLI                             │
│   ui     ─ menu, status bar, HUD, automap, intermission, finale, wipe    │
│   render ─ software BSP renderer → indexed framebuffer                   │
│   sound  ─ channels, mixer, music (MUS/MIDI → OPL3)                      │
│  ─────────────── reads the state, never writes (except via Game) ─────── │
│  SIMULATION (free form, fixed content — ADR-007)                         │
│   game       ─ session, state flow, ticcmd, demos, cheats                │
│   player     ─ player movement, weapons, psprites, inventory             │
│   ai         ─ monster behaviour                                         │
│   combat     ─ hitscan, projectiles, damage, explosions, pickups         │
│   mapspecial ─ doors, floors, ceilings, lifts, lights, triggers          │
│   physics    ─ movement, collision, line of sight, traversal             │
│   actor      ─ Actor, state machine, thinkers                            │
│   world      ─ level geometry, blockmap, reject, loading                 │
│   data       ─ game definitions (DSL, ADR-014)                           │
│  BASE                                                                    │
│   wad ─ WAD reading and lump decoding                                    │
│   math ─ fixed-point, BAM, tables, RNG, bbox                             │
│   platform ─ interfaces: VideoSink, AudioSink, InputSource, Clock, Vfs   │
└──────────────────────────────────────────────────────────────────────────┘
```

**Dependency rules** (enforced by JPMS and by each module's `requires`/`exports`, ADR-004):

- The **simulation** (`world` … `game`) imports nothing from the **presentation** or from `platform`. It emits events through its own interfaces (`SimEvents`: sound started, music, message to the player, end of level). The layer above implements those interfaces.
- The **presentation** reads the simulation state through **read-only views** (`WorldView`, `ActorView`, `PlayerView`, in `doom.game.view`) and only changes it through `doom.game.api` commands (new game, load, save, quit). This is how the menu acts. The entity packages are not exported, so writing to the state does not even compile.
- Within the simulation, the dependency is bottom-up in the diagram's order. Cycles are forbidden; when two areas need to call each other (`ai` ↔ `combat`), the upper one depends on the lower one through an interface.

## 2. Maven and JPMS modules

Each row is a Maven module with its own `module-info.java` (ADR-004). The dependency arrows are the only ones allowed.

| Maven module | JPMS module | Contents | `requires` |
|--------------|-------------|----------|------------|
| `doom-math` | `doom.math` | Fixed-point, BAM, tables, RNG, bbox | — |
| `doom-wad` | `doom.wad` | WAD reading, lump directory, format decoders | — |
| `doom-game` | `doom.game` | The entire simulation: `data`, `world`, `actor`, `physics`, `ai`, `combat`, `player`, `mapspecial`, `game`, `persist` | `doom.math`, `doom.wad` |
| `doom-platform-api` | `doom.platform` | `VideoSink`, `AudioSink`, `InputSource`, `Clock` interfaces | — |
| `doom-render` | `doom.render` | 3D renderer, `FrameBuffer`, `Canvas2D`, wipe | `doom.math`, `doom.wad`, `doom.game` (`view` only) |
| `doom-sound` | `doom.sound` | Channels, mixer, music (MUS/MIDI, sequencer, OPL3) | `doom.math`, `doom.wad`, `doom.game` (`view` and events only), `doom.platform` |
| `doom-ui` | `doom.ui` | Menu, status bar, HUD, automap, intermission, finale | `doom.game`, `doom.render`, `doom.wad` |
| `doom-engine` | `doom.engine` | `DoomEngine`, main loop, ticcmd sources, config, CLI | all of the above |
| `doom-platform-lwjgl` | `doom.platform.lwjgl` | GLFW + OpenGL; `provides doom.platform.*` | `doom.platform`, LWJGL |
| `doom-platform-javasound` | `doom.platform.javasound` | `AudioSink` with `javax.sound.sampled` | `doom.platform`, `java.desktop` |
| `doom-app` | `doom.app` | `Main`, `jlink`/`jpackage` | `doom.engine` + backends through `ServiceLoader` |
| `doom-testkit` | `doom.testkit` | IWAD locator, trace format, oracle runner, demo generator (used in `test` scope; the headless engine stays in `doom-engine`, ADR-022) | `doom.wad` only (ADR-022; formerly `doom.engine`) |
| `doom-tools` | `doom.tools` | `ref-coverage`, `trace-diff`, `wad-dump`, `gen-demo`, `demo-suite`, `demo-run`, `frame-dump`, `bench-suite` (`gen-defs` → `doom-codegen`, angle checker → `doom-lint`, ADR-022) | `doom.engine`, `doom.testkit` |
| `doom-codegen` | `doom.codegen` | `gen-tables` and `gen-defs` generators: JDK only, run by hand, output committed (ADR-022) | — |
| `doom-lint` | `doom.lint` | `@Bam` javac plugin for the static check of ADR-006 (ADR-022) | `jdk.compiler` |
| `doom-it` | — (classpath, tests only) | Every `*IT` integration test (ADR-022) | the modules under test (`test` scope) |
| `doom-bench` *(profile `bench`)* | — | JMH | `doom.render`, `doom.game` |
| *(backlog)* `doom-platform-awt` | `doom.platform.awt` | 100% Java backend (Java2D + `javax.sound`) | `doom.platform`, `java.desktop` |

**(ADR-022)** `doom-codegen`, `doom-lint` and `doom-it` are the three modules added by ADR-022. The generators cannot live in `doom-tools`, because `doom-tools` depends on `doom-game`; `doom-lint` depends only on `jdk.compiler`.
`doom-testkit` requires only `doom.wad`, and the synthetic `MapBuilder` lives in `doom-game`'s test sources, because `doom-game` tests → `doom-testkit` → `doom-engine` → `doom-game` would be a cycle that Maven rejects.

```
            doom-math     doom-wad
                 \         /
                  doom-game ───────────────┐   (exports only doom.game.view and doom.game.api)
                /     |      \             │
       doom-render  doom-sound  …          │
              \       |       /            │
               doom-ui (render + game)     │
                      \                    │
                   doom-engine ◄───────────┘
                        │        doom-platform-api ◄── doom-platform-lwjgl / -javasound
                     doom-app  (discovers backends through ServiceLoader)
```

**Exports of `doom.game`:** `doom.game.view` (read) and `doom.game.api` (session commands). Qualified `exports … to` only where indispensable, with a justification in the `module-info`.

## 3. Simulation domain model

### 3.1 World (`doom.world`)

- `Level`: owner of everything that exists during a map. It includes geometry (`Vertex`, `Sector`, `Side`, `Line`, `Seg`, `Subsector`, `Node`), `Blockmap`, `Reject`, `ThinkerList`, the level "clock" (`levelTime`) and the records of active specials.
- `LevelLoader`: builds a `Level` from the map lumps (THINGS, LINEDEFS, SIDEDEFS, VERTEXES, SEGS, SSECTORS, NODES, SECTORS, REJECT, BLOCKMAP). It spawns the actors in the order of the THINGS lump (the order matters for the thinkers).
- World entities use **package-private or public fields** and have no getters or setters. This is a choice for performance and clarity for high-frequency mutable data. Invariants live in the services, not in the entities.
- Fields that only the renderer uses (for example, the rendering `validcount`, per-sector *visplanes*) are **not** kept in the entities. The renderer maintains parallel arrays indexed by the entity id.

### 3.2 Actors and thinkers (`doom.actor`)

- `sealed interface Thinker permits Actor, SectorEffect` with `void think(Level)`.
- `Actor` (the original's *mobj*): position, momentum, angle, type, current state, `tics`, flags (**not** `EnumSet`: an `int` of bits, because flags take part in logic and in saves), `target`, `tracer`, `health`, `reactionTime`, `moveDir`, `moveCount`, `threshold`, sector and blockmap links. Plus the interpolation fields `prevX/prevY/prevZ/prevAngle` (ADR-011), written by the simulation and read only by the renderer.
- `StateMachine`: state switching with the same semantics as the original. This includes chaining zero-duration states in the same tic and calling the new state's action **before** returning.
- `ThinkerList`: insertion order preserved and deferred removal (mark → remove on the next pass), with the same "remove during iteration" semantics as the original. The implementation may be an intrusive list or an array with compaction, as long as the order tests pass.
- `SectorEffect` (`sealed`): `Door`, `FloorMover`, `CeilingMover`, `Platform`, `LightFlash`, `LightStrobe`, `LightGlow`, `FireFlicker`… Each one is a small class with a phase `enum` instead of magic integers.

### 3.3 Physics (`doom.physics`)

- `Movement`: XY movement with sub-steps, wall sliding, friction, gravity and Z movement.
- `CollisionQuery`: the equivalent of the original's position checks. The "output globals" (resulting floor, ceiling and *dropoff*, special lines crossed) live in a **reused** context object (`CollisionResult`), not allocated per call.
- `Sight`: line-of-sight test (REJECT + BSP traversal, like the original).
- `Traversal`: blockmap traversal and collection of sorted intercepts, the basis for *hitscan*, autoaim and line use.

### 3.4 Game (`doom.game`)

- `Game`: the session. It holds the state flow (`LEVEL`, `INTERMISSION`, `FINALE`, `DEMOSCREEN`), the pending actions (new game, load, save, complete level…), the players, the difficulty, the episode and the map, the `Rng` and the demos.
- `TicCmd`: a mutable, reused `record`-like (`forwardMove`, `sideMove`, `angleTurn`, `buttons`, `consistency`, `chatChar`), with the same signed-type semantics as the original.
- `Rng`: two **separate** indices, the game one (affects the simulation) and the "menu" one (inconsequential effects). Using the wrong RNG is a classic desync.

### 3.5 Rule areas

`player`, `ai`, `combat` and `mapspecial` contain the rules proper: monster behaviour, weapons, damage, line triggers and sector effects. The actions that the states fire (`look`, `chase`, `fireMissile`…) are methods referenced by the definitions DSL (ADR-014).

## 4. Where each rule lives in the original

Use this table to find the specification of each area. It is not a class mapping.

| Remake area | Where to read in linuxdoom-1.10 | Attention |
|-------------|---------------------------------|-----------|
| `math` | `m_fixed.c`, `tables.c/h`, `m_bbox.c`, `m_random.c` | `FixedDiv` with saturation; tables are constants |
| `wad` | `w_wad.c`, `r_data.c` (textures/patches) | See [05](05-wad-data.md) |
| `data` | `info.c/h`, `sounds.c/h`, `d_items.c` | ADR-014 |
| `world` | `p_setup.c`, `r_defs.h`, `p_local.h` | Spawn order of the map things |
| `actor` | `p_mobj.c/h`, `p_tick.c`, `d_think.h` | Zero-duration states; deferred removal |
| `physics` | `p_map.c`, `p_maputl.c`, `p_sight.c`, `r_main.c` (`R_PointToAngle*`, `R_PointInSubsector`) | "Renderer" functions used by the simulation live in `math`/`physics` with the original behaviour |
| `ai` | `p_enemy.c` | Biggest source of RNG calls |
| `combat` | `p_inter.c`, `p_map.c` (attacks), `p_mobj.c` (projectiles) | |
| `player` | `p_user.c`, `p_pspr.c`, `d_player.h` | |
| `mapspecial` | `p_spec.c`, `p_doors.c`, `p_floor.c`, `p_ceilng.c`, `p_plats.c`, `p_lights.c`, `p_switch.c`, `p_telept.c` | Limits on platforms, ceilings and buttons |
| `game` | `g_game.c`, `m_cheat.c`, `d_main.c` (demo/title flow), `d_net.c` (ticcmds) | Demo format and `angleturn` quantization |
| `render` | `r_*.c`, `v_video.c`, `f_wipe.c` | Freedom of implementation; same visual result ([04](04-rendering.md)) |
| `ui` | `m_menu.c`, `st_*.c`, `hu_*.c`, `am_map.c`, `wi_stuff.c`, `f_finale.c` | Times and animations in tics; texts in `d_englsh.h` |
| `sound` | `s_sound.c`, `i_sound.c` | Priority, pan and distance-based volume |
| `persist` | `p_saveg.c` (only to know what needs to be saved) | Custom format (ADR-013) |

When linuxdoom's behaviour diverges from DOOM.EXE v1.9, v1.9 prevails (ADR-005). Each case goes into `docs/divergences.md`.

## 5. Root objects and state ownership

```
DoomEngine                       (one per process; several in tests)
 ├─ CommandLine, Config
 ├─ LumpDirectory                (IWAD + PWADs)
 ├─ GameDefs                     (tables built by the DSL; mutable copy)
 ├─ Game                         (session: flow, players[4], Rng, demo)
 │   └─ Level                    (recreated for every map: geometry, ThinkerList, collision contexts)
 ├─ Renderer                     (FrameBuffer, TextureCache, pools)
 ├─ Ui                           (Menu, StatusBar, Hud, Automap, Intermission, Finale, Wipe)
 ├─ SoundSystem                  (channels, music) → Mixer → AudioSink
 └─ MainLoop                     (Clock, InputSource, TicCmdSource, VideoSink)
```

- Mutable `static` is **forbidden** in all the game modules. Constants and immutable tables (`Tables.FINE_SINE`) may be `static final`.
- Dependency injection is manual, by constructor. There is no DI framework.

## 6. Threads and concurrency

Full policy in ADR-020. Summary:

| Thread | Type | Responsibility |
|--------|------|----------------|
| **main** | Platform (the process's first thread) | GLFW events → simulation (tics) → rendering → OpenGL presentation. On macOS it **has** to be the first thread (`-XstartOnFirstThread`). |
| **audio** | Dedicated platform, `MAX_PRIORITY` | Mixer loop: reads commands, mixes SFX and music, writes PCM blocks to the `SourceDataLine` (which blocks until there is space, which sets the pace). |
| **loaders** | Virtual threads | Level loading: texture composition, decoding of sounds and patches, *precache*. Independent tasks that only produce data; `main` waits for all of them before assembling the `Level`. |
| **io** | Virtual threads | Saving the game, writing config, screenshot (PNG), logs. Triggered by `main`, without blocking it. |
| *(post-v1)* **render workers** | Fixed platform pool | Rendering in parallel strips ([04 §9](04-rendering.md#9-performance)). |

**main → audio communication:** lock-free SPSC queue (`AudioCommandQueue`, a circular array of pre-allocated mutable commands):
`StartSfx(channel, sfx, vol, sep, pitch)`, `UpdateChannel`, `StopChannel`, `PlayMusic`, `StopMusic`, `SetVolume`.
The channel logic runs on the **main** thread; the audio thread only executes commands and mixes.

**Asynchronous save:** the simulation produces the save *snapshot* in memory (`byte[]`) in the tic itself, synchronously and deterministically. Only the disk write goes to a virtual thread.

The simulation is single-threaded and deterministically ordered. No simulation structure is accessed by another thread.

## 7. Main loop

Same semantics as the original's loop for single-player:

```text
start = clock.now()
loop:
    input.poll()                                   // GLFW → event queue
    targetTic = (clock.now() - start) / TIC_NANOS
    ran = 0
    while gametic < targetTic and ran < MAX_TICS_PER_FRAME:   // 10; avoids a lag spiral
        ui.dispatchEvents()       // original order: menu → chat/HUD → automap → game
        cmd = ticSource.next()    // local input or demo reading
        demoRecorder.write(cmd)   // if recording (quantizes angleTurn like the original)
        ui.menuTick()
        game.tick(cmd)            // flow + level simulation
        gametic++; ran++
    if ran == MAX_TICS_PER_FRAME: start = adjust()   // discards accumulated lag
    alpha = interpolation ? ticFraction : 1.0
    display(alpha)                // world + UI, or wipe step
    sound.update(listener)
    video.present(framebuffer, palette)               // vsync / FPS limiter
```

- `-timedemo`: ignores the clock, runs one tic and one frame per iteration and measures the total time.
- **Wipe (melt):** as in the original, the simulation **does not advance** during the wipe. The wipe animation has its own timing in tics.
- **Pause and menu:** the menu pauses the game in single-player, as in the original.
- `TicCmdSource` is the seam for multiplayer: in v1 there are `LocalTicSource` and `DemoTicSource`; in the future, `NetTicSource` implements lockstep.

## 8. Memory and performance

- Lumps come from `MappedByteBuffer`. Composed textures, columns and flats live in renderer caches.
- **Zero allocation in steady state** in the renderer, the mixer and the main loop: `vissprite`, `drawseg`, `visplane`, intercepts, audio commands and input events use pre-allocated, growable arrays. In the simulation, `new Actor`/`new Door` at spawn is acceptable (a discrete event); apart from that, nothing allocates per tic. Verification: a test with JFR in `-timedemo` ([09](09-testing-quality.md)).
- Forbidden in the hot path: `stream()`, capturing lambdas, *boxing*, `String.format`, collection iterators.

## 9. Errors

- Fatal game error → `DoomFatalException` (unchecked) with a message equivalent to the original's. `doom-app` shows it in a dialog or on the console and exits with a code ≠ 0.
- **Bad data in a PWAD:** when the original would access out of bounds (undefined behaviour), the remake **must not** throw a raw `ArrayIndexOutOfBoundsException`. It either (1) emulates the effect that Chocolate Doom documents (for example, REJECT and special-lines-crossed *overflow*), or (2) logs a *warning* and applies a safe fallback. The case goes into `docs/divergences.md`.

## 10. Code conventions

- Formatting: `.editorconfig` + the IDE's formatter (no plugin in the build, ADR-021). Imports without wildcards.
- Simulation: `@VanillaRef("file.c:Function")` on methods that reproduce a specific rule; `// VANILLA-BUG:` on a preserved bug; `// DIVERGE:` on an intentional deviation, with a reference to `divergences.md`.
- Fixed-point and BAM: type annotations `@FixedPoint int` **(ADR-022)**, `@Bam int` for the static check of ADR-006 (javac plugin in `doom-lint`).
- Names: from the game domain, in English (`Actor`, `Door`, `FloorMover`, `MonsterAi.chase`). Do not use C prefixes (`P_`, `mo->`, `_t`).
- Tests: `*Test` (unit), `*IT` (integration/IWAD), `*Bench` (JMH in `src/jmh`).
- Comments and documentation in English (ADR-022; this replaces the former Portuguese rule); identifiers in English; game texts in English, as in the original.
