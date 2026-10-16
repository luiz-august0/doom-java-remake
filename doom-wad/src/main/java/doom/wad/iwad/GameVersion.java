package doom.wad.iwad;

/**
 * Which DOS executable's behaviour is emulated ({@code GameVersion_t}, ADR-005). The declaration order is the
 * chronological order used by {@link #isAtLeast}: rules that changed in a later executable are written as
 * {@code if (version.isAtLeast(ULTIMATE))}, never with subclasses (spec 03 §9).
 */
public enum GameVersion {
    /** DOOM.EXE / DOOM2.EXE v1.9. */
    V1_9("1.9"),
    /** The Ultimate DOOM. */
    ULTIMATE("ultimate"),
    /** Final DOOM (TNT, Plutonia). */
    FINAL("final"),
    /** Final DOOM, alternate release with fixes. */
    FINAL2("final2");

    private final String cliName;

    GameVersion(String cliName) {
        this.cliName = cliName;
    }

    /** Name accepted by {@code -gameversion} and printed in trace headers. */
    public String cliName() {
        return cliName;
    }

    public boolean isAtLeast(GameVersion other) {
        return ordinal() >= other.ordinal();
    }

    /** Parses a {@code -gameversion} value; returns null when unknown. */
    public static GameVersion fromCliName(String name) {
        for (GameVersion v : values()) {
            if (v.cliName.equalsIgnoreCase(name)) {
                return v;
            }
        }
        return null;
    }
}
