package doom.wad.audio;

import doom.wad.WadFormatException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Standard MIDI File of format 0 or 1, parsed the way Chocolate Doom's {@code midifile.c} does: chunk headers are
 * checked, each track is read event by event up to its end-of-track meta event (the declared chunk length is ignored),
 * running status is supported, and the time division may be SMPTE.
 */
public final class MidiFile {

    /** MIDI event type bytes (status nibble for channel events). */
    public static final int NOTE_OFF = 0x80;
    public static final int NOTE_ON = 0x90;
    public static final int AFTERTOUCH = 0xA0;
    public static final int CONTROLLER = 0xB0;
    public static final int PROGRAM_CHANGE = 0xC0;
    public static final int CHAN_AFTERTOUCH = 0xD0;
    public static final int PITCH_BEND = 0xE0;
    public static final int SYSEX = 0xF0;
    public static final int SYSEX_SPLIT = 0xF7;
    public static final int META = 0xFF;

    public static final int META_END_OF_TRACK = 0x2F;
    public static final int META_SET_TEMPO = 0x51;

    /**
     * One decoded event. For channel events {@code param1}/{@code param2} are the raw data bytes (not masked); for
     * meta events {@code metaType} and {@code data} are set; for sysex {@code data} holds the payload.
     */
    public record Event(int deltaTime, int type, int channel, int param1, int param2, int metaType, byte[] data) {
    }

    private final int format;
    private final int division;
    private final List<List<Event>> tracks;

    private MidiFile(int format, int division, List<List<Event>> tracks) {
        this.format = format;
        this.division = division;
        this.tracks = tracks;
    }

    /** MIDI file format, 0 or 1. */
    public int format() {
        return format;
    }

    /** Ticks per quarter note, with SMPTE divisions converted as Chocolate Doom does. */
    public int division() {
        return division;
    }

    /** Number of tracks. */
    public int trackCount() {
        return tracks.size();
    }

    /** Events of track {@code i}, in file order, ending with the end-of-track meta event. */
    public List<Event> track(int i) {
        return tracks.get(i);
    }

    /**
     * Parses a MIDI file.
     *
     * @throws WadFormatException when the data is not a supported MIDI file
     */
    public static MidiFile parse(byte[] data) {
        return new Parser(data).parse();
    }

    /**
     * Merges all tracks into one {@link MusicScore}, ordered by absolute tick (ties keep track order). Track membership,
     * per-track deltas and per-track chains are preserved; every end-of-track event becomes {@link MusicScore#TYPE_END}.
     */
    public MusicScore toScore() {
        int total = 0;
        for (List<Event> track : tracks) {
            total += track.size();
        }
        long[] key = new long[total];
        Event[] events = new Event[total];
        int[] trackOfEvent = new int[total];
        int n = 0;
        for (int t = 0; t < tracks.size(); t++) {
            long tick = 0;
            for (Event e : tracks.get(t)) {
                tick += e.deltaTime() & 0xFFFFFFFFL;
                if (tick > Integer.MAX_VALUE) {
                    throw new WadFormatException(null, null, -1, "MIDI: song longer than 2^31 ticks");
                }
                // Sort key: tick, then position in the concatenation (track order, then file order).
                key[n] = (tick << 32) | n;
                events[n] = e;
                trackOfEvent[n] = t;
                n++;
            }
        }
        long[] sorted = key;
        Arrays.sort(sorted);

        int[] ticks = new int[total];
        int[] types = new int[total];
        int[] channels = new int[total];
        int[] p1 = new int[total];
        int[] p2 = new int[total];
        int[] trackOf = new int[total];
        int[] deltas = new int[total];
        int[] nextInTrack = new int[total];
        int[] firstOfTrack = new int[tracks.size()];
        int[] lastOfTrack = new int[tracks.size()];
        Arrays.fill(firstOfTrack, -1);
        Arrays.fill(lastOfTrack, -1);
        for (int i = 0; i < total; i++) {
            int idx = (int) (sorted[i] & 0xFFFFFFFFL);
            Event e = events[idx];
            ticks[i] = (int) (sorted[i] >>> 32);
            int t = trackOfEvent[idx];
            trackOf[i] = t;
            deltas[i] = (int) e.deltaTime();
            nextInTrack[i] = -1;
            if (lastOfTrack[t] >= 0) {
                nextInTrack[lastOfTrack[t]] = i;
            } else {
                firstOfTrack[t] = i;
            }
            lastOfTrack[t] = i;
            if (e.type() == META) {
                if (e.metaType() == META_END_OF_TRACK) {
                    types[i] = MusicScore.TYPE_END;
                } else if (e.metaType() == META_SET_TEMPO && e.data().length == 3) {
                    types[i] = MusicScore.TYPE_TEMPO;
                    p1[i] = ((e.data()[0] & 0xFF) << 16) | ((e.data()[1] & 0xFF) << 8) | (e.data()[2] & 0xFF);
                } else {
                    types[i] = MusicScore.TYPE_OTHER;
                }
            } else if (e.type() == SYSEX || e.type() == SYSEX_SPLIT) {
                types[i] = MusicScore.TYPE_OTHER;
            } else {
                types[i] = e.type();
                channels[i] = e.channel();
                p1[i] = e.param1();
                p2[i] = e.param2();
            }
        }
        return new MusicScore(division, AudioFormat.DEFAULT_TEMPO, ticks, types, channels, p1, p2, trackOf, deltas,
                nextInTrack, firstOfTrack);
    }

    private static final class Parser {
        private final byte[] data;
        private int pos;
        private int lastEventType;

        Parser(byte[] data) {
            this.data = data;
        }

        private WadFormatException fail(String message) {
            return new WadFormatException(null, null, pos, "MIDI: " + message);
        }

        private int u8() {
            if (pos >= data.length) {
                throw fail("unexpected end of file");
            }
            return data[pos++] & 0xFF;
        }

        private int be16() {
            int hi = u8();
            return (hi << 8) | u8();
        }

        private long be32() {
            long hi = be16();
            return (hi << 16) | be16();
        }

        private void expectChunk(String id) {
            for (int i = 0; i < 4; i++) {
                if (u8() != id.charAt(i)) {
                    pos -= i + 1;
                    throw fail("expected '" + id + "' chunk header");
                }
            }
        }

        private int variableLength() {
            int result = 0;
            for (int i = 0; i < 4; i++) {
                int b = u8();
                result = (result << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) {
                    return result;
                }
            }
            throw fail("variable-length value too long");
        }

        private byte[] bytes(int n) {
            if (n < 0 || n > data.length - pos) {
                throw fail("event data beyond the end of the file");
            }
            byte[] result = new byte[n];
            System.arraycopy(data, pos, result, 0, n);
            pos += n;
            return result;
        }

        private Event readEvent() {
            int delta = variableLength();
            int eventType = u8();
            if ((eventType & 0x80) == 0) {
                eventType = lastEventType;
                pos--;
            } else {
                lastEventType = eventType;
            }
            switch (eventType & 0xF0) {
                case NOTE_OFF, NOTE_ON, AFTERTOUCH, CONTROLLER, PITCH_BEND -> {
                    int p1 = u8();
                    int p2 = u8();
                    return new Event(delta, eventType & 0xF0, eventType & 0x0F, p1, p2, 0, null);
                }
                case PROGRAM_CHANGE, CHAN_AFTERTOUCH -> {
                    int p1 = u8();
                    return new Event(delta, eventType & 0xF0, eventType & 0x0F, p1, 0, 0, null);
                }
                default -> {
                }
            }
            switch (eventType) {
                case SYSEX, SYSEX_SPLIT -> {
                    int length = variableLength();
                    return new Event(delta, eventType, 0, 0, 0, 0, bytes(length));
                }
                case META -> {
                    int metaType = u8();
                    int length = variableLength();
                    return new Event(delta, META, 0, 0, 0, metaType, bytes(length));
                }
                default -> throw fail("unknown event type " + eventType);
            }
        }

        private List<Event> readTrack() {
            expectChunk("MTrk");
            be32();
            List<Event> events = new ArrayList<>();
            lastEventType = 0;
            for (;;) {
                Event e = readEvent();
                events.add(e);
                if (e.type() == META && e.metaType() == META_END_OF_TRACK) {
                    return events;
                }
            }
        }

        MidiFile parse() {
            expectChunk("MThd");
            if (be32() != 6) {
                throw fail("bad header chunk size");
            }
            int format = be16();
            int trackCount = be16();
            int timeDivision = be16();
            if ((format != 0 && format != 1) || trackCount < 1) {
                throw fail("unsupported format " + format + " with " + trackCount + " tracks");
            }
            int division;
            short signed = (short) timeDivision;
            if (signed < 0) {
                division = -(signed / 256) * (signed & 0xFF);
            } else {
                division = signed;
            }
            if (division <= 0) {
                throw fail("invalid time division");
            }
            List<List<Event>> tracks = new ArrayList<>(trackCount);
            for (int i = 0; i < trackCount; i++) {
                tracks.add(readTrack());
            }
            return new MidiFile(format, division, tracks);
        }
    }
}
