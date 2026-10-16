package doom.ui;

/**
 * Which lines the player has seen ({@code ML_MAPPED}). The simulation does not keep this flag; the renderer sets it
 * when it draws a line, so the engine supplies it from the renderer.
 */
@FunctionalInterface
public interface MappedLines {

    boolean isMapped(int line);

    /** Nothing is mapped (the automap then shows only what the all-map power-up or the cheat reveal). */
    MappedLines NONE = line -> false;
}
