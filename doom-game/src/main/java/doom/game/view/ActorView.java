package doom.game.view;

/**
 * Map objects ({@code mobj_t}) by actor id. Ids are small non-negative integers below {@link #capacity()}; an id is
 * reused only after the actor was removed and {@code SimEvents.stopSound(id)} was emitted.
 * Positions are fixed point; angles BAM.
 */
public interface ActorView {

    /** All valid ids are in {@code [0, capacity())}. */
    int capacity();

    /** Whether {@code id} is a live actor (not removed). */
    boolean exists(int id);

    /** Thing type index ({@code mobjtype_t}). */
    int type(int id);

    int x(int id);

    int y(int id);

    int z(int id);

    int angle(int id);

    /** Position at the start of the last tic (equal to the current one when interpolation is snapped). */
    int prevX(int id);

    int prevY(int id);

    int prevZ(int id);

    int prevAngle(int id);

    /** Sprite number ({@code spritenum_t}). */
    int sprite(int id);

    /** Frame including {@link FrameFlags#FULLBRIGHT}. */
    int frame(int id);

    /** {@link MobjFlags} bits. */
    int flags(int id);

    int radius(int id);

    int height(int id);

    int health(int id);

    int floorZ(int id);

    int ceilingZ(int id);

    /** Player number 0..3 if this is a player's body, else -1. */
    int player(int id);

    /** Sector containing the actor ({@code subsector->sector}). */
    int sector(int id);

    /** Next actor in the same sector's thing list ({@code snext}), or -1. */
    int nextInSector(int id);

    /** First actor in thinker-list order, or -1. */
    int first();

    /** Next actor in thinker-list order, or -1. */
    int next(int id);
}
