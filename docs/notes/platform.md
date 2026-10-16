# Platform backend notes (W1-E)

Owner: W1-E. Covers `doom-platform-lwjgl`, the 2D foundation in `doom-render` and the development viewer.
Spec: `docs/spec/04-rendering.md` §2, §6, §8; `07-ui-input-config.md` §3; ADR-003, ADR-009, ADR-010.

## 1. JVM flags (verified on JDK 25.0.1 Corretto, macOS 26 arm64, LWJGL 3.4.3)

Launch the application on the **module path** (reactor `target/classes` directories plus the LWJGL jars) with:

```
java --enable-native-access=org.lwjgl,org.lwjgl.glfw,org.lwjgl.opengl \
     -XstartOnFirstThread                                   # macOS only
     --module-path <...> -m doom.app/<main class>
```

| Flag | Why |
|---|---|
| `--enable-native-access=org.lwjgl,org.lwjgl.glfw,org.lwjgl.opengl` | LWJGL loads its natives with `System.load`, a restricted method on JDK 24+. Without the flag every LWJGL module prints a 4-line warning (and a future JDK will refuse). Each LWJGL module warns separately, so all three used modules must be listed. With the flag there is no output at all. If the jars are put on the **class path** instead, use `--enable-native-access=ALL-UNNAMED`. |
| `-XstartOnFirstThread` | macOS only. GLFW needs the Cocoa main thread. Without it `LwjglPlatformProvider.isSupported()` returns `false` and logs `On macOS the LWJGL/GLFW backend must run on the first thread: start the JVM with -XstartOnFirstThread`; `createVideoSink()`/`open()` throw `IllegalStateException` with the same message. Detection: the JVM exports the environment variable `JAVA_STARTED_ON_FIRST_THREAD_<pid>` when the flag is given. |

`-Dorg.lwjgl.system.memoryBackend`: **not needed**. Its value is a class name, not a keyword (`FFM`/`JNI` fail with
`ClassNotFoundException`). On JDK 25 the default is `MemoryBackendFFM` (`-Dorg.lwjgl.util.Debug=true` prints it): it
works with no extra flags and is slower than Unsafe, which is irrelevant here (two native buffers, a handful of calls per
frame; the frame data is one `glTexSubImage2D` from a direct buffer). Alternatives, measured to work:

* `-Dorg.lwjgl.system.memoryBackend=org.lwjgl.system.MemoryBackendUnsafe --add-exports=java.base/jdk.internal.misc=org.lwjgl`
  (fastest; needs the export, otherwise LWJGL logs a warning and falls back to FFM);
* on the class path (unnamed module, e.g. Failsafe) LWJGL picks `MemoryBackendUnsafeLegacy`, which prints a
  `sun.misc.Unsafe` deprecation warning unless `--sun-misc-unsafe-memory-access=allow` is given.

`bin/viewer` builds the module path from the reactor output (`./mvnw -o -q -pl doom-app -am compile dependency:build-classpath`)
and starts Java with the flags above. Environment: `VIEWER_NO_BUILD=1` skips Maven, `VIEWER_JVM_OPTS` appends JVM
options, `VIEWER_NO_FIRST_THREAD=1` omits `-XstartOnFirstThread` (to see the error message).

## 2. Video sink

* GLFW window, OpenGL **3.3 core, forward compatible** (works on macOS's 4.1 core implementation).
* HiDPI: `outputWidth()/outputHeight()` are the GLFW **framebuffer** size in physical pixels, updated from the
  framebuffer-size callback and re-read on every `present`.
* Index texture `GL_R8`, size of the frame (transposed for column-major), created on size change, updated each frame
  with `glTexSubImage2D` from a pre-allocated direct buffer (`MemoryUtil.memAlloc`, grown only when the frame grows).
  Palette texture `GL_RGBA8` 256×1, uploaded only when the palette content changed.
* Shader: vertex shader generates the quad from `gl_VertexID` and a rectangle uniform; fragment shader does
  `texelFetch` on the index texture (swapping the coordinates for column-major) and `texelFetch` on the palette.
  Sharp bilinear: four fetches resolved through the palette and blended with the usual "sharp bilinear" weight
  `q = floor(p) + 0.5 + (d - clamp(d, -r, r)) * s`, `r = 0.5 - 0.5/s` (filtering the index texture itself would be
  meaningless).
* Scaling (`PresentGeometry`, unit tested): a frame pixel is `pixelAspect` times taller than wide. `FIT` = largest size
  keeping the aspect, `INTEGER` = largest whole multiple of the horizontal scale (falls back to FIT when the output
  is smaller than the frame), `STRETCH` = whole output. Result centred, black bars. For the automatic resolution
  (`W = outputWidth`, `H = outputHeight/1.2`, aspect 1.2) the frame covers the output exactly (2560×900 -> 2560×1080).
* Vsync: `glfwSwapInterval`. Without vsync and with `maxFps > 0`: hybrid limiter using `Clock.waitUntil` (deadline
  advanced by one frame period; resets when more than a frame late).
* Window modes: `WINDOWED`, `BORDERLESS` (undecorated window at the monitor's video-mode size), `FULLSCREEN` (GLFW monitor
  mode of the primary monitor). `reconfigure` switches with `glfwSetWindowMonitor` keeping the GL context and restores the
  previous windowed position/size. The size changes arrive as `RESIZED` events (macOS emits a few intermediate sizes;
  always use the last one).
* `readBackOutput()`: re-renders the last uploaded frame into the back buffer and `glReadPixels` it (the content of the
  back buffer after a swap is undefined), flipped to top row first. RGBA, `outputWidth*outputHeight*4`.
  Verified: identical to a CPU model of the presentation (0 differing pixels) at 1280×720, 2560×1080, row- and
  column-major, for the automatic resolution. With non-integer scale factors the GPU and a CPU model disagree on the
  exact-tie pixels (about 3 % at 3.125×3.75); that is a rounding convention, not a bug.

## 3. Input

`LwjglInputSource.poll(queue)` runs `glfwPollEvents` and the callbacks append to the queue (events GLFW fires outside
`poll` wait in a private queue). No allocation per event.

* Keys: `GlfwKeys.translate` follows Chocolate Doom's `i_input.c` with `vanilla_keyboard_mapping` (position based US
  layout): letters -> lower-case ASCII, digits/punctuation -> ASCII, both Shifts -> `RSHIFT`, both Ctrls -> `RCTRL`,
  left Alt -> `LALT`, right Alt -> `RALT`, keypad digits -> `KEYP_n` (numeric keypad 8 = `UPARROW`, ...), F1–F12,
  navigation keys, Pause, Print Screen, Caps/Num/Scroll lock. Super keys and unknown keys produce no event. Key repeat
  produces more `KEY_DOWN`.
* `CHAR`: Unicode code point from the GLFW char callback (layout aware).
* Mouse: `MOUSE_MOVE` only while captured (`setMouseCaptured(true)` = `GLFW_CURSOR_DISABLED` + `GLFW_RAW_MOUSE_MOTION`
  when supported), deltas of the cursor position (the first event after capture is dropped); buttons 0 left, 1 right,
  2 middle, 3+ extra; `WHEEL` with `dx`/`dy`.
* Gamepad: joystick ids 0–3 through `glfwGetGamepadState`; `PAD_DOWN/UP` (button 0–14, GLFW/SDL gamepad layout),
  `PAD_AXIS` (axis 0–5, change > 0.005); a disconnected pad releases everything it held.
* `FOCUS_GAINED/LOST`, `RESIZED` (`code` = width, `device` = height, physical pixels).

## 4. 2D foundation (doom-render)

See the Javadoc of `FrameBuffer` and `Canvas2D`. Summary:

* `px(v) = round(W/2 + H*(v-160)/200)` and `py(r) = round(H*r/200)` (integer arithmetic, half up) delimit the physical
  pixels of virtual column `v` / row `r`; nearest sampling follows from drawing every virtual pixel as the block
  between its two boundaries. At 320×200 the mapping is the identity -> pixel identical to `V_DrawPatch` (unit tests
  against a transcription of the C code on random patches, both layouts, plus real TITLEPICs in `doom-it`).
* Tables are rebuilt when the frame size changes; drawing calls do not allocate.

## 5. Tests

* Unit: `doom-wad` `GfxDecodersTest`; `doom-render` `FrameBufferTest`, `Canvas2DTest`, `PngWriterTest`;
  `doom-platform-lwjgl` `PresentGeometryTest` (geometry and key map; no GL needed).
* IT (`doom-it`, `doom.it.platform`): `TitlepicCanvasIT` (needs `DOOM_IWAD_DIR`); `LwjglWindowIT` opens a real window and
  is skipped when the backend is not usable. Failsafe does not pass `-XstartOnFirstThread`, so on macOS run it with
  `-Dtest.jvm.args="-XstartOnFirstThread -Xmx1g" -Dit.test=LwjglWindowIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
  (add `-am` and the usual `-o -pl doom-it`). It was run that way and passes.
* Visual: `bin/viewer --shot out.png --size 2560x1080 [--shotout presented.png] <iwad>` (headless, no window) and
  `bin/viewer --glshot out.png --size WxH <iwad>` (real window, read-back, writes `out.cpu.png` and prints the number
  of differing pixels against the CPU model). `--cycle --frames 100` exercises window-mode, filter and scale-mode
  switching.
