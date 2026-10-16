package doom.it.sync;

import doom.game.api.DemoPlayback;
import doom.game.api.GameFactory;
import doom.game.api.GameSetup;
import doom.game.api.TicCmd;
import doom.game.impl.SessionImpl;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.iwad.IwadIdentifier;
import doom.wad.iwad.IwadInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Plays a demo headless in a session, writes our trace and compares it with the oracle trace (gated keys of contracts
 * §6.2: everything except {@code m}). Reports the first divergent tic and key and the first stub hit.
 */
final class DemoSync {

    /** Result of one demo. */
    record Result(String demo, int ticsRef, int ticsOurs, int divergenceTic, String divergenceKey, String detail,
                  int firstStubTic, String firstStubKey, Map<String, Integer> stubHits, String error) {

        boolean gatePasses() {
            if (error != null) {
                return false;
            }
            return divergenceTic < 0 || (firstStubTic >= 0 && divergenceTic >= firstStubTic);
        }

        String line() {
            return demo + "\t" + ticsRef + "\t" + ticsOurs + "\t" + divergenceTic + "\t"
                    + (divergenceKey == null ? "-" : divergenceKey) + "\t" + firstStubTic + "\t"
                    + (firstStubKey == null ? "-" : firstStubKey) + "\t" + (gatePasses() ? "PASS" : "FAIL")
                    + (error != null ? "\t" + error : "");
        }
    }

    private DemoSync() {
    }

    static List<String> readTrace(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream raw = Files.newInputStream(file);
             InputStream in = file.toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("#") && !line.startsWith("  ")) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /**
     * Runs the demo.
     *
     * @param oursOut      where to write our trace (gz), or null
     * @param verboseOut   where to write our verbose trace, or null
     * @param verboseFrom  first tic of the verbose range
     * @param verboseTo    last tic of the verbose range
     */
    static Result run(String demoName, Path iwad, byte[] demo, Path refTrace, Path oursOut, Path verboseOut,
            int verboseFrom, int verboseTo) throws IOException {
        return run(demoName, iwad, List.of(), demo, refTrace, oursOut, verboseOut, verboseFrom, verboseTo);
    }

    /** Same as above with PWADs loaded after the IWAD ({@code -file}). */
    static Result run(String demoName, Path iwad, List<Path> pwads, byte[] demo, Path refTrace, Path oursOut,
            Path verboseOut, int verboseFrom, int verboseTo) throws IOException {
        List<String> ref = readTrace(refTrace);
        IwadInfo info = IwadIdentifier.identify(WadFile.open(iwad));
        List<Path> files = new ArrayList<>();
        files.add(iwad);
        files.addAll(pwads);
        LumpDirectory lumps = LumpDirectory.open(files);
        SessionImpl session = (SessionImpl) GameFactory.createSession(
                new GameSetup(lumps, info, info.version(), List.of(), null));
        session.playDemo(demoName, demo, DemoPlayback.TIMEDEMO);

        TicCmd[] cmds = {new TicCmd(), new TicCmd(), new TicCmd(), new TicCmd()};
        List<String> ours = new ArrayList<>(ref.size() + 1);
        StringBuilder sb = new StringBuilder(4096);
        Writer verbose = verboseOut == null ? null
                : Files.newBufferedWriter(verboseOut, StandardCharsets.UTF_8);
        String error = null;
        try {
            int limit = ref.size() + 10;
            for (int tic = 0; tic < limit && !session.quit(); tic++) {
                session.tick(cmds, false);
                if (session.quit()) {
                    break;
                }
                sb.setLength(0);
                session.appendTraceLine(sb);
                ours.add(sb.toString());
                if (verbose != null && tic >= verboseFrom && tic <= verboseTo) {
                    verbose.write(sb.toString());
                    verbose.write('\n');
                    sb.setLength(0);
                    session.appendTraceVerbose(sb);
                    verbose.write(sb.toString());
                }
            }
        } catch (RuntimeException e) {
            error = e.toString();
            StackTraceElement[] st = e.getStackTrace();
            if (st.length > 0) {
                error += " at " + st[0];
            }
        } finally {
            if (verbose != null) {
                verbose.close();
            }
        }
        if (oursOut != null) {
            try (Writer w = new java.io.OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(oursOut)),
                    StandardCharsets.UTF_8)) {
                for (String l : ours) {
                    w.write(l);
                    w.write('\n');
                }
            }
        }

        int divTic = -1;
        String divKey = null;
        String detail = null;
        int n = Math.min(ref.size(), ours.size());
        for (int i = 0; i < n && divTic < 0; i++) {
            String[] diff = firstDiff(ref.get(i), ours.get(i));
            if (diff != null) {
                divTic = i;
                divKey = diff[0];
                detail = diff[1];
            }
        }
        if (divTic < 0 && ref.size() != ours.size()) {
            divTic = n;
            divKey = "length";
            detail = "ref " + ref.size() + " lines, ours " + ours.size();
        }
        return new Result(demoName, ref.size(), ours.size(), divTic, divKey, detail,
                session.unimplemented().firstTic(), session.unimplemented().firstKey(),
                session.unimplemented().hits(), error);
    }

    private static final String[] PLAYER_FIELDS = {"x", "y", "z", "angle", "momx", "momy", "momz", "mo.health",
        "viewz", "viewheight", "deltaviewheight", "bob", "playerstate", "health", "armorpoints", "armortype",
        "readyweapon", "pendingweapon", "weaponowned", "ammo0", "ammo1", "ammo2", "ammo3", "maxammo0", "maxammo1",
        "maxammo2", "maxammo3", "cards", "backpack", "pw0", "pw1", "pw2", "pw3", "pw4", "pw5", "cheats", "refire",
        "killcount", "itemcount", "secretcount", "damagecount", "bonuscount", "extralight", "fixedcolormap",
        "colormap", "attacker", "wstate", "wtics", "wsx", "wsy", "fstate", "ftics", "fsx", "fsy", "attackdown",
        "usedown", "frag0", "frag1", "frag2", "frag3", "didsecret"};

    /** First differing gated key: {key, "ref=... ours=..."}, or null. */
    static String[] firstDiff(String ref, String ours) {
        String[] a = ref.split(";");
        String[] b = ours.split(";");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            String x = i < a.length ? a[i] : "";
            String y = i < b.length ? b[i] : "";
            String kx = x.contains("=") ? x.substring(0, x.indexOf('=')) : x;
            if (kx.equals("m")) {
                continue;
            }
            if (!x.equals(y)) {
                String key = kx;
                if (kx.startsWith("p") && x.contains("=") && y.contains("=")) {
                    String[] fx = x.substring(x.indexOf('=') + 1).split(",");
                    String[] fy = y.substring(y.indexOf('=') + 1).split(",");
                    for (int k = 0; k < Math.min(fx.length, fy.length); k++) {
                        if (!fx[k].equals(fy[k])) {
                            key = kx + "." + (k < PLAYER_FIELDS.length ? PLAYER_FIELDS[k] : Integer.toString(k));
                            return new String[] {key, "ref=" + fx[k] + " ours=" + fy[k]};
                        }
                    }
                }
                return new String[] {key, "ref=" + x + " ours=" + y};
            }
        }
        return null;
    }
}
