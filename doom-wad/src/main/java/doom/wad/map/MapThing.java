package doom.wad.map;

/**
 * One THINGS entry ({@code mapthing_t}, 10 bytes). All fields are signed 16-bit values, as in the original.
 *
 * @param x       map units
 * @param y       map units
 * @param angle   degrees
 * @param type    editor number (doomednum)
 * @param options skill bits, deaf (ambush) and multiplayer-only flags
 */
public record MapThing(int x, int y, int angle, int type, int options) {

    /** Bytes per entry. */
    public static final int SIZE = 10;

    /** {@code MTF_EASY}: present in skills 1 and 2. */
    public static final int MTF_EASY = 1;
    /** {@code MTF_NORMAL}: present in skill 3. */
    public static final int MTF_NORMAL = 2;
    /** {@code MTF_HARD}: present in skills 4 and 5. */
    public static final int MTF_HARD = 4;
    /** {@code MTF_AMBUSH}: deaf monster. */
    public static final int MTF_AMBUSH = 8;
    /** Not in single player (bit 4 of {@code options}). */
    public static final int MTF_NOTSINGLE = 16;
}
