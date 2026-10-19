# doom-java

A remake of **DOOM** (id Software, 1993) written from scratch in **Java 25**.

The game rules are not a port line by line: they are a new implementation that behaves **exactly like the DOS v1.9
executable**. Given the same IWAD and the same inputs, the state of the world after every 35 Hz tic is bit-for-bit
identical to vanilla DOOM, which is proved by replaying the original demos against a patched
[Chocolate Doom](https://www.chocolate-doom.org/) and comparing a per-tic trace. On top of that exact simulation the
presentation is modern: any resolution, widescreen up to 32:9, uncapped frame rate with interpolation, vsync, and a
software renderer that is pixel-identical to the original at 320x200 in vanilla mode.

The only runtime dependency is [LWJGL](https://www.lwjgl.org/) (GLFW window and OpenGL presentation). Audio, music
(an OPL3 emulator), rendering, WAD reading, configuration and logging are plain Java on top of the JDK.

## Quick start

The free IWADs (shareware `doom1.wad`, Freedoom) are in the repository and in every release, so nothing else needs to be downloaded.

**Docker, in the browser** (macOS, Windows, Linux; no sound). Needs only Docker:

```sh
docker run --rm -p 6080:6080 ghcr.io/luiz-august0/doom-java-remake
```

Then open <http://localhost:6080>. Anything after the image name goes to the game, for example
`... doom-java-remake -iwad freedoom2.wad -warp 1`. The image runs the game on a virtual X server with Mesa software
OpenGL and streams it with noVNC. `docker compose up` builds the image from a checkout and keeps your configuration and
saves in a volume; `bin/doom-docker [args]` wraps `docker run` for you (`--build` builds the image from the checkout first).

**Docker, native window with sound** (Linux with an X server and PulseAudio / PipeWire):

```sh
bin/doom-docker --x11
```

**Portable release** (no Java needed): download `DoomJava-<version>-<os>-portable.zip` from the
[releases page](https://github.com/luiz-august0/doom-java-remake/releases), unzip it and run the launcher inside:
`doom-java.command` (macOS, double click; right-click, Open the first time), `doom-java.sh` (Linux) or `doom-java.bat`
(Windows). It includes `doom1.wad`. The `.dmg` / `.msi` installers and the `...-jar.zip` (fat jar, needs JDK 25) include it too.

**From source** (JDK 25):

```sh
git clone https://github.com/luiz-august0/doom-java-remake.git && cd doom-java-remake
bin/doom-java                    # finds .iwads/doom1.wad by itself; add -iwad freedoom1.wad to pick another
```

**IWAD licenses.** `.iwads/` holds only freely redistributable game data: `doom1.wad` is the id Software shareware
episode, redistributed unmodified under the shareware terms, and Freedoom / FreeDM are BSD licensed (`.iwads/README.md`,
`.iwads/COPYING.txt`). The commercial IWADs (`doom.wad`, `doom2.wad`, `tnt.wad`, `plutonia.wad`) are never included:
bring your own copy.

## Features

- **Complete single-player game**: DOOM (shareware, registered, Ultimate), DOOM II, Final DOOM (TNT, Plutonia) and
  Freedoom, with menus, status bar, automap, intermissions, finales, cheats, save and load, demo recording and playback.
- **Vanilla-exact simulation**: monster AI, physics, specials, RNG and even the documented overflow bugs. Demos stay in
  sync (399 of 401 test demos and all 14 IWAD demos are identical to the oracle, tic by tic).
- **Modern presentation**: arbitrary window size and render scale (0.25x to 2x), Hor+ widescreen, uncapped FPS with
  interpolation of actors, sectors and the camera, windowed, borderless and fullscreen modes, sharp bilinear or nearest
  scaling. `-vanilla` gives back 320x200, 4:3 and no interpolation.
- **Sound and music**: DMX sound effects mixed in Java, MUS/MIDI music through a bit-exact Java port of the Nuked OPL3
  emulator with the GENMIDI instruments, played through `javax.sound`.
- **DEHACKED and PWADs**: `-file`, `-merge`, `-deh`, and the DEHACKED lump embedded in Freedoom.
- **Gamepad** through the GLFW gamepad API, raw mouse input, configurable bindings.
- **Developer tools**: per-tic trace, `trace-diff`, `wad-dump`, golden-frame comparison, demo suite, benchmark.

## Requirements

- To play: Docker, **or** a release (the installers and the portable zip include their own Java runtime), **or** a JDK 25 for the fat jar.
- To build: JDK 25 (any distribution) and nothing else; Maven is provided by the wrapper (`./mvnw`).
- Platforms: macOS (arm64 and x86-64), Windows and Linux with OpenGL 3.3. The build selects the LWJGL natives of the
  machine it runs on.
- A game data file (an IWAD), see below.

## Game data (IWADs)

The repository ships the freely redistributable IWADs in `.iwads/` (ADR-023); releases and the Docker image include
`doom1.wad`, and the Docker image all four. For anything else you provide the file:

| IWAD | Where it comes from |
|---|---|
| `doom1.wad` | Shareware DOOM 1.9, **bundled** (`.iwads/`, releases, Docker). |
| `freedoom1.wad`, `freedoom2.wad`, `freedm.wad` | Freedoom 0.13.0, BSD licensed, **bundled** (`.iwads/`, Docker). More at <https://freedoom.github.io/>. |
| `doom.wad`, `doom2.wad`, `tnt.wad`, `plutonia.wad` | Your own copy of the commercial games (Steam, GOG, ...). Never bundled. |

The search order is: `-iwad <file>`, the directory named by `DOOMWADDIR`, the directories of `DOOMWADPATH` (`:`
separated, `;` on Windows), the **current directory**, the application data directory (below), then the **bundled
directories**: `iwads/` next to the installed application, the portable folder or the jar (or under `DOOM_APP_DIR`),
and `/opt/doom-java/iwads` in the Docker image. The first directory that contains an IWAD wins; inside a directory the
order is `doom2`, `plutonia`, `tnt`, `doom`, `doom1`, `freedoom2`, `freedoom1`, `freedm`. So a commercial IWAD in the
current directory beats the bundled shareware.

## Run

With an installer (macOS `.dmg`, Windows `.msi`, Linux archive) start **DoomJava**: it opens the bundled `doom1.wad`.
To play another IWAD pass `-iwad` on the command line or copy it to the application data directory.

From source:

```sh
./mvnw -q verify                 # build and run the unit tests
bin/doom-java                    # the IWAD is found in .iwads/ (set DOOMWADDIR or use -iwad for others)
bin/doom-java -iwad ~/wads/freedoom1.wad
bin/doom-java -iwad doom1.wad -warp 1 3 -skill 4 -window -width 1920 -height 1080
```

`bin/doom-java` builds the classpath with Maven on first use and starts the game on the module path with the flags that
LWJGL needs (`--enable-native-access=...`, and `-XstartOnFirstThread` on macOS). `DOOM_NO_BUILD=1` skips the Maven step,
`DOOM_JVM_OPTS` adds JVM options.

Fat jar for developers (class path, one file):

```sh
./mvnw -q -Pdist -pl doom-app -am package -DskipTests
java -XstartOnFirstThread --sun-misc-unsafe-memory-access=allow \
     -jar doom-app/target/doom-app-*-all.jar -iwad freedoom1.wad      # -XstartOnFirstThread: macOS only
```

Installer for the current OS (the `dist` profile; also produces the fat jar above and the portable zips in
`doom-app/target/dist/portable/`):

```sh
./mvnw -Pdist -pl doom-app -am package -DskipTests
# macOS: doom-app/target/dist/installer/DoomJava-1.0.0.dmg
# Windows: add -Ddist.jpackage.type=msi (needs the WiX Toolset); Linux: an app-image directory
```

The profile links a runtime image with `maven-jlink-plugin` (the JDK modules the game needs plus the game's own
modules) and wraps it with `jpackage`. The macOS launcher already carries `-XstartOnFirstThread` and the native-access
flags. Pushing a tag `vX.Y.Z` runs `.github/workflows/release.yml`, which builds the installers, the portable zips and
the fat jar zips (all with `doom1.wad`) on every OS, and `.github/workflows/docker.yml`, which publishes the multi-arch
image to `ghcr.io/<owner>/<repo>`.

Docker: `docker build -t doom-java .` builds the image from the checkout (multi-stage: Maven build, then a slim runtime
with Xvfb, Mesa, x11vnc and noVNC). `DOOM_MODE=x11` uses the host's display and sound instead of the browser;
`DOOM_GEOMETRY=1920x1080` sets the browser-mode screen size; `DOOM_SOUND=1` enables the audio engine (there is no sound
device in browser mode, the game then runs silently).

## Command line

Options follow Chocolate Doom where it makes sense. `-help` prints the list.

| Option | Effect |
|---|---|
| `-iwad <file>` | IWAD to use (otherwise searched, see above) |
| `-file <files...>` | PWADs (and `.deh` patches) to load |
| `-merge <files...>` | PWADs whose sprites and flats are merged |
| `-deh <files...>`, `-nodeh` | Apply DEHACKED patches / do not apply the IWAD's own patch |
| `-warp <e m>` or `-warp <mm>` | Start on a map |
| `-skill <1-5>`, `-episode <n>` | Difficulty and episode |
| `-nomonsters`, `-respawn`, `-fast`, `-deathmatch`, `-altdeath` | Game parameters |
| `-playdemo <file or lump>` | Play a demo and quit |
| `-timedemo <file or lump>` | Play a demo as fast as possible; prints tics, time and FPS |
| `-record <name>` | Record a demo |
| `-loadgame <slot>` | Load a saved game at start-up |
| `-gameversion <1.9\|ultimate\|final\|final2>` | Force the executable variant |
| `-config <file>`, `-importcfg <default.cfg>` | Alternative configuration file / import a vanilla `default.cfg` |
| `-window`, `-fullscreen`, `-width <n>`, `-height <n>` | Video shortcuts |
| `-vanilla` | Vanilla presentation: 320x200, 4:3, no interpolation, no corrections |
| `-headless` | No window and no audio (tests, CI) |
| `-nosound`, `-nomusic` | Disable sound effects / music |
| `-trace <file>` | Write the per-tic trace (`.gz` is compressed); `-traceverbose <file> [-tracefrom <tic> -traceto <tic>]` |
| `-maxtics <n>` | Quit after `n` game tics |
| `-loglevel <level>` | `off`, `error`, `warning`, `info`, `debug`, `trace` |

## Configuration

The first run writes `doom-java.cfg` (`key = value`, `#` comments) to the application data directory; unknown keys
and invalid values produce a warning and the default, and comments survive a rewrite.

| OS | Data directory |
|---|---|
| macOS | `~/Library/Application Support/doom-java/` |
| Windows | `%APPDATA%\doom-java\` |
| Linux | `$XDG_CONFIG_HOME/doom-java/` (default `~/.config/doom-java/`) |

Below it: `saves/<iwad>/` (save games are kept per IWAD), `screenshots/`, `logs/doom-java.log`.

```properties
video.mode = windowed            # windowed | borderless | fullscreen
video.width = 1280
video.height = 960
video.aspectCorrection = true    # 5:6 pixels, a 4:3 picture at 320x200
video.renderScale = 1.0          # 0.25 .. 2.0
video.vsync = true
video.maxFps = 0                 # without vsync, 0 = unlimited
video.interpolation = true
video.scaleFilter = sharp-bilinear   # nearest | sharp-bilinear
video.scaleMode = fit            # fit | integer | stretch
render.precisionFixes = true
render.vanillaMode = false
hud.fullscreenHud = off          # off | minimal
sound.sfxVolume = 8              # 0..15
sound.musicVolume = 8
sound.channels = 8               # 1..32
sound.oplMode = opl2             # opl2 (9 voices, like DOS) | opl3
sound.resampler = linear         # nearest | linear
input.mouseSensitivityX = 5
input.mouseSensitivityY = 5
input.mouseVertical = move       # move | off
input.alwaysRun = false
key.forward = W, UP              # any input name, comma separated
```

Every setting, with its default, range and description, is written to the file on the first run.

## Controls

All of them can be rebound with the `key.<action>` settings.

| Action | Default |
|---|---|
| Move forward / back | `W` `Up` / `S` `Down` |
| Turn left / right | `Left` / `Right`, or the mouse |
| Strafe left / right | `A` / `D` (also `,` and `.`) |
| Strafe modifier | `Alt`, right mouse button |
| Run | `Shift` |
| Fire | `Ctrl`, left mouse button |
| Use (doors, switches) | `E`, `Space` |
| Weapons | `1` to `7`, mouse wheel for next / previous |
| Pause | `Pause` |
| Menu | `Esc`, arrows, `Enter`; `F1` help, `F2` save, `F3` load, `F6` quick save, `F9` quick load |
| Automap | `Tab` (follow, grid and marks with `F`, `G`, `M`, `C`; zoom with `-` and `=`) |

Gamepad (GLFW mapping): left stick moves and strafes, right stick turns, `A` use, right trigger fires, left trigger
runs, bumpers change weapon. The classic cheat codes are typed in game as in vanilla.

## Architecture

A Maven multi-module build; every module has a `module-info.java` (JPMS) and only the packages that must be public are
exported. The simulation never sees the renderer, the UI or the platform: it exposes commands and read-only views.

| Module | Role |
|---|---|
| `doom-math` | Fixed point, binary angles, tables, the RNG (`doom.math`) |
| `doom-wad` | WAD files, lump formats, IWAD identification, demos |
| `doom-game` | The simulation: world, actors, physics, AI, combat, specials, session flow, save games. Exports only `doom.game.api` and `doom.game.view` |
| `doom-render` | 2D canvas, the software 3D renderer at any resolution, the screen wipe, PNG |
| `doom-ui` | Menus, status bar, HUD, automap, intermission, finale |
| `doom-sound` | Sound channel logic, mixer, MUS/MIDI sequencer, OPL3 emulator |
| `doom-engine` | Main loop, configuration, command line, glue between the other modules |
| `doom-platform-api`, `-lwjgl`, `-javasound` | Window/input/audio interfaces and their LWJGL and Java Sound backends (found with `ServiceLoader`) |
| `doom-app` | Entry point, logging; also the `dist` profile |
| `doom-testkit`, `doom-tools`, `doom-it` | Trace format and oracle runner, command line tools, integration tests |
| `doom-codegen`, `doom-lint` | Generators for the data tables; a javac plugin that checks unsigned angle arithmetic (`@Bam`) |

Design documents, in reading order:

- [`docs/spec/00-overview.md`](docs/spec/00-overview.md): goals, non-goals, principles
- [`01-decisions.md`](docs/spec/01-decisions.md): the architecture decisions (ADRs) and the rejected alternatives
- [`02-architecture.md`](docs/spec/02-architecture.md): modules, threads, main loop
- [`03-simulation.md`](docs/spec/03-simulation.md), [`04-rendering.md`](docs/spec/04-rendering.md),
  [`05-wad-data.md`](docs/spec/05-wad-data.md), [`06-audio.md`](docs/spec/06-audio.md),
  [`07-ui-input-config.md`](docs/spec/07-ui-input-config.md), [`08-persistence.md`](docs/spec/08-persistence.md)
- [`09-testing-quality.md`](docs/spec/09-testing-quality.md) and [`10-roadmap.md`](docs/spec/10-roadmap.md)
- [`docs/contracts.md`](docs/contracts.md): the implementation contracts (module ownership, coding rules, trace format)
- [`docs/divergences.md`](docs/divergences.md): every deliberate difference from the original, one table
- [`docs/notes/`](docs/notes/): platform, audio latency and simulation notes

The simulation code points to the rule it reproduces with `@VanillaRef("p_map.c:P_TryMove")`; preserved original bugs
are marked `// VANILLA-BUG:` and deliberate differences `// DIVERGE:`. `bin/doom-tools ref-coverage` lists the original
simulation functions that no `@VanillaRef` refers to.

## Testing

```sh
./mvnw verify                                                  # unit tests of every module (no IWAD needed)
DOOM_IWAD_DIR=~/wads ./mvnw -pl doom-it -am verify             # integration tests (skipped without DOOM_IWAD_DIR)
./mvnw -Pbench verify                                          # JMH benchmarks of the drawers
```

Fidelity is established by three kinds of comparison against the **oracle**, Chocolate Doom 3.1.1 plus
[`tools/oracle/chocolate-trace.patch`](tools/oracle/README.md), which dumps one line per tic (positions, health,
hashes of all actors and sectors) and rendered frames:

- **Demo sync** (`doom.it.sync.DemoSyncIT`): the 14 demos of the DOOM and Freedoom IWADs and a corpus of 387
  generated demos replay with a trace identical to the oracle's. `-Dsync.synthetic=all` runs the whole corpus; the two
  accepted exceptions are listed in `testdata/demos/exceptions.tsv`.
- **Golden frames** (`GoldenFrameIT`): the renderer in vanilla mode against 167 oracle frames, pixel by pixel.
- **Unit tests** on synthetic maps built in code, on every lump format, the mixer and the OPL3 emulator (test vectors
  from the C implementation).

Build the oracle once with `tools/oracle/build.sh` (CMake and SDL2 needed); `bin/doom-tools demo-suite` and
`bin/doom-tools trace-diff` drive it. CI (`.github/workflows/ci.yml`) runs `mvnw verify` on Linux, macOS and Windows,
then downloads Freedoom, builds the oracle and runs the sync suite.

## Contributing

doom-java is **free and open source software**, and contributions of every size are welcome: bug reports, fixes,
tests, documentation, new features, ports and ideas.

- **Found a bug or have a question?** [Open an issue](https://github.com/luiz-august0/doom-java-remake/issues).
  Please include the OS, the Java version, the IWAD, the command line and the log
  (`logs/doom-java.log` in the data directory). For a simulation desync, attach the demo or the `-trace` output.
- **Want to send a change?** Pull requests are welcome:
  1. Fork the repository and create a branch from `main` (`git checkout -b fix/short-description`).
  2. Make the change, following the coding rules in [`docs/contracts.md`](docs/contracts.md) and the architecture in
     [`docs/spec/`](docs/spec/00-overview.md). Keep the diff focused: one concern per pull request.
  3. Add or update tests, and run `./mvnw verify` (and the integration tests when you touch the simulation or the
     renderer, see [Testing](#testing)). Changes to the simulation must keep the demos in sync with the oracle.
  4. Document deliberate differences from the original in [`docs/divergences.md`](docs/divergences.md).
  5. Push the branch and open a pull request against `main`, describing what changed and why. Link the issue it
     closes, if any.
- **Big change or new feature?** Open an issue first to discuss it, so nobody spends time on something that does not fit
  the project goals ([`docs/spec/00-overview.md`](docs/spec/00-overview.md)).
- **Commit messages** are short and in the imperative mood, with the area first when it helps
  (`Render: fix off-by-one in the visplane merge`).

Be respectful and constructive with everyone in the community; harassment and personal attacks are not tolerated.

By contributing you agree that your work is licensed under the same terms as the project (GPL v2, see below).

## License

GNU General Public License, version 2 (see [`LICENSE`](LICENSE)). The project reproduces behaviour studied in id
Software's GPL-released DOOM source and in Chocolate Doom, and is therefore a derivative work. See
[`NOTICE`](NOTICE) for the credits: id Software, Chocolate Doom, Nuked OPL3, LWJGL and Freedoom.

DOOM is a registered trademark of id Software. The game data is not included and is not covered by this license.
