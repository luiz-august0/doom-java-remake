package doom.testkit.trace;

import java.util.List;

/**
 * The outcome of {@link TraceComparator#compare}.
 *
 * @param refHeader     header of the reference trace
 * @param oursHeader    header of our trace
 * @param linesCompared number of tic lines found identical (on gated keys) before the divergence, or in total
 * @param headerNotes   informational differences between the headers (never a divergence)
 * @param divergence    the first divergence, or null when the traces are equivalent
 */
public record TraceComparison(TraceHeader refHeader, TraceHeader oursHeader, int linesCompared,
                              List<String> headerNotes, TraceDivergence divergence) {

    public TraceComparison {
        headerNotes = List.copyOf(headerNotes);
    }

    /** Whether every gated key of every line is equal and both traces have the same number of lines. */
    public boolean identical() {
        return divergence == null;
    }

    /** A human-readable summary (several lines when divergent). */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (String note : headerNotes) {
            sb.append("note: ").append(note).append('\n');
        }
        if (divergence == null) {
            sb.append("Identical: ").append(linesCompared).append(" tic lines\n");
        } else {
            sb.append(divergence.describe());
        }
        return sb.toString();
    }
}
