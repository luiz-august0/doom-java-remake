# 01 — Architecture decisions (ADRs)

Each decision has a status, context, choice, rejected alternatives and consequences.
To change a decision, create a new ADR that supersedes it (`Supersedes ADR-0xx`) instead of editing the old one.

Summary:

| ADR | Topic | Decision |
|-----|-------|----------|
| 001 | Language | Java 25 (LTS) |
| 002 | Build | Multi-module Maven (reactor), Apache plugins, native profiles |
| 003 | Platform | LWJGL 3.4.x (GLFW + OpenGL 3.3 core) — the only runtime dependency |
| 004 | Modularization | Maven modules + JPMS (`module-info`); isolated simulation; platform through SPI |
| 005 | Behaviour reference | linuxdoom-1.10 rules; DOOM.EXE v1.9 behaviour (Chocolate Doom as the oracle) |
| 006 | Arithmetic | Fixed-point and BAM in primitive `int` + pure static utilities |
| 007 | Remake vs. port | Reimplementation with its own architecture and behavioural equivalence; `@VanillaRef` in the simulation |
| 008 | Renderer | Software, palettized (8-bit), arbitrary resolution, extended precision |
| 009 | Presentation | Indexed framebuffer → `R8` texture + palette in the shader |
| 010 | Widescreen | Hor+ with 1.2 aspect correction (5:6 pixel) |
| 011 | Uncapped FPS | Simulation fixed at 35 Hz + interpolation in the renderer only |
| 012 | Audio | 100% Java: own mixer + `javax.sound.sampled`; music through OPL3 emulation + GENMIDI |
| 013 | Save games | Custom, versioned binary format |
| 014 | Game definitions | Custom format (Java DSL) bootstrapped by a tool and checked against `info.c` by a test |
| 015 | Testing | JUnit 5 + AssertJ; per-tic trace against an instrumented Chocolate Doom; isolated, optional JMH |
| 016 | Distribution | `jlink` + `jpackage` per OS |
| 017 | Configuration | Custom `key = value` file, no library; vanilla-compatible CLI |
| 018 | Logging | `System.Logger` (JDK), no SLF4J |
| 019 | License and assets | GPLv2; no commercial IWAD in the repository |
| 020 | Concurrency | Virtual Threads for loading, I/O and tests; platform threads for the loop, audio and parallel rendering |
| 021 | Dependencies | Absolute minimum; everything the JDK allows is pure Java; the enforcer blocks the rest |
| 022 | Implementation adjustments | Amends ADR-004, ADR-014 and ADR-015: adds `doom-codegen`, `doom-lint` and `doom-it`; multiplayer demo playback and minimal DEHACKED in v1; one `StateAction` enum; demos live in the session; English comments and documentation |

---

## ADR-001 — Java 25 (LTS)

**Status:** accepted.

**Context.** The core is CPU-bound (column renderer, simulation) and needs predictable latency.
Java 25 is the current LTS (September 2025).

**Decision.** Java 25 as the minimum and target version. Use `record` (for immutable lump data),
`sealed`, pattern matching in `switch` and text blocks freely. The FFM API (final since Java 22) remains available if needed,
but LWJGL already handles native access.

**Rejected alternatives.**
- *Java 21*: works, but there is no reason to stay on the previous LTS in a new project.
- *Kotlin*: viable for a remake, but the simulation depends on integer arithmetic with overflow and on predictable primitive types. Kotlin's unsigned types are inline classes with a cost and subtle semantics, and comparing the Java rule with the C reference is more direct.
- *Incubator/preview APIs* (Vector API, Valhalla): **forbidden** in production code. They may be evaluated in an isolated benchmark.

**Consequences.** The embedded runtime (via `jlink`, including `java.desktop` for audio) ends up in the 50–80 MB range. GC: default G1. Since the goal is zero allocation in steady state, the choice of GC is secondary. Generational ZGC remains a tuning option, not a requirement.

---

## ADR-002 — Multi-module Maven

**Status:** accepted. Supersedes the initial version (Gradle).

**Decision.** Maven 3.9+ with a parent POM (`doom-java-parent`, `packaging pom`), a multi-module *reactor*, and `dependencyManagement` + `pluginManagement` centralized in the parent.
Modules and the dependencies between them in [02 §2](02-architecture.md#2-maven-and-jpms-modules).

- `maven-compiler-plugin` with `<release>25</release>`.
- **Apache-only plugins** whenever possible: `compiler`, `surefire` (JUnit 5), `failsafe` (`*IT` tests with IWAD), `jar`, `jlink`, `enforcer` (Maven and JDK version, ban on unapproved dependencies — see ADR-021).
- `exec-maven-plugin` only to call `jpackage` and the `doom-tools` tools.
- **LWJGL natives:** *profiles* activated by OS and architecture (`<os><family>mac</family><arch>aarch64</arch></os>` → `natives-macos-arm64` etc.), in the format that LWJGL itself documents for Maven.
- Maven Wrapper (`mvnw`) committed.

**Rejected alternative.** *Gradle*: more flexible for custom tasks, but Maven is the team's choice, the build is simple and the special tasks (generating definitions, running demos) are Java programs in `doom-tools` called through `exec`.

---

## ADR-003 — LWJGL 3.4.x only for window, input and GPU

**Status:** accepted.

**Decision.** LWJGL is the project's **only runtime dependency** (ADR-021), and only the `lwjgl`, `lwjgl-glfw` and `lwjgl-opengl` modules, with the version pinned in the parent POM (3.4.3 is the latest at the time of this spec).

- **GLFW:** window, GL context, keyboard, mouse with *raw motion* (`GLFW_RAW_MOUSE_MOTION`), gamepads (gamepad API with SDL mappings), monitors and HiDPI.
- **OpenGL 3.3 core:** only to present the framebuffer (ADR-009). It is supported on macOS (up to 4.1 core), Windows and Linux.
- **Audio does not use LWJGL:** output goes through `javax.sound.sampled` (pure Java, ADR-012).

**macOS:** GLFW requires running on the process's *main thread* → JVM with `-XstartOnFirstThread`. The launcher and `jpackage` include this flag ([02 §6](02-architecture.md#6-threads-and-concurrency)).

**Why not 100% Java (AWT/Swing) here.** There is no reliable way in pure Java to get vsync, *raw* mouse input for FPS, gamepad and low-latency fullscreen presentation.
Since the platform is isolated in a module (`doom-platform-lwjgl`), a 100% Java backend (`doom-platform-awt`, Java2D + `javax.sound`) is possible and is in the backlog. It serves to run without natives, with an inferior experience.

**Rejected alternatives.**
- *libGDX*: brings scenes, sprites and assets that a software renderer does not use, and hides fine control of the loop.
- *Vulkan*: disproportionate complexity for a texture *blit*.
- *SDL3 via FFM (pure Java + native library)*: viable and aligned with "pure Java", but it would require writing and maintaining the *bindings* and packaging SDL per OS. Remains an alternative in case LWJGL becomes a problem.

---

## ADR-004 — Modular architecture: Maven modules + JPMS

**Status:** accepted.

**Decision.**
- Each area of the system is a **Maven module** with its own `module-info.java` (JPMS). The boundaries are enforced by the **compiler**, not by convention or an extra tool:
  - a module only sees what it declared in `requires`;
  - a module only exposes what it declared in `exports`. Internal packages are invisible.
- **Simulation isolated from presentation:** `doom-game` does not depend on `doom-render`, `doom-ui`, `doom-sound` or on the platform. Any attempt to import them does not even compile.
- **Read-only access to state:** `doom-game` exports to the presentation only `doom.game.view` (interfaces `WorldView`, `ActorView`, `PlayerView`, with an index-based API: `floorHeight(sector)`, `actorX(i)`…) and `doom.game.api` (commands: new game, load, save). The entity packages (`world`, `actor`…) are **not** exported. The *view* implementations are monomorphic and the JIT inlines them; the M2 benchmark confirms that there is no cost ([09](09-testing-quality.md)).
- **Platform through SPI:** `doom-platform-api` defines interfaces (`VideoSink`, `AudioSink`, `InputSource`, `Clock`). The backend is loaded by `ServiceLoader` (`provides … with …` in the `module-info`), which allows swapping LWJGL for AWT without recompiling the rest.
- Only `doom-platform-lwjgl` depends on anything outside the JDK.

**Consequences.** Headless tests use `doom-game` + `doom-render` with no backend at all. ArchUnit becomes unnecessary. `jlink` produces a minimal runtime because all modules are named.

---

## ADR-005 — Behaviour reference

**Status:** accepted.

**Context.** The `linuxdoom-1.10` that we have in `../DOOM` is **not** identical to the DOS DOOM.EXE v1.9.
For example, it records and only accepts demos with version `110`, whereas the demos inside the IWADs are `109`.
Chocolate Doom documented and reproduced these differences, including per-executable variants (1.9, Ultimate, Final).

**Decision.**
- **Game rules:** `linuxdoom-1.10`, read as a specification (ADR-007).
- **Observable behaviour:** DOOM.EXE v1.9 and variants. Where linuxdoom diverges, the behaviour that Chocolate Doom reproduces prevails. The divergence is recorded in `docs/divergences.md` with the reference excerpt.
- Enum `GameVersion { V1_9, ULTIMATE, FINAL, FINAL2 }` selectable through `-gameversion`, with automatic detection from the IWAD, as in Chocolate.
- Accepted demos: version `109`; version `110` is accepted and treated as 1.9. Demos recorded by the remake are written as `109`.

**License.** Chocolate Doom is also GPLv2, so consulting it is compatible with the project's license (ADR-019).

---

## ADR-006 — Fixed-point and angles as primitive `int`

**Status:** accepted.

**Decision.**
- `fixed_t` → `int` (16.16). Operations in a `final class Fixed` with pure `static` methods: `mul(a, b)` = `(int) (((long) a * b) >> 16)`; `div(a, b)` replicates vanilla `FixedDiv` **exactly**, including the overflow saturation (`(abs(a) >> 14) >= abs(b)` → `MININT`/`MAXINT`).
- `angle_t` (unsigned 32) → `int`. Every comparison uses `Integer.compareUnsigned`, every shift to a table index uses `>>>`. Utilities in a `final class Angle` (`ANG45`, `ANG90`, `toFine(a) = a >>> ANGLETOFINESHIFT`, `lt(a, b)`…).
- Lump `byte`/`short` values are read with a mask (`& 0xFF`, `& 0xFFFF`) when the C uses `unsigned`.
- Using `float`/`double` in the simulation is **forbidden**. The trigonometric tables are constants extracted from the original, not recomputed (ADR-014).

**Rejected alternative.** *Wrappers* (`Fixed`, `Angle` as an object or `record`): they allocate or depend on escape analysis in the hottest path of the game.

**Consequences.** The biggest bug risk is the sign in angles. Mitigation: (1) property tests for `Angle`; (2) static checking (own javac plugin in `doom-lint`, ADR-021/ADR-022) that flags `<`/`>` and `>>` between variables annotated `@Bam`; (3) the per-tic trace (ADR-015) finds the divergence at the first tic in which it appears.

---

## ADR-007 — Remake with behavioural equivalence (not a port)

**Status:** accepted. Supersedes the initial version of this ADR ("structural port").

**Context.** The goal is to rewrite the game from scratch in idiomatic Java and, at the same time, to have a game that behaves exactly like the original.
In the simulation, "exactly" is verifiable: the `.lmp` demos only stay in sync if every tic produces the same state.

**Decision.**
- **Our own architecture.** Packages by domain (`world`, `actor`, `physics`, `ai`, `combat`, `player`, `mapspecial`…), our own names and classes with clear responsibilities. Polymorphism instead of function pointers. No C file or function has a mandatory counterpart. See [02](02-architecture.md).
- **The C code is a specification, not a source.** For each rule, the original (and Chocolate Doom, when it diverges) is read and the rule is implemented in our design.
- **Equivalence invariants** that the implementation **must not** violate, however free its form:
  1. same sequence of game RNG calls (`P_Random`), in quantity **and** order, per tic;
  2. same arithmetic: 16.16 fixed-point, BAM, tables, truncations, `int` overflow;
  3. same update order: players by index, thinkers in creation order, deferred removal;
  4. same spatial iteration order: blockmap, per-sector object lists and intercepts sorted by distance, with the same tie-breaking criterion;
  5. same limits that affect gameplay (for example, `MAXSPECIALCROSS`, `MAXINTERCEPTS` and the Pain Elemental's limit of 20 *lost souls*) and same overflow effects that Chocolate Doom emulates;
  6. same bugs, marked with `// VANILLA-BUG: <description>`.
- **Lightweight traceability.** Methods that reproduce a specific rule of the original get `@VanillaRef("p_map.c:P_TryMove")`, which may cite more than one function. It is mandatory in `sim`/`game` and optional outside them. The `doom-tools ref-coverage` tool cross-references the annotations with the linuxdoom functions and lists the **rules not yet covered**. It serves as a checklist, not as a 1:1 mapping.
- **Complete freedom outside the simulation.** Renderer, UI (menus, HUD, automap, intermission), audio, WAD and platform only need to preserve the visual and audible *result*. For example, the menu can be a small data-driven widget framework instead of the original `menu_t` tables.

**Allowed in the simulation:**
- `Actor` with sub-components, `enum` for door states, `switch` with pattern matching, `sealed interface Thinker`;
- replacing intrusive linked lists with another structure, **provided that** the iteration order and the effect of inserting or removing during iteration are the same;
- unifying repeated code from the original (for example, line traversal with *callbacks*) into our own abstractions.

**Not allowed in the simulation:**
- "fixing" collisions, *hitscan* or AI;
- replacing fixed-point with `double`;
- changing the evaluation order of expressions with RNG. Where the C depends on unspecified order (`P_Random() - P_Random()`), the order that Chocolate Doom fixed prevails (`P_SubRandom`).

**Risks and mitigation.** Freedom of form increases the risk of a subtle desync. The mitigation is the **per-tic trace** (ADR-015), run from the first simulation milestone: every divergence shows up at the exact tic and field.

**Rejected alternatives.**
- *Structural port (function-by-function translation)*: safer for sync, but not what the project wants. The result would be C written in Java.
- *"Faithful in feel" remake, without demo sync*: frees physics to use `double` and allows bug fixes, but loses the only objective proof of fidelity and changes gameplay in edge cases.
- *Clean-room*: would require not reading the original code, which is incompatible with exact fidelity.

---

## ADR-008 — Palettized software renderer at arbitrary resolution

**Status:** accepted.

**Decision.** Keep DOOM's algorithm (front-to-back BSP, vertical columns for walls and sprites, horizontal *spans* for floors and ceilings via *visplanes*), generalized to any `W×H`:

- `byte[W*H]` framebuffer of palette indices;
- lighting through `COLORMAP` (32 levels + invulnerability), Spectre *fuzz*, sky and palette effects identical to the original;
- static limits (`MAXVISPLANES`, `MAXDRAWSEGS`, `MAXVISSPRITES`, `MAXOPENINGS`) become growable structures. They do not affect the simulation, so removing them is safe;
- **optional extended precision** (calculations in `long`, fixes for *wall wiggle*, *long wall error*, sprite precision) controlled by `RenderOptions.precisionFixes`. Off in vanilla mode to guarantee pixel-identical golden frames (G2).

**Critical rule.** "Renderer" functions that the simulation uses (`R_PointToAngle`, `R_PointToAngle2`, `R_PointInSubsector`, trigonometric tables) stay in `doom.math`/`doom.physics` with the vanilla behaviour **untouched**. The renderer may have its own high-precision versions, under other names.

**Rejected alternatives.**
- *GPU renderer (polygons)*: changes the look (floors, distance lighting, sprites) and is a separate project.
- *RGB/true color framebuffer*: loses the fidelity of colormap lighting and makes palette effects more expensive.

---

## ADR-009 — Presentation through an `R8` texture + palette in the shader

**Status:** accepted.

**Decision.** On every frame, the indexed `byte[]` is uploaded as a `GL_R8` texture (via `glTexSubImage2D` from a pre-allocated direct `ByteBuffer`). A *fragment shader* looks up a 256×1 RGBA palette texture.
Switching palettes (damage, item, radiation suit) and applying gamma costs only a 1 KB upload.
The full-screen *quad* applies scaling and aspect correction with *nearest* or "sharp bilinear" filtering (configurable).

**Why.** It separates the internal resolution from the window resolution, makes *upscaling* cheap and keeps the CPU free of palette-conversion work.

**Future.** Optional post-processing (scanlines, CRT) fits in the same shader without touching the core.

---

## ADR-010 — Hor+ widescreen with 1.2 aspect correction

**Status:** accepted.

**Context.** DOOM renders 320×200 for 4:3 monitors, so each pixel is 1.2× taller than it is wide (5:6 pixel).
The 90° horizontal FOV is tied to that width of 320.

**Decision.**
- The renderer works in "vanilla pixel space" (5:6 pixels). For an internal height `H` and a screen aspect `A` (for example 64/27 for 21:9): `W = round(H × 1.2 × A)`.
  Examples for `H = 200`: 4:3 → 320; 16:9 → 427; 21:9 → 569. For `H = 1080` at 21:9: `W ≈ 3072`, presented at 2560×1080.
- The projection distance is computed from the equivalent 4:3 width (`H × 1.2 × 4/3`). Thus the vertical FOV is always vanilla's and the horizontal one grows with the aspect.
- **"Square pixels"** option (no aspect correction) for those who prefer the look of modern ports: `W = H × A`, with no vertical stretch.
- 2D elements (menus, intermissions, status bar) use a virtual 320×200 screen, scaled and centered. The side areas are filled in (see [04 §6](04-rendering.md#6-2d-layer-and-widescreen)). Graphics wider than 320, such as those of the IWADs re-released with widescreen assets, are centered by the `leftoffset`.

---

## ADR-011 — Uncapped FPS through interpolation

**Status:** accepted.

**Decision.** The simulation runs in fixed steps of 1/35 s. The renderer draws on every frame with
`alpha = (now − lastTicTime) / ticDuration`, interpolating between the previous and the current state:
position and angle of mobjs, the player's `viewz` and *bob*, sector floor and ceiling heights, *psprites* (weapon) and texture *scroll*.
The local player's view angle receives the mouse input **per frame**, with the residue accumulated and quantized into the `ticcmd` (see [04 §7](04-rendering.md#7-uncapped-fps-and-interpolation)).

**Rules.**
- The "previous" state is recorded by the simulation at the start of every tic (`Actor.prevX…`). These are extra fields that the simulation **writes but never reads**.
- Teleport, *spawn* and level start turn off interpolation for that object for one tic.
- `-timedemo` and vanilla mode turn off interpolation.

**Rejected alternative.** *Running the simulation above 35 Hz*: breaks demos, physics and animation timings.

---

## ADR-012 — 100% Java audio: own mixer, `javax.sound.sampled`, OPL3 music

**Status:** accepted.

**Decision.**
- **SFX:** own software mixer (`doom.sound.mixer`), with 8 channels by default (up to 32 configurable), reproducing the original's channel logic (priority, stereo separation and distance-based volume, cutting off by sound type) and resampling from 11025 Hz to 48 kHz.
- **Output:** `javax.sound.sampled.SourceDataLine` (16-bit stereo PCM, 48 kHz), fed by a dedicated platform thread. Configurable buffer, target of ~512–1024 frames.
- **Music:** MUS → MIDI events → **OPL3 emulator written in Java** (own port of Nuked OPL3, LGPL-2.1, compatible with GPLv2) using the timbres of the IWAD's own `GENMIDI` lump. This is the sound of the DOS version, with no SoundFont or external library. MIDI lumps from PWADs go through the same sequencer.
- `MusicSynth` interface for alternative backends. *Backlog:* SoundFont through Gervill (`javax.sound.midi`, which requires `--add-exports` of an internal JDK package).

**Exit criterion (M1 spike).** Measure the end-to-end latency (sound event → output) on macOS, Windows and Linux. If it exceeds **~50 ms** on any OS, an alternative `AudioSink` with OpenAL via LWJGL (`lwjgl-openal`) comes in **only for that OS**. The mixer stays the same; only the output changes.

**Rejected alternatives.**
- *OpenAL as the default*: lower latency, but adds natives and goes against the pure-Java guideline (ADR-021). Remains as a fallback.
- *One source per channel with 3D spatialization*: the result would not be the original's pan and volume.

---

## ADR-013 — Save games in a custom format

**Status:** accepted.

**Decision.** Custom binary format, little-endian, with a magic header, format version, game version, IWAD/PWADs (name and hash) and per-section blocks.
References between objects (a monster's target, `tracer`, a thinker's sector) are serialized by index.
The save restores more than vanilla does, which loses `target`/`tracer`. This is acceptable because saves do not take part in demo sync.
Details in [08](08-persistence.md).

**Rejected alternative.** *Vanilla format (dump of 32-bit structs)*: low value and high complexity.

---

## ADR-014 — Game definitions in a custom format, verified against the original

**Status:** accepted.

**Context.** Animation and AI states (~970), actor types (~137), sprites, sounds and weapons define a large part of the behaviour.
In the original, they are C tables (`info.c`, `sounds.c`, `d_items.c`). The numeric indices matter: they appear in demos, in DEHACKED and in the order of the states.

**Decision.**
- **Custom format:** a Java DSL of *builders*, readable and grouped by actor. Illustrative example of the shape (values not checked):
  ```java
  actor(ZOMBIEMAN).editorNum(3004).health(20).speed(8).radius(20).height(56)
      .painChance(200)
      .sounds(s -> s.see(POSIT1).attack(PISTOL).pain(POPAIN).death(PODTH1).active(POSACT))
      .flags(SOLID, SHOOTABLE, COUNTKILL)
      .states(st -> st
          .spawn("POSS", "AB", 10, MonsterAi::look)
          .see("POSS", "AABBCCDD", 4, MonsterAi::chase)
          /* ... */);
  ```
  **(ADR-022)** Actions are referenced through a single `StateAction` enum instead of method references, dispatched by the simulation.
  The global state indices are assigned in the **same order** as the original. At the end, the DSL builds flat tables (`State[]`, `ActorType[]`) that the engine uses at run time.
- **Tool-based bootstrap:** `doom-tools gen-defs` reads `info.c`/`sounds.c`/`d_items.c` once and generates the first draft of the DSL. From then on the code is **ours** and is edited by hand (comments, grouping, names).
- **Continuous verification:** `DefsEquivalenceTest` compares, field by field, the flat tables built by the DSL with those of `info.c`, extracted by the same parser. A serialized *snapshot* of the original tables is committed, so CI can run without the C repository.
- **Numeric tables** (`finesine`, `finetangent`, `tantoangle`, `rndtable`): constants copied from the original by a tool. They are **not** recomputed with `Math.sin`, because the rounding differs.
- The built tables are **mutable** and copied per game, in preparation for DEHACKED.

**Why.** We gain the readability of a remake without giving up equivalence guaranteed by a test. Transcribing by hand would be the biggest potential source of silent bugs.

---

## ADR-015 — Testing: JUnit 5 + AssertJ + external oracle

**Status:** accepted.

**Decision.**
- **JUnit 5** (Jupiter) + **AssertJ**: the only test libraries of the normal suite (`test` scope).
- Unit tests (`*Test`, Surefire) and integration tests with IWAD (`*IT`, Failsafe, skipped without `DOOM_IWAD_DIR`).
- Architecture rules: guaranteed by JPMS (ADR-004), without ArchUnit.
- **Microbenchmarks:** JMH in an isolated `doom-bench` module, outside the default build (profile `bench`). It is the only exception to the dependency policy, because measuring the renderer rigorously requires JMH. For rough benchmarks, `-timedemo` is enough.
- The most important test is the **per-tic trace**. A small patch to Chocolate Doom emits, on every tic of a demo, a record (`gametic`, RNG index, position, angle and health of each player, hash of all actors).
  The remake emits the same record and `doom-tools trace-diff` points to the **first divergent tic** and the field that diverged.
- The demo suite runs the demos **in parallel** (one headless simulation per task, see ADR-020).

Details in [09](09-testing-quality.md).

---

## ADR-016 — Distribution with `jlink` + `jpackage`

**Status:** accepted.

**Decision.** Minimal runtime via `maven-jlink-plugin` (game modules + `java.base`, `java.desktop` for audio, `jdk.unsupported` if LWJGL requires it) and installers via `jpackage` (called through `exec-maven-plugin`):
`.dmg` (macOS arm64 and x64, with `-XstartOnFirstThread`), `.msi` (Windows x64) and `.deb`/`.tar.gz` (Linux x64/arm64). Matrix build on GitHub Actions.
There will also be a *fat jar* for developers (`java -jar`).

---

## ADR-017 — In-house configuration, no library

**Status:** accepted.

**Decision.** Text file `doom-java.cfg` (`key = value`, comments with `#`), read and written by about 150 lines of our own code,
in `~/Library/Application Support/doom-java` (macOS), `%APPDATA%\doom-java` (Windows) or `$XDG_CONFIG_HOME/doom-java` (Linux).
Optional import of the vanilla `default.cfg` (keys, volumes, sensitivity).
Vanilla-compatible CLI (`-iwad`, `-file`, `-warp`, `-skill`, `-playdemo`, `-timedemo`, `-record`, `-nomonsters`, `-respawn`, `-fast`…). See [07](07-ui-input-config.md).

---

## ADR-018 — Logging with `System.Logger`

**Status:** accepted. Keeps the game modules free of dependencies (ADR-021). The app configures the output (console + file) through its own simple `System.LoggerFinder`. The log file is written outside the main loop (ADR-020).

---

## ADR-019 — GPLv2 license and asset policy

**Status:** accepted.

**Decision.**
- The code is **GPLv2**. Even though it is rewritten from scratch, the remake reproduces behaviour studied directly in id Software's code (and in Chocolate Doom, also GPLv2), so it is treated as a derivative work. The repository's `NOTICE` credits both sources.
- **No commercial IWAD** (DOOM, DOOM II, Final DOOM) is committed or downloaded by the build.
- **(Amended by ADR-023)** The free IWADs (shareware `doom1.wad`, Freedoom, FreeDM) are committed under `.iwads/`.
- **Freedoom** (BSD license) may be used in automated tests and in CI.
- The shareware `DOOM1.WAD` can only be redistributed unmodified and under the shareware terms. For that reason it does not go into the repository either; tests that depend on it look for the file locally. *(Superseded by ADR-023: `doom1.wad` is committed unmodified.)*
- Tests that require commercial IWADs read `DOOM_IWAD_DIR` and are **skipped** (not failed) when the variable is absent.

---

## ADR-020 — Concurrency: Virtual Threads where they help, platform threads where there is a deadline

**Status:** accepted.

**Context.** Virtual Threads (final since Java 21) make it cheap to have thousands of tasks that **block** (I/O, waiting). They do **not** make CPU-bound code faster.
The scheduler is a `ForkJoinPool` with one carrier thread per core, and a virtual thread only yields its carrier when it blocks. There is no time-based preemption.
In a game, most of the work is CPU-bound and has a deadline (a frame, an audio block). So the choice of where to use them matters.

**Decision.**

| Work | Thread type | Why |
|------|-------------|-----|
| Main loop (input, simulation, render, presentation) | **Platform: the *main thread*** | GLFW requires the first thread on macOS; the simulation is sequential by definition |
| Audio output (mixer + `SourceDataLine.write`) | **Dedicated platform thread**, `MAX_PRIORITY` | It cannot be late; virtual threads have no priority and may wait behind CPU-bound tasks on the same carrier |
| Parallel rendering by strips (post-v1) | **Fixed platform pool**, size = cores − 2 | CPU-bound with a frame deadline; virtual threads bring no gain and add scheduling uncertainty |
| Level and asset loading: lump reading, texture composition, sound decoding, *precache* | **Virtual threads** (`Executors.newVirtualThreadPerTaskExecutor()`) | Many short, independent tasks, with mixed I/O; simple code, one task per texture or sound |
| Saving the game, screenshot (PNG encoding), writing config and logs | **Virtual threads** | I/O outside the main loop, without stalling the frame |
| Demo suite and regression tests | **Virtual threads** | One headless simulation per demo. The real gain comes from the cores, but the code becomes trivial |
| Tools (`doom-tools`: WAD dump, batch `trace-diff`) | **Virtual threads** | Same reason |

- **Forbidden:** `synchronized` with blocking I/O inside virtual tasks (use `ReentrantLock`); costly per-task `ThreadLocal`.
- **Structured Concurrency** (`StructuredTaskScope`) is still *preview* in Java 25 → forbidden in production (ADR-001). When it becomes final, it replaces the `invokeAll`s of the loading code.
- **The loading result is deterministic:** parallel tasks only **produce** data (composed textures, decoded sounds). Assembling the `Level` and spawning actors remain sequential and in the original order.

---

## ADR-021 — Minimum dependencies, maximum pure Java

**Status:** accepted.

**Decision.** Everything that can be written in Java with the JDK is written in Java with the JDK.

**Allowed dependencies** (the `maven-enforcer-plugin` fails the build on any other):

| Dependency | Scope | Module | Reason |
|------------|-------|--------|--------|
| LWJGL (`lwjgl`, `lwjgl-glfw`, `lwjgl-opengl` + natives) | runtime | `doom-platform-lwjgl` | Window, input and GPU (ADR-003) |
| JUnit 5 (`junit-jupiter`) | test | all | ADR-015 |
| AssertJ (`assertj-core`) | test | all | ADR-015 |
| JMH | test | `doom-bench` only (optional) | ADR-015 |
| `lwjgl-openal` | runtime, conditional | `doom-platform-lwjgl` | Only if the audio spike rejects `javax.sound` (ADR-012) |

**Done in pure Java, in the project itself:** WAD reading and decoding of all lump formats; OPL3 emulator and MUS/MIDI sequencer; mixer and resampling; PNG encoder (through `java.util.zip`, without `javax.imageio`, to avoid pulling in `java.desktop` just for that); config and CLI parser; logging (`System.Logger`); save serialization; trace format and comparison; static angle checking (own javac plugin on top of the JDK's *Compiler Tree API*, in `doom-lint` — ADR-022).

**JDK modules at runtime:** `java.base` in all game modules; `java.desktop` only in the audio module (`javax.sound.sampled`) and in the future AWT backend; `jdk.unsupported` if LWJGL requires it.

**Build tools** (Maven plugins) are not game dependencies, but follow the same spirit: prefer the official Apache plugins.
Formatting via `.editorconfig` + the IDE's formatter, with no formatting plugin in the build.

---

## ADR-022 — Implementation adjustments

**Status:** accepted. Amends ADR-004, ADR-014 and ADR-015 and the module table of spec [02 §2](02-architecture.md#2-maven-and-jpms-modules).

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
6. **Demos live in the session**, not in engine `TicCmdSource` implementations (amends spec [08 §1.2](08-persistence.md#12-integration)): see [`docs/contracts.md` §11](../contracts.md#11-cross-module-api-notes).
7. **Language.** Comments and documentation are in English (amends spec [02 §10](02-architecture.md#10-code-conventions)).
8. **Small corrections from Chocolate Doom:**
   - "registered" is detected by `E3M1`;
   - the map-8 sound rule also applies to Doom II's MAP08;
   - `P_InitSwitchList` includes retail;
   - JUnit stays on 5.x (5.14.4);
   - trig tables ship as a generated binary resource because a 10,240-element array literal exceeds the 64 KB `<clinit>` limit.

**Consequences.** The module table in spec [02 §2](02-architecture.md#2-maven-and-jpms-modules) gains three rows. ADR-015's test placement moves every `*IT` to `doom-it`.
Two backlog items move into v1: DEHACKED (subset) and multiplayer demo playback.

---

## ADR-023 — Free IWADs in the repository, bundled releases and Docker

**Status:** accepted. Amends ADR-019 (asset policy): the shareware `doom1.wad` and Freedoom now live in the repository.

**Context.** A game that needs a manual download of game data is hard to run. The three freely redistributable IWADs
(DOOM shareware 1.9, Freedoom, FreeDM) are small enough to commit (each file is below 50 MB, so no Git LFS).

**Decisions.**
1. **`.iwads/` is versioned**: `doom1.wad` (unmodified shareware 1.9), `freedoom1.wad`, `freedoom2.wad`, `freedm.wad`,
   `README.md` (licenses, MD5) and Freedoom's `COPYING.txt` (BSD 3-clause). `doom1.wad` must never be modified.
   Every other `*.wad` (commercial IWADs, PWADs, generated test WADs) stays out of the repository.
2. **Commercial IWADs stay forbidden**: never committed, never downloaded, never baked into an image or release.
3. **The game finds a bundled IWAD by itself.** `IwadLocator` appends bundled directories after the current and
   application data directories: `<app dir>/iwads` (from `jpackage.app-path`, the running jar, or `DOOM_APP_DIR`)
   and `/opt/doom-java/iwads` (inside the Docker image). The usual name preference then picks `doom1.wad`
   before Freedoom.
4. **Releases ship `doom1.wad`**: the jpackage installers embed it, and a portable zip per OS (jlink image + `iwads/doom1.wad`
   + launcher) and the fat jar zip carry it too.
5. **Docker image** (`Dockerfile`, `docker/entrypoint.sh`): multi-stage, Linux, no host dependencies. Default mode is the browser
   (Xvfb + Mesa software OpenGL + x11vnc + noVNC on port 6080, no sound); `DOOM_MODE=x11` uses the host X server and
   PulseAudio. The image contains the four free IWADs and is published to GHCR by `.github/workflows/docker.yml`.

**Consequences.** The repository grows by about 80 MB. The shareware license obliges us to keep `doom1.wad` byte-identical.
