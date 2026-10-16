package doom.game.api;

/**
 * Encoding of a sound's origin as an {@code int} (contracts §11): {@link #NONE}, an actor id, or a sector's sound
 * origin ({@code sec->soundorg}) as {@code SECTOR_BASE + sector}.
 */
public final class SoundOrigins {

    /** No origin: full volume, centred ({@code S_StartSound(NULL, ...)}). */
    public static final int NONE = -1;
    /** First code of sector origins. Actor ids are always below it. */
    public static final int SECTOR_BASE = 1 << 24;

    private SoundOrigins() {
    }

    public static int sector(int sector) {
        return SECTOR_BASE + sector;
    }

    public static boolean isSector(int origin) {
        return origin >= SECTOR_BASE;
    }

    public static boolean isActor(int origin) {
        return origin >= 0 && origin < SECTOR_BASE;
    }

    public static int sectorOf(int origin) {
        return origin - SECTOR_BASE;
    }
}
