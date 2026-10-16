package doom.it.audio;

import doom.platform.javasound.JavaSoundAudioProvider;
import doom.platform.javasound.JavaSoundAudioSink;
import doom.sound.mixer.AudioEngine;
import doom.sound.mixer.AudioEngineConfig;
import doom.sound.mixer.SfxSample;
import doom.wad.LumpDirectory;
import doom.wad.audio.DmxSound;
import doom.wad.audio.GenMidi;
import doom.wad.audio.MusicScore;
import java.nio.file.Path;
import java.util.List;

/**
 * Manual check through the real audio device: plays a few seconds of music and sound effects at moderate volume.
 * Arguments: IWAD path, music lump (default D_E1M1), seconds (default 3).
 */
public final class LiveAudioDemo {

    private LiveAudioDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (!new JavaSoundAudioProvider().isSupported()) {
            System.out.println("no audio output available");
            return;
        }
        LumpDirectory lumps = LumpDirectory.open(List.of(Path.of(args[0])));
        GenMidi genMidi = GenMidi.parse(lumps.data(lumps.get("GENMIDI")));
        MusicScore score = MusicScore.fromLump(lumps.data(lumps.get(args.length > 1 ? args[1] : "D_E1M1")), "music");
        double seconds = args.length > 2 ? Double.parseDouble(args[2]) : 3.0;

        AudioEngine engine = new AudioEngine(AudioEngineConfig.defaults().withDevice(null, 1024), genMidi);
        JavaSoundAudioSink sink = new JavaSoundAudioSink();
        engine.start(sink);
        engine.commands().setVolumes(15, 4); // moderate music: 4 * 8 = 32 of 127
        engine.commands().playMusic(score, true);
        String[] names = {"DSPISTOL", "DSSHOTGN", "DSBAREXP"};
        long end = System.nanoTime() + (long) (seconds * 1e9);
        int i = 0;
        while (System.nanoTime() < end) {
            Thread.sleep(500);
            int l = lumps.find(names[i % names.length]);
            if (l >= 0) {
                engine.commands().startSfx(i % 8, SfxSample.of(DmxSound.parse(lumps.data(l), names[0])), 45,
                        i % 2 == 0 ? 60 : 190, 128);
            }
            i++;
        }
        System.out.println("underruns=" + engine.underruns() + " frames=" + engine.framesRendered());
        engine.stop();
    }
}
