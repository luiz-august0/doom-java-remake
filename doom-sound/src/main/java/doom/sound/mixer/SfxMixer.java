package doom.sound.mixer;

/**
 * The sound-effect voices: up to 32 channels, each playing one {@link SfxSample} with a 32.32 fixed-point read position,
 * resampled to the output rate (nearest or linear), panned with the vanilla separation law and summed in {@code int}.
 *
 * <p>Pan law (Chocolate Doom {@code I_SDL_UpdateSoundParams}): {@code left = ((254 - sep) * vol) / 127} and
 * {@code right = (sep * vol) / 127}, clamped to 0..255, each ear scaled by {@code level / 255}. A centred sound
 * therefore sits about 6 dB below a hard-panned one, as in the DOS game.
 *
 * <p>Zero allocation; used by one thread (the audio thread) only.
 */
final class SfxMixer {

    static final int MAX_CHANNELS = 32;

    private final int outputRate;
    private final boolean linear;
    private final int channels;

    private final SfxSample[] sample = new SfxSample[MAX_CHANNELS];
    private final long[] position = new long[MAX_CHANNELS];
    private final long[] step = new long[MAX_CHANNELS];
    private final int[] gainLeft = new int[MAX_CHANNELS];
    private final int[] gainRight = new int[MAX_CHANNELS];
    private final int[] vol = new int[MAX_CHANNELS];
    private final int[] sep = new int[MAX_CHANNELS];

    private int masterSfx = 15;

    SfxMixer(int outputRate, int channels, boolean linear) {
        if (channels < 1 || channels > MAX_CHANNELS) {
            throw new IllegalArgumentException("channels must be 1.." + MAX_CHANNELS + ": " + channels);
        }
        this.outputRate = outputRate;
        this.channels = channels;
        this.linear = linear;
    }

    int channelCount() {
        return channels;
    }

    boolean isPlaying(int channel) {
        return channel >= 0 && channel < channels && sample[channel] != null;
    }

    void setMasterVolume(int level0to15) {
        masterSfx = Math.max(0, Math.min(15, level0to15));
        for (int c = 0; c < channels; c++) {
            if (sample[c] != null) {
                computeGains(c);
            }
        }
    }

    void start(int channel, SfxSample s, int volume, int separation, int pitch) {
        if (channel < 0 || channel >= channels || s == null || s.length() == 0) {
            return;
        }
        sample[channel] = s;
        position[channel] = 0;
        long base = ((long) s.sampleRate() << 32) / outputRate;
        // Vanilla pitch (when enabled) stretches the sound by 2 - pitch/128; speed is its inverse, 128 / (256 - pitch).
        if (pitch != 128 && pitch >= 0 && pitch < 256) {
            base = Math.min(base * 128 / (256 - pitch), base * 4);
        }
        step[channel] = base;
        vol[channel] = volume;
        sep[channel] = separation;
        computeGains(channel);
    }

    void update(int channel, int volume, int separation) {
        if (isPlaying(channel)) {
            vol[channel] = volume;
            sep[channel] = separation;
            computeGains(channel);
        }
    }

    void stop(int channel) {
        if (channel >= 0 && channel < channels) {
            sample[channel] = null;
        }
    }

    void stopAll() {
        for (int c = 0; c < channels; c++) {
            sample[c] = null;
        }
    }

    private void computeGains(int c) {
        int v = Math.max(0, vol[c]);
        int s = Math.max(0, Math.min(255, sep[c]));
        int left = ((254 - s) * v) / 127;
        int right = (s * v) / 127;
        left = Math.max(0, Math.min(255, left));
        right = Math.max(0, Math.min(255, right));
        gainLeft[c] = left * masterSfx / 15;
        gainRight[c] = right * masterSfx / 15;
    }

    /**
     * Adds {@code frames} stereo frames of every playing channel into {@code acc} (interleaved, caller zeroes it).
     * Finished channels are freed.
     */
    void mixInto(int[] acc, int frames) {
        for (int c = 0; c < channels; c++) {
            SfxSample s = sample[c];
            if (s == null) {
                continue;
            }
            short[] pcm = s.data();
            int length = pcm.length;
            long pos = position[c];
            long st = step[c];
            int gl = gainLeft[c];
            int gr = gainRight[c];
            int i = 0;
            int o = 0;
            if (linear) {
                while (i < frames) {
                    int idx = (int) (pos >>> 32);
                    if (idx >= length) {
                        break;
                    }
                    int s0 = pcm[idx];
                    int s1 = idx + 1 < length ? pcm[idx + 1] : s0;
                    int frac = (int) ((pos >>> 16) & 0xFFFF);
                    int v = s0 + (((s1 - s0) * frac) >> 16);
                    acc[o] += (v * gl) >> 8;
                    acc[o + 1] += (v * gr) >> 8;
                    o += 2;
                    i++;
                    pos += st;
                }
            } else {
                while (i < frames) {
                    int idx = (int) (pos >>> 32);
                    if (idx >= length) {
                        break;
                    }
                    int v = pcm[idx];
                    acc[o] += (v * gl) >> 8;
                    acc[o + 1] += (v * gr) >> 8;
                    o += 2;
                    i++;
                    pos += st;
                }
            }
            if ((int) (pos >>> 32) >= length) {
                sample[c] = null;
            } else {
                position[c] = pos;
            }
        }
    }
}
