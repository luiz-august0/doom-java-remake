package doom.tools.frame;

import doom.game.api.GameFactory;
import doom.game.view.DefsView;
import doom.game.view.WorldView;
import doom.render.Camera;
import doom.render.FrameBuffer;
import doom.render.RenderOptions;
import doom.render.SceneRenderer;
import doom.render.image.PngWriter;
import doom.testkit.Iwads;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.gfx.Palettes;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.IwadIdentifier;
import doom.wad.iwad.IwadInfo;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Renders oracle scene sidecars with the 3D renderer and compares the view window with the oracle's raw frames.
 * One instance per IWAD.
 */
public final class GoldenFrames {

    /** Outcome of one frame. */
    public record Result(String name, int tic, boolean automap, int windowPixels, int differing,
                         int differingBelowHud, int firstX, int firstY, Path png) {
        /** Every pixel equal. */
        public boolean identical() {
            return differing == 0;
        }

        /** Equal except for the HUD message line drawn over the top rows of the view window. */
        public boolean identicalExceptHud() {
            return differingBelowHud == 0;
        }
    }

    /**
     * Rows at the top of the view window that the oracle frame may overlay with the HUD message line ("Picked up a
     * clip."): the 2D layer is not part of the 3D renderer.
     */
    public static final int HUD_MESSAGE_ROWS = 10;

    private final Path iwadPath;
    private final LumpDirectory lumps;
    private final IwadInfo iwad;
    private final DefsView defs;
    private final Palettes palettes;
    private final Map<String, WorldView> maps = new HashMap<>();
    private SceneRenderer vanilla;
    private SceneRenderer wide;

    public GoldenFrames(Path iwadPath) throws IOException {
        this.iwadPath = iwadPath;
        this.lumps = LumpDirectory.open(List.of(iwadPath));
        this.iwad = IwadIdentifier.identify(WadFile.open(iwadPath));
        this.defs = GameFactory.loadDefs(lumps, iwad, List.of());
        this.palettes = Palettes.read(lumps);
    }

    public LumpDirectory lumps() {
        return lumps;
    }

    public Palettes palettes() {
        return palettes;
    }

    public IwadInfo iwad() {
        return iwad;
    }

    public DefsView defs() {
        return defs;
    }

    /** Geometry of the map a sidecar belongs to. */
    public WorldView geometry(SceneSidecar sc) throws IOException {
        String map = mapNameFromTrace(sc);
        if (map != null) {
            return map(map);
        }
        // fall back: the map whose sector and side counts match
        for (String name : candidateMaps()) {
            WorldView w;
            try {
                w = map(name);
            } catch (RuntimeException e) {
                continue;
            }
            if (w.sectorCount() == sc.sectorFloor.length && w.sideCount() == sc.sideMid.length) {
                return w;
            }
        }
        throw new IOException("cannot determine the map of " + sc.iwad + " " + sc.demo);
    }

    private WorldView map(String name) {
        return maps.computeIfAbsent(name, n -> GameFactory.loadLevelGeometry(lumps, iwad, n));
    }

    private List<String> candidateMaps() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (iwad.mode() == GameMode.COMMERCIAL) {
            for (int m = 1; m <= 32; m++) {
                out.add(String.format(Locale.ROOT, "MAP%02d", m));
            }
        } else {
            for (int e = 1; e <= 4; e++) {
                for (int m = 1; m <= 9; m++) {
                    out.add("E" + e + "M" + m);
                }
            }
        }
        return out;
    }

    private String mapNameFromTrace(SceneSidecar sc) throws IOException {
        String base = sc.iwad.replaceFirst("\\.wad$", "") + "-" + sc.demo.toLowerCase(Locale.ROOT) + ".trace.gz";
        Path dir = Path.of("").toAbsolutePath();
        for (; dir != null; dir = dir.getParent()) {
            Path trace = dir.resolve("testdata/traces").resolve(base);
            if (Files.isRegularFile(trace)) {
                try (InputStream in = new GZIPInputStream(Files.newInputStream(trace))) {
                    String text = new String(in.readNBytes(4096), StandardCharsets.UTF_8);
                    int i = text.indexOf(";map=");
                    if (i < 0) {
                        return null;
                    }
                    String[] f = text.substring(i + 5, text.indexOf(';', i + 5)).split(",");
                    int e = Integer.parseInt(f[0]);
                    int m = Integer.parseInt(f[1]);
                    return iwad.mode() == GameMode.COMMERCIAL
                            ? String.format(Locale.ROOT, "MAP%02d", m) : "E" + e + "M" + m;
                }
            }
        }
        return null;
    }

    /** Renders the sidecar in vanilla mode (320x200, window parameters of the oracle frame). */
    public FrameBuffer renderVanilla(SceneSidecar sc) throws IOException {
        RenderOptions o = RenderOptions.vanillaMode().withScreenBlocks(sc.screenBlocks).withDetail(sc.detailShift);
        if (vanilla == null) {
            vanilla = new SceneRenderer(lumps, defs, o);
        } else if (!vanilla.options().equals(o)) {
            vanilla.setOptions(o);
        }
        FrameBuffer fb = new FrameBuffer(320, 200);
        draw(vanilla, fb, sc);
        return fb;
    }

    /** Renders the sidecar in non-vanilla mode at {@code w x h} (screenblocks 11: full frame). */
    public FrameBuffer renderWide(SceneSidecar sc, int w, int h) throws IOException {
        FrameBuffer fb = new FrameBuffer(w, h);
        renderWith(RenderOptions.of(w, h).withScreenBlocks(11), fb, sc);
        return fb;
    }

    /** Renders the sidecar with arbitrary options into {@code fb} (which must have the size of the options). */
    public void renderWith(RenderOptions o, FrameBuffer fb, SceneSidecar sc) throws IOException {
        if (wide == null) {
            wide = new SceneRenderer(lumps, defs, o);
        } else if (!wide.options().equals(o)) {
            wide.setOptions(o);
        }
        draw(wide, fb, sc);
    }

    /** The renderer used by {@link #renderWith} (created on first use), for tests that inspect it. */
    public SceneRenderer wideRenderer() {
        return wide;
    }

    private void draw(SceneRenderer r, FrameBuffer fb, SceneSidecar sc) throws IOException {
        SceneView view = new SceneView(geometry(sc), sc);
        r.setFuzzPos(sc.fuzzPos);
        Camera cam = new Camera().set(sc.viewX, sc.viewY, sc.viewZ, sc.viewAngle, sc.extraLight, sc.fixedColormap,
                view.viewActor());
        r.render(fb, view, view, view, cam);
    }

    /** Palette (768 bytes) of the sidecar's palette index. */
    public byte[] palette(SceneSidecar sc) {
        return palettes.toArray(sc.palette);
    }

    /**
     * Renders and compares the view window with the oracle frame; on mismatch writes a side-by-side PNG (oracle |
     * ours | difference mask) into {@code outDir}.
     */
    public Result compare(Path sceneFile, Path outDir) throws IOException {
        SceneSidecar sc = SceneSidecar.read(sceneFile);
        byte[] raw = Files.readAllBytes(sceneFile.resolveSibling(sc.rawName));
        FrameBuffer ours = renderVanilla(sc);
        int x0 = sc.windowX;
        int y0 = sc.windowY;
        int w = sc.windowWidth;
        int h = sc.windowHeight;
        int diff = 0;
        int belowHud = 0;
        int fx = -1;
        int fy = -1;
        boolean[] mask = new boolean[320 * 200];
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (raw[y * 320 + x] != ours.pixels()[y * 320 + x]) {
                    if (diff == 0) {
                        fx = x;
                        fy = y;
                    }
                    diff++;
                    if (y >= y0 + HUD_MESSAGE_ROWS) {
                        belowHud++;
                    }
                    mask[y * 320 + x] = true;
                }
            }
        }
        String name = sceneFile.getParent().getFileName() + "-" + String.format(Locale.ROOT, "%06d", sc.tic);
        Path png = null;
        if (belowHud > 0 && outDir != null) {
            Files.createDirectories(outDir);
            png = outDir.resolve(name + ".png");
            writeComparison(png, sc, raw, ours.pixels(), mask);
        }
        return new Result(name, sc.tic, sc.automap != 0, w * h, diff, belowHud, fx, fy, png);
    }

    private void writeComparison(Path png, SceneSidecar sc, byte[] raw, byte[] ours, boolean[] mask)
            throws IOException {
        byte[] rgba = new byte[960 * 200 * 4];
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 320; x++) {
                int i = y * 320 + x;
                put(rgba, y * 960 + x, palettes.rgb(sc.palette, raw[i] & 0xFF));
                put(rgba, y * 960 + 320 + x, palettes.rgb(sc.palette, ours[i] & 0xFF));
                put(rgba, y * 960 + 640 + x, mask[i] ? 0xFF2020 : 0x000000);
            }
        }
        PngWriter.writeRgba(png, rgba, 960, 200);
    }

    private static void put(byte[] rgba, int pixel, int rgb) {
        int o = pixel * 4;
        rgba[o] = (byte) (rgb >> 16);
        rgba[o + 1] = (byte) (rgb >> 8);
        rgba[o + 2] = (byte) rgb;
        rgba[o + 3] = (byte) 0xFF;
    }

    /** Writes a frame of any size as a PNG using the sidecar's palette. */
    public void writePng(Path file, FrameBuffer fb, SceneSidecar sc) throws IOException {
        PngWriter.writeIndexed(file, fb, palette(sc));
    }

    /** The IWAD file this instance was opened from. */
    public Path iwadPath() {
        return iwadPath;
    }

    /** Finds {@code <iwadName>} through {@code DOOM_IWAD_DIR}. */
    public static Path locate(String iwadName, Path dirOverride) {
        if (dirOverride != null) {
            return dirOverride.resolve(iwadName);
        }
        return Iwads.find(iwadName).orElseThrow(() -> new IllegalStateException(iwadName + " not found; set "
                + Iwads.ENV));
    }
}
