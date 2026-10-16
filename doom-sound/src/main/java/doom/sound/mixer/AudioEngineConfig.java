package doom.sound.mixer;

import doom.sound.music.DmxVersion;

/**
 * Settings of the {@link AudioEngine}.
 *
 * @param sampleRate        output rate in Hz (48000 default, 44100 optional)
 * @param sfxChannels       number of sound-effect channels, 1..32 (8 as in vanilla by default)
 * @param blockFrames       frames mixed per audio-thread iteration, 256..512 recommended
 * @param linearInterpolation {@code true}: linear resampling (default); {@code false}: nearest, closest to the raw DMX sound
 * @param opl3Mode          {@code false}: 9 OPL voices as DMX used in DOOM (default); {@code true}: 18 voices and stereo pan
 * @param dmxVersion        which DMX voice-allocation behaviour to emulate
 * @param deviceName        output device for {@link AudioEngine#start(doom.platform.AudioSink)}, {@code null} for the default
 * @param deviceBufferFrames device buffer size in frames, 0 for the default of two blocks (about 21 ms at 512 frames, see docs/notes/audio-latency.md)
 */
public record AudioEngineConfig(int sampleRate, int sfxChannels, int blockFrames, boolean linearInterpolation,
                                boolean opl3Mode, DmxVersion dmxVersion, String deviceName, int deviceBufferFrames) {

    /** 48 kHz, 8 channels, 512-frame blocks, linear resampling, OPL2 behaviour, DMX 1.9, default device. */
    public static AudioEngineConfig defaults() {
        return new AudioEngineConfig(48000, 8, 512, true, false, DmxVersion.DOOM_1_9, null, 0);
    }

    /** Copy with another sample rate. */
    public AudioEngineConfig withSampleRate(int rate) {
        return new AudioEngineConfig(rate, sfxChannels, blockFrames, linearInterpolation, opl3Mode, dmxVersion,
                deviceName, deviceBufferFrames);
    }

    /** Copy with another channel count. */
    public AudioEngineConfig withSfxChannels(int channels) {
        return new AudioEngineConfig(sampleRate, channels, blockFrames, linearInterpolation, opl3Mode, dmxVersion,
                deviceName, deviceBufferFrames);
    }

    /** Copy with another resampling mode. */
    public AudioEngineConfig withLinearInterpolation(boolean linear) {
        return new AudioEngineConfig(sampleRate, sfxChannels, blockFrames, linear, opl3Mode, dmxVersion, deviceName,
                deviceBufferFrames);
    }

    /** Copy with OPL3 (18 voices) switched on or off. */
    public AudioEngineConfig withOpl3Mode(boolean opl3) {
        return new AudioEngineConfig(sampleRate, sfxChannels, blockFrames, linearInterpolation, opl3, dmxVersion,
                deviceName, deviceBufferFrames);
    }

    /** Copy with another output device and device buffer. */
    public AudioEngineConfig withDevice(String device, int bufferFrames) {
        return new AudioEngineConfig(sampleRate, sfxChannels, blockFrames, linearInterpolation, opl3Mode, dmxVersion,
                device, bufferFrames);
    }

    /** Copy with another block size. */
    public AudioEngineConfig withBlockFrames(int frames) {
        return new AudioEngineConfig(sampleRate, sfxChannels, frames, linearInterpolation, opl3Mode, dmxVersion,
                deviceName, deviceBufferFrames);
    }
}
