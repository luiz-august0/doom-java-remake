package doom.game.actor;

import java.util.Arrays;

/**
 * Hands out actor ids (contracts §11): small non-negative integers, reused only after the removed actor's
 * {@code stopSound} was emitted. Ids never influence the simulation; they exist for the presentation layer.
 */
public final class ActorRegistry {

    private Actor[] byId = new Actor[256];
    private int[] free = new int[256];
    private int freeCount;
    private int nextId;

    /** Gives {@code actor} a fresh id. */
    public void register(Actor actor) {
        int id;
        if (freeCount > 0) {
            id = free[--freeCount];
        } else {
            id = nextId++;
            if (id >= byId.length) {
                byId = Arrays.copyOf(byId, byId.length * 2);
            }
        }
        byId[id] = actor;
        actor.id = id;
    }

    /** Releases the id of a removed actor (after {@code stopSound(id)}). */
    public void release(Actor actor) {
        int id = actor.id;
        if (id < 0) {
            return;
        }
        byId[id] = null;
        actor.id = -1;
        if (freeCount == free.length) {
            free = Arrays.copyOf(free, free.length * 2);
        }
        free[freeCount++] = id;
    }

    /** All ids are below this value. */
    public int capacity() {
        return nextId;
    }

    /** The live actor with this id, or null. */
    public Actor get(int id) {
        return id >= 0 && id < nextId ? byId[id] : null;
    }

    /** Forgets every id (new level). */
    public void clear() {
        Arrays.fill(byId, null);
        freeCount = 0;
        nextId = 0;
    }
}
