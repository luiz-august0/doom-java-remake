package doom.platform;

/**
 * Audio output settings (ADR-012): PCM 16-bit signed, interleaved.
 *
 * @param sampleRate   frames per second (48000 by default, 44100 optional)
 * @param channels     2 (stereo)
 * @param bufferFrames device buffer size in frames (latency target: a few blocks of 256–512 frames)
 * @param deviceName   device name, or {@code null} for the system default
 */
public record AudioConfig(int sampleRate, int channels, int bufferFrames, String deviceName) {
}
