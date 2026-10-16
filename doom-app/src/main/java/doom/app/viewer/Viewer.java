package doom.app.viewer;

import doom.platform.InputSource;
import doom.platform.PlatformProvider;
import doom.platform.ScaleFilter;
import doom.platform.ScaleMode;
import doom.platform.VideoConfig;
import doom.platform.VideoSink;
import doom.platform.WindowMode;
import doom.platform.input.InputEvent;
import doom.platform.input.InputEventQueue;
import doom.platform.input.KeyCode;
import doom.render.FrameBuffer;
import doom.render.FrameLayout;
import doom.render.canvas.Canvas2D;
import doom.render.image.PngWriter;
import doom.wad.LumpDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Development viewer (W1-E): shows a full-screen picture of an IWAD (TITLEPIC by default) through {@link Canvas2D}
 * and the platform backend, to check the 2D foundation and the presentation path.
 *
 * <pre>
 * usage: viewer [options] &lt;iwad&gt;
 *   --vanilla            320x200 frame (aspect corrected 4:3) instead of the automatic resolution
 *   --size WxH           window size / output size for the shots (default 1280x720)
 *   --lump NAME          picture lump to show (default TITLEPIC)
 *   --flat NAME          flat for the tiled pillarbox (default FLOOR4_8)
 *   --palette N          initial palette 0..13
 *   --column-major       use the transposed (column-major) frame layout
 *   --sharp              start with the sharp-bilinear filter
 *   --square             square pixels (no 1.2 aspect correction)
 *   --shot FILE.png      headless: write the indexed frame (internal resolution) as PNG and exit
 *   --shotout FILE.png   headless: also write the CPU model of the presented output (RGBA, WxH)
 *   --glshot FILE.png    open a window, present, read back the real output, save it, compare to the CPU model, exit
 *   --mode M             initial window mode: windowed, borderless, fullscreen
 *   --cycle              self-test: every 20 frames switch window mode, filter and scale mode (use with --frames)
 *   --frames N           exit after N frames (window mode)
 * keys: 0-9 palette, - / = previous / next palette, F window mode, S filter, M scale mode, B black/flat pillarbox,
 *       V vsync, Esc quit
 * </pre>
 */
public final class Viewer {

    private static final System.Logger LOG = System.getLogger("doom.app.viewer");

    private final Options opt;
    private final ViewerScene scene;
    private final FrameBuffer frame;
    private final Canvas2D canvas;

    private int palette;
    private boolean flatSides;
    private WindowMode mode = WindowMode.WINDOWED;
    private ScaleFilter filter = ScaleFilter.NEAREST;
    private ScaleMode scaleMode = ScaleMode.FIT;
    private boolean vsync = true;

    private Viewer(Options opt, ViewerScene scene) {
        this.opt = opt;
        this.scene = scene;
        this.frame = new FrameBuffer(320, 200, opt.columnMajor ? FrameLayout.COLUMN_MAJOR : FrameLayout.ROW_MAJOR);
        this.canvas = new Canvas2D(frame);
        this.mode = opt.mode;
        this.palette = opt.palette;
        this.filter = opt.sharp ? ScaleFilter.SHARP_BILINEAR : ScaleFilter.NEAREST;
        this.flatSides = opt.flatSides;
    }

    private static final class Options {
        String iwad;
        boolean vanilla;
        boolean square;
        boolean columnMajor;
        boolean sharp;
        boolean cycle;
        WindowMode mode = WindowMode.WINDOWED;
        int width = 1280;
        int height = 720;
        String lump = "TITLEPIC";
        String flat;
        int palette;
        boolean flatSides;
        String shot;
        String shotOut;
        String glShot;
        int frames = -1;
        String backend;
    }

    public static void main(String[] args) throws Exception {
        Options opt;
        try {
            opt = parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("viewer: " + e.getMessage());
            System.err.println("usage: viewer [--vanilla] [--size WxH] [--lump NAME] [--palette N] [--square]"
                    + " [--shot f.png [--shotout f.png]] [--glshot f.png] [--frames N] <iwad>");
            System.exit(2);
            return;
        }
        LumpDirectory lumps = LumpDirectory.open(List.of(Path.of(opt.iwad)));
        ViewerScene scene = new ViewerScene(lumps, opt.lump, opt.flat, opt.vanilla);
        Viewer viewer = new Viewer(opt, scene);
        if (opt.shot != null) {
            viewer.headlessShot();
        } else {
            viewer.runWindow();
        }
    }

    private static Options parse(String[] args) {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--vanilla" -> o.vanilla = true;
                case "--square" -> o.square = true;
                case "--column-major" -> o.columnMajor = true;
                case "--sharp" -> o.sharp = true;
                case "--cycle" -> o.cycle = true;
                case "--mode" -> o.mode = WindowMode.valueOf(need(args, ++i, a).toUpperCase(java.util.Locale.ROOT));
                case "--size" -> {
                    String[] p = need(args, ++i, a).split("x");
                    if (p.length != 2) {
                        throw new IllegalArgumentException("--size needs WxH");
                    }
                    o.width = Integer.parseInt(p[0]);
                    o.height = Integer.parseInt(p[1]);
                }
                case "--lump" -> o.lump = need(args, ++i, a);
                case "--flat" -> {
                    o.flat = need(args, ++i, a);
                    o.flatSides = true;
                }
                case "--palette" -> o.palette = Integer.parseInt(need(args, ++i, a));
                case "--shot" -> o.shot = need(args, ++i, a);
                case "--shotout" -> o.shotOut = need(args, ++i, a);
                case "--glshot" -> o.glShot = need(args, ++i, a);
                case "--frames" -> o.frames = Integer.parseInt(need(args, ++i, a));
                case "--backend" -> o.backend = need(args, ++i, a);
                default -> {
                    if (a.startsWith("--")) {
                        throw new IllegalArgumentException("unknown option " + a);
                    }
                    o.iwad = a;
                }
            }
        }
        if (o.iwad == null) {
            throw new IllegalArgumentException("missing IWAD path");
        }
        return o;
    }

    private static String need(String[] args, int i, String opt) {
        if (i >= args.length) {
            throw new IllegalArgumentException(opt + " needs a value");
        }
        return args[i];
    }

    private double pixelAspect() {
        return opt.square ? 1.0 : 1.2;
    }

    private void layoutFor(int outW, int outH) {
        int[] s = ViewerScene.frameSize(opt.vanilla, opt.square, outW, outH);
        frame.resize(s[0], s[1]);
        scene.draw(frame, canvas, flatSides);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Headless
    // ---------------------------------------------------------------------------------------------------------

    private void headlessShot() throws IOException {
        layoutFor(opt.width, opt.height);
        byte[] pal = scene.palettes().toArray(palette);
        PngWriter.writeIndexed(Path.of(opt.shot), frame, pal);
        System.out.println("wrote " + opt.shot + " (" + frame.width() + "x" + frame.height() + " indexed, palette "
                + palette + ")");
        if (opt.shotOut != null) {
            byte[] rgba = ViewerScene.presentOnCpu(frame, pal, opt.width, opt.height, pixelAspect());
            PngWriter.writeRgba(Path.of(opt.shotOut), rgba, opt.width, opt.height);
            System.out.println("wrote " + opt.shotOut + " (" + opt.width + "x" + opt.height + " presented)");
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Window
    // ---------------------------------------------------------------------------------------------------------

    private PlatformProvider chooseProvider() {
        List<PlatformProvider> all = new ArrayList<>();
        ServiceLoader.load(PlatformProvider.class).forEach(all::add);
        all.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        for (PlatformProvider p : all) {
            if (opt.backend != null && !p.name().equals(opt.backend)) {
                continue;
            }
            if (p.isSupported()) {
                return p;
            }
        }
        throw new IllegalStateException("no supported platform backend (found: " + all.stream().map(
                PlatformProvider::name).toList() + "). On macOS run with -XstartOnFirstThread (bin/viewer does).");
    }

    private VideoConfig config(String title) {
        return new VideoConfig(title, mode, opt.width, opt.height, vsync, 0, scaleMode, filter, pixelAspect());
    }

    private void runWindow() throws IOException {
        PlatformProvider provider = chooseProvider();
        boolean shotMode = opt.glShot != null;
        try (VideoSink video = provider.createVideoSink()) {
            video.open(config(title(0)));
            InputSource input = provider.createInputSource(video);
            InputEventQueue queue = new InputEventQueue();
            layoutFor(video.outputWidth(), video.outputHeight());
            video.setPalette(scene.palettes().toArray(palette));
            LOG.log(System.Logger.Level.INFO, "output " + video.outputWidth() + "x" + video.outputHeight()
                    + ", frame " + frame.width() + "x" + frame.height());

            int frames = 0;
            int fpsFrames = 0;
            long fpsStart = System.nanoTime();
            boolean quit = false;
            while (!quit && !video.closeRequested()) {
                queue.clear();
                input.poll(queue);
                boolean redraw = false;
                for (int i = 0; i < queue.size(); i++) {
                    InputEvent e = queue.get(i);
                    if (e.type == InputEvent.KEY_DOWN) {
                        switch (handleKey(e.code, video)) {
                            case QUIT -> quit = true;
                            case REDRAW -> redraw = true;
                            default -> { }
                        }
                    } else if (e.type == InputEvent.RESIZED) {
                        LOG.log(System.Logger.Level.INFO, "resized to " + e.code + "x" + e.device);
                        redraw = true;
                    }
                }
                if (redraw) {
                    layoutFor(video.outputWidth(), video.outputHeight());
                    video.setPalette(scene.palettes().toArray(palette));
                }
                video.present(frame.pixels(), frame.width(), frame.height(), frame.columnMajor());
                frames++;
                fpsFrames++;
                if (opt.cycle && frames % 20 == 0) {
                    handleKey('f', video);
                    handleKey('s', video);
                    handleKey('m', video);
                    redrawAfterModeChange(video);
                }
                long now = System.nanoTime();
                if (now - fpsStart >= 1_000_000_000L) {
                    video.reconfigure(config(title(fpsFrames * 1e9 / (now - fpsStart))));
                    fpsFrames = 0;
                    fpsStart = now;
                }
                if (shotMode && frames >= 2) {
                    glShot(video);
                    quit = true;
                }
                if (opt.frames > 0 && frames >= opt.frames) {
                    quit = true;
                }
            }
        }
    }

    private void glShot(VideoSink video) throws IOException {
        int w = video.outputWidth();
        int h = video.outputHeight();
        byte[] real = video.readBackOutput();
        PngWriter.writeRgba(Path.of(opt.glShot), real, w, h);
        byte[] pal = scene.palettes().toArray(palette);
        byte[] cpu = ViewerScene.presentOnCpu(frame, pal, w, h, pixelAspect());
        String cpuName = opt.glShot.endsWith(".png") ? opt.glShot.substring(0, opt.glShot.length() - 4) + ".cpu.png"
                : opt.glShot + ".cpu.png";
        PngWriter.writeRgba(Path.of(cpuName), cpu, w, h);
        long different = 0;
        int maxDiff = 0;
        for (int i = 0; i < w * h; i++) {
            int d = 0;
            for (int c = 0; c < 3; c++) {
                d = Math.max(d, Math.abs((real[i * 4 + c] & 0xFF) - (cpu[i * 4 + c] & 0xFF)));
            }
            if (d != 0) {
                different++;
            }
            maxDiff = Math.max(maxDiff, d);
        }
        System.out.println("wrote " + opt.glShot + " and " + cpuName + " (" + w + "x" + h + "), frame "
                + frame.width() + "x" + frame.height());
        System.out.println("GL vs CPU model: " + different + " of " + (long) w * h + " pixels differ, max channel diff "
                + maxDiff);
    }

    private String title(double fps) {
        return "doom-java viewer - " + opt.lump + " " + frame.width() + "x" + frame.height() + " - "
                + Math.round(fps) + " fps";
    }

    private void redrawAfterModeChange(VideoSink video) {
        layoutFor(video.outputWidth(), video.outputHeight());
        video.setPalette(scene.palettes().toArray(palette));
    }

    private enum Action { NONE, REDRAW, QUIT }

    private Action handleKey(int code, VideoSink video) {
        if (code == KeyCode.ESCAPE) {
            return Action.QUIT;
        }
        int n = scene.palettes().count();
        if (code >= '0' && code <= '9') {
            palette = Math.min(code - '0', n - 1);
            video.setPalette(scene.palettes().toArray(palette));
            return Action.NONE;
        }
        if (code == KeyCode.MINUS) {
            palette = (palette + n - 1) % n;
            video.setPalette(scene.palettes().toArray(palette));
            return Action.NONE;
        }
        if (code == KeyCode.EQUALS) {
            palette = (palette + 1) % n;
            video.setPalette(scene.palettes().toArray(palette));
            return Action.NONE;
        }
        switch (code) {
            case 'f' -> {
                mode = WindowMode.values()[(mode.ordinal() + 1) % WindowMode.values().length];
                video.reconfigure(config(title(0)));
                return Action.REDRAW;
            }
            case 's' -> {
                filter = filter == ScaleFilter.NEAREST ? ScaleFilter.SHARP_BILINEAR : ScaleFilter.NEAREST;
                video.reconfigure(config(title(0)));
            }
            case 'm' -> {
                scaleMode = ScaleMode.values()[(scaleMode.ordinal() + 1) % ScaleMode.values().length];
                video.reconfigure(config(title(0)));
            }
            case 'v' -> {
                vsync = !vsync;
                video.reconfigure(config(title(0)));
            }
            case 'b' -> {
                flatSides = !flatSides;
                return Action.REDRAW;
            }
            default -> { }
        }
        return Action.NONE;
    }
}
