package doom.game.actor;

/**
 * A thinker that is not a map object: the sector effects of the map-special area (doors, floors, ceilings,
 * platforms, lights). Each subclass describes itself for the trace (contracts §6.5, key {@code x}): a kind code and
 * its fields, in the documented order. A ceiling or platform in stasis reports code 102 / 104 and returns true from
 * {@link #hasNullFunction()}.
 *
 * <p>Like the oracle (which zero-fills {@code PU_LEVSPEC} memory), subclasses must start with every field zero and
 * set them in the original order.
 */
public abstract non-sealed class SpecialThinker extends Thinker {

    protected SpecialThinker() {
    }

    /** Kind code of contracts §6.5 (1 door, 2 ceiling, 3 floor, 4 platform, 5..8 lights, 102/104 stasis, 0 unknown). */
    public abstract int traceCode();

    /** Feeds the fields that follow the code (sector references as sector indices, booleans as 0/1). */
    public abstract void traceFields(TraceSink sink);
}
