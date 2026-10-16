package doom.testkit.trace;

import java.util.List;

/**
 * The first difference between a reference trace and ours.
 *
 * @param kind         what differs
 * @param lineIndex    0-based index of the tic line (not the file line)
 * @param tic          the tic ({@code t}) of the reference line, or of ours when the reference ended first
 * @param key          the gated key that differs (null for {@link Kind#REF_ENDED} / {@link Kind#OURS_ENDED})
 * @param fieldIndex   0-based index of the differing comma-separated value, or -1 when the whole value is reported
 * @param fieldName    the name of that value (player field names from §6.4, component names for {@code map}, {@code tot},
 *                     {@code a}, {@code x}), or null
 * @param refValue     the differing value in the reference (null when absent)
 * @param oursValue    the differing value in ours (null when absent)
 * @param refContext   reference lines around the divergence (before, the line itself, after)
 * @param oursContext  our lines around the divergence
 * @param objectDetail the first differing object when both sides have verbose lines for that tic, or null
 */
public record TraceDivergence(Kind kind, int lineIndex, int tic, String key, int fieldIndex, String fieldName,
                              String refValue, String oursValue, List<TraceLine> refContext,
                              List<TraceLine> oursContext, ObjectDetail objectDetail) {

    public TraceDivergence {
        refContext = List.copyOf(refContext);
        oursContext = List.copyOf(oursContext);
    }

    /** The nature of a divergence. */
    public enum Kind {
        /** A gated key has different values. */
        VALUE,
        /** A gated key is present on one side only. */
        MISSING_KEY,
        /** Both lines have the same gated values but the keys are in a different order (the format requires identical lines). */
        KEY_ORDER,
        /** The reference trace ended while ours continues. */
        REF_ENDED,
        /** Our trace ended while the reference continues. */
        OURS_ENDED
    }

    /**
     * The first differing object of a verbose trace (§6.6).
     *
     * @param kind       {@code 'a'} or {@code 'x'}
     * @param position   0-based position among that tic's verbose entries of this kind
     * @param fieldIndex index of the differing value, or -1 when the object is missing on one side
     * @param fieldName  name of the differing value, or null
     * @param ref        the reference entry (null when missing)
     * @param ours       our entry (null when missing)
     */
    public record ObjectDetail(char kind, int position, int fieldIndex, String fieldName, VerboseEntry ref,
                               VerboseEntry ours) {
    }

    /** A multi-line human-readable description, as printed by {@code trace-diff}. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        switch (kind) {
            case REF_ENDED -> sb.append("Divergence: the reference trace ends before tic ").append(tic)
                    .append(" (line ").append(lineIndex).append("), ours continues\n");
            case OURS_ENDED -> sb.append("Divergence: our trace ends before tic ").append(tic)
                    .append(" (line ").append(lineIndex).append("), the reference continues\n");
            default -> {
                sb.append("Divergence at tic ").append(tic).append(" (line ").append(lineIndex).append("), key '")
                        .append(key).append('\'');
                String description = TraceFormat.KEY_DESCRIPTIONS.get(key);
                if (description != null) {
                    sb.append(" (").append(description).append(')');
                }
                if (fieldIndex >= 0) {
                    sb.append(", field ").append(fieldIndex + 1);
                    if (fieldName != null) {
                        sb.append(" '").append(fieldName).append('\'');
                    }
                }
                if (kind == Kind.MISSING_KEY) {
                    sb.append(" present on one side only");
                } else if (kind == Kind.KEY_ORDER) {
                    sb.append(": keys in a different order");
                }
                sb.append('\n');
                sb.append("  ref : ").append(refValue == null ? "(absent)" : refValue).append('\n');
                sb.append("  ours: ").append(oursValue == null ? "(absent)" : oursValue).append('\n');
            }
        }
        if (objectDetail != null) {
            ObjectDetail d = objectDetail;
            sb.append("  first differing object: ").append(d.kind()).append(" #").append(d.position());
            if (d.fieldIndex() >= 0) {
                sb.append(", field ").append(d.fieldIndex() + 1);
                if (d.fieldName() != null) {
                    sb.append(" '").append(d.fieldName()).append('\'');
                }
            } else {
                sb.append(" present on one side only");
            }
            sb.append('\n');
            sb.append("    ref : ").append(d.ref() == null ? "(absent)" : d.ref()).append('\n');
            sb.append("    ours: ").append(d.ours() == null ? "(absent)" : d.ours()).append('\n');
        }
        appendContext(sb, "reference", refContext);
        appendContext(sb, "ours", oursContext);
        return sb.toString();
    }

    private static void appendContext(StringBuilder sb, String title, List<TraceLine> lines) {
        sb.append("--- ").append(title).append(" ---\n");
        for (TraceLine l : lines) {
            sb.append(l.raw()).append('\n');
        }
    }
}
