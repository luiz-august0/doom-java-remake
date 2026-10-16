package doom.sound;

import doom.sound.mixer.SfxSample;
import doom.wad.audio.MusicScore;

/**
 * Silent {@link SoundBackend} for headless runs: no audio, but the channels keep the lifetime the real mixer would
 * give them (the length of the sample in game tics), so that channel allocation follows the same logic.
 */
public final class NullSoundBackend implements SoundBackend {

    private static final int TICRATE = 35;

    private final int[] endTic;
    private int tic;

    public NullSoundBackend(int channels) {
        endTic = new int[channels];
    }

    @Override
    public void startSfx(int channel, SfxSample sample, int vol, int sep, int pitch) {
        if (sample == null || sample.sampleRate() <= 0) {
            endTic[channel] = 0;
            return;
        }
        long tics = ((long) sample.length() * TICRATE + sample.sampleRate() - 1) / sample.sampleRate();
        endTic[channel] = tic + (int) Math.max(1, tics);
    }

    @Override
    public void updateChannel(int channel, int vol, int sep) {
    }

    @Override
    public void stopChannel(int channel) {
        endTic[channel] = 0;
    }

    @Override
    public boolean isPlaying(int channel) {
        return endTic[channel] > tic;
    }

    @Override
    public void stopAllSfx() {
        java.util.Arrays.fill(endTic, 0);
    }

    @Override
    public void playMusic(MusicScore score, boolean looping) {
    }

    @Override
    public void stopMusic() {
    }

    @Override
    public void pauseMusic() {
    }

    @Override
    public void resumeMusic() {
    }

    @Override
    public void setMusicVolume(int level) {
    }

    @Override
    public void update(int gameTic) {
        tic = gameTic;
    }
}
