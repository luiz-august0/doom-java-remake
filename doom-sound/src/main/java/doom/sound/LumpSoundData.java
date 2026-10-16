package doom.sound;

import doom.sound.mixer.SfxSample;
import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import doom.wad.audio.DmxSound;
import doom.wad.audio.MusicScore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * {@link SoundData} backed by the loaded WADs. Samples are decoded on first use and cached; {@link #precache} decodes a
 * list of effects in parallel on virtual threads (ADR-020), as {@code I_PrecacheSounds} does at start-up.
 * Main thread only after construction (the caches are plain maps).
 */
public final class LumpSoundData implements SoundData {

    private static final System.Logger LOG = System.getLogger(LumpSoundData.class.getName());

    /** Marks a name whose lump is missing or invalid, so that it is looked up (and reported) only once. */
    private static final SfxSample MISSING = SfxSample.ofPcm(new short[1], 11025);

    private final LumpDirectory lumps;
    private final Map<String, SfxSample> samples = new HashMap<>();
    private final Map<String, MusicScore> scores = new HashMap<>();

    public LumpSoundData(LumpDirectory lumps) {
        this.lumps = lumps;
    }

    @Override
    public SfxSample sfx(String name) {
        SfxSample s = samples.get(name);
        if (s == null) {
            s = load(lumps, name);
            samples.put(name, s == null ? MISSING : s);
        }
        return s == MISSING ? null : s;
    }

    private static SfxSample load(LumpDirectory lumps, String name) {
        String lumpName = "DS" + name.toUpperCase(Locale.ROOT);
        int lump = lumps.find(lumpName);
        if (lump < 0) {
            // shareware and Freedoom IWADs lack some of the sounds; only report it when asked
            LOG.log(System.Logger.Level.DEBUG, "sound lump {0} not found", lumpName);
            return null;
        }
        DmxSound dmx = DmxSound.tryParse(lumps.data(lump));
        if (dmx == null) {
            LOG.log(System.Logger.Level.WARNING, "sound lump {0} is not a valid DMX sound", lumpName);
            return null;
        }
        return SfxSample.of(dmx);
    }

    /** Decodes the named effects in parallel and caches them. Names already cached are skipped. */
    public void precache(List<String> names) {
        List<String> todo = new ArrayList<>();
        for (String n : names) {
            if (!samples.containsKey(n) && !todo.contains(n)) {
                todo.add(n);
            }
        }
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<SfxSample>> futures = new ArrayList<>(todo.size());
            for (String n : todo) {
                futures.add(pool.submit(() -> load(lumps, n)));
            }
            for (int i = 0; i < todo.size(); i++) {
                try {
                    SfxSample s = futures.get(i).get();
                    samples.put(todo.get(i), s == null ? MISSING : s);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    samples.put(todo.get(i), MISSING);
                }
            }
        }
    }

    @Override
    public boolean hasMusic(String name) {
        return lumps.find("D_" + name.toUpperCase(Locale.ROOT)) >= 0;
    }

    @Override
    public MusicScore music(String name) {
        MusicScore cached = scores.get(name);
        if (cached != null) {
            return cached;
        }
        String lumpName = "D_" + name.toUpperCase(Locale.ROOT);
        int lump = lumps.find(lumpName);
        if (lump < 0) {
            LOG.log(System.Logger.Level.WARNING, "music lump {0} not found", lumpName);
            return null;
        }
        try {
            MusicScore score = MusicScore.fromLump(lumps.bytes(lump), lumpName);
            scores.put(name, score);
            return score;
        } catch (WadFormatException e) {
            LOG.log(System.Logger.Level.WARNING, "music lump {0} cannot be decoded: {1}", lumpName, e.getMessage());
            return null;
        }
    }
}
