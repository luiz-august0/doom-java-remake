package doom.wad.audio;

import doom.wad.WadFormatException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A song reduced to one ordered list of events with absolute times (spec 06 §3.1). Both MUS lumps (through
 * {@link MusConverter}) and MIDI files (formats 0 and 1, through {@link MidiFile}) end up here; the sequencer only
 * knows this form.
 *
 * <p>Time is in MIDI ticks; {@link #division()} ticks make one quarter note and the tempo (microseconds per quarter
 * note) starts at {@link #initialTempo()} and changes with {@link #TYPE_TEMPO} events. For MUS lumps the division is 70
 * and the tempo 500000, i.e. exactly 140 ticks per second.
 *
 * <p>Events are stored in parallel arrays (no per-event objects): {@link #type(int)} is the MIDI status nibble
 * ({@code 0x80..0xE0}), {@link #TYPE_TEMPO}, {@link #TYPE_END} or {@link #TYPE_OTHER}. Events that the OPL driver
 * ignores (aftertouch, sysex, text meta events) are kept as {@link #TYPE_OTHER} because Chocolate Doom still schedules
 * a timer for each of them, which slightly changes timing.
 *
 * <p>The list is ordered by absolute tick, but a format 1 MIDI file keeps its tracks apart: every event knows its
 * {@link #track(int) track}, its {@link #delta(int) delta time within that track} and the
 * {@link #nextInTrack(int) next event of the same track}. Chocolate Doom runs each track on its own timer chain, so
 * that the quantisation of event times to audio samples differs per track; a sequencer that wants identical timing
 * follows these chains instead of the merged order. MUS lumps and format 0 files have a single track.
 */
public final class MusicScore {

    /** Tempo change; {@link #param1(int)} holds microseconds per quarter note. */
    public static final int TYPE_TEMPO = 0x151;
    /** End of a track (every track has one as its last event); the song ends when all tracks have ended. */
    public static final int TYPE_END = 0x12F;
    /** Any event that has no effect on the OPL driver but takes its place in time. */
    public static final int TYPE_OTHER = 0x1FF;

    private final int division;
    private final int initialTempo;
    private final int[] ticks;
    private final int[] types;
    private final int[] channels;
    private final int[] params1;
    private final int[] params2;
    private final int[] trackOf;
    private final int[] deltas;
    private final int[] nextInTrack;
    private final int[] firstOfTrack;

    MusicScore(int division, int initialTempo, int[] ticks, int[] types, int[] channels, int[] params1, int[] params2,
               int[] trackOf, int[] deltas, int[] nextInTrack, int[] firstOfTrack) {
        this.division = division;
        this.initialTempo = initialTempo;
        this.ticks = ticks;
        this.types = types;
        this.channels = channels;
        this.params1 = params1;
        this.params2 = params2;
        this.trackOf = trackOf;
        this.deltas = deltas;
        this.nextInTrack = nextInTrack;
        this.firstOfTrack = firstOfTrack;
    }

    /**
     * Builds a single-track score from raw arrays (tests); the arrays are copied. The last event should be
     * {@link #TYPE_END}.
     */
    public static MusicScore of(int division, int initialTempo, int[] ticks, int[] types, int[] channels,
                                int[] params1, int[] params2) {
        int n = ticks.length;
        if (types.length != n || channels.length != n || params1.length != n || params2.length != n) {
            throw new IllegalArgumentException("event arrays differ in length");
        }
        int[] trackOf = new int[n];
        int[] deltas = new int[n];
        int[] next = new int[n];
        for (int i = 0; i < n; i++) {
            deltas[i] = ticks[i] - (i == 0 ? 0 : ticks[i - 1]);
            next[i] = i + 1 < n ? i + 1 : -1;
        }
        return new MusicScore(division, initialTempo, ticks.clone(), types.clone(), channels.clone(), params1.clone(),
                params2.clone(), trackOf, deltas, next, new int[] {n == 0 ? -1 : 0});
    }

    /**
     * Loads a music lump: a MIDI file when it starts with {@code MThd}, otherwise a MUS lump (Chocolate Doom does not
     * check the MUS signature either).
     *
     * @throws WadFormatException when the data cannot be decoded
     */
    public static MusicScore fromLump(ByteBuffer lump, String lumpName) {
        byte[] data = new byte[lump.remaining()];
        lump.get(lump.position(), data);
        return fromLump(data, lumpName);
    }

    /** Same as {@link #fromLump(ByteBuffer, String)} for a byte array. */
    public static MusicScore fromLump(byte[] data, String lumpName) {
        try {
            if (data.length > 4 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd') {
                return MidiFile.parse(data).toScore();
            }
            return MidiFile.parse(MusConverter.toMidi(data)).toScore();
        } catch (WadFormatException e) {
            throw new WadFormatException(null, lumpName, 0, e.getMessage());
        }
    }

    /** Ticks per quarter note. */
    public int division() {
        return division;
    }

    /** Microseconds per quarter note at the start of the song. */
    public int initialTempo() {
        return initialTempo;
    }

    /** Ticks per second at the initial tempo (140.0 for MUS). */
    public double ticksPerSecond() {
        return division * 1_000_000.0 / initialTempo;
    }

    /** Number of tracks (1 for MUS and format 0). */
    public int trackCount() {
        return firstOfTrack.length;
    }

    /** Track of event {@code i}. */
    public int track(int i) {
        return trackOf[i];
    }

    /** Delta time of event {@code i} relative to the previous event of its own track, in ticks. */
    public int delta(int i) {
        return deltas[i];
    }

    /** Index of the event that follows {@code i} in its own track, or -1 after the track's last event. */
    public int nextInTrack(int i) {
        return nextInTrack[i];
    }

    /** Index of the first event of {@code track}, or -1 when the track is empty. */
    public int firstOfTrack(int track) {
        return firstOfTrack[track];
    }

    /** Number of events, including one {@link #TYPE_END} per track. */
    public int size() {
        return ticks.length;
    }

    /** Absolute time of event {@code i} in ticks. */
    public int tick(int i) {
        return ticks[i];
    }

    /** Event kind: MIDI status nibble, {@link #TYPE_TEMPO}, {@link #TYPE_END} or {@link #TYPE_OTHER}. */
    public int type(int i) {
        return types[i];
    }

    /** MIDI channel 0..15 of a channel event (MIDI numbering: 9 is percussion). */
    public int channel(int i) {
        return channels[i];
    }

    /** First parameter (key, controller number, program, pitch bend LSB, or the tempo for {@link #TYPE_TEMPO}). */
    public int param1(int i) {
        return params1[i];
    }

    /** Second parameter (velocity, controller value, pitch bend MSB). */
    public int param2(int i) {
        return params2[i];
    }

    /** Tick of the last event. */
    public int lengthTicks() {
        return ticks.length == 0 ? 0 : ticks[ticks.length - 1];
    }

    /** Duration in seconds if the tempo never changed. */
    public double nominalSeconds() {
        return lengthTicks() / ticksPerSecond();
    }

    @Override
    public String toString() {
        return "MusicScore[" + size() + " events, " + lengthTicks() + " ticks, division " + division + "]";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MusicScore s && division == s.division && initialTempo == s.initialTempo
                && Arrays.equals(ticks, s.ticks) && Arrays.equals(types, s.types)
                && Arrays.equals(channels, s.channels) && Arrays.equals(params1, s.params1)
                && Arrays.equals(params2, s.params2);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ticks) * 31 + Arrays.hashCode(types);
    }
}
