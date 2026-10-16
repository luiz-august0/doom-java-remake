package doom.wad.map;

/**
 * Lumps that follow a map marker ({@code E1M1}, {@code MAP01}), in their fixed order (spec 05 §1).
 *
 * <p><b>Skeleton (Wave 0):</b> owned and implemented by W1-A.
 */
public enum MapLump {
    THINGS,
    LINEDEFS,
    SIDEDEFS,
    VERTEXES,
    SEGS,
    SSECTORS,
    NODES,
    SECTORS,
    REJECT,
    BLOCKMAP;

    /** Lump index relative to the map marker ({@code ML_THINGS = 1} ... {@code ML_BLOCKMAP = 10}). */
    public int offsetFromMarker() {
        return ordinal() + 1;
    }
}
