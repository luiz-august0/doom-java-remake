package doom.tools.frame;

import doom.render.FrameBuffer;
import doom.testkit.oracle.OracleRunner;
import doom.tools.Tool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Render frames of a demo in vanilla mode and compare them with oracle frames.
 *
 * <pre>
 * frame-dump [--cache DIR] [--iwad-dir DIR] [--out DIR] [--size WxH] [name ...]
 * </pre>
 *
 * {@code name} is a cache sub-directory such as {@code doom1-demo1} (all when omitted). Without {@code --size} every
 * sidecar is rendered at 320x200 in vanilla mode and the view window is compared byte for byte with the oracle's raw
 * frame; mismatches write a side-by-side PNG (oracle | ours | difference) into {@code --out}. With {@code --size}
 * (for example {@code 2560x900}) the frames are rendered in Hor+ mode and written as PNGs, without comparison.
 */
public final class FrameDumpTool implements Tool {

    @Override
    public String name() {
        return "frame-dump";
    }

    @Override
    public String summary() {
        return "Render frames of a demo in vanilla mode and compare them with oracle frames";
    }

    @Override
    public int run(String[] args) throws Exception {
        Path cache = OracleRunner.cacheDirectory().resolve("frames");
        Path iwadDir = null;
        Path out = Path.of("target/golden-frames");
        int w = 0;
        int h = 0;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cache" -> cache = Path.of(args[++i]);
                case "--iwad-dir" -> iwadDir = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--size" -> {
                    String[] wh = args[++i].toLowerCase(Locale.ROOT).split("x");
                    w = Integer.parseInt(wh[0]);
                    h = Integer.parseInt(wh[1]);
                }
                case "-h", "--help" -> {
                    System.err.println("usage: frame-dump [--cache DIR] [--iwad-dir DIR] [--out DIR] [--size WxH] [name ...]");
                    return 0;
                }
                default -> names.add(args[i]);
            }
        }
        if (!Files.isDirectory(cache)) {
            System.err.println("frame-dump: no frame cache at " + cache);
            return 2;
        }
        if (names.isEmpty()) {
            try (Stream<Path> s = Files.list(cache)) {
                s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().forEach(names::add);
            }
        }
        int bad = 0;
        int total = 0;
        int hudOnly = 0;
        for (String name : names) {
            Path dir = cache.resolve(name);
            List<Path> scenes = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".scene")).sorted().forEach(scenes::add);
            }
            if (scenes.isEmpty()) {
                continue;
            }
            SceneSidecar first = SceneSidecar.read(scenes.get(0));
            GoldenFrames gf = new GoldenFrames(GoldenFrames.locate(first.iwad, iwadDir));
            for (Path scene : scenes) {
                total++;
                if (w > 0) {
                    SceneSidecar sc = SceneSidecar.read(scene);
                    FrameBuffer fb = gf.renderWide(sc, w, h);
                    Files.createDirectories(out);
                    Path png = out.resolve(name + "-" + scene.getFileName().toString().replace(".scene", "") + "-" + w
                            + "x" + h + ".png");
                    gf.writePng(png, fb, sc);
                    System.out.println(png);
                    continue;
                }
                GoldenFrames.Result r = gf.compare(scene, out);
                if (r.identical()) {
                    System.out.printf("%s: identical (%d px)%s%n", r.name(), r.windowPixels(),
                            r.automap() ? " [automap]" : "");
                } else if (r.identicalExceptHud()) {
                    hudOnly++;
                    System.out.printf("%s: identical except the HUD message rows (%d px)%n", r.name(), r.differing());
                } else {
                    bad++;
                    System.out.printf("%s: %d of %d px differ, first at (%d,%d)%s -> %s%n", r.name(), r.differing(),
                            r.windowPixels(), r.firstX(), r.firstY(), r.automap() ? " [automap]" : "", r.png());
                }
            }
        }
        if (w == 0) {
            System.out.printf("%d frames, %d differ only in the HUD message rows, %d with other differences%n", total,
                    hudOnly, bad);
        }
        return bad == 0 ? 0 : 1;
    }
}
