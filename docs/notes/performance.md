# Rendering performance (W4-R)

Measured with `doom.it.render.TimedemoBenchmark` (`-Dit.test=TimedemoBenchmark -Dbench.timedemo=true`): the whole engine
frame (simulation, 3D renderer, UI/status bar, palette) in `-timedemo` mode (one tic and one frame per iteration, no
interpolation, no vsync, no sound), with a null video sink, so the number excludes the GPU upload. One warm-up pass,
then the measured pass of the same demo in the same JVM. Apple M2, JDK 25 (G1, default flags), row-major frame.

| Demo | Output (internal) | fps, precision fixes on | fps, off | Target (spec 04 section 9) |
|---|---|---|---|---|
| freedoom2 DEMO1 (MAP03) | 320x200 vanilla | 1476 | 1476 | > 2000 (sanity) |
| freedoom2 DEMO3 (MAP22) | 320x200 vanilla | 1568 | 1568 | > 2000 |
| doom1 DEMO1 (E1M5) | 320x200 vanilla | 2536 | 2536 | > 2000 |
| freedoom2 DEMO1 | 2560x1080 (2560x900) | 264 | 249 | >= 200 |
| freedoom2 DEMO3 | 2560x1080 (2560x900) | 279 | 273 | >= 200 |
| doom1 DEMO1 | 2560x1080 (2560x900) | 306 | 311 | >= 200 |
| freedoom2 DEMO1 | 3840x2160 (3840x1800) | 103 | 99 | >= 90 |
| freedoom2 DEMO3 | 3840x2160 (3840x1800) | 105 | 106 | >= 90 |
| doom1 DEMO1 | 3840x2160 (3840x1800) | 105 | 108 | >= 90 |

Simulation alone (headless timedemo) runs at 38 000 to 114 000 tics/s, so it is below 3% of a frame at every size.

Findings:

* Both high-resolution targets are met on the three demos. The precise code paths (exact wall scale, 32.32 column and
  span steps) cost nothing measurable: they are within noise of the plain generalisation (+-5%).
* 320x200 is at 1500 fps on the two Freedoom 2 demos (a bit under the 2000 fps sanity target) and 2500 fps on E1M5.
  A JFR profile of the freedoom2 run shows 30% in `Draw.drawColumn`, 16% in `Draw.drawSpan`, 12% in the status bar
  (`Canvas2D.drawPatchImpl`, redrawn every frame like the original did at 35 Hz, but here once per frame) and the rest
  spread over BSP, visplanes and the UI. No single hot spot is left; the column loop is already one load, one colormap
  lookup and one store per pixel. Row-major patch drawing (contiguous runs instead of strided writes) was the only
  change that mattered for the 2D layer.
* Transposed (column-major) framebuffer: +2% at 2560 and +6% at 3840 on the same demos, within the error of the
  measurement. Not worth the cost for spans, the UI and the wipe; the engine keeps row-major frames
  (`FrameLayout` remains switchable inside `doom.render`).
* The remaining headroom is multi-threading by vertical strips (spec 04 section 9, post-v1).

## Reproducing

```
DOOM_IWAD_DIR=.iwads ./mvnw -o -q -pl doom-it -am verify -Dit.test=TimedemoBenchmark -Dbench.timedemo=true \
    -Dtest=NoSuchTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
```

`-Dbench.small=true` runs only 320x200; `-Dbench.quick=true` skips the fixes-off comparison.
