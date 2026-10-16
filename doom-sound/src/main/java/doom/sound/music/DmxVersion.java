package doom.sound.music;

/**
 * Which DMX library behaviour the OPL driver emulates (Chocolate Doom {@code opl_driver_ver_t}). Doom v1.9, the
 * default, steals voices with the "lowest MIDI channel wins" rule and allocates both voices of two-voice instruments.
 */
public enum DmxVersion {
    /** Doom 1 v1.666. */
    DOOM1_1_666,
    /** Doom 2 v1.666, Hexen, Heretic. */
    DOOM2_1_666,
    /** Doom v1.9 and Strife (default). */
    DOOM_1_9
}
