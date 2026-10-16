package doom.it.sync;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the PWADs of {@code testdata/flow} from Freedoom's E1M1 at test time (a Java port of the PWAD part of
 * {@code make-flow-testdata.py}; the PWADs are derived game data and are never committed). The line of E1M1 that
 * the player start looks straight at (two-sided, more than 40 units away) becomes an exit (special 52, {@code exit.wad})
 * or a secret exit (special 124, {@code secret.wad}).
 */
final class FlowWads {

    private static final String[] ORDER = {"THINGS", "LINEDEFS", "SIDEDEFS", "VERTEXES", "SEGS", "SSECTORS", "NODES",
        "SECTORS", "REJECT", "BLOCKMAP"};
    private static final int LINEDEF_SIZE = 14;

    private FlowWads() {
    }

    /** Writes {@code exit.wad} and {@code secret.wad} into {@code outDir} (created if needed) and returns it. */
    static Path generate(Path freedoom1, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        ByteBuffer iwad = ByteBuffer.wrap(Files.readAllBytes(freedoom1)).order(ByteOrder.LITTLE_ENDIAN);
        int count = iwad.getInt(4);
        int dirOfs = iwad.getInt(8);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(name(iwad, dirOfs + 16 * i + 8));
        }
        int idx = names.indexOf("E1M1");
        if (idx < 0) {
            throw new IOException("E1M1 not found in " + freedoom1);
        }
        Map<String, byte[]> lumps = new LinkedHashMap<>();
        for (int k = 1; k <= 10; k++) {
            int e = dirOfs + 16 * (idx + k);
            byte[] b = new byte[iwad.getInt(e + 4)];
            iwad.get(iwad.getInt(e), b);
            lumps.put(names.get(idx + k), b);
        }
        ByteBuffer verts = ByteBuffer.wrap(lumps.get("VERTEXES")).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer lines = ByteBuffer.wrap(lumps.get("LINEDEFS")).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer things = ByteBuffer.wrap(lumps.get("THINGS")).order(ByteOrder.LITTLE_ENDIAN);
        int sx = 0;
        int sy = 0;
        int ang = 0;
        for (int i = 0; i < things.capacity() / 10; i++) {
            if (things.getShort(10 * i + 6) == 1) {
                sx = things.getShort(10 * i);
                sy = things.getShort(10 * i + 2);
                ang = things.getShort(10 * i + 4);
                break;
            }
        }
        double dx = Math.cos(Math.toRadians(ang));
        double dy = Math.sin(Math.toRadians(ang));
        int best = -1;
        double bestT = 0;
        for (int i = 0; i < lines.capacity() / LINEDEF_SIZE; i++) {
            int o = LINEDEF_SIZE * i;
            if ((lines.getShort(o + 4) & 4) == 0) {
                continue;
            }
            int v1 = lines.getShort(o) & 0xFFFF;
            int v2 = lines.getShort(o + 2) & 0xFFFF;
            int x1 = verts.getShort(4 * v1);
            int y1 = verts.getShort(4 * v1 + 2);
            int ex = verts.getShort(4 * v2) - x1;
            int ey = verts.getShort(4 * v2 + 2) - y1;
            double den = dx * ey - dy * ex;
            if (Math.abs(den) < 1e-9) {
                continue;
            }
            double t = ((x1 - sx) * ey - (y1 - sy) * ex) / den;
            double u = ((x1 - sx) * dy - (y1 - sy) * dx) / den;
            if (t > 40 && u >= 0 && u <= 1 && (best < 0 || t < bestT)) {
                best = i;
                bestT = t;
            }
        }
        if (best < 0) {
            throw new IOException("no exit candidate line in E1M1");
        }
        write(outDir.resolve("exit.wad"), lumps, best, 52);
        write(outDir.resolve("secret.wad"), lumps, best, 124);
        return outDir;
    }

    private static void write(Path file, Map<String, byte[]> lumps, int line, int special) throws IOException {
        byte[] linedefs = lumps.get("LINEDEFS").clone();
        ByteBuffer.wrap(linedefs).order(ByteOrder.LITTLE_ENDIAN).putShort(LINEDEF_SIZE * line + 6, (short) special);
        List<String> nm = new ArrayList<>();
        List<byte[]> data = new ArrayList<>();
        nm.add("E1M1");
        data.add(new byte[0]);
        for (String k : ORDER) {
            nm.add(k);
            data.add(k.equals("LINEDEFS") ? linedefs : lumps.get(k));
        }
        int total = 12;
        for (byte[] b : data) {
            total += b.length;
        }
        ByteBuffer out = ByteBuffer.allocate(total + 16 * data.size()).order(ByteOrder.LITTLE_ENDIAN);
        out.put("PWAD".getBytes(StandardCharsets.US_ASCII)).putInt(data.size()).putInt(total);
        for (byte[] b : data) {
            out.put(b);
        }
        int pos = 12;
        for (int i = 0; i < data.size(); i++) {
            out.putInt(pos).putInt(data.get(i).length);
            byte[] n = new byte[8];
            byte[] s = nm.get(i).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(s, 0, n, 0, s.length);
            out.put(n);
            pos += data.get(i).length;
        }
        Files.write(file, out.array());
    }

    private static String name(ByteBuffer b, int pos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            byte c = b.get(pos + i);
            if (c == 0) {
                break;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }
}
