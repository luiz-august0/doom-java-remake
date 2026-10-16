package doom.render.texture;

/**
 * One composed wall texture ({@code R_GenerateLookup} + {@code R_GenerateComposite}).
 *
 * <p>Opaque drawing reads column {@code c} as {@code wallData[c][wallOfs[c] + row]} (with {@code c} already masked by
 * {@link #widthMask}). In vanilla mode a column covered by exactly one patch points straight into that patch's lump
 * (as {@code R_GetColumn} does), so the {@code & 127} wrap of the column drawer reads the post header bytes that follow
 * the pixels; columns covered by several patches (or by none) live in one contiguous composite block per texture, so
 * a short column reads on into the next composite column. Outside vanilla mode every column is a composite column of
 * exactly {@link #height} bytes and the drawer wraps at the real height.
 *
 * <p>Masked drawing uses {@link #posts()}: the posts of the column, taken from the patch for single-patch columns and
 * derived from the pixels the patches actually covered for composite columns (so multi-patch masked textures work;
 * the "Medusa effect" is not reproduced, see divergences).
 */
public final class WallTexture {

    public final int width;
    public final int height;
    /** {@code texturewidthmask}. */
    public final int widthMask;
    /** {@code textureheight}, fixed point. */
    public final int heightFixed;
    public final byte[][] wallData;
    public final int[] wallOfs;
    private final PostImage posts;

    WallTexture(int width, int height, int widthMask, byte[][] wallData, int[] wallOfs, PostImage posts) {
        this.width = width;
        this.height = height;
        this.widthMask = widthMask;
        this.heightFixed = height << 16;
        this.wallData = wallData;
        this.wallOfs = wallOfs;
        this.posts = posts;
    }

    /** Post structure for masked drawing. */
    public PostImage posts() {
        return posts;
    }
}
