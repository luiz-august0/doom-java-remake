package doom.sound.mixer;

import doom.wad.audio.MusicScore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free single-producer / single-consumer queue of audio commands (spec 02 §6). The producer is the main thread
 * (the sound system's channel logic); the consumer is the audio thread. Commands are pre-allocated mutable records in a
 * ring, so neither side allocates. All methods of the "producer" group must be called from one thread only.
 *
 * <p>Every producer method returns {@code false} when the ring is full and the command was dropped; with the default
 * 1024 slots that never happens in practice because the audio thread drains the ring every 256–512 frames.
 */
public final class AudioCommandQueue {

    /** Kinds of command. */
    static final int START_SFX = 1;
    static final int UPDATE_CHANNEL = 2;
    static final int STOP_CHANNEL = 3;
    static final int STOP_ALL_SFX = 4;
    static final int PLAY_MUSIC = 5;
    static final int STOP_MUSIC = 6;
    static final int PAUSE_MUSIC = 7;
    static final int RESUME_MUSIC = 8;
    static final int SET_VOLUMES = 9;

    /** Mutable command record. */
    static final class Command {
        int kind;
        int channel;
        SfxSample sample;
        int vol;
        int sep;
        int pitch;
        MusicScore score;
        boolean looping;
        int sfxVolume;
        int musicVolume;
    }

    private static final int DEFAULT_CAPACITY = 1024;

    private final Command[] ring;
    private final int mask;
    private final AtomicLong head = new AtomicLong();
    private final AtomicLong tail = new AtomicLong();
    private long producerIndex;
    private long consumerIndex;

    /** Creates a queue with the default capacity of 1024 commands. */
    public AudioCommandQueue() {
        this(DEFAULT_CAPACITY);
    }

    /** Creates a queue; {@code capacity} must be a power of two. */
    public AudioCommandQueue(int capacity) {
        if (Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two: " + capacity);
        }
        ring = new Command[capacity];
        for (int i = 0; i < capacity; i++) {
            ring[i] = new Command();
        }
        mask = capacity - 1;
    }

    // ---------------------------------------------------------------- producer (main thread)

    private Command next() {
        if (producerIndex - head.getAcquire() >= ring.length) {
            return null;
        }
        return ring[(int) (producerIndex & mask)];
    }

    private void publish() {
        producerIndex++;
        tail.setRelease(producerIndex);
    }

    /**
     * Starts a sound on a channel, replacing whatever the channel was playing.
     *
     * @param channel channel number, 0 until the engine's channel count
     * @param sample  the sound
     * @param vol     loudness 0..127 as in {@code I_StartSound}, with the SFX volume already folded in (see
     *                {@link AudioEngine})
     * @param sep     stereo separation 0..254 (0 = full left, 128 = centre, 254 = full right); 255 is clamped
     * @param pitch   pitch 0..255 with 128 normal; only values other than 128 change the playback speed
     */
    public boolean startSfx(int channel, SfxSample sample, int vol, int sep, int pitch) {
        Command c = next();
        if (c == null) {
            return false;
        }
        c.kind = START_SFX;
        c.channel = channel;
        c.sample = sample;
        c.vol = vol;
        c.sep = sep;
        c.pitch = pitch;
        publish();
        return true;
    }

    /** Changes volume and separation of the sound playing on a channel ({@code I_UpdateSoundParams}). */
    public boolean updateChannel(int channel, int vol, int sep) {
        Command c = next();
        if (c == null) {
            return false;
        }
        c.kind = UPDATE_CHANNEL;
        c.channel = channel;
        c.vol = vol;
        c.sep = sep;
        publish();
        return true;
    }

    /** Stops the sound on a channel. */
    public boolean stopChannel(int channel) {
        Command c = next();
        if (c == null) {
            return false;
        }
        c.kind = STOP_CHANNEL;
        c.channel = channel;
        publish();
        return true;
    }

    /** Stops every sound effect. */
    public boolean stopAllSfx() {
        Command c = next();
        if (c == null) {
            return false;
        }
        c.kind = STOP_ALL_SFX;
        publish();
        return true;
    }

    /** Starts a song, stopping the one playing; resumes from pause. */
    public boolean playMusic(MusicScore score, boolean looping) {
        Command c = next();
        if (c == null) {
            return false;
        }
        c.kind = PLAY_MUSIC;
        c.score = score;
        c.looping = looping;
        publish();
        return true;
    }

    /** Stops the song and releases its notes. */
    public boolean stopMusic() {
        return simple(STOP_MUSIC);
    }

    /** Pauses the song ({@code I_PauseSong}). */
    public boolean pauseMusic() {
        return simple(PAUSE_MUSIC);
    }

    /** Resumes a paused song. */
    public boolean resumeMusic() {
        return simple(RESUME_MUSIC);
    }

    /**
     * Sets the volumes with the menu scale 0..15. The music level goes to the OPL driver as {@code level * 8}
     * (0..120), exactly like {@code S_SetMusicVolume}. The SFX level is a master gain {@code level / 15} on top of the
     * per-sound loudness; leave it at 15 when the per-sound {@code vol} already includes the SFX volume.
     */
    public boolean setVolumes(int sfxVolume, int musicVolume) {
        Command c = next();
        if (c == null) {
            return false;
        }
        c.kind = SET_VOLUMES;
        c.sfxVolume = sfxVolume;
        c.musicVolume = musicVolume;
        publish();
        return true;
    }

    private boolean simple(int kind) {
        Command c = next();
        if (c == null) {
            return false;
        }
        c.kind = kind;
        publish();
        return true;
    }

    // ---------------------------------------------------------------- consumer (audio thread)

    /** Returns the next command to execute, or {@code null}. Release it with {@link #consumed()}. */
    Command poll() {
        if (consumerIndex >= tail.getAcquire()) {
            return null;
        }
        return ring[(int) (consumerIndex & mask)];
    }

    /** Marks the command returned by {@link #poll()} as executed. */
    void consumed() {
        Command c = ring[(int) (consumerIndex & mask)];
        c.sample = null;
        c.score = null;
        consumerIndex++;
        head.setRelease(consumerIndex);
    }

    /** Number of commands waiting (approximate when called from another thread). */
    public int pending() {
        return (int) (tail.get() - head.get());
    }
}
