package doom.wad.iwad;

/** What the IWAD contains ({@code GameMode_t}): decides episodes, maps and several rules (spec 05 §2). */
public enum GameMode {
    /** DOOM 1 shareware: episode 1 only, no PWADs. */
    SHAREWARE,
    /** DOOM 1 registered: episodes 1–3. */
    REGISTERED,
    /** The Ultimate DOOM: episodes 1–4. */
    RETAIL,
    /** DOOM II and Final DOOM: MAP01–MAP32. */
    COMMERCIAL,
    /** Not identified. */
    INDETERMINATE
}
