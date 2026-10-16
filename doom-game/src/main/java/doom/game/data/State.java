package doom.game.data;

/**
 * One entry of the state table ({@code state_t}). Mutable: DEHACKED and {@code -fast}/Nightmare change tics and
 * links. The table is owned by one {@link GameDefs} (one per session), so nothing here is shared.
 */
public final class State {

    /** Sprite number ({@link SpriteId}). */
    public int sprite;
    /** Sprite frame; {@link doom.game.view.FrameFlags#FULLBRIGHT} may be set. */
    public int frame;
    /** Duration in tics; -1 means forever. */
    public int tics;
    /** Action called when the state is entered. */
    public StateAction action;
    /** Next state ({@link StateId}). */
    public int nextState;
    /** {@code misc1}: weapon sprite x offset or other action-specific value. */
    public int misc1;
    /** {@code misc2}: weapon sprite y offset or other action-specific value. */
    public int misc2;

    public State(int sprite, int frame, int tics, StateAction action, int nextState, int misc1, int misc2) {
        this.sprite = sprite;
        this.frame = frame;
        this.tics = tics;
        this.action = action;
        this.nextState = nextState;
        this.misc1 = misc1;
        this.misc2 = misc2;
    }

    /** A deep copy. */
    public State copy() {
        return new State(sprite, frame, tics, action, nextState, misc1, misc2);
    }

    @Override
    public String toString() {
        return "State[sprite=" + sprite + ", frame=" + frame + ", tics=" + tics + ", action=" + action + ", next="
                + nextState + ", misc1=" + misc1 + ", misc2=" + misc2 + "]";
    }
}
