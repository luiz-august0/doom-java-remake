package doom.math;

import doom.math.annotation.VanillaRef;

/**
 * The two random number generators of the original ({@code m_random.c}), sharing {@code rndtable}:
 * <ul>
 *   <li>{@link #game()} ({@code P_Random}, index {@code prndindex}): the only randomness allowed in the simulation;</li>
 *   <li>{@link #misc()} ({@code M_Random}, index {@code rndindex}): effects without gameplay consequence
 *       (see contracts §8 for its exact call sites).</li>
 * </ul>
 * Each call advances its index by one (8-bit wrap) <i>before</i> reading the table. Both are reset by {@link #clear()}.
 * One instance per game session; never static (no mutable static state).
 */
public final class Rng {

    /** {@code prndindex}, always in {@code [0, 255]}. */
    private int gameIndex;
    /** {@code rndindex}, always in {@code [0, 255]}. */
    private int miscIndex;

    /** Creates a generator with both indices at zero, the state after {@code M_ClearRandom}. */
    public Rng() {
    }

    /** {@code P_Random}: next game random number in {@code [0, 255]}. */
    @VanillaRef("m_random.c:P_Random")
    public int game() {
        gameIndex = (gameIndex + 1) & 0xff;
        return Tables.rnd(gameIndex);
    }

    /** {@code M_Random}: next misc random number in {@code [0, 255]}. */
    @VanillaRef("m_random.c:M_Random")
    public int misc() {
        miscIndex = (miscIndex + 1) & 0xff;
        return Tables.rnd(miscIndex);
    }

    /**
     * {@code P_SubRandom}: {@code game() - game()}, first call minus second call (Chocolate Doom's fixed order).
     * The result is in {@code [-255, 255]}.
     */
    @VanillaRef("m_random.c:P_SubRandom")
    public int subRandom() {
        int first = game();
        return first - game();
    }

    /** {@code M_ClearRandom}: resets both indices to zero. */
    @VanillaRef("m_random.c:M_ClearRandom")
    public void clear() {
        gameIndex = 0;
        miscIndex = 0;
    }

    /** Current game index ({@code prndindex}), for traces and saves. */
    public int gameIndex() {
        return gameIndex;
    }

    /** Current misc index ({@code rndindex}), for traces and saves. */
    public int miscIndex() {
        return miscIndex;
    }

    /**
     * Restores both indices (save games).
     *
     * @throws IllegalArgumentException when an index is outside {@code [0, 255]}
     */
    public void setIndices(int gameIndex, int miscIndex) {
        checkIndex(gameIndex, "game");
        checkIndex(miscIndex, "misc");
        this.gameIndex = gameIndex;
        this.miscIndex = miscIndex;
    }

    /**
     * Restores the game index ({@code prndindex}) only.
     *
     * @throws IllegalArgumentException when the index is outside {@code [0, 255]}
     */
    public void setGameIndex(int gameIndex) {
        checkIndex(gameIndex, "game");
        this.gameIndex = gameIndex;
    }

    /**
     * Restores the misc index ({@code rndindex}) only.
     *
     * @throws IllegalArgumentException when the index is outside {@code [0, 255]}
     */
    public void setMiscIndex(int miscIndex) {
        checkIndex(miscIndex, "misc");
        this.miscIndex = miscIndex;
    }

    private static void checkIndex(int index, String which) {
        if (index < 0 || index > 255) {
            throw new IllegalArgumentException("RNG " + which + " index out of range [0, 255]: " + index);
        }
    }

    @Override
    public String toString() {
        return "Rng[game=" + gameIndex + ", misc=" + miscIndex + "]";
    }
}
