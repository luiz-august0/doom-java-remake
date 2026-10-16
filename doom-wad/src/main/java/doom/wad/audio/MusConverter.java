package doom.wad.audio;

import doom.wad.WadFormatException;
import java.util.Arrays;

/**
 * MUS to MIDI conversion with the semantics of Chocolate Doom's {@code mus2mid.c}: the output is the same Standard
 * MIDI File (type 0, one track, division 70) byte for byte, so that 70 ticks per quarter note at the default tempo of
 * 500000 us gives the 140 Hz MUS clock.
 *
 * <p>Details that are reproduced on purpose: MUS channel 15 becomes MIDI channel 9; other channels get the next free MIDI
 * channel (skipping 9) in order of first use, preceded by an "all notes off" controller (the "D_DDTBLU disease" fix);
 * velocities are remembered per MIDI channel (initially 127); controller values with bit 7 set become 127; a
 * {@code scoreend} event still allocates its channel.
 *
 * <p>One difference: Chocolate keeps the per-channel velocities in a static array, so a song inherits the last velocities
 * of the previous song converted in the same process. Here every conversion starts from 127 (deterministic).
 */
public final class MusConverter {

    private static final int MUS_PERCUSSION_CHANNEL = 15;
    private static final int MIDI_PERCUSSION_CHANNEL = 9;
    private static final int NUM_CHANNELS = 16;

    private static final int MUS_RELEASE_KEY = 0x00;
    private static final int MUS_PRESS_KEY = 0x10;
    private static final int MUS_PITCH_WHEEL = 0x20;
    private static final int MUS_SYSTEM_EVENT = 0x30;
    private static final int MUS_CHANGE_CONTROLLER = 0x40;
    private static final int MUS_SCORE_END = 0x60;

    private static final byte[] MIDI_HEADER = {
        'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 0x46,
        'M', 'T', 'r', 'k', 0, 0, 0, 0
    };

    private static final int[] CONTROLLER_MAP = {
        0x00, 0x20, 0x01, 0x07, 0x0A, 0x0B, 0x5B, 0x5D, 0x40, 0x43, 0x78, 0x7B, 0x7E, 0x7F, 0x79
    };

    private final byte[] in;
    private int inPos;
    private byte[] out = new byte[4096];
    private int outLen;
    private int trackSize;
    private int queuedTime;
    private final int[] channelMap = new int[NUM_CHANNELS];
    private final int[] channelVelocities = new int[NUM_CHANNELS];

    private MusConverter(byte[] in) {
        this.in = in;
        Arrays.fill(channelMap, -1);
        Arrays.fill(channelVelocities, 127);
    }

    /**
     * Converts a MUS lump into a Standard MIDI File.
     *
     * @throws WadFormatException when the MUS data is truncated or has an invalid event (mus2mid returns failure)
     */
    public static byte[] toMidi(byte[] mus) {
        return new MusConverter(mus).convert();
    }

    private WadFormatException fail(String message) {
        return new WadFormatException(null, null, inPos, "MUS: " + message);
    }

    private int readByte() {
        if (inPos >= in.length) {
            throw fail("unexpected end of data");
        }
        return in[inPos++] & 0xFF;
    }

    private int readShort() {
        int lo = readByte();
        return lo | (readByte() << 8);
    }

    private void write(int b) {
        if (outLen == out.length) {
            out = Arrays.copyOf(out, out.length * 2);
        }
        out[outLen++] = (byte) b;
    }

    private void writeTime(int time) {
        int buffer = time & 0x7F;
        int t = time;
        while ((t >>>= 7) != 0) {
            buffer <<= 8;
            buffer |= (t & 0x7F) | 0x80;
        }
        for (;;) {
            write(buffer & 0xFF);
            trackSize++;
            if ((buffer & 0x80) != 0) {
                buffer >>>= 8;
            } else {
                queuedTime = 0;
                return;
            }
        }
    }

    private void writePressKey(int channel, int key, int velocity) {
        writeTime(queuedTime);
        write(0x90 | channel);
        write(key & 0x7F);
        write(velocity & 0x7F);
        trackSize += 3;
    }

    private void writeReleaseKey(int channel, int key) {
        writeTime(queuedTime);
        write(0x80 | channel);
        write(key & 0x7F);
        write(0);
        trackSize += 3;
    }

    private void writePitchWheel(int channel, int wheel) {
        writeTime(queuedTime);
        write(0xE0 | channel);
        write(wheel & 0x7F);
        write((wheel >> 7) & 0x7F);
        trackSize += 3;
    }

    private void writeChangePatch(int channel, int patch) {
        writeTime(queuedTime);
        write(0xC0 | channel);
        write(patch & 0x7F);
        trackSize += 2;
    }

    private void writeController(int channel, int control, int value) {
        writeTime(queuedTime);
        write(0xB0 | channel);
        write(control & 0x7F);
        write((value & 0x80) != 0 ? 0x7F : value);
        trackSize += 3;
    }

    private int allocateMidiChannel() {
        int max = -1;
        for (int i = 0; i < NUM_CHANNELS; i++) {
            if (channelMap[i] > max) {
                max = channelMap[i];
            }
        }
        int result = max + 1;
        if (result == MIDI_PERCUSSION_CHANNEL) {
            result++;
        }
        return result;
    }

    private int midiChannel(int musChannel) {
        if (musChannel == MUS_PERCUSSION_CHANNEL) {
            return MIDI_PERCUSSION_CHANNEL;
        }
        if (channelMap[musChannel] == -1) {
            channelMap[musChannel] = allocateMidiChannel();
            writeController(channelMap[musChannel], 0x7B, 0);
        }
        return channelMap[musChannel];
    }

    private byte[] convert() {
        // Header: id[4], scorelength, scorestart, primarychannels, secondarychannels, instrumentcount.
        if (in.length < 14) {
            throw fail("truncated header");
        }
        inPos = 4;
        readShort();
        int scoreStart = readShort();
        readShort();
        readShort();
        readShort();
        if (scoreStart > in.length) {
            throw fail("score start outside the lump");
        }
        inPos = scoreStart;

        for (byte b : MIDI_HEADER) {
            write(b);
        }
        trackSize = 0;

        boolean hitScoreEnd = false;
        while (!hitScoreEnd) {
            while (!hitScoreEnd) {
                int descriptor = readByte();
                int channel = midiChannel(descriptor & 0x0F);
                int event = descriptor & 0x70;
                switch (event) {
                    case MUS_RELEASE_KEY -> writeReleaseKey(channel, readByte());
                    case MUS_PRESS_KEY -> {
                        int key = readByte();
                        if ((key & 0x80) != 0) {
                            channelVelocities[channel] = readByte() & 0x7F;
                        }
                        writePressKey(channel, key, channelVelocities[channel]);
                    }
                    case MUS_PITCH_WHEEL -> writePitchWheel(channel, (short) (readByte() * 64));
                    case MUS_SYSTEM_EVENT -> {
                        int controller = readByte();
                        if (controller < 10 || controller > 14) {
                            throw fail("invalid system event " + controller);
                        }
                        writeController(channel, CONTROLLER_MAP[controller], 0);
                    }
                    case MUS_CHANGE_CONTROLLER -> {
                        int controller = readByte();
                        int value = readByte();
                        if (controller == 0) {
                            writeChangePatch(channel, value);
                        } else {
                            if (controller > 9) {
                                throw fail("invalid controller " + controller);
                            }
                            writeController(channel, CONTROLLER_MAP[controller], value);
                        }
                    }
                    case MUS_SCORE_END -> hitScoreEnd = true;
                    default -> throw fail("invalid event " + event);
                }
                if ((descriptor & 0x80) != 0) {
                    break;
                }
            }
            if (!hitScoreEnd) {
                int delay = 0;
                for (;;) {
                    int working = readByte();
                    delay = delay * 128 + (working & 0x7F);
                    if ((working & 0x80) == 0) {
                        break;
                    }
                }
                queuedTime += delay;
            }
        }

        // End of track.
        writeTime(queuedTime);
        write(0xFF);
        write(0x2F);
        write(0x00);
        trackSize += 3;

        out[18] = (byte) (trackSize >>> 24);
        out[19] = (byte) (trackSize >>> 16);
        out[20] = (byte) (trackSize >>> 8);
        out[21] = (byte) trackSize;
        return Arrays.copyOf(out, outLen);
    }
}
