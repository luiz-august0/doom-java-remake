package doom.wad.map;

/**
 * One LINEDEFS entry ({@code maplinedef_t}, 14 bytes). Every field is a <b>signed</b> 16-bit value, exactly as in
 * DOOM v1.9 and Chocolate Doom: a side number of {@code 0xFFFF} reads as {@code -1} ("no side"), and indices of
 * {@code 0x8000} or more are negative (vanilla cannot address them; such maps do not load).
 *
 * @param v1        start vertex
 * @param v2        end vertex
 * @param flags     {@code ML_*} flags
 * @param special   line special
 * @param tag       sector tag
 * @param frontSide {@code sidenum[0]}, or -1
 * @param backSide  {@code sidenum[1]}, or -1
 */
public record MapLinedef(int v1, int v2, int flags, int special, int tag, int frontSide, int backSide) {

    /** Bytes per entry. */
    public static final int SIZE = 14;

    /** {@code sidenum[side]}: 0 = front, 1 = back. */
    public int side(int side) {
        return side == 0 ? frontSide : backSide;
    }
}
