package doom.game.api;

/** {@code skill_t}; the ordinal is the original value ({@code sk_baby} = 0), as stored in demos. */
public enum Skill {
    BABY,
    EASY,
    MEDIUM,
    HARD,
    NIGHTMARE;

    /** From the original 0-based value. */
    public static Skill of(int value) {
        return values()[value];
    }
}
