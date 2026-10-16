package doom.sound.music;

import doom.math.annotation.VanillaRef;
import doom.sound.opl.Opl3;
import doom.wad.audio.GenMidi;
import doom.wad.audio.MusicScore;

/**
 * MIDI sequencer plus OPL3 chip plus {@link OplMusicDriver}: the music half of the audio thread (spec 06 §3.2–3.3).
 *
 * <p>The sequencer has no wall clock. Time advances only while samples are rendered ({@link #render}), exactly like
 * Chocolate Doom's SDL OPL back end ({@code opl_sdl.c}): events are callbacks in a microsecond queue; a render call
 * generates samples up to the next due callback, then advances the clock by the (integer) duration of those samples and
 * fires the callbacks. That reproduces its timing, including the sub-sample quantisation of event times.
 *
 * <p>Typical use on the audio thread: {@code setMusicVolume}, {@code play}, then {@code render} per output block.
 * No allocation after construction; not thread safe.
 */
@VanillaRef({"opl_sdl.c:OPL_Mix_Callback", "i_oplmusic.c"})
public final class OplMusicPlayer implements OplPort {

    /** Receives every register write that reaches the chip (for tests and tools); never used on the audio path. */
    public interface RegisterListener {
        void onWrite(long timeMicros, int reg, int value);
    }

    private static final long OPL_SECOND = 1_000_000L;
    private static final int KIND_RESTART = -1;
    private static final int MAX_TRACKS = 256;

    private final int sampleRate;
    private final Opl3 chip;
    private final OplMusicDriver driver;
    private final OplCallbackQueue queue = new OplCallbackQueue();
    private RegisterListener listener;

    private long currentTime;
    private long pauseOffset;
    private boolean paused;

    private MusicScore score;
    private final int[] trackNext = new int[MAX_TRACKS];
    private int numTracks;
    private int runningTracks;
    private boolean looping;
    private boolean playing;
    private long usPerBeat;
    private long ticksPerBeat;

    /**
     * Creates the music engine and initialises the chip registers like DOOM does at start-up.
     *
     * @param genMidi    instrument bank of the IWAD
     * @param sampleRate output rate in Hz (the chip is resampled to it)
     * @param opl3Mode   {@code false}: 9 voices, OPL2 behaviour as DMX (default); {@code true}: 18 voices and panning
     * @param version    DMX behaviour, {@link DmxVersion#DOOM_1_9} for vanilla
     */
    public OplMusicPlayer(GenMidi genMidi, int sampleRate, boolean opl3Mode, DmxVersion version) {
        this(genMidi, sampleRate, opl3Mode, version, null);
    }

    /**
     * Same as {@link #OplMusicPlayer(GenMidi, int, boolean, DmxVersion)} with a listener that also sees the
     * register initialisation writes made by the constructor.
     */
    public OplMusicPlayer(GenMidi genMidi, int sampleRate, boolean opl3Mode, DmxVersion version,
                          RegisterListener listener) {
        this.listener = listener;
        this.sampleRate = sampleRate;
        this.chip = new Opl3(sampleRate);
        this.driver = new OplMusicDriver(genMidi, this, opl3Mode, version);
    }

    /** Sets (or clears with {@code null}) the register-write listener. */
    public void setRegisterListener(RegisterListener listener) {
        this.listener = listener;
    }

    @Override
    public void writeRegister(int reg, int value) {
        // The timer registers are handled by the (absent) timer emulation in opl_sdl.c, not by the chip.
        if (reg == 0x02 || reg == 0x03 || reg == 0x04) {
            return;
        }
        if (listener != null) {
            listener.onWrite(currentTime, reg, value);
        }
        chip.writeRegBuffered(reg, value);
    }

    /** Output sample rate in Hz. */
    public int sampleRate() {
        return sampleRate;
    }

    /** Music volume 0..127 (the DMX scale; the game's 0..15 menu value times 8). */
    public void setMusicVolume(int volume127) {
        driver.setMusicVolume(volume127);
    }

    /** The driver, for inspection (voice counts) in tests and debug overlays. */
    public OplMusicDriver driver() {
        return driver;
    }

    /**
     * Starts a song ({@code I_OPL_PlaySong}); a song already playing is stopped first, and pause is cleared.
     *
     * @param looping restart automatically 5 ms after the end of the song
     */
    public void play(MusicScore newScore, boolean looping) {
        stop();
        this.score = newScore;
        this.looping = looping;
        this.numTracks = Math.min(newScore.trackCount(), MAX_TRACKS);
        this.runningTracks = numTracks;
        this.ticksPerBeat = newScore.division();
        this.usPerBeat = 500 * 1000;
        this.playing = true;
        driver.markSongStart();
        for (int t = 0; t < numTracks; t++) {
            trackNext[t] = newScore.firstOfTrack(t);
            scheduleTrack(t);
        }
        driver.initChannels();
        paused = false;
    }

    /** {@code I_OPL_StopSong}: cancels pending events and releases all voices. */
    public void stop() {
        queue.clear();
        driver.allNotesOffAllChannels();
        score = null;
        playing = false;
        runningTracks = 0;
    }

    /** {@code I_OPL_PauseSong}: freezes the sequencer and keys off the melodic voices (percussion rings out). */
    public void pause() {
        paused = true;
        driver.pauseVoices();
    }

    /** {@code I_OPL_ResumeSong}. */
    public void resume() {
        paused = false;
    }

    /** Whether the sequencer is paused. */
    public boolean isPaused() {
        return paused;
    }

    /** Whether a song was started and not stopped (a finished non-looping song still counts, like the original). */
    public boolean isPlaying() {
        return playing;
    }

    /** Whether a non-looping song has played its end event. */
    public boolean isFinished() {
        return playing && runningTracks <= 0 && !looping;
    }

    /** Elapsed sequencer clock in microseconds (advances only while rendering). */
    public long timeMicros() {
        return currentTime;
    }

    // ---------------------------------------------------------------- sequencer

    private void setCallback(long us, int kind) {
        queue.push(kind, currentTime - pauseOffset + us);
    }

    private void scheduleTrack(int track) {
        int next = trackNext[track];
        long nticks = next >= 0 ? (score.delta(next) & 0xFFFFFFFFL) : 0;
        long us = nticks * usPerBeat / ticksPerBeat;
        setCallback(us, track);
    }

    private void trackTimerCallback(int track) {
        if (score == null || trackNext[track] < 0) {
            return;
        }
        int i = trackNext[track];
        trackNext[track] = score.nextInTrack(i);
        int type = score.type(i);
        switch (type) {
            case MusicScore.TYPE_TEMPO: {
                int tempo = score.param1(i);
                queue.adjust(currentTime, (float) usPerBeat / (float) tempo);
                usPerBeat = tempo & 0xFFFFFFFFL;
                break;
            }
            case MusicScore.TYPE_END:
                --runningTracks;
                // When all tracks have finished, restart the song after 5 ms (never immediately, so that an empty
                // file cannot lock up the game).
                if (runningTracks <= 0 && looping) {
                    setCallback(5000, KIND_RESTART);
                }
                return;
            case MusicScore.TYPE_OTHER:
                break;
            default:
                driver.processEvent(type, score.channel(i), score.param1(i), score.param2(i));
                break;
        }
        scheduleTrack(track);
    }

    private void restartSong() {
        runningTracks = numTracks;
        driver.markSongStart();
        for (int t = 0; t < numTracks; t++) {
            trackNext[t] = score.firstOfTrack(t);
            scheduleTrack(t);
        }
        driver.initChannels();
    }

    private void advanceTime(long nsamples) {
        long us = nsamples * OPL_SECOND / sampleRate;
        currentTime += us;
        if (paused) {
            pauseOffset += us;
        }
        while (!queue.isEmpty() && Long.compareUnsigned(currentTime, queue.peek() + pauseOffset) >= 0) {
            if (!queue.pop()) {
                break;
            }
            if (queue.poppedKind() == KIND_RESTART) {
                restartSong();
            } else {
                trackTimerCallback(queue.poppedKind());
            }
        }
    }

    // ---------------------------------------------------------------- audio

    /**
     * Renders {@code frames} stereo frames of music (interleaved 16-bit, left first) into {@code out} starting at
     * {@code offset}, running the sequencer in between ({@code OPL_Mix_Callback}). The result replaces the contents of
     * the target range. Splitting a render into different block sizes slightly changes event timing (below one sample
     * per block), as in the original where it depends on the audio buffer size.
     */
    public void render(short[] out, int offset, int frames) {
        int filled = 0;
        while (filled < frames) {
            long nsamples;
            if (paused || queue.isEmpty()) {
                nsamples = frames - filled;
            } else {
                long nextCallbackTime = queue.peek() + pauseOffset;
                nsamples = (nextCallbackTime - currentTime) * sampleRate;
                nsamples = Long.divideUnsigned(nsamples + OPL_SECOND - 1, OPL_SECOND);
                if (Long.compareUnsigned(nsamples, frames - filled) > 0) {
                    nsamples = frames - filled;
                }
            }
            int n = (int) nsamples;
            chip.generateStream(out, offset + filled * 2, n);
            filled += n;
            advanceTime(n);
        }
    }
}
