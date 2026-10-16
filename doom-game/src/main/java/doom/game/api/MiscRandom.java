package doom.game.api;

/**
 * The misc random number generator ({@code M_Random}), owned by the session and shared with the presentation layer.
 * Only the call sites listed in docs/contracts.md §8 may use it.
 */
@FunctionalInterface
public interface MiscRandom {

    /** Next value in {@code [0, 255]}. */
    int next();
}
