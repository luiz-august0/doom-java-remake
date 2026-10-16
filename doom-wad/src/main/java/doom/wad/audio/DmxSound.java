package doom.wad.audio;

import doom.wad.WadFormatException;
import java.nio.ByteBuffer;

/**
 * A DMX sound effect lump ({@code DS*}, format 3): {@code uint16 format (=3)}, {@code uint16 rate},
 * {@code uint32 count}, then {@code count} unsigned 8-bit samples. The first and last 16 bytes of the sample area are
 * padding that DMX never plays; like Chocolate Doom (i_sdlsound.c {@code CacheSFX}) they are dropped, and lumps with
 * {@code count <= 48} or a count larger than the lump are rejected.
 */
public final class DmxSound {

    /** Padding bytes dropped at each end of the sample area. */
    public static final int PADDING = 16;

    private final int sampleRate;
    private final byte[] samples;

    private DmxSound(int sampleRate, byte[] samples) {
        this.sampleRate = sampleRate;
        this.samples = samples;
    }

    /**
     * Parses a DMX sound lump.
     *
     * @return the sound, or {@code null} when the lump is not a valid DMX sound (Chocolate silently ignores those)
     */
    public static DmxSound tryParse(ByteBuffer lump) {
        int lumpLength = lump.remaining();
        int base = lump.position();
        if (lumpLength < 8 || (lump.get(base) & 0xFF) != AudioFormat.DMX_FORMAT || lump.get(base + 1) != 0) {
            return null;
        }
        int rate = (lump.get(base + 2) & 0xFF) | ((lump.get(base + 3) & 0xFF) << 8);
        long length = (lump.get(base + 4) & 0xFFL) | ((lump.get(base + 5) & 0xFFL) << 8)
                | ((lump.get(base + 6) & 0xFFL) << 16) | ((lump.get(base + 7) & 0xFFL) << 24);
        if (length > lumpLength - 8L || length <= 48 || rate == 0) {
            return null;
        }
        int count = (int) length - 2 * PADDING;
        byte[] data = new byte[count];
        lump.get(base + 8 + PADDING, data, 0, count);
        return new DmxSound(rate, data);
    }

    /** Same as {@link #tryParse(ByteBuffer)} for a byte array. */
    public static DmxSound tryParse(byte[] lump) {
        return tryParse(ByteBuffer.wrap(lump));
    }

    /**
     * Parses a DMX sound lump, throwing {@link WadFormatException} (naming {@code lumpName}) when it is invalid.
     */
    public static DmxSound parse(ByteBuffer lump, String lumpName) {
        DmxSound sound = tryParse(lump);
        if (sound == null) {
            throw new WadFormatException(null, lumpName, 0, "not a valid DMX sound lump (format 3, more than 48 samples)");
        }
        return sound;
    }

    /** Sample rate in Hz (normally 11025, sometimes 22050). */
    public int sampleRate() {
        return sampleRate;
    }

    /** Number of samples after padding removal. */
    public int sampleCount() {
        return samples.length;
    }

    /** Unsigned 8-bit sample {@code i}, 0..255 (128 is silence). */
    public int sample(int i) {
        return samples[i] & 0xFF;
    }

    /** Converts to signed 16-bit mono PCM: {@code (u8 - 128) << 8}, as SDL's AUDIO_U8 to AUDIO_S16 conversion. */
    public short[] toSigned16() {
        short[] out = new short[samples.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) (((samples[i] & 0xFF) - 128) << 8);
        }
        return out;
    }

    /** Duration of the sound in seconds. */
    public double durationSeconds() {
        return (double) samples.length / sampleRate;
    }
}
