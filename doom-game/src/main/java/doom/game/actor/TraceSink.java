package doom.game.actor;

/** Receives the int32 values of an object described for the trace (contracts §6.5). */
public interface TraceSink {

    void put(int value);

    /** Thinker index of contracts §6.3: -1 for null, -2 for a thinker that is not live. */
    int thinkerIndex(Thinker thinker);
}
