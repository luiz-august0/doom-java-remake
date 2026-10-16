package doom.wad.gfx;

import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A decoded picture ("patch", spec 05 §4): the column-based, masked image format of sprites, wall patches and every
 * 2D graphic ({@code patch_t} / {@code column_t} in {@code r_defs.h}).
 *
 * <p>Lump layout (little-endian):
 * <pre>
 *   int16  width, height, leftoffset, topoffset
 *   int32  columnofs[width]          byte offset of each column from the start of the lump
 *   column = post* 0xFF
 *   post   = uint8 topdelta, uint8 length, uint8 pad, uint8 pixels[length], uint8 pad
 * </pre>
 *
 * <p>Posts are flattened into parallel arrays, indexed by a <em>post number</em>: the posts of column {@code c} are
 * {@code firstPost(c) .. endPost(c) - 1}, in lump order. {@link #postTop(int)} is the absolute row of the first pixel,
 * {@link #postDataOffset(int)} the index of that pixel in {@link #data()}.
 *
 * <p>{@link #data()} is a copy of the whole lump followed by {@link #DATA_PADDING} zero bytes. Keeping the raw bytes
 * (including the pad bytes around each post and the post headers) lets a vanilla-exact column drawer read exactly what
 * the original reads when its texture coordinate runs past the end of a post ({@code dc_source[(frac >> 16) & 127]}),
 * and the trailing zeros guarantee such a read never leaves the array.
 *
 * <p><b>Tall patches.</b> In the original a post's {@code topdelta} is absolute, so a column cannot start a post below
 * row 254. The DeePsea convention for taller pictures makes a {@code topdelta} that is not greater than the previous
 * post's top <em>relative</em> to it ({@code top = previousTop + topdelta}). Vanilla does not know this rule; it is only
 * applied when decoding with {@code tallPatches = true} (outside vanilla mode, spec 05 §4).
 *
 * <p>Instances are immutable by contract: the arrays returned by {@link #data()} must not be modified.
 */
public final class Picture {

    /** Size of the fixed header (width, height, leftoffset, topoffset). */
    public static final int HEADER_BYTES = 8;
    /** {@code topdelta} value that ends a column. */
    public static final int END_OF_COLUMN = 0xFF;
    /** Zero bytes appended after the lump in {@link #data()}. */
    public static final int DATA_PADDING = 128;

    private final String name;
    private final int width;
    private final int height;
    private final int leftOffset;
    private final int topOffset;
    private final int lumpLength;
    private final int[] columnOffsets;
    /** First post number of each column; {@code columnFirstPost[width]} is the total number of posts. */
    private final int[] columnFirstPost;
    private final int[] postTop;
    private final int[] postTopDelta;
    private final int[] postLength;
    private final int[] postDataOffset;
    private final byte[] data;
    private final boolean tallPosts;

    private Picture(String name, int width, int height, int leftOffset, int topOffset, int lumpLength,
                    int[] columnOffsets, int[] columnFirstPost, int[] postTop, int[] postTopDelta, int[] postLength,
                    int[] postDataOffset, byte[] data, boolean tallPosts) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.leftOffset = leftOffset;
        this.topOffset = topOffset;
        this.lumpLength = lumpLength;
        this.columnOffsets = columnOffsets;
        this.columnFirstPost = columnFirstPost;
        this.postTop = postTop;
        this.postTopDelta = postTopDelta;
        this.postLength = postLength;
        this.postDataOffset = postDataOffset;
        this.data = data;
        this.tallPosts = tallPosts;
    }

    /** Decodes a picture with vanilla (absolute) post offsets. */
    public static Picture decode(ByteBuffer lump) {
        return decode(lump, null, null, false);
    }

    /**
     * Decodes a picture.
     *
     * @param lump        the lump bytes, from {@code position()} to {@code limit()}
     * @param lumpName    lump name for error messages (may be {@code null})
     * @param tallPatches apply the DeePsea tall-patch rule (never in vanilla mode)
     */
    public static Picture decode(ByteBuffer lump, String lumpName, boolean tallPatches) {
        return decode(lump, null, lumpName, tallPatches);
    }

    /**
     * Decodes a picture.
     *
     * @param lump        the lump bytes, from {@code position()} to {@code limit()}
     * @param fileName    WAD file name for error messages (may be {@code null})
     * @param lumpName    lump name for error messages (may be {@code null})
     * @param tallPatches apply the DeePsea tall-patch rule (never in vanilla mode)
     * @throws WadFormatException if the lump is not a well-formed picture
     */
    public static Picture decode(ByteBuffer lump, String fileName, String lumpName, boolean tallPatches) {
        ByteBuffer b = lump.slice().order(ByteOrder.LITTLE_ENDIAN);
        int len = b.remaining();
        if (len < HEADER_BYTES) {
            throw new WadFormatException(fileName, lumpName, 0,
                    "picture header truncated (" + len + " bytes, need " + HEADER_BYTES + ")");
        }
        int width = b.getShort(0);
        int height = b.getShort(2);
        int leftOffset = b.getShort(4);
        int topOffset = b.getShort(6);
        if (width < 0 || height < 0) {
            throw new WadFormatException(fileName, lumpName, 0, "invalid picture size " + width + "x" + height);
        }
        long tableEnd = HEADER_BYTES + 4L * width;
        if (tableEnd > len) {
            throw new WadFormatException(fileName, lumpName, HEADER_BYTES,
                    "column offset table truncated (" + width + " columns need " + tableEnd + " bytes, lump has " + len + ")");
        }
        byte[] data = new byte[len + DATA_PADDING];
        b.get(0, data, 0, len);

        int[] columnOffsets = new int[width];
        int[] columnFirstPost = new int[width + 1];
        // Pass 1: validate every column and count the posts.
        int posts = 0;
        for (int col = 0; col < width; col++) {
            int ofs = b.getInt(HEADER_BYTES + 4 * col);
            if (ofs < 0 || ofs >= len) {
                throw new WadFormatException(fileName, lumpName, HEADER_BYTES + 4L * col,
                        "column " + col + " offset " + ofs + " outside the lump (" + len + " bytes)");
            }
            columnOffsets[col] = ofs;
            columnFirstPost[col] = posts;
            int p = ofs;
            while (true) {
                if (p >= len) {
                    throw new WadFormatException(fileName, lumpName, p,
                            "column " + col + " not terminated (no 0xFF before the end of the lump)");
                }
                int topDelta = data[p] & 0xFF;
                if (topDelta == END_OF_COLUMN) {
                    break;
                }
                if (p + 1 >= len) {
                    throw new WadFormatException(fileName, lumpName, p, "column " + col + " post header truncated");
                }
                int length = data[p + 1] & 0xFF;
                if (p + 3 + length > len) {
                    throw new WadFormatException(fileName, lumpName, p,
                            "column " + col + " post of " + length + " pixels runs past the end of the lump");
                }
                posts++;
                p += length + 4;
            }
        }
        columnFirstPost[width] = posts;

        // Pass 2: fill the post arrays.
        int[] postTop = new int[posts];
        int[] postTopDelta = new int[posts];
        int[] postLength = new int[posts];
        int[] postDataOffset = new int[posts];
        boolean tallPosts = false;
        int n = 0;
        for (int col = 0; col < width; col++) {
            int p = columnOffsets[col];
            int top = -1;
            while (true) {
                int topDelta = data[p] & 0xFF;
                if (topDelta == END_OF_COLUMN) {
                    break;
                }
                int length = data[p + 1] & 0xFF;
                if (tallPatches && topDelta <= top) {
                    top += topDelta;
                    tallPosts = true;
                } else {
                    top = topDelta;
                }
                postTop[n] = top;
                postTopDelta[n] = topDelta;
                postLength[n] = length;
                postDataOffset[n] = p + 3;
                n++;
                p += length + 4;
            }
        }
        return new Picture(lumpName, width, height, leftOffset, topOffset, len, columnOffsets, columnFirstPost,
                postTop, postTopDelta, postLength, postDataOffset, data, tallPosts);
    }

    /** Decodes lump number {@code lump} of {@code lumps}; error messages name the file and the lump. */
    public static Picture read(LumpDirectory lumps, int lump, boolean tallPatches) {
        return decode(lumps.data(lump), lumps.wadOf(lump).fileName(), lumps.name(lump), tallPatches);
    }

    /** Decodes the last lump named {@code name} ({@code W_CacheLumpName}); fails if there is none. */
    public static Picture read(LumpDirectory lumps, String name, boolean tallPatches) {
        return read(lumps, lumps.get(name), tallPatches);
    }

    /**
     * Whether {@code lump} decodes as a picture with a plausible size (1..8192 × 1..8192). Meant for tools and tests
     * that scan lumps of unknown type; game code decodes the lumps it expects to be pictures.
     */
    public static boolean looksLikePicture(ByteBuffer lump) {
        ByteBuffer b = lump.slice().order(ByteOrder.LITTLE_ENDIAN);
        if (b.remaining() < HEADER_BYTES) {
            return false;
        }
        int w = b.getShort(0);
        int h = b.getShort(2);
        if (w <= 0 || h <= 0 || w > 8192 || h > 8192) {
            return false;
        }
        try {
            decode(b, null, null, true);
            return true;
        } catch (WadFormatException e) {
            return false;
        }
    }

    /** Lump name, or {@code null} when decoded without one. */
    public String name() {
        return name;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** {@code leftoffset}: subtracted from the x coordinate when drawing ({@code V_DrawPatch}). */
    public int leftOffset() {
        return leftOffset;
    }

    /** {@code topoffset}: subtracted from the y coordinate when drawing ({@code V_DrawPatch}). */
    public int topOffset() {
        return topOffset;
    }

    /** Size of the lump in bytes. */
    public int lumpLength() {
        return lumpLength;
    }

    /** {@code columnofs[col]}: offset of the column's first post from the start of the lump (and of {@link #data()}). */
    public int columnOffset(int col) {
        return columnOffsets[col];
    }

    /** Number of posts of all columns. */
    public int postCount() {
        return postTop.length;
    }

    /** Post number of the first post of column {@code col}. */
    public int firstPost(int col) {
        return columnFirstPost[col];
    }

    /** One past the post number of the last post of column {@code col}. */
    public int endPost(int col) {
        return columnFirstPost[col + 1];
    }

    /** Absolute row of the first pixel of the post (equals {@link #postTopDelta} unless a tall-patch rule applied). */
    public int postTop(int post) {
        return postTop[post];
    }

    /** The {@code topdelta} byte as stored in the lump. */
    public int postTopDelta(int post) {
        return postTopDelta[post];
    }

    /** Number of pixels of the post. */
    public int postLength(int post) {
        return postLength[post];
    }

    /** Index in {@link #data()} of the first pixel of the post (lump offset of the post + 3). */
    public int postDataOffset(int post) {
        return postDataOffset[post];
    }

    /** Pixel {@code i} (0-based) of the post, as an unsigned palette index. */
    public int postPixel(int post, int i) {
        return data[postDataOffset[post] + i] & 0xFF;
    }

    /** The pad byte stored right before the post's pixels (unused by the original drawers). */
    public int postPadBefore(int post) {
        return data[postDataOffset[post] - 1] & 0xFF;
    }

    /** The pad byte stored right after the post's pixels (read by column drawers that overrun by one pixel). */
    public int postPadAfter(int post) {
        return data[postDataOffset[post] + postLength[post]] & 0xFF;
    }

    /**
     * The lump bytes followed by {@link #DATA_PADDING} zeros. Shared, never copied: do not modify.
     */
    public byte[] data() {
        return data;
    }

    /** True if decoding applied the DeePsea relative-offset rule to at least one post. */
    public boolean hasTallPosts() {
        return tallPosts;
    }

    @Override
    public String toString() {
        return "Picture[" + (name == null ? "?" : name) + " " + width + "x" + height + " offset " + leftOffset + ","
                + topOffset + ", " + postTop.length + " posts]";
    }
}
