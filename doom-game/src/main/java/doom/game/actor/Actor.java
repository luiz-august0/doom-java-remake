package doom.game.actor;

import doom.game.data.State;
import doom.game.data.ThingType;
import doom.game.player.Player;
import doom.game.world.SpawnPoint;
import doom.game.world.Subsector;

/**
 * A map object ({@code mobj_t}). Fields keep the original names (camel case) and meaning; positions are 16.16 fixed
 * point, angles BAM. The interpolation fields {@code prev*} are written at the start of each tic and are never read by
 * the simulation (ADR-011).
 */
public final class Actor extends Thinker {

    /** Runs {@code P_MobjThinker} for this actor. */
    private final Mobjs mobjs;

    /** Actor id handed to the presentation (contracts §11); -1 once removed. */
    public int id = -1;

    public int x;
    public int y;
    public int z;
    /** Sector thing list links ({@code snext}, {@code sprev}). */
    public Actor snext;
    public Actor sprev;
    public int angle;
    public int sprite;
    public int frame;
    /** Blockmap links ({@code bnext}, {@code bprev}). */
    public Actor bnext;
    public Actor bprev;
    public Subsector subsector;
    public int floorZ;
    public int ceilingZ;
    public int radius;
    public int height;
    public int momX;
    public int momY;
    public int momZ;
    /** {@code validcount}: unused by vanilla code paths but kept for parity. */
    public int validCount;
    /** {@code mobjtype_t}. */
    public int type;
    /** {@code mobjinfo[type]} of the session tables. */
    public ThingType info;
    public int tics;
    /** Current state ({@code states[stateNum]}); null after {@code P_SetMobjState(S_NULL)}. */
    public State state;
    /** Index of {@link #state} in the state table; 0 ({@code S_NULL}) when {@link #state} is null. */
    public int stateNum;
    public int flags;
    public int health;
    /** 0-7, {@code DI_NODIR} = 8. */
    public int moveDir;
    public int moveCount;
    public Actor target;
    public int reactionTime;
    public int threshold;
    public Player player;
    public int lastLook;
    /** {@code spawnpoint}: copy of the map thing this actor was spawned from. */
    public final SpawnPoint spawnPoint = new SpawnPoint();
    public Actor tracer;

    // ------------------------------------------------------------------ presentation only

    public int prevX;
    public int prevY;
    public int prevZ;
    public int prevAngle;

    public Actor(Mobjs mobjs) {
        this.mobjs = mobjs;
    }

    @Override
    protected void think() {
        mobjs.mobjThinker(this);
    }

    /** Copies the current position into the interpolation fields (spawn, teleport). */
    public void snapInterpolation() {
        prevX = x;
        prevY = y;
        prevZ = z;
        prevAngle = angle;
    }

    @Override
    public String toString() {
        return "Actor[id=" + id + ", type=" + type + ", x=" + x + ", y=" + y + ", z=" + z + ", state=" + stateNum + "]";
    }
}
