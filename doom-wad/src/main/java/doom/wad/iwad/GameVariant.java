package doom.wad.iwad;

/**
 * Release variant of the IWAD ({@code gamevariant} in Chocolate Doom). Freedoom variants automatically apply the
 * IWAD's own DEHACKED lump, exactly like Chocolate Doom (ADR-022).
 */
public enum GameVariant {
    VANILLA,
    FREEDOOM,
    FREEDM,
    BFG_EDITION
}
