# 07 — UI, input, configuration and CLI

The UI is in the freedom-of-implementation zone (ADR-007). It must **look and behave** like the original (same WAD graphics, positions, timings in tics and sounds), but the code is ours.
Everything is drawn on the `Canvas2D` (scaled virtual 320×200 screen, [04 §6](04-rendering.md#6-2d-layer-and-widescreen)), except the automap, which uses the native resolution.

## 1. UI framework

- Simple **data-driven widgets**: `Screen` (stack), `MenuList`, `MenuItem` (action, *slider*, *toggle*, text entry), `PatchWidget`, `TextWidget` with the WAD font (`STCFN*`), `NumberWidget` (status bar fonts).
- Time is measured in **tics** (35 Hz), not in frames. Blinking cursors, intermission counts and animations advance in `tick()`; `draw(alpha)` only draws.
- UI sounds (moving the cursor, selecting, opening and closing the menu) come from the original.

## 2. Screens

| Screen | Behaviour to preserve |
|--------|-----------------------|
| **Title loop** | TITLEPIC → demo → CREDIT/HELP → demo… in the order and with the timings of each `GameMode` (includes DEMO4 in Ultimate). Title music |
| **Main menu** | New Game, Options, Load Game, Save Game, Read This!, Quit Game. `M_*` graphics, animated skull cursor. Episode selection (with the shareware warning) and difficulty selection (with Nightmare confirmation) |
| **Options (original)** | End Game, Messages, Graphic Detail, Screen Size, Mouse Sensitivity, Sound Volume |
| **Extended options (new)** | Text submenus with the WAD font: *Video* (resolution, window mode, aspect, aspect correction, render scale, vsync, max FPS, interpolation, scale filter, precision fixes, full-screen HUD), *Controls* (*bindings*, X/Y sensitivity, vertical mouse, always run, gamepad), *Audio* (channels, resampling, OPL2/OPL3 mode, device), *Compatibility* (vanilla mode, `-merge`, `GameVersion`) |
| **Load/Save** | 6 slots with a 24-character description, the original's border graphics, *quicksave*/*quickload* (F6/F9) with confirmation |
| **Read This!** | HELP1/HELP2 or HELP depending on the `GameMode` |
| **Quit** | Random message ("misc" RNG) from the original's list and exit sound. ENDOOM on exit is optional (backlog) |
| **Status bar** | STBAR, current ammo, health, weapons, face, armor, keycards, ammo table. The face follows the original rules (pain, looking at the attacker, evil grin, ouch, invulnerable, dead). Uses the "misc" RNG |
| **HUD** | Messages at the top (with the original's display time), map title in the automap. Optional minimal HUD in full screen (new, [04 §6](04-rendering.md#6-2d-layer-and-widescreen)) |
| **Automap** | Follow player, *pan*, zoom, grid, marks, colors by line type, cheat `iddt`, map title |
| **Intermission** | Count of kills, items, secrets, time and par time with counting sounds; per-episode animated maps of DOOM 1; "Entering…" screen; DOOM II background |
| **Finale** | Letter-by-letter text; per-episode background; DOOM II *cast call*; bunny scroll (E3); E4 end screen |
| **Wipe** | Melt between screens ([04 §6](04-rendering.md#6-2d-layer-and-widescreen)) |
| **Debug overlay** (new, F12, configurable) | FPS, frame time, tic, audio *underruns*, internal resolution. Off by default |

Game texts in English, the same as the original (`d_englsh.h`).

## 3. Input

- **Events:** the backend converts GLFW into `InputEvent` (`KeyDown/KeyUp(code)`, `MouseMove(dx, dy)`, `MouseButton`, `Wheel`, `GamepadAxis/Button`, `Char`) in a pre-allocated queue.
- **Dispatch per tic:** menu → messages/chat → automap → game, in the original's order. Whoever consumes the event stops it.
- **Bindable actions:** forward, back, turn left/right, *strafe* left/right, *strafe on*, run, fire, use, weapons 1–7, next/previous weapon (new), automap, pause, *screenshot*, quick save/load, cheat console through the keyboard (like the original, by typing).
- **Mouse:** capture with *raw motion*. Separate horizontal and vertical sensitivity. Vertical mouse movement **moves forward and backward as in the original** by default (`mouseVertical = move`; option `off`). Per-frame turning on the camera ([04 §7](04-rendering.md#7-uncapped-fps-and-interpolation)).
- **Keyboard:** turn acceleration in the first tics as in the original. Optional "always run".
- **Gamepad:** GLFW gamepad API (SDL mappings). Analog sticks with configurable *deadzone* and curve become `forwardMove`/`sideMove`/`angleTurn` within the `TicCmd` limits.
- Everything that becomes a `TicCmd` respects the original's limits and quantizations. No "modern" input generates movement that the original could not generate.

## 4. Configuration

- `doom-java.cfg` file (`key = value`, `#` comments) in the app's data directory:
  macOS `~/Library/Application Support/doom-java/`, Windows `%APPDATA%\doom-java\`, Linux `$XDG_CONFIG_HOME/doom-java/`. Saves and screenshots in subfolders, saves kept separate per IWAD.
- Tolerant reading: unknown key → *warning*; invalid value → default + *warning*. Writing preserves comments and order (writing in a virtual thread, ADR-020).
- Optional import of the vanilla `default.cfg` (`-importcfg <file>`): keys, volumes, sensitivity, screen size.

Example:
```properties
# video
video.mode = borderless          # windowed | borderless | fullscreen
video.width = 2560
video.height = 1080
video.aspectCorrection = true
video.renderScale = 1.0
video.vsync = true
video.maxFps = 0
video.interpolation = true
video.scaleFilter = sharp-bilinear
render.precisionFixes = true
render.vanillaMode = false
hud.fullscreenHud = minimal      # off | minimal

# audio
sound.sfxVolume = 8
sound.musicVolume = 8
sound.channels = 8
sound.oplMode = opl2             # opl2 | opl3
sound.resampler = linear         # nearest | linear

# controls
input.mouseSensitivityX = 5
input.mouseSensitivityY = 5
input.mouseVertical = move       # move | off
input.alwaysRun = false
key.forward = W
key.back = S
key.strafeLeft = A
key.strafeRight = D
key.use = E
key.fire = MOUSE1
```

## 5. Command line

Compatible with vanilla/Chocolate where it makes sense:

| Parameter | Effect |
|-----------|--------|
| `-iwad <file>` | Explicit IWAD |
| `-file <files…>` | PWADs |
| `-merge <files…>` | PWADs with sprite/flat merge |
| `-warp <e m>` or `-warp <mm>` | Starts on the map |
| `-skill <1-5>`, `-episode <n>` | Difficulty and episode |
| `-nomonsters`, `-respawn`, `-fast` | Game parameters |
| `-playdemo <file\|lump>` | Plays a demo |
| `-timedemo <file\|lump>` | Plays a demo with no time limit and prints tics/time/FPS |
| `-record <name>` | Records a demo |
| `-loadgame <slot>` | Loads a save |
| `-gameversion <1.9\|ultimate\|final\|final2>` | Forces the executable variant |
| `-config <file>` | Alternative configuration file |
| `-window`, `-fullscreen`, `-width`, `-height` | Video shortcuts |
| `-vanilla` | Vanilla presentation mode (320×200, 4:3, no interpolation, no corrections) |
| `-headless` | No window or audio (tests, `-timedemo` in CI) |
| `-trace <file>` | Writes the per-tic trace ([09](09-testing-quality.md)) |
