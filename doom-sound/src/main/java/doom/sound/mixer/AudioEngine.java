package doom.sound.mixer;

import doom.platform.AudioConfig;
import doom.platform.AudioSink;
import doom.sound.music.OplMusicPlayer;
import doom.wad.audio.GenMidi;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * The audio half of the game (spec 06 §2–3): sound-effect mixer, OPL3 music and the dedicated audio thread, behind one
 * small API.
 *
 * <pre>
 *   main thread                                 audio thread (MAX_PRIORITY)
 *   engine.commands().startSfx(...)  --queue--&gt; drain commands
 *                                               mix sfx voices + OPL music -&gt; soft limiter -&gt; AudioSink.write (blocks)
 * </pre>
 *
 * <p>Lifecycle: construct, then either {@link #start(AudioSink)} (real device, own thread) or call
 * {@link #renderOffline} repeatedly (tests, headless runs, WAV export); {@link #stop()} / {@link #close()} at the end.
 * Commands are applied at block boundaries (every {@code blockFrames} frames, 512 = 10.7 ms at 48 kHz).
 *
 * <p><b>Volumes.</b> {@link AudioCommandQueue#startSfx} takes the vanilla {@code vol} (0..127, already including the
 * SFX volume setting, as {@code S_StartSound} computes it) and the vanilla separation 0..254. The SFX level of
 * {@link AudioCommandQueue#setVolumes} is an additional master gain; keep it at 15 if {@code vol} already contains the
 * SFX volume. The music level 0..15 reaches the OPL driver as {@code level * 8}.
 *
 * <p>Zero allocation per block; all buffers are allocated in the constructor.
 */
public final class AudioEngine implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(AudioEngine.class.getName());

    private final AudioEngineConfig config;
    private final AudioCommandQueue queue;
    private final SfxMixer sfx;
    private final OplMusicPlayer music;
    private final int blockFrames;

    private final int[] acc;
    private final short[] musicBuffer;
    private final short[] outBuffer;
    private final AtomicIntegerArray channelPlaying;

    private volatile boolean musicPlaying;
    private volatile boolean musicPaused;
    private volatile long framesRendered;
    private volatile boolean running;
    private volatile Throwable failure;
    private Thread thread;
    private AudioSink sink;

    /**
     * Creates the engine.
     *
     * @param config  settings, see {@link AudioEngineConfig#defaults()}
     * @param genMidi instrument bank for the music, or {@code null} for an engine without music
     */
    public AudioEngine(AudioEngineConfig config, GenMidi genMidi) {
        if (config.blockFrames() < 16 || config.blockFrames() > 8192) {
            throw new IllegalArgumentException("blockFrames out of range: " + config.blockFrames());
        }
        this.config = config;
        this.blockFrames = config.blockFrames();
        this.queue = new AudioCommandQueue();
        this.sfx = new SfxMixer(config.sampleRate(), config.sfxChannels(), config.linearInterpolation());
        this.music = genMidi == null ? null
                : new OplMusicPlayer(genMidi, config.sampleRate(), config.opl3Mode(), config.dmxVersion());
        if (music != null) {
            music.setMusicVolume(8 * 8); // the vanilla default music volume (menu level 8)
        }
        this.acc = new int[blockFrames * 2];
        this.musicBuffer = new short[blockFrames * 2];
        this.outBuffer = new short[blockFrames * 2];
        this.channelPlaying = new AtomicIntegerArray(SfxMixer.MAX_CHANNELS);
    }

    // ---------------------------------------------------------------- API for the main thread

    /** The command queue; its producer methods must be called from one thread (the main thread). */
    public AudioCommandQueue commands() {
        return queue;
    }

    /** Output sample rate in Hz. */
    public int sampleRate() {
        return config.sampleRate();
    }

    /** Number of sound-effect channels. */
    public int channelCount() {
        return config.sfxChannels();
    }

    /** Whether the sound started on {@code channel} is still audible (updated once per block). */
    public boolean isChannelPlaying(int channel) {
        return channel >= 0 && channel < SfxMixer.MAX_CHANNELS && channelPlaying.get(channel) != 0;
    }

    /** Whether a song is playing (not stopped, not finished); a looping song always is. */
    public boolean isMusicPlaying() {
        return musicPlaying;
    }

    /** Whether the song is paused. */
    public boolean isMusicPaused() {
        return musicPaused;
    }

    /** Frames mixed so far. */
    public long framesRendered() {
        return framesRendered;
    }

    /** Output buffer underruns reported by the sink (0 while offline). */
    public long underruns() {
        AudioSink s = sink;
        return s == null ? 0 : s.underruns();
    }

    /** The error that stopped the audio thread, or {@code null}. */
    public Throwable failure() {
        return failure;
    }

    // ---------------------------------------------------------------- threaded operation

    /**
     * Opens the sink with the configured rate, device and buffer and starts the audio thread.
     *
     * @throws IllegalStateException if already started or the device does not run at the configured rate
     */
    public synchronized void start(AudioSink audioSink) {
        if (running) {
            throw new IllegalStateException("already started");
        }
        int buffer = config.deviceBufferFrames() > 0 ? config.deviceBufferFrames() : blockFrames * 2;
        audioSink.open(new AudioConfig(config.sampleRate(), 2, buffer, config.deviceName()));
        if (audioSink.sampleRate() != config.sampleRate()) {
            int actual = audioSink.sampleRate();
            audioSink.close();
            throw new IllegalStateException("audio device runs at " + actual + " Hz, engine configured for "
                    + config.sampleRate() + " Hz");
        }
        sink = audioSink;
        running = true;
        thread = new Thread(this::run, "doom-audio");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            while (running) {
                mixBlock(outBuffer, 0, blockFrames);
                sink.write(outBuffer, blockFrames);
            }
        } catch (Throwable t) {
            failure = t;
            running = false;
            LOG.log(System.Logger.Level.ERROR, "audio thread failed", t);
        }
    }

    /** Stops the audio thread and closes the sink. Safe to call twice. */
    public synchronized void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        AudioSink s = sink;
        sink = null;
        if (s != null) {
            s.close();
        }
    }

    @Override
    public void close() {
        stop();
    }

    // ---------------------------------------------------------------- offline operation

    /**
     * Mixes {@code frames} frames synchronously on the calling thread into {@code dst} (interleaved stereo, 16-bit),
     * applying pending commands at every block boundary. Not usable while the audio thread runs.
     *
     * @param dstFrameOffset first frame of {@code dst} to write
     */
    public void renderOffline(short[] dst, int dstFrameOffset, int frames) {
        if (running) {
            throw new IllegalStateException("engine is running on its own thread");
        }
        int done = 0;
        while (done < frames) {
            int n = Math.min(blockFrames, frames - done);
            mixBlock(outBuffer, 0, n);
            System.arraycopy(outBuffer, 0, dst, (dstFrameOffset + done) * 2, n * 2);
            done += n;
        }
    }

    // ---------------------------------------------------------------- mixing

    private void drainCommands() {
        AudioCommandQueue.Command c;
        while ((c = queue.poll()) != null) {
            switch (c.kind) {
                case AudioCommandQueue.START_SFX:
                    sfx.start(c.channel, c.sample, c.vol, c.sep, c.pitch);
                    break;
                case AudioCommandQueue.UPDATE_CHANNEL:
                    sfx.update(c.channel, c.vol, c.sep);
                    break;
                case AudioCommandQueue.STOP_CHANNEL:
                    sfx.stop(c.channel);
                    break;
                case AudioCommandQueue.STOP_ALL_SFX:
                    sfx.stopAll();
                    break;
                case AudioCommandQueue.PLAY_MUSIC:
                    if (music != null && c.score != null) {
                        music.play(c.score, c.looping);
                    }
                    break;
                case AudioCommandQueue.STOP_MUSIC:
                    if (music != null) {
                        music.stop();
                    }
                    break;
                case AudioCommandQueue.PAUSE_MUSIC:
                    if (music != null) {
                        music.pause();
                    }
                    break;
                case AudioCommandQueue.RESUME_MUSIC:
                    if (music != null) {
                        music.resume();
                    }
                    break;
                case AudioCommandQueue.SET_VOLUMES:
                    sfx.setMasterVolume(c.sfxVolume);
                    if (music != null) {
                        music.setMusicVolume(Math.max(0, Math.min(15, c.musicVolume)) * 8);
                    }
                    break;
                default:
                    break;
            }
            queue.consumed();
        }
    }

    private void mixBlock(short[] out, int outOffset, int frames) {
        drainCommands();

        int samples = frames * 2;
        java.util.Arrays.fill(acc, 0, samples, 0);
        sfx.mixInto(acc, frames);

        if (music != null) {
            music.render(musicBuffer, 0, frames);
            for (int i = 0; i < samples; i++) {
                acc[i] += musicBuffer[i];
            }
            musicPlaying = music.isPlaying() && !music.isFinished();
            musicPaused = music.isPaused();
        }

        for (int i = 0; i < samples; i++) {
            out[outOffset + i] = SoftLimiter.apply(acc[i]);
        }

        for (int c = 0; c < sfx.channelCount(); c++) {
            channelPlaying.set(c, sfx.isPlaying(c) ? 1 : 0);
        }
        framesRendered += frames;
    }
}
