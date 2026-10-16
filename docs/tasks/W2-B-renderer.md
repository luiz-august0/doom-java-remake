# Task W2-B — software 3D renderer (vanilla-exact first, any W×H)

Read `docs/agent-guide.md` and `docs/contracts.md` first.

Spec: `docs/spec/04-rendering.md` (all), ADR-008/010/011. Original: `linuxdoom-1.10` `r_main.c r_bsp.c r_segs.c r_plane.c
r_things.c r_draw.c r_data.c r_sky.c f_wipe.c`; Chocolate `src/doom/r_*.c`, `f_wipe.c` (wins when different).

You own: `doom-render` packages `doom.render.scene`, `doom.render.texture`, `doom.render.wipe` (you may add classes to
`doom.render` without breaking `FrameBuffer`/`FrameLayout`), `doom-tools` package `doom.tools.frame` (`frame-dump`),
`doom-it` package `doom.it.render`, `docs/divergences/W2-B.md`.

Inputs on `main`:
- Views `doom.game.view.WorldView` / `ActorView` / `PlayerView` (index-based). `GameFactory.loadLevelGeometry(lumps, iwad, map)`
  gives a static `WorldView`; `GameFactory.loadDefs(lumps, iwad, List.of())` gives sprite names etc.
- `doom.wad.gfx` (Picture, Palettes, Colormaps, Flat), `doom.wad.texture` (TextureDefs, TextureDirectory, FlatDirectory,
  SpriteDirectory), `doom.render.FrameBuffer`, `doom.render.canvas.Canvas2D`, `doom.render.image.PngWriter`.
- Oracle frames + scene sidecars: `$DOOM_HOME/tools/oracle/work/cache/frames/<iwad>-<demo>/frame-NNNNNN.{raw,scene}`
  (14 demos × ~11 frames); syntax in `tools/oracle/FORMAT.md`.

Deliverables:
1. Texture composition (TEXTUREx+PNAMES → columns incl. masked; vanilla 128-wrap in vanilla mode; may use virtual
   threads), flats, sprite frame definitions (`R_InitSprites`: rotations, flips), colormaps/light tables.
2. Renderer for any W×H (`RenderOptions`: width, height, vanilla mode, precisionFixes (off for now), detail,
   screenblocks/view window): BSP, solid/upper/lower/masked walls, visplanes with growable pools, sky (view angle,
   `skytexturemid`), sprites (projection, drawseg clipping, vanilla sort incl. tie order from sector thing lists),
   psprites, lighting (`scalelight`/`zlight`, extralight, fake contrast, fixed colormap, scale normalised by `W43/320`
   per spec 04 §4), fuzz (50-entry table, given `fuzzpos`). Projection per spec 04 §2 (Hor+). Zero allocation per frame.
3. Wipe (melt) in `doom.render.wipe`: timed in tics, exactly 160 misc-RNG draws at start (`MiscRandom`), strips of 2·s.
4. Test fixture `SceneView` implementing the views from static geometry + a parsed sidecar (view, sectors, sides,
   translations, things per sector in snext order, psprites, powers, fuzzpos, sky).
5. `frame-dump` tool + IT `doom.it.render.GoldenFrameIT`: for every cached sidecar render 320×200 vanilla and compare
   the view-window region (sidecar `window` record) byte-for-byte with the `.raw`; on mismatch write side-by-side PNG +
   diff mask under `target/`. Gate: all view windows identical (list justified exceptions).
6. Visual check: a few scenes at 2560×900 (Hor+) viewed with the Read tool.

Build: `./mvnw -o -q -pl doom-render,doom-tools -am verify`; ITs: `DOOM_IWAD_DIR=$DOOM_HOME/.iwads ./mvnw -o -q -pl doom-it -am verify`.
Report: pass counts per demo, renderer API summary for engine/UI, contract requests.
