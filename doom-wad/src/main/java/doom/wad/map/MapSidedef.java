package doom.wad.map;

/**
 * One SIDEDEFS entry ({@code mapsidedef_t}, 30 bytes). Offsets and sector are signed 16-bit values; texture names are
 * {@code char[8]} kept in their original case (lookups are case-insensitive), {@code "-"} meaning "no texture".
 */
public record MapSidedef(int textureOffset, int rowOffset, String topTexture, String bottomTexture,
                         String midTexture, int sector) {

    /** Bytes per entry. */
    public static final int SIZE = 30;
}
