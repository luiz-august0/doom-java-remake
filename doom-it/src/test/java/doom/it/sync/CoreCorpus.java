package doom.it.sync;

import doom.testkit.demo.Demo;
import doom.testkit.demo.DemoGenerator;
import doom.testkit.demo.DemoPolicy;
import doom.testkit.demo.DemoSpec;
import doom.testkit.oracle.OracleRunner;
import doom.testkit.oracle.TraceCache;
import doom.wad.LumpDirectory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The "core" corpus of W2-A: maps of the Freedoom IWADs with every sector and line special removed (a PWAD built at
 * test time), played with movement-only, no-monster synthetic demos. Nothing in them needs the Wave 3 areas, so they
 * must sync for their whole length; they exercise spawning, movement, collision, sliding, pickups, player view and
 * weapon bobbing, coop/deathmatch spawning and altdeath item respawn. Oracle traces are produced with
 * {@code tools/oracle/run.sh ... -file <pwad>} and cached under {@code $DOOM_ORACLE_CACHE/core}.
 */
final class CoreCorpus {

    /** One corpus demo. */
    record Entry(String name, String iwad, String map, Path pwad, byte[] demo) {
    }

    private static final String[][] MAPS = {
        {"freedoom1.wad", "E1M1"}, {"freedoom1.wad", "E1M2"}, {"freedoom1.wad", "E1M4"}, {"freedoom1.wad", "E2M2"},
        {"freedoom1.wad", "E3M3"}, {"freedoom1.wad", "E4M1"},
        {"freedoom2.wad", "MAP01"}, {"freedoom2.wad", "MAP02"}, {"freedoom2.wad", "MAP05"}, {"freedoom2.wad", "MAP07"},
        {"freedoom2.wad", "MAP11"}, {"freedoom2.wad", "MAP20"},
    };

    /** {skill, players, deathmatch, tics, buttons}; buttons = 1 adds use presses and weapon changes. */
    private static final int[][] VARIANTS = {{3, 1, 0, 2100, 0}, {1, 2, 0, 1500, 0}, {2, 4, 1, 1500, 0},
        {4, 3, 2, 3000, 0}, {0, 1, 0, 2500, 1}, {3, 4, 0, 1800, 1}, {2, 2, 1, 2000, 1}};

    private static final String[] MAP_LUMPS = {"THINGS", "LINEDEFS", "SIDEDEFS", "VERTEXES", "SEGS", "SSECTORS",
        "NODES", "SECTORS", "REJECT", "BLOCKMAP"};

    private CoreCorpus() {
    }

    /** Builds (or reuses) the PWADs and demos in {@code dir}. */
    static List<Entry> build(Path iwadDir, Path dir) throws IOException {
        Files.createDirectories(dir);
        List<Entry> out = new ArrayList<>();
        for (String[] m : MAPS) {
            Path iwad = iwadDir.resolve(m[0]);
            if (!Files.isRegularFile(iwad)) {
                continue;
            }
            String tag = (m[0].startsWith("freedoom1") ? "f1" : "f2") + m[1].toLowerCase(Locale.ROOT);
            Path pwad = dir.resolve(tag + "ns.wad");
            Files.write(pwad, stripSpecials(LumpDirectory.open(List.of(iwad)), m[1]));
            for (int v = 0; v < VARIANTS.length; v++) {
                int[] var = VARIANTS[v];
                String name = "c" + tag + v; // e.g. cf1e1m10 (at most 8 characters)
                long seed = 0x5eedL * 31 + name.hashCode();
                DemoSpec spec = new DemoSpec(var[0], m[1], var[1], var[2], false, false, true, var[3], seed,
                        DemoPolicy.MOVEMENT_ONLY);
                Demo demo = DemoGenerator.generate(spec);
                if (var[4] != 0) {
                    demo = withButtons(demo, seed);
                }
                byte[] bytes = demo.toBytes();
                Files.write(dir.resolve(name + ".lmp"), bytes);
                out.add(new Entry(name, m[0], m[1], pwad, bytes));
            }
        }
        return out;
    }

    /**
     * Adds use presses (a 1-3 tic press every 20-60 tics) and weapon changes (weapon 0-7, every 50-150 tics) to a
     * movement-only demo. Without line specials, use only reaches {@code PTR_UseTraverse}'s "noway" path.
     */
    static Demo withButtons(Demo demo, long seed) {
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(seed ^ 0xb077045L);
        Demo.Cmd[][] cmds = new Demo.Cmd[demo.tics()][];
        int players = demo.header().playerCount();
        int[] nextUse = new int[players];
        int[] useLeft = new int[players];
        int[] nextChange = new int[players];
        for (int p = 0; p < players; p++) {
            nextUse[p] = 20 + rnd.nextInt(40);
            nextChange[p] = 50 + rnd.nextInt(100);
        }
        for (int t = 0; t < demo.tics(); t++) {
            cmds[t] = new Demo.Cmd[players];
            for (int p = 0; p < players; p++) {
                Demo.Cmd c = demo.cmds()[t][p];
                int buttons = c.buttons();
                if (t == nextUse[p]) {
                    useLeft[p] = 1 + rnd.nextInt(3);
                    nextUse[p] = t + 20 + rnd.nextInt(40);
                }
                if (useLeft[p] > 0) {
                    buttons |= Demo.BT_USE;
                    useLeft[p]--;
                }
                if (t == nextChange[p]) {
                    buttons |= Demo.BT_CHANGE | (rnd.nextInt(8) << Demo.BT_WEAPONSHIFT);
                    nextChange[p] = t + 50 + rnd.nextInt(100);
                }
                cmds[t][p] = new Demo.Cmd(c.forward(), c.side(), c.turn(), buttons);
            }
        }
        return new Demo(demo.header(), cmds);
    }

    /** A PWAD with the map's lumps, sector specials and line specials set to 0. */
    static byte[] stripSpecials(LumpDirectory lumps, String map) {
        int marker = lumps.get(map);
        List<String> names = new ArrayList<>();
        List<byte[]> data = new ArrayList<>();
        names.add(map);
        data.add(new byte[0]);
        for (int i = 0; i < MAP_LUMPS.length; i++) {
            int lump = marker + 1 + i;
            if (!lumps.name(lump).equalsIgnoreCase(MAP_LUMPS[i])) {
                throw new IllegalStateException(map + ": expected " + MAP_LUMPS[i] + ", found " + lumps.name(lump));
            }
            byte[] b = lumps.bytes(lump);
            if (MAP_LUMPS[i].equals("SECTORS")) {
                for (int off = 0; off + 26 <= b.length; off += 26) {
                    b[off + 22] = 0;
                    b[off + 23] = 0;
                }
            } else if (MAP_LUMPS[i].equals("LINEDEFS")) {
                for (int off = 0; off + 14 <= b.length; off += 14) {
                    b[off + 6] = 0;
                    b[off + 7] = 0;
                }
            }
            names.add(MAP_LUMPS[i]);
            data.add(b);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int[] offsets = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            offsets[i] = 12 + body.size();
            body.writeBytes(data.get(i));
        }
        ByteBuffer dirBuf = ByteBuffer.allocate(16 * names.size()).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < names.size(); i++) {
            dirBuf.putInt(offsets[i]).putInt(data.get(i).length);
            byte[] n = new byte[8];
            byte[] s = names.get(i).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(s, 0, n, 0, Math.min(8, s.length));
            dirBuf.put(n);
        }
        ByteBuffer head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        head.put("PWAD".getBytes(StandardCharsets.US_ASCII)).putInt(names.size()).putInt(12 + body.size());
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.writeBytes(head.array());
        all.writeBytes(body.toByteArray());
        all.writeBytes(dirBuf.array());
        return all.toByteArray();
    }

    /** The cached oracle trace of the entry, running the oracle when it is missing; empty when unavailable. */
    static Optional<Path> oracleTrace(Entry e, Path iwadDir) throws IOException {
        TraceCache cache = new TraceCache(OracleRunner.cacheDirectory().resolve("core"));
        Optional<OracleRunner> runner = OracleRunner.fromEnvironment();
        byte[] pwad = Files.readAllBytes(e.pwad());
        byte[] keyBytes = new byte[e.demo().length + pwad.length];
        System.arraycopy(e.demo(), 0, keyBytes, 0, e.demo().length);
        System.arraycopy(pwad, 0, keyBytes, e.demo().length, pwad.length);
        if (runner.isEmpty()) {
            return cache.findAny(e.name());
        }
        String key = TraceCache.key(e.iwad(), keyBytes, runner.get().fingerprint());
        Path path = cache.path(e.name(), key);
        if (Files.isRegularFile(path)) {
            return Optional.of(path);
        }
        Files.createDirectories(path.getParent());
        Path demoFile = e.pwad().resolveSibling(e.name() + ".lmp");
        OracleRunner.Request req = new OracleRunner.Request(e.name(), iwadDir.resolve(e.iwad()), demoFile, path,
                null, -1, -1, List.of("-file", e.pwad().toAbsolutePath().toString()), null);
        OracleRunner.Result r = runner.get().run(req);
        if (!r.ok()) {
            throw new IOException("oracle failed for " + e.name() + ": " + r.message());
        }
        return Optional.of(path);
    }

    /** Development entry point: {@code CoreCorpus <iwad dir> <work dir> [name regex]}. */
    public static void main(String[] args) throws Exception {
        Path iwadDir = Path.of(args[0]);
        String filter = args.length > 2 ? args[2] : ".*";
        int pass = 0;
        int total = 0;
        for (Entry e : build(iwadDir, Path.of(args[1]))) {
            if (!e.name().matches(filter)) {
                continue;
            }
            Optional<Path> ref = oracleTrace(e, iwadDir);
            if (ref.isEmpty()) {
                System.out.println(e.name() + "\tno oracle trace");
                continue;
            }
            DemoSync.Result r = DemoSync.run(e.name().toUpperCase(Locale.ROOT), iwadDir.resolve(e.iwad()),
                    List.of(e.pwad()), e.demo(), ref.get(), null, null, 0, -1);
            total++;
            if (r.divergenceTic() < 0 && r.error() == null) {
                pass++;
            }
            System.out.println(r.line() + "\t" + (r.detail() == null ? "" : r.detail()) + "\t" + r.stubHits());
        }
        System.out.println("fully synced: " + pass + "/" + total);
    }
}
