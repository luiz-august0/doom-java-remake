package doom.testkit.trace;

import java.util.List;

/**
 * A whole trace in memory.
 *
 * @param header the header line
 * @param lines  one line per tic, in file order
 */
public record Trace(TraceHeader header, List<TraceLine> lines) {

    public Trace {
        lines = List.copyOf(lines);
    }
}
