package doom.game.api;

/**
 * One DEHACKED patch to apply when a session starts, in order (ADR-022).
 *
 * @param source human-readable origin, e.g. {@code "freedoom2.wad:DEHACKED"} or a file name
 * @param text   the patch text
 */
public record DehackedPatch(String source, String text) {
}
