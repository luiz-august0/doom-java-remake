package doom.game.player;

import doom.game.data.State;

/** {@code pspdef_t}: a player sprite (weapon or muzzle flash), a small state machine of its own. */
public final class PSprite {

    /** Null when the sprite is off. */
    public State state;
    /** Index of {@link #state}; -1 when {@link #state} is null (the trace value). */
    public int stateNum = -1;
    public int tics;
    /** Screen position, 16.16 fixed point. */
    public int sx;
    public int sy;

    /** Interpolation (presentation only). */
    public int prevSx;
    public int prevSy;

    void clear() {
        state = null;
        stateNum = -1;
        tics = 0;
        sx = 0;
        sy = 0;
        prevSx = 0;
        prevSy = 0;
    }
}
