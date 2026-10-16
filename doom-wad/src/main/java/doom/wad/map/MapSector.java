package doom.wad.map;

/**
 * One SECTORS entry ({@code mapsector_t}, 26 bytes). Heights, light, special and tag are signed 16-bit values; flat
 * names are {@code char[8]} kept in their original case.
 */
public record MapSector(int floorHeight, int ceilingHeight, String floorPic, String ceilingPic, int lightLevel,
                        int special, int tag) {

    /** Bytes per entry. */
    public static final int SIZE = 26;
}
