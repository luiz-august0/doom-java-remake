package doom.game.world;

/**
 * {@code side_t}. Offsets are 16.16 fixed point; textures are texture numbers (0 = none).
 */
public final class Side {
    /** Position in the SIDEDEFS lump. */
    public int index;
    public int textureOffset;
    public int rowOffset;
    public int topTexture;
    public int bottomTexture;
    public int midTexture;
    public Sector sector;
    /** Horizontal texture offset at the start of the last tic (presentation only, ADR-011). */
    public int prevTextureOffset;
}
