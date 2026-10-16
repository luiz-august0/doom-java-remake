package doom.wad.audio;

import doom.wad.WadFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * The {@code GENMIDI} lump: the OPL instrument bank DMX uses (spec 05 §4). Layout: the 8 bytes {@code #OPL_II#}, then
 * 175 instrument records of 36 bytes (128 melodic, then 47 percussion for MIDI keys 35..81), then 175 names of 32 bytes.
 *
 * <p>An instrument record: {@code uint16 flags}, {@code uint8 fineTuning}, {@code uint8 fixedNote}, then two voices of 16
 * bytes: modulator (6 bytes: tremolo/vibrato/sustain/KSR/multiplier, attack/decay, sustain/release, waveform, scale
 * (KSL), level), {@code uint8 feedback}, carrier (6 bytes), {@code uint8 unused}, {@code int16 baseNoteOffset}.
 *
 * <p>All data is decoded once into immutable objects, so the music driver can use them without allocating. Like DMX and
 * Chocolate Doom, the header text is not enforced (see {@link #hasValidHeader()}).
 */
public final class GenMidi {

    /** Instrument flag: fixed pitch (the note comes from {@link Instrument#fixedNote()}). */
    public static final int FLAG_FIXED = 0x0001;
    /** Instrument flag: double voice (the instrument uses both {@link Instrument#voice(int) voices}). */
    public static final int FLAG_TWO_VOICE = 0x0004;

    /** One operator (modulator or carrier). All fields are raw register values 0..255. */
    public record Operator(int tremolo, int attack, int sustain, int waveform, int scale, int level) {
    }

    /** One OPL voice of an instrument. */
    public record Voice(Operator modulator, int feedback, Operator carrier, int unused, int baseNoteOffset) {
    }

    /** One of the 175 instruments. {@code index} 0..127 melodic, 128..174 percussion (key {@code index - 128 + 35}). */
    public static final class Instrument {
        private final int index;
        private final int flags;
        private final int fineTuning;
        private final int fixedNote;
        private final Voice[] voices;
        private final String name;

        Instrument(int index, int flags, int fineTuning, int fixedNote, Voice voice0, Voice voice1, String name) {
            this.index = index;
            this.flags = flags;
            this.fineTuning = fineTuning;
            this.fixedNote = fixedNote;
            this.voices = new Voice[] {voice0, voice1};
            this.name = name;
        }

        /** Position in the lump, 0..174. */
        public int index() {
            return index;
        }

        /** Raw flags field. */
        public int flags() {
            return flags;
        }

        /** Whether {@link #FLAG_FIXED} is set. */
        public boolean fixed() {
            return (flags & FLAG_FIXED) != 0;
        }

        /** Whether {@link #FLAG_TWO_VOICE} is set. */
        public boolean twoVoice() {
            return (flags & FLAG_TWO_VOICE) != 0;
        }

        /** Fine tuning of the second voice, 0..255 (128 = none). */
        public int fineTuning() {
            return fineTuning;
        }

        /** Note played by a fixed-pitch instrument. */
        public int fixedNote() {
            return fixedNote;
        }

        /** Voice 0 or 1. */
        public Voice voice(int i) {
            return voices[i];
        }

        /** Name from the lump (up to 32 characters). */
        public String name() {
            return name;
        }
    }

    private final boolean validHeader;
    private final Instrument[] instruments;

    private GenMidi(boolean validHeader, Instrument[] instruments) {
        this.validHeader = validHeader;
        this.instruments = instruments;
    }

    /**
     * Decodes a GENMIDI lump.
     *
     * @throws WadFormatException when the lump is too short to hold 175 instruments
     */
    public static GenMidi parse(ByteBuffer lump) {
        ByteBuffer b = lump.slice().order(ByteOrder.LITTLE_ENDIAN);
        int header = AudioFormat.GENMIDI_MAGIC.length();
        int instrumentsEnd = header + AudioFormat.GENMIDI_INSTRUMENTS * AudioFormat.GENMIDI_INSTRUMENT_SIZE;
        if (b.remaining() < instrumentsEnd) {
            throw new WadFormatException(null, "GENMIDI", b.remaining(),
                    "lump too short: need at least " + instrumentsEnd + " bytes for 175 instruments");
        }
        byte[] magic = new byte[header];
        b.get(0, magic);
        boolean valid = new String(magic, StandardCharsets.US_ASCII).equals(AudioFormat.GENMIDI_MAGIC);
        boolean hasNames = b.remaining() >= instrumentsEnd + AudioFormat.GENMIDI_INSTRUMENTS * AudioFormat.GENMIDI_NAME_SIZE;
        Instrument[] result = new Instrument[AudioFormat.GENMIDI_INSTRUMENTS];
        for (int i = 0; i < result.length; i++) {
            int at = header + i * AudioFormat.GENMIDI_INSTRUMENT_SIZE;
            int flags = b.getShort(at) & 0xFFFF;
            int fine = b.get(at + 2) & 0xFF;
            int fixed = b.get(at + 3) & 0xFF;
            Voice v0 = voice(b, at + 4);
            Voice v1 = voice(b, at + 20);
            String name = hasNames ? name(b, instrumentsEnd + i * AudioFormat.GENMIDI_NAME_SIZE) : "";
            result[i] = new Instrument(i, flags, fine, fixed, v0, v1, name);
        }
        return new GenMidi(valid, result);
    }

    /** Decodes a GENMIDI lump held in an array. */
    public static GenMidi parse(byte[] lump) {
        return parse(ByteBuffer.wrap(lump));
    }

    private static Operator operator(ByteBuffer b, int at) {
        return new Operator(b.get(at) & 0xFF, b.get(at + 1) & 0xFF, b.get(at + 2) & 0xFF, b.get(at + 3) & 0xFF,
                b.get(at + 4) & 0xFF, b.get(at + 5) & 0xFF);
    }

    private static Voice voice(ByteBuffer b, int at) {
        return new Voice(operator(b, at), b.get(at + 6) & 0xFF, operator(b, at + 7), b.get(at + 13) & 0xFF,
                b.getShort(at + 14));
    }

    private static String name(ByteBuffer b, int at) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < AudioFormat.GENMIDI_NAME_SIZE; i++) {
            int c = b.get(at + i) & 0xFF;
            if (c == 0) {
                break;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    /** Whether the lump starts with {@code #OPL_II#}. */
    public boolean hasValidHeader() {
        return validHeader;
    }

    /** Instrument {@code i}, 0..174. */
    public Instrument instrument(int i) {
        return instruments[i];
    }

    /** Melodic instrument for a MIDI program number (masked to 7 bits). */
    public Instrument melodic(int program) {
        return instruments[program & 0x7F];
    }

    /** Percussion instrument for a MIDI key 35..81. */
    public Instrument percussion(int key) {
        return instruments[AudioFormat.GENMIDI_MELODIC + key - 35];
    }

    /** Number of instruments, always 175. */
    public int size() {
        return instruments.length;
    }
}
