package doom.wad.audio;

/**
 * Identifiers and constants of the audio lumps (spec 05 §4, spec 06).
 */
public final class AudioFormat {
    /** DMX sound effect format number in the lump header. */
    public static final int DMX_FORMAT = 3;
    /** MUS lump signature. */
    public static final String MUS_MAGIC = "MUS\u001a";
    /** MIDI file signature. */
    public static final String MIDI_MAGIC = "MThd";
    /** GENMIDI lump signature. */
    public static final String GENMIDI_MAGIC = "#OPL_II#";
    /** Instruments in GENMIDI: 128 melodic + 47 percussion. */
    public static final int GENMIDI_INSTRUMENTS = 175;
    /** Melodic instruments in GENMIDI. */
    public static final int GENMIDI_MELODIC = 128;
    /** Percussion instruments in GENMIDI (MIDI keys 35..81). */
    public static final int GENMIDI_PERCUSSION = 47;
    /** Size in bytes of one GENMIDI instrument record. */
    public static final int GENMIDI_INSTRUMENT_SIZE = 36;
    /** Size in bytes of one GENMIDI instrument name. */
    public static final int GENMIDI_NAME_SIZE = 32;

    /** MUS tick rate in ticks per second (spec 06 §3.1). */
    public static final int MUS_TICKS_PER_SECOND = 140;
    /** MIDI division written by mus2mid (ticks per quarter note). */
    public static final int MUS_MIDI_DIVISION = 70;
    /** Default MIDI tempo in microseconds per quarter note (120 bpm). */
    public static final int DEFAULT_TEMPO = 500_000;

    private AudioFormat() {
    }
}
