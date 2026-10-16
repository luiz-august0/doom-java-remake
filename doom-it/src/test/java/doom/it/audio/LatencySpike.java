package doom.it.audio;

import doom.platform.javasound.JavaSoundAudioSink;
import doom.sound.mixer.AudioEngine;
import doom.sound.mixer.AudioEngineConfig;
import doom.sound.mixer.SfxSample;
import doom.wad.LumpDirectory;
import doom.wad.audio.GenMidi;
import doom.wad.audio.MusicScore;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Manual latency spike (docs/notes/audio-latency.md). Not a test: run its {@code main} with the module output
 * directories on the class path and optionally an IWAD path ({@code args[0]}) for real music load.
 *
 * <p>For every combination of device buffer and block size it plays music (looping) through the real Java Sound line,
 * starts a sound effect 40 times at random moments and measures the time from {@code startSfx} until the line's frame
 * position reaches the first frame that contains the sound. It also reports the steady-state fill of the line buffer
 * and the underruns. The figure excludes the operating system's own output latency (CoreAudio IO buffer, driver),
 * which Java Sound cannot report.
 */
public final class LatencySpike {

    private LatencySpike() {
    }

    public static void main(String[] args) throws Exception {
        GenMidi genMidi = null;
        MusicScore score = null;
        if (args.length > 0) {
            LumpDirectory lumps = LumpDirectory.open(List.of(Path.of(args[0])));
            genMidi = GenMidi.parse(lumps.data(lumps.get("GENMIDI")));
            score = MusicScore.fromLump(lumps.data(lumps.get(args.length > 1 ? args[1] : "D_E1M1")), "music");
        }
        short[] click = new short[800];
        for (int i = 0; i < click.length; i++) {
            click[i] = (short) (i % 40 < 20 ? 6000 : -6000);
        }
        SfxSample sample = SfxSample.ofPcm(click, 11025);

        System.out.println("device buffer | block | line buffer | queued (avg/min) | event->played ms (median/p90/max) | underruns");
        int[] buffers = {512, 1024, 2048, 4096};
        int[] blocks = {256, 512};
        for (int block : blocks) {
            for (int buffer : buffers) {
                AudioEngineConfig cfg = AudioEngineConfig.defaults().withBlockFrames(block).withDevice(null, buffer);
                AudioEngine engine = new AudioEngine(cfg, genMidi);
                JavaSoundAudioSink sink = new JavaSoundAudioSink();
                engine.start(sink);
                if (score != null) {
                    engine.commands().setVolumes(15, 3);
                    engine.commands().playMusic(score, true);
                }
                Thread.sleep(1000);

                long queuedSum = 0;
                int queuedMin = Integer.MAX_VALUE;
                int samples = 0;
                double[] ms = new double[40];
                int trial = 0;
                java.util.Random rnd = new java.util.Random(buffer * 31L + block);
                while (trial < ms.length) {
                    Thread.sleep(80 + rnd.nextInt(60));
                    int q = sink.queuedFrames();
                    queuedSum += q;
                    queuedMin = Math.min(queuedMin, q);
                    samples++;

                    long t0 = System.nanoTime();
                    // quiet click: volume 20 of 127
                    engine.commands().startSfx(trial % 8, sample, 20, 128, 128);
                    int ch = trial % 8;
                    // wait until the audio thread has mixed the block that contains the click
                    while (!engine.isChannelPlaying(ch) && System.nanoTime() - t0 < 500_000_000L) {
                        Thread.onSpinWait();
                    }
                    long blockEnd = engine.framesRendered();
                    long firstFrame = blockEnd - block;
                    while (sink.framePosition() < firstFrame && System.nanoTime() - t0 < 1_000_000_000L) {
                        Thread.onSpinWait();
                    }
                    ms[trial++] = (System.nanoTime() - t0) / 1e6;
                    engine.commands().stopChannel(ch);
                }
                Arrays.sort(ms);
                System.out.printf("%13d | %5d | %11d | %7.0f / %-6d | %6.1f / %5.1f / %5.1f | %d%n", buffer, block,
                        sink.bufferFrames(), (double) queuedSum / samples, queuedMin, ms[ms.length / 2],
                        ms[(int) (ms.length * 0.9)], ms[ms.length - 1], engine.underruns());
                engine.stop();
                Thread.sleep(200);
            }
        }
    }
}
