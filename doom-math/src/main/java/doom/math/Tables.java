package doom.math;

import doom.math.annotation.Bam;
import doom.math.annotation.VanillaRef;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The original lookup tables, copied from {@code tables.c} by the {@code gen-tables} generator into a binary
 * resource (a {@code finesine[10240]} array literal would exceed the 64 KB class-initializer limit, ADR-022).
 * The values are constants of the original and must never be recomputed with floating point (ADR-014).
 *
 * <p>The resource {@code doom/math/tables.bin} is written by {@code doom-codegen gen-tables} from
 * {@code tables.c} ({@code finetangent}, {@code finesine}, {@code tantoangle}), {@code m_random.c}
 * ({@code rndtable}) and {@code v_video.c} ({@code gammatable}). Its layout, all numbers big-endian:
 * magic {@code "DMTB"}, format version 1, then the sections {@code FTAN}, {@code FSIN}, {@code TTOA}, {@code RNDT},
 * {@code GAMA} (each: 4-byte tag, element size, element count, elements) and the trailer {@code "END."}.
 * See {@code doom.codegen.tables.GenTables} for the full description. Its SHA-256 is pinned by {@code TablesTest}.
 *
 * <p>All arrays are private and never written after loading, so the class holds no mutable state.
 */
public final class Tables {

    /** Number of entries in {@link #fineSine} (5/4 of a turn, so cosine can be read with an offset). */
    public static final int FINE_SINE_SIZE = 10240;
    /** Number of entries in {@link #fineTangent}. */
    public static final int FINE_TANGENT_SIZE = 4096;
    /** {@code SLOPERANGE}: number of steps of {@link #tanToAngle} minus one. */
    public static final int SLOPERANGE = 2048;
    /** {@code SLOPEBITS}. */
    public static final int SLOPEBITS = 11;
    /** {@code DBITS = FRACBITS - SLOPEBITS}. */
    public static final int DBITS = Fixed.FRACBITS - SLOPEBITS;
    /** Number of entries in {@link #rnd} ({@code rndtable}). */
    public static final int RND_TABLE_SIZE = 256;
    /** Number of gamma correction levels ({@code usegamma} 0..4). */
    public static final int GAMMA_LEVELS = 5;

    /** Resource name, relative to this class. */
    static final String RESOURCE = "tables.bin";

    private static final int[] FINE_TANGENT;
    private static final int[] FINE_SINE;
    private static final int[] TAN_TO_ANGLE;
    private static final int[] RND_TABLE;
    private static final int[] GAMMA_TABLE;

    static {
        try (InputStream raw = Tables.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("Missing resource doom/math/" + RESOURCE);
            }
            DataInputStream in = new DataInputStream(raw);
            expectTag(in, "DMTB");
            int version = in.readInt();
            if (version != 1) {
                throw new IllegalStateException(RESOURCE + ": unsupported format version " + version);
            }
            FINE_TANGENT = readSection(in, "FTAN", 4, FINE_TANGENT_SIZE);
            FINE_SINE = readSection(in, "FSIN", 4, FINE_SINE_SIZE);
            TAN_TO_ANGLE = readSection(in, "TTOA", 4, SLOPERANGE + 1);
            RND_TABLE = readSection(in, "RNDT", 1, RND_TABLE_SIZE);
            GAMMA_TABLE = readSection(in, "GAMA", 1, GAMMA_LEVELS * 256);
            expectTag(in, "END.");
            if (in.read() != -1) {
                throw new IllegalStateException(RESOURCE + ": trailing data");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read doom/math/" + RESOURCE, e);
        }
    }

    private Tables() {
    }

    /** {@code finesine[index]}, index in {@code [0, 10240)}. */
    public static int fineSine(int index) {
        return FINE_SINE[index];
    }

    /** {@code finecosine[index]} = {@code finesine[index + FINEANGLES / 4]}, index in {@code [0, 8192)}. */
    public static int fineCosine(int index) {
        return FINE_SINE[index + Angle.FINEANGLES / 4];
    }

    /** {@code finetangent[index]}, index in {@code [0, 4096)}. */
    public static int fineTangent(int index) {
        return FINE_TANGENT[index];
    }

    /** {@code tantoangle[index]}, index in {@code [0, 2048]}; the result is a BAM angle. */
    public static @Bam int tanToAngle(int index) {
        return TAN_TO_ANGLE[index];
    }

    /**
     * {@code rndtable[index]}, index in {@code [0, 256)}: the table shared by both random number generators
     * (see {@link Rng}). The result is in {@code [0, 255]}.
     */
    public static int rnd(int index) {
        return RND_TABLE[index];
    }

    /**
     * {@code gammatable[level][value]} ({@code v_video.c}): gamma correction applied to the palette components
     * by {@code I_SetPalette}.
     *
     * @param level gamma level ({@code usegamma}) in {@code [0, 5)}
     * @param value palette component in {@code [0, 256)}
     * @return the corrected component in {@code [0, 255]}
     */
    public static int gamma(int level, int value) {
        if (level < 0 || level >= GAMMA_LEVELS || value < 0 || value > 255) {
            throw new IndexOutOfBoundsException("gamma(" + level + ", " + value + ")");
        }
        return GAMMA_TABLE[(level << 8) + value];
    }

    /**
     * {@code SlopeDiv(num, den)} with unsigned semantics, as in {@code tables.c}: returns {@link #SLOPERANGE} when
     * {@code den < 512}, otherwise {@code min((num << 3) / (den >> 8), SLOPERANGE)}, all in unsigned 32-bit arithmetic.
     */
    @VanillaRef("tables.c:SlopeDiv")
    public static int slopeDiv(int num, int den) {
        if (Integer.compareUnsigned(den, 512) < 0) {
            return SLOPERANGE;
        }
        int ans = Integer.divideUnsigned(num << 3, den >>> 8);
        return Integer.compareUnsigned(ans, SLOPERANGE) <= 0 ? ans : SLOPERANGE;
    }

    private static void expectTag(DataInputStream in, String tag) throws IOException {
        byte[] b = new byte[4];
        in.readFully(b);
        String found = new String(b, StandardCharsets.ISO_8859_1);
        if (!found.equals(tag)) {
            throw new IllegalStateException(RESOURCE + ": expected section " + tag + ", found " + found);
        }
    }

    private static int[] readSection(DataInputStream in, String tag, int elementSize, int count) throws IOException {
        expectTag(in, tag);
        int size = in.readInt();
        int n = in.readInt();
        if (size != elementSize || n != count) {
            throw new IllegalStateException(RESOURCE + ": section " + tag + " has " + n + " elements of " + size
                    + " bytes, expected " + count + " of " + elementSize);
        }
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = elementSize == 4 ? in.readInt() : in.readUnsignedByte();
        }
        return values;
    }
}
