package doom.render.scene;

import doom.game.view.ActorView;
import doom.game.view.DefsView;
import doom.game.view.PlayerView;
import doom.game.view.WorldView;
import doom.math.annotation.VanillaRef;
import doom.render.Camera;
import doom.render.FrameBuffer;
import doom.render.RenderOptions;
import doom.render.texture.FlatCache;
import doom.render.texture.SpriteSet;
import doom.render.texture.TextureCache;
import doom.wad.LumpDirectory;
import doom.wad.gfx.Colormaps;

/**
 * The software 3D renderer: BSP walls, visplanes, sky, sprites and player sprites into an indexed framebuffer
 * (spec 04). Public so that {@code doom.render.SceneRenderer} can wrap it; the package is not exported.
 *
 * <p>Not thread-safe; zero allocation per frame once the pools and the lazily loaded textures, flats and sprites have
 * warmed up.
 */
public final class Renderer {

    private final LumpDirectory lumps;
    private final DefsView defs;
    private final RenderState rs;
    private final Draw draw;
    private final Planes planes;
    private final Things things;
    private final BspWalls walls;

    private String lastMap = "";
    private int lastLineCount = -1;
    private RenderOptions options;

    public Renderer(LumpDirectory lumps, DefsView defs, RenderOptions options) {
        this.lumps = lumps;
        this.defs = defs;
        this.options = options;
        byte[] colormaps = Colormaps.read(lumps).table();
        TextureCache textures = new TextureCache(lumps, options.vanilla());
        FlatCache flats = new FlatCache(lumps);
        SpriteSet sprites = new SpriteSet(lumps, defs, options.vanilla());
        this.rs = new RenderState(textures, flats, sprites, colormaps);
        this.draw = new Draw(rs);
        this.planes = new Planes(rs, draw);
        this.things = new Things(rs, draw);
        this.walls = new BspWalls(rs, draw, planes, things);
        this.things.walls = walls;
        applyOptions(options);
    }

    private void applyOptions(RenderOptions o) {
        rs.setViewSize(o);
        planes.resize();
        walls.resize();
        things.resize();
    }

    /** Changes the options (size, detail, view size, mode). Rebuilds the texture and sprite caches if the mode changed. */
    public void setOptions(RenderOptions o) {
        if (o.vanilla() != options.vanilla()) {
            rs.textures = new TextureCache(lumps, o.vanilla());
            rs.sprites = new SpriteSet(lumps, defs, o.vanilla());
        }
        options = o;
        applyOptions(o);
    }

    public RenderOptions options() {
        return options;
    }

    /** Composes every wall texture and loads every flat and sprite now (so no frame pays for it). */
    public void precache() {
        rs.textures.composeAll();
        rs.flats.loadAll();
        rs.sprites.loadAll();
    }

    public int viewWindowX() {
        return rs.winX;
    }

    public int viewWindowY() {
        return rs.winY;
    }

    public int viewWindowWidth() {
        return rs.winW;
    }

    public int viewWindowHeight() {
        return rs.viewHeight;
    }

    /** {@code fuzzpos}: position in the spectre fuzz table; state that persists from frame to frame. */
    public int fuzzPos() {
        return rs.fuzzPos;
    }

    public void setFuzzPos(int pos) {
        rs.fuzzPos = pos;
    }

    /** Whether the line has been seen so far ({@code ML_MAPPED}), for the automap. */
    public boolean lineMapped(int line) {
        return line >= 0 && line < walls.lineMapped.length && walls.lineMapped[line];
    }

    /** Forgets the lines seen so far (new level). */
    public void resetMapped(int lineCount) {
        walls.resetMapped(lineCount);
        lastLineCount = lineCount;
    }

    /** Number of drawsegs / visplanes used by the last frame (statistics and tests). */
    public int lastDrawSegs() {
        return walls.numDrawSegs;
    }

    public int lastVisplanes() {
        return planes.numPlanes;
    }

    /** {@code R_RenderPlayerView}. */
    @VanillaRef("r_main.c:R_RenderPlayerView")
    public void render(FrameBuffer frame, WorldView world, ActorView actors, PlayerView player, Camera cam) {
        rs.world = world;
        rs.actors = actors;
        rs.player = player;
        walls.world = world;
        rs.bindFrame(frame);
        String map = world.mapName();
        int lines = world.lineCount();
        if (lines != lastLineCount || !map.equals(lastMap)) {
            resetMapped(lines);
            lastMap = map;
        }
        things.resetSectors(world.sectorCount());
        rs.setupFrame(cam);

        walls.clearFrame();
        planes.clearPlanes();
        things.clearFrame();
        draw.startFrame();

        walls.renderBsp();
        planes.drawPlanes();
        things.drawMasked();
    }
}
