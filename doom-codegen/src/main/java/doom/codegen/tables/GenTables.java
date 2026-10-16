package doom.codegen.tables;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * {@code doom-codegen gen-tables <linuxdoom dir> <output dir> [--verify <chocolate-doom dir>]}.
 *
 * <p>Copies the original lookup tables, as written in the C sources, into the binary resource
 * {@code tables.bin} that {@code doom.math.Tables} loads (ADR-014, ADR-022). The values are never recomputed:
 * they are parsed from
 * <ul>
 *   <li>{@code tables.c}: {@code finetangent[4096]}, {@code finesine[10240]}, {@code tantoangle[2049]};</li>
 *   <li>{@code m_random.c}: {@code rndtable[256]};</li>
 *   <li>{@code v_video.c}: {@code gammatable[5][256]}.</li>
 * </ul>
 * With {@code --verify}, the same tables are also parsed from Chocolate Doom ({@code src/tables.c},
 * {@code src/doom/m_random.c}; Chocolate keeps {@code gammatable} in {@code tables.c}) and must be identical.
 *
 * <h2>Resource layout ({@value #FORMAT_VERSION})</h2>
 * All multi-byte numbers are <b>big-endian</b> (the {@link java.io.DataInput} order).
 * <pre>
 * offset  size  content
 * 0       4     magic "DMTB" (ASCII)
 * 4       4     format version (int32) = 1
 * 8       ...   five sections, in this order:
 *                 tag (4 ASCII bytes), element size in bytes (int32: 4 or 1), element count (int32), elements
 *               "FTAN" 4  4096   finetangent, int32 two's complement
 *               "FSIN" 4 10240   finesine, int32 two's complement
 *               "TTOA" 4  2049   tantoangle, angle_t stored as its 32-bit pattern
 *               "RNDT" 1   256   rndtable, unsigned bytes
 *               "GAMA" 1  1280   gammatable[5][256], unsigned bytes, row-major (level * 256 + value)
 * end     4     "END." (ASCII)
 * </pre>
 * The generator prints the SHA-256 of the file; {@code doom-math}'s {@code TablesTest} pins it.
 */
public final class GenTables {

    /** Version of the resource layout. */
    public static final int FORMAT_VERSION = 1;
    /** Output file name. */
    public static final String OUTPUT_NAME = "tables.bin";

    private GenTables() {
    }

    /** The parsed tables. Arrays are owned by the record and never modified after parsing. */
    record TableSet(long[] fineTangent, long[] fineSine, long[] tanToAngle, long[] rndTable, long[] gammaTable) {

        List<long[]> all() {
            return List.of(fineTangent, fineSine, tanToAngle, rndTable, gammaTable);
        }
    }

    public static int run(String[] args) {
        return run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 2 && !(args.length == 4 && args[2].equals("--verify"))) {
            err.println("usage: doom-codegen gen-tables <linuxdoom dir> <output dir> [--verify <chocolate-doom dir>]");
            return 2;
        }
        try {
            TableSet tables = parseLinuxDoom(Path.of(args[0]));
            if (args.length == 4) {
                TableSet chocolate = parseChocolate(Path.of(args[3]));
                String diff = firstDifference(tables, chocolate);
                if (diff != null) {
                    err.println("gen-tables: linuxdoom and Chocolate Doom tables differ: " + diff);
                    return 1;
                }
                out.println("Verified: linuxdoom and Chocolate Doom tables are identical.");
            }
            byte[] bytes = encode(tables);
            Path dir = Path.of(args[1]);
            Files.createDirectories(dir);
            Path file = dir.resolve(OUTPUT_NAME);
            Files.write(file, bytes);
            out.println("Wrote " + file + " (" + bytes.length + " bytes)");
            out.println("SHA-256 " + sha256(bytes));
            return 0;
        } catch (IOException | IllegalArgumentException e) {
            err.println("gen-tables: " + e.getMessage());
            return 1;
        }
    }

    /** Parses the tables from the linuxdoom-1.10 sources. */
    static TableSet parseLinuxDoom(Path dir) throws IOException {
        String tables = read(dir.resolve("tables.c"));
        String random = read(dir.resolve("m_random.c"));
        String video = read(dir.resolve("v_video.c"));
        return parse(tables, random, video);
    }

    /** Parses the tables from the Chocolate Doom sources (root of the clone). */
    static TableSet parseChocolate(Path dir) throws IOException {
        String tables = read(dir.resolve("src/tables.c"));
        String random = read(dir.resolve("src/doom/m_random.c"));
        return parse(tables, random, tables);
    }

    static TableSet parse(String tablesC, String randomC, String gammaC) {
        long[] fineTangent = CArrayParser.parse(tablesC, "finetangent", new int[] {4096},
                Integer.MIN_VALUE, Integer.MAX_VALUE);
        long[] fineSine = CArrayParser.parse(tablesC, "finesine", new int[] {10240},
                Integer.MIN_VALUE, Integer.MAX_VALUE);
        long[] tanToAngle = CArrayParser.parse(tablesC, "tantoangle", new int[] {2049}, 0, 0xFFFF_FFFFL);
        long[] rndTable = CArrayParser.parse(randomC, "rndtable", new int[] {256}, 0, 255);
        long[] gammaTable = CArrayParser.parse(gammaC, "gammatable", new int[] {5, 256}, 0, 255);
        return new TableSet(fineTangent, fineSine, tanToAngle, rndTable, gammaTable);
    }

    /** Describes the first difference between two table sets, or returns null when they are identical. */
    static String firstDifference(TableSet a, TableSet b) {
        String[] names = {"finetangent", "finesine", "tantoangle", "rndtable", "gammatable"};
        List<long[]> la = a.all();
        List<long[]> lb = b.all();
        for (int t = 0; t < names.length; t++) {
            long[] x = la.get(t);
            long[] y = lb.get(t);
            if (x.length != y.length) {
                return names[t] + ": " + x.length + " vs " + y.length + " values";
            }
            for (int i = 0; i < x.length; i++) {
                if (x[i] != y[i]) {
                    return names[t] + "[" + i + "]: " + x[i] + " vs " + y[i];
                }
            }
        }
        return null;
    }

    /** Encodes the tables in the resource layout described in the class documentation. */
    static byte[] encode(TableSet t) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024);
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeBytes("DMTB");
            out.writeInt(FORMAT_VERSION);
            writeInts(out, "FTAN", t.fineTangent());
            writeInts(out, "FSIN", t.fineSine());
            writeInts(out, "TTOA", t.tanToAngle());
            writeBytes(out, "RNDT", t.rndTable());
            writeBytes(out, "GAMA", t.gammaTable());
            out.writeBytes("END.");
            out.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeInts(DataOutputStream out, String tag, long[] values) throws IOException {
        out.writeBytes(tag);
        out.writeInt(4);
        out.writeInt(values.length);
        for (long v : values) {
            out.writeInt((int) v);
        }
    }

    private static void writeBytes(DataOutputStream out, String tag, long[] values) throws IOException {
        out.writeBytes(tag);
        out.writeInt(1);
        out.writeInt(values.length);
        for (long v : values) {
            out.writeByte((int) v);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(Path file) throws IOException {
        // The sources are ASCII; ISO-8859-1 maps every byte to one char, so nothing can fail to decode.
        return Files.readString(file, StandardCharsets.ISO_8859_1);
    }
}
