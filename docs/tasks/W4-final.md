# Waves 4–5 — remaining features (parallel agents)

Read `docs/agent-guide.md` and `docs/contracts.md` first. Status: the simulation is vanilla-exact (399/401 demos and all
14 IWAD demos identical to the oracle; `DemoSyncIT -Dsync.synthetic=all` must stay green after your change — run it).
Renderer (golden frames 167/167), UI, sound, engine and app are on `main`; `bin/doom-java` runs the game.

## W4-P — save games (`doom.game.persist`)
Spec `docs/spec/08-persistence.md` §2 (DJSV format: header with magic, format version, game version/mode, WAD names +
SHA-256, description, timestamp; sections Session/Players/World/Thinkers/Specials with tag + length; references by index;
saves more than vanilla: RNG indices, target/tracer). Implement `doom.game.session.SaveGameHandler` and install it in
`SessionFactory`/`Game` (see `docs/divergences/W3-D.md` and `SessionImpl.game()`); snapshot taken synchronously at the
save point, written by the engine on a virtual thread (engine already calls `SimEvents.gameSaved`). Load = rebuild the
level from map lumps and overlay the saved state; snap interpolation. Corrupt file → clear error, never a crash; different
WADs → warning. Tests (in `doom-it` package `doom.it.persist`): for every map of freedoom1/freedoom2 and doom1, run a
synthetic demo N tics, save, load into a fresh session, compare the trace line immediately and after continuing the same
ticcmds for M more tics (must equal the uninterrupted run). Also engine wiring check: menu save/load and `-loadgame`.

## W4-R — modern presentation (`doom-render` scene/wipe, `doom-engine` rendering adapter, `doom-ui` where needed)
Spec `04-rendering.md` §2–§3, §6–§7, ADR-010/011: `precisionFixes` (64-bit wall scale, full-precision vertex angles, long
sprite/wall scale limits, finer span steps — only when not vanilla mode), `correctTextureWrap` outside vanilla, scaled fuzz
style, render scale 0.25–2.0, square-pixel option, Hor+ up to 32:9; interpolation (actors prev*, sectors prev heights, side
prev offsets for scrollers, psprites, camera with per-frame mouse turn already in the engine) verified visually; status
bar sides and wide patches; minimal fullscreen HUD. Gate: golden frames still 167/167 in vanilla mode; demos still sync
with interpolation on (presentation must not touch the simulation); PNG checks at 2560×900 and 3440×1440-equivalent.
Then a quick `-timedemo` benchmark at 320×200, 2560×900 and 3840×1800 (record fps in `docs/notes/performance.md`) and
optimise the obvious hot spots (column/span drawers) if far from spec 04 §9 targets.

## W5-D — distribution and docs
`maven-jlink-plugin` runtime + `jpackage` (via exec plugin, profile `dist`) producing a macOS arm64 `.dmg` that includes
`-XstartOnFirstThread` and the native-access flags (verify it builds and launches here); fat jar for developers
(`java -jar`, with `META-INF/services` entries since ServiceLoader on the classpath needs them); GitHub Actions workflows
(`.github/workflows/ci.yml`: `mvnw verify` on Linux/macOS/Windows + Freedoom download + oracle build + sync suite;
`release.yml`: jpackage per OS on tags `v*`); `NOTICE` (id Software, Chocolate Doom, Nuked OPL3 credits); final user
**README.md** in English (what it is, features, requirements, where to put IWADs / how to get Freedoom, build & run commands,
CLI flags, config file, controls, architecture overview with links to `docs/spec`, testing/oracle, license); consolidate
`docs/divergences/*.md` into `docs/divergences.md` (one table, IDs D-006…); `ref-coverage` tool (`doom.tools.coverage`) —
list linuxdoom simulation functions not referenced by any `@VanillaRef`; wire the `doom-lint` BamLint plugin into
`doom-game` in warn mode (`-Adoom.lint.mode=warn`; snippet in `docs/divergences/W1-B.md`/report) and report warnings count.
You may edit POMs and `module-info.java` for this task only where listed above (dist profile, doom-game compiler config).
