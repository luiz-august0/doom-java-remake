package doom.sound;

import doom.game.view.ActorView;
import doom.game.view.DefsView;
import doom.game.view.GameView;
import doom.game.view.MusicId;
import doom.game.view.PlayerView;
import doom.game.view.SoundId;
import doom.game.view.WorldView;
import doom.sound.mixer.SfxSample;
import doom.wad.audio.MusicScore;
import doom.wad.iwad.GameMode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Minimal views and a recording backend for {@link SoundSystem} tests. Only the methods the sound system reads work. */
final class FakeGame {

    int map = 1;
    int episode = 1;
    GameMode mode = GameMode.SHAREWARE;
    int gametic = 0;
    int consolePlayer = 0;

    final int[] actorX = new int[16];
    final int[] actorY = new int[16];
    final int[] actorAngle = new int[16];
    final boolean[] actorExists = new boolean[16];
    final int[] sectorX = new int[4];
    final int[] sectorY = new int[4];
    final int[] priority = new int[SoundId.COUNT];
    int playerActor = 0;

    final GameView view;
    final DefsView defs;
    final ActorView actors;
    final WorldView world;
    final PlayerView player;

    /** Misc RNG draws: each call returns the next value of {@link #rngValues}, cycling. */
    int[] rngValues = {0};
    int rngCalls;

    FakeGame() {
        java.util.Arrays.fill(priority, 64);
        actorExists[0] = true;
        defs = proxy(DefsView.class, (name, a) -> switch (name) {
            case "sfxPriority" -> priority[(Integer) a[0]];
            case "sfxLink" -> -1;
            case "sfxLinkPitch", "sfxLinkVolume" -> 0;
            case "sfxName" -> "s" + a[0];
            case "musicName" -> "m" + a[0];
            default -> throw new UnsupportedOperationException(name);
        });
        actors = proxy(ActorView.class, (name, a) -> switch (name) {
            case "x" -> actorX[(Integer) a[0]];
            case "y" -> actorY[(Integer) a[0]];
            case "angle" -> actorAngle[(Integer) a[0]];
            case "exists" -> actorExists[(Integer) a[0]];
            default -> throw new UnsupportedOperationException(name);
        });
        world = proxy(WorldView.class, (name, a) -> switch (name) {
            case "sectorSoundOriginX" -> sectorX[(Integer) a[0]];
            case "sectorSoundOriginY" -> sectorY[(Integer) a[0]];
            default -> throw new UnsupportedOperationException(name);
        });
        player = proxy(PlayerView.class, (name, a) -> switch (name) {
            case "actor" -> playerActor;
            default -> throw new UnsupportedOperationException(name);
        });
        view = proxy(GameView.class, (name, a) -> switch (name) {
            case "map" -> map;
            case "episode" -> episode;
            case "mode" -> mode;
            case "gametic" -> gametic;
            case "consolePlayer" -> consolePlayer;
            case "player" -> player;
            case "defs" -> defs;
            case "actors" -> actors;
            case "world" -> world;
            default -> throw new UnsupportedOperationException(name);
        });
    }

    int nextRandom() {
        int v = rngValues[rngCalls % rngValues.length];
        rngCalls++;
        return v;
    }

    private interface Handler {
        Object call(String name, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler h) {
        InvocationHandler ih = (p, m, args) -> h.call(m.getName(), args == null ? new Object[0] : args);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, ih);
    }

    /** Backend that records calls. */
    static final class RecordingBackend implements SoundBackend {

        record Start(int channel, SfxSample sample, int vol, int sep, int pitch) {
        }

        final List<Start> starts = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final boolean[] playing = new boolean[32];
        int musicStarts;
        MusicScore lastScore;

        @Override
        public void startSfx(int channel, SfxSample sample, int vol, int sep, int pitch) {
            starts.add(new Start(channel, sample, vol, sep, pitch));
            playing[channel] = true;
            log.add("start " + channel);
        }

        @Override
        public void updateChannel(int channel, int vol, int sep) {
            log.add("update " + channel + " " + vol + " " + sep);
        }

        @Override
        public void stopChannel(int channel) {
            playing[channel] = false;
            log.add("stop " + channel);
        }

        @Override
        public boolean isPlaying(int channel) {
            return playing[channel];
        }

        @Override
        public void stopAllSfx() {
            java.util.Arrays.fill(playing, false);
            log.add("stopAll");
        }

        @Override
        public void playMusic(MusicScore score, boolean looping) {
            musicStarts++;
            lastScore = score;
            log.add("music " + looping);
        }

        @Override
        public void stopMusic() {
            log.add("stopMusic");
        }

        @Override
        public void pauseMusic() {
            log.add("pause");
        }

        @Override
        public void resumeMusic() {
            log.add("resume");
        }

        @Override
        public void setMusicVolume(int level) {
            log.add("musicVolume " + level);
        }
    }

    /** Sound data that serves one dummy sample and score for everything. */
    static final class DummyData implements SoundData {
        final SfxSample sample = SfxSample.ofPcm(new short[11025], 11025);
        final MusicScore score = MusicScore.of(70, 500000, new int[] {0}, new int[] {MusicScore.TYPE_END},
                new int[] {0}, new int[] {0}, new int[] {0});
        final List<String> musicRequests = new ArrayList<>();
        boolean introaExists = false;

        @Override
        public SfxSample sfx(String name) {
            return sample;
        }

        @Override
        public boolean hasMusic(String name) {
            return introaExists && name.equals("m" + MusicId.INTROA);
        }

        @Override
        public MusicScore music(String name) {
            musicRequests.add(name);
            return score;
        }
    }
}
