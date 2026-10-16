package doom.game.world;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records when the simulation first needed a rule that is not implemented yet (the stubs of the Wave 3 areas).
 * Demo sync is only expected up to {@link #firstTic()}. One instance per session (no static state); never consulted by
 * the simulation itself.
 */
public final class Unimplemented {

    private final World world;
    private final Map<String, Integer> firstHits = new LinkedHashMap<>();
    private int firstTic = -1;
    private String firstKey;

    public Unimplemented(World world) {
        this.world = world;
    }

    /** A stub was reached: remembers the first tic for {@code key} (e.g. {@code "ai.A_Look"}). */
    public void hit(String key) {
        if (!firstHits.containsKey(key)) {
            firstHits.put(key, world.gametic);
        }
        if (firstTic < 0) {
            firstTic = world.gametic;
            firstKey = key;
        }
    }

    /** Tic of the first stub hit, or -1 when none. */
    public int firstTic() {
        return firstTic;
    }

    /** Key of the first stub hit, or null. */
    public String firstKey() {
        return firstKey;
    }

    /** First tic of every key hit so far, in order of first hit (a copy). */
    public Map<String, Integer> hits() {
        return new LinkedHashMap<>(firstHits);
    }
}
