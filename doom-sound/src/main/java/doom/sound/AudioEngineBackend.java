package doom.sound;

import doom.sound.mixer.AudioCommandQueue;
import doom.sound.mixer.AudioEngine;
import doom.sound.mixer.SfxSample;
import doom.wad.audio.MusicScore;

/**
 * {@link SoundBackend} over the {@link AudioEngine}: every call becomes a command for the audio thread.
 *
 * <p>The engine reports "channel playing" once per mixed block, so a sound started just now is not visible yet. To keep
 * {@link SoundSystem} from freeing a channel whose command has not been executed, a channel counts as playing until the
 * engine has mixed two blocks after the start command was posted.
 */
public final class AudioEngineBackend implements SoundBackend {

    private final AudioEngine engine;
    private final AudioCommandQueue queue;
    private final long graceFrames;
    /** {@code framesRendered()} when the last start command was posted on the channel; -1 = none. */
    private final long[] startedAt;

    /**
     * @param engine the audio engine (started with a sink, or driven offline)
     * @param blockFrames the engine's block size, to size the start-up grace period
     */
    public AudioEngineBackend(AudioEngine engine, int blockFrames) {
        this.engine = engine;
        this.queue = engine.commands();
        this.graceFrames = 2L * blockFrames;
        this.startedAt = new long[engine.channelCount()];
        java.util.Arrays.fill(startedAt, -1L);
    }

    @Override
    public void startSfx(int channel, SfxSample sample, int vol, int sep, int pitch) {
        if (sample == null) {
            startedAt[channel] = -1L;
            queue.stopChannel(channel);
            return;
        }
        if (queue.startSfx(channel, sample, vol, sep, pitch)) {
            startedAt[channel] = engine.framesRendered();
        } else {
            startedAt[channel] = -1L; // queue full: the sound is dropped
        }
    }

    @Override
    public void updateChannel(int channel, int vol, int sep) {
        queue.updateChannel(channel, vol, sep);
    }

    @Override
    public void stopChannel(int channel) {
        startedAt[channel] = -1L;
        queue.stopChannel(channel);
    }

    @Override
    public boolean isPlaying(int channel) {
        if (engine.isChannelPlaying(channel)) {
            return true;
        }
        long at = startedAt[channel];
        return at >= 0 && engine.framesRendered() < at + graceFrames;
    }

    @Override
    public void stopAllSfx() {
        java.util.Arrays.fill(startedAt, -1L);
        queue.stopAllSfx();
    }

    @Override
    public void playMusic(MusicScore score, boolean looping) {
        queue.playMusic(score, looping);
    }

    @Override
    public void stopMusic() {
        queue.stopMusic();
    }

    @Override
    public void pauseMusic() {
        queue.pauseMusic();
    }

    @Override
    public void resumeMusic() {
        queue.resumeMusic();
    }

    @Override
    public void setMusicVolume(int level) {
        // The per-sound volume already contains the SFX volume, so the engine's SFX master gain stays at 15.
        queue.setVolumes(15, level);
    }
}
