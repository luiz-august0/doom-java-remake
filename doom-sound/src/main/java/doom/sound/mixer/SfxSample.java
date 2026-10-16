package doom.sound.mixer;

import doom.wad.audio.DmxSound;

/**
 * A sound effect ready for the mixer: signed 16-bit mono PCM at its original sample rate (usually 11025 Hz).
 * Immutable; build it once at load time (spec 05 §5) and share it freely between threads.
 */
public final class SfxSample {

    private final short[] pcm;
    private final int sampleRate;

    private SfxSample(short[] pcm, int sampleRate) {
        this.pcm = pcm;
        this.sampleRate = sampleRate;
    }

    /** Converts a DMX sound (unsigned 8-bit, padding already removed) to signed 16-bit. */
    public static SfxSample of(DmxSound sound) {
        return new SfxSample(sound.toSigned16(), sound.sampleRate());
    }

    /** Wraps 16-bit PCM (tests, generated sounds); the array is copied. */
    public static SfxSample ofPcm(short[] pcm, int sampleRate) {
        if (sampleRate <= 0 || sampleRate > 0xFFFF) {
            throw new IllegalArgumentException("sample rate out of range: " + sampleRate);
        }
        return new SfxSample(pcm.clone(), sampleRate);
    }

    /** Sample rate of the data in Hz. */
    public int sampleRate() {
        return sampleRate;
    }

    /** Number of samples. */
    public int length() {
        return pcm.length;
    }

    /** Sample {@code i}. */
    public short sample(int i) {
        return pcm[i];
    }

    /** Duration in seconds at normal pitch. */
    public double durationSeconds() {
        return (double) pcm.length / sampleRate;
    }

    short[] data() {
        return pcm;
    }
}
