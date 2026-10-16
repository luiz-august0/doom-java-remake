package doom.engine;

import doom.game.api.TicCmd;

/**
 * Produces the local player's command for one tic: the seam for multiplayer (spec 02 section 7). In v1 the only
 * implementation is {@code LocalTicSource}; demos are played by the session itself (contracts section 11), which
 * ignores the commands passed to {@code GameSession.tick} while a demo plays.
 */
@FunctionalInterface
public interface TicCmdSource {

    /** Fills {@code out} (cleared first) with the command for the next tic ({@code G_BuildTiccmd}). */
    void next(TicCmd out);
}
