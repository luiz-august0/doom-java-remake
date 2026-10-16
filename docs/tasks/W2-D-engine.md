# Task W2-D — engine, main loop, sound system, app

Read `docs/agent-guide.md` and `docs/contracts.md` first (§8 misc RNG, §11 synchronous SimEvents, demos live in the session).

Spec: `docs/spec/02-architecture.md` §5–§7 (objects, threads, main loop), `06-audio.md` §1 (channel logic),
`07-ui-input-config.md` §3–§5 (input, config, CLI), ADR-011/017/018/020. Original: `s_sound.c` (Chocolate `src/doom/s_sound.c`
wins), `g_game.c G_BuildTiccmd`, `d_main.c` (loop, CLI), `m_misc.c` (default.cfg).

You own: `doom-sound` package `doom.sound` (SoundSystem), `doom-engine` (whole), `doom-app` package `doom.app`
(Main, logging; not `doom.app.viewer`), `bin/doom-java`, `bin/doom-tools`, `doom-tools` package `doom.tools.demo`
class `DemoRunTool` only, `doom-it` package `doom.it.engine`, `docs/divergences/W2-D.md`.

Available on `main`:
- Audio engine (W1-D): `GenMidi.parse(...)`, `new AudioEngine(AudioEngineConfig.defaults(), genmidi)`, `engine.start(sink)` /
  `renderOffline(short[], off, frames)` / `stop()`, `engine.commands()` → `startSfx(ch, SfxSample, vol 0..127, sep 0..254, pitch)`,
  `updateChannel`, `stopChannel`, `stopAllSfx`, `playMusic(MusicScore, looping)`, `stopMusic`, `pause/resumeMusic`,
  `setVolumes(sfx0..15, music0..15)`; `isChannelPlaying(ch)`; `SfxSample.of(DmxSound.parse(...))`, `MusicScore.fromLump(...)`.
- Platform SPI + LWJGL backend (`docs/notes/platform.md` for JVM flags), `javax.sound` backend, `FrameBuffer`, `Canvas2D`, `PngWriter`.
- Game API (`doom.game.api`): `GameFactory.createSession(GameSetup)` (implementation in progress by W2-A; until it lands,
  test against a fake `GameSession`), `TicCmd`, `SimEvents`, `MiscRandom`, views.
- Renderer (W2-B) and UI (W2-C) are being written in parallel: code the engine against small interfaces/adapters and wire
  them when they reach `main` (the orchestrator will tell you; `git merge main`).

Deliverables:
1. `doom.sound.SoundSystem`: the `s_sound.c` channel logic on the main thread, implementing the sound part of `SimEvents`
   synchronously (startSound/stopSound/changeMusic/pauseSound): link volume/pitch, audibility via `S_AdjustSoundParams`
   (distance clip, close distance, map-8 rule incl. MAP08, separation by angle), the **misc RNG pitch draw exactly as
   Chocolate** (after the audibility check; none for itemup/tink; saw sounds &15, others &31), origin replacement,
   priority-based channel stealing, per-frame `updateSounds(listener)` recomputing vol/sep for moving origins, freeing
   finished channels; sfx/music volume 0–15; runs headless too (no sink → no audio, same logic and RNG use).
2. Engine: `DoomEngine` owning session, renderer, UI, sound; main loop per spec 02 §7 (fixed 35 Hz tics, MAX_TICS_PER_FRAME,
   interpolation alpha, wipe pauses simulation, menu pauses single player via `tick(cmds, menuActive)`), `-timedemo`
   (one tic + one frame per iteration, prints tics/time/FPS), headless mode, `-trace <file>` writing the session's trace
   lines (header per contracts §6.1), `LocalTicSource` (exact `G_BuildTiccmd` semantics: walk/run speeds, turn acceleration
   over the first tics, strafe, mouse, gamepad within ticcmd limits, weapon change bits, per-frame mouse turn with the
   quantization residue carried to the next tic per spec 04 §7), key bindings.
3. Config: `doom-java.cfg` (key = value, `#` comments, lenient parse with warnings, writes preserving comments/order, via a
   virtual thread), per-OS data directory, `-importcfg` of vanilla `default.cfg`. CLI: all spec 07 §5 flags.
4. `doom-app`: `Main` (CLI → IWAD discovery via `IwadLocator` → engine), `LoggerFinder` with console + log file written off
   the main loop, ServiceLoader backend selection; `bin/doom-java` (builds module path, macOS `-XstartOnFirstThread`,
   `--enable-native-access=org.lwjgl,org.lwjgl.glfw,org.lwjgl.opengl`), `bin/doom-tools`.
5. `DemoRunTool` (`demo-run`): play a demo headless and write its trace (used by `demo-suite --ours-dir`).

Verification: unit tests for SoundSystem (channel allocation, priorities, misc-RNG draw count/order with fake views),
G_BuildTiccmd equivalence cases, config round-trip, CLI parsing; headless engine test with a fake session; once W2-A's
session is on `main`, `demo-run` on doom1 DEMO1 produces a trace (compare with `testdata/traces/doom1-demo1.trace.gz` via
`trace-diff`; divergences belong to the simulation, just report them). A short window run showing the title page (once UI
lands) with music. Report: engine API, how renderer/UI are wired, contract requests.
