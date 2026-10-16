package doom.engine;

import static org.assertj.core.api.Assertions.assertThat;

import doom.engine.cli.LaunchOptions;
import doom.engine.config.Config;
import doom.engine.config.DataDirs;
import doom.game.api.DemoPlayback;
import doom.game.api.GameParams;
import doom.game.api.GameSession;
import doom.game.api.GameSetup;
import doom.game.api.GameState;
import doom.game.api.SimEvents;
import doom.game.api.Skill;
import doom.game.api.SoundOrigins;
import doom.game.api.TicCmd;
import doom.game.view.ActorView;
import doom.game.view.DefsView;
import doom.game.view.GameView;
import doom.game.view.PlayerView;
import doom.game.view.SoundId;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.WadWriter;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;
import doom.wad.iwad.IwadInfo;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The headless engine against a fake session: loop, commands, trace, demos, events. */
class DoomEngineHeadlessTest {

    @TempDir
    Path tmp;

    /** A scriptable stand-in for the simulation. */
    static final class FakeSession {
        final List<String> calls = new ArrayList<>();
        final List<TicCmd[]> ticCmds = new ArrayList<>();
        final List<Boolean> menuFlags = new ArrayList<>();
        final List<GameParams> newGames = new ArrayList<>();
        byte[] demoBytes;
        DemoPlayback demoMode;
        String demoName;
        int tics;
        int endAtTic = -1;
        DemoPlayback endMode = DemoPlayback.TIMEDEMO;
        boolean ended;
        SimEvents events;
        IntConsumer onTick = t -> {
        };
        final int[] actorX = new int[4];
        final int[] priority = new int[SoundId.COUNT];
        GameSession session;
        GameView view;

        FakeSession() {
            java.util.Arrays.fill(priority, 64);
            DefsView defs = proxy(DefsView.class, (n, a) -> switch (n) {
                case "sfxPriority" -> priority[(Integer) a[0]];
                case "sfxLink" -> -1;
                case "sfxLinkVolume", "sfxLinkPitch" -> 0;
                case "sfxName" -> "none" + a[0];
                case "musicName" -> "none";
                default -> throw new UnsupportedOperationException(n);
            });
            ActorView actors = proxy(ActorView.class, (n, a) -> switch (n) {
                case "x", "y", "angle" -> 0;
                case "exists" -> true;
                default -> throw new UnsupportedOperationException(n);
            });
            PlayerView player = proxy(PlayerView.class, (n, a) -> switch (n) {
                case "actor" -> 0;
                default -> throw new UnsupportedOperationException(n);
            });
            view = proxy(GameView.class, (n, a) -> switch (n) {
                case "consolePlayer" -> 0;
                case "gametic" -> tics;
                case "map" -> 1;
                case "episode" -> 1;
                case "mode" -> GameMode.SHAREWARE;
                case "state" -> GameState.DEMOSCREEN;
                case "pageName" -> "TITLEPIC";
                case "defs" -> defs;
                case "actors" -> actors;
                case "player" -> player;
                default -> throw new UnsupportedOperationException(n);
            });
            session = proxy(GameSession.class, (n, a) -> {
                switch (n) {
                    case "tick" -> {
                        ticCmds.add(copy((TicCmd[]) a[0]));
                        menuFlags.add((Boolean) a[1]);
                        tics++;
                        onTick.accept(tics);
                        if (tics == endAtTic) {
                            ended = true;
                            events.demoFinished(endMode, tics);
                        }
                        return null;
                    }
                    case "view" -> {
                        return view;
                    }
                    case "miscRandom" -> {
                        return (doom.game.api.MiscRandom) () -> 0;
                    }
                    case "appendTraceLine" -> {
                        if (!ended) {
                            ((StringBuilder) a[0]).append("t=").append(tics - 1);
                        }
                        return null;
                    }
                    case "appendTraceVerbose" -> {
                        ((StringBuilder) a[0]).append("  a0=1\n");
                        return null;
                    }
                    case "playDemo" -> {
                        demoName = (String) a[0];
                        demoBytes = (byte[]) a[1];
                        demoMode = (DemoPlayback) a[2];
                        calls.add("playDemo");
                        return null;
                    }
                    case "newGame" -> {
                        newGames.add((GameParams) a[0]);
                        calls.add("newGame");
                        return null;
                    }
                    default -> {
                        calls.add(n);
                        return null;
                    }
                }
            });
        }

        private static TicCmd[] copy(TicCmd[] in) {
            TicCmd[] out = new TicCmd[in.length];
            for (int i = 0; i < in.length; i++) {
                out[i] = new TicCmd();
                out[i].copyFrom(in[i]);
            }
            return out;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, FakeHandler h) {
        InvocationHandler ih = (p, m, args) -> h.call(m.getName(), args == null ? new Object[0] : args);
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, ih);
    }

    private interface FakeHandler {
        Object call(String name, Object[] args) throws Exception;
    }

    private GameSources sources(GameMode mode, byte[]... demo1) {
        // a one second DMX sound for sfx 1 ("none1"), so that its channel stays busy
        byte[] dmx = new byte[8 + 11025 + 32];
        dmx[0] = 3;
        dmx[2] = (byte) (11025 & 0xFF);
        dmx[3] = (byte) (11025 >> 8);
        int count = 11025 + 32;
        dmx[4] = (byte) count;
        dmx[5] = (byte) (count >> 8);
        WadWriter w = WadWriter.iwad().add("PLAYPAL", new byte[768 * 14]).add("DSNONE1", dmx);
        if (demo1.length > 0) {
            w.add("DEMO1", demo1[0]);
        }
        WadFile wad = WadFile.of("test.wad", ByteBuffer.wrap(w.toBytes()));
        IwadInfo info = new IwadInfo(tmp.resolve("test.wad"), mode,
                mode == GameMode.COMMERCIAL ? GameMission.DOOM2 : GameMission.DOOM, GameVariant.VANILLA,
                GameVersion.V1_9);
        return new GameSources(LumpDirectory.of(List.of(wad)), info, List.of());
    }

    private DoomEngine engine(FakeSession fake, GameSources sources, String... args) {
        String[] all = new String[args.length + 1];
        all[0] = "-headless";
        System.arraycopy(args, 0, all, 1, args.length);
        EngineParams params = new EngineParams(LaunchOptions.parse(all)).config(Config.defaults())
                .dirs(DataDirs.resolve("Linux", Map.of("XDG_CONFIG_HOME", tmp.toString()), tmp.toString()))
                .sources(sources).out(new PrintStream(new ByteArrayOutputStream()))
                .sessionFactory((GameSetup setup) -> {
                    fake.events = setup.events();
                    return fake.session;
                });
        return new DoomEngine(params);
    }

    @Test
    void startsTheTitleLoopAndRunsTheRequestedTics() {
        FakeSession fake = new FakeSession();
        try (DoomEngine e = engine(fake, sources(GameMode.SHAREWARE), "-maxtics", "50")) {
            assertThat(e.run()).isZero();
            assertThat(e.ranTics()).isEqualTo(50);
        }
        assertThat(fake.calls).contains("startTitle");
        assertThat(fake.ticCmds).hasSize(50);
        assertThat(fake.ticCmds.get(0)).hasSize(4);
        assertThat(fake.menuFlags).doesNotContain(true);
        assertThat(fake.ticCmds.get(10)[0].forwardMove).isZero();
    }

    @Test
    void warpStartsANewGameWithTheGivenParameters() {
        FakeSession doom1 = new FakeSession();
        try (DoomEngine e = engine(doom1, sources(GameMode.RETAIL), "-warp", "2", "3", "-skill", "4", "-nomonsters",
                "-fast", "-maxtics", "1")) {
            e.run();
        }
        assertThat(doom1.newGames).hasSize(1);
        GameParams p = doom1.newGames.get(0);
        assertThat(p.episode()).isEqualTo(2);
        assertThat(p.map()).isEqualTo(3);
        assertThat(p.skill()).isEqualTo(Skill.HARD);
        assertThat(p.noMonsters() && p.fast()).isTrue();
        assertThat(doom1.calls).doesNotContain("startTitle");

        FakeSession doom2 = new FakeSession();
        try (DoomEngine e = engine(doom2, sources(GameMode.COMMERCIAL), "-warp", "15", "-maxtics", "1")) {
            e.run();
        }
        assertThat(doom2.newGames.get(0).map()).isEqualTo(15);
        assertThat(doom2.newGames.get(0).episode()).isEqualTo(1);
        assertThat(doom2.newGames.get(0).skill()).isEqualTo(Skill.MEDIUM);
    }

    @Test
    void timedemoStopsAtTheEndMarkerAndReports() throws Exception {
        byte[] demo = {1, 2, 3};
        FakeSession fake = new FakeSession();
        fake.endAtTic = 20;
        Path trace = tmp.resolve("t.trace.gz");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        EngineParams params = new EngineParams(LaunchOptions.parse("-headless", "-timedemo", "demo1", "-trace",
                trace.toString())).config(Config.defaults())
                .dirs(DataDirs.resolve("Linux", Map.of("XDG_CONFIG_HOME", tmp.toString()), tmp.toString()))
                .sources(sources(GameMode.SHAREWARE, demo)).out(new PrintStream(captured))
                .sessionFactory(s -> {
                    fake.events = s.events();
                    return fake.session;
                });
        try (DoomEngine e = new DoomEngine(params)) {
            e.run();
            assertThat(e.demoEnded()).isTrue();
            assertThat(e.timedemoResult().gameTics()).isEqualTo(20);
            e.printTimedemoResult();
        }
        assertThat(fake.demoMode).isEqualTo(DemoPlayback.TIMEDEMO);
        assertThat(fake.demoBytes).isEqualTo(demo);
        assertThat(captured.toString(StandardCharsets.UTF_8)).startsWith("timed 20 gametics in ");

        List<String> lines = readGzip(trace);
        assertThat(lines.get(0)).isEqualTo("#doom-trace v1 iwad=test.wad demo=DEMO1 version=1.9");
        // 20 tics ran, the last one ended the program inside G_Ticker and wrote no line
        assertThat(lines).hasSize(1 + 19);
        assertThat(lines.get(1)).isEqualTo("t=0");
        assertThat(lines.get(19)).isEqualTo("t=18");
    }

    @Test
    void playdemoQuitsWhenTheDemoEnds() {
        FakeSession fake = new FakeSession();
        fake.endAtTic = 7;
        fake.endMode = DemoPlayback.SINGLE;
        try (DoomEngine e = engine(fake, sources(GameMode.SHAREWARE, new byte[] {9}), "-playdemo", "demo1")) {
            e.run();
            assertThat(e.ranTics()).isEqualTo(7);
        }
        assertThat(fake.demoMode).isEqualTo(DemoPlayback.SINGLE);
    }

    @Test
    void titleDemosDoNotStopTheEngine() {
        FakeSession fake = new FakeSession();
        fake.endAtTic = 5;
        fake.endMode = DemoPlayback.TITLE;
        try (DoomEngine e = engine(fake, sources(GameMode.SHAREWARE), "-maxtics", "12")) {
            e.run();
            assertThat(e.ranTics()).isEqualTo(12);
        }
    }

    @Test
    void verboseTraceCoversOnlyTheRange() throws Exception {
        FakeSession fake = new FakeSession();
        Path verbose = tmp.resolve("v.txt");
        try (DoomEngine e = engine(fake, sources(GameMode.SHAREWARE), "-maxtics", "5", "-traceverbose",
                verbose.toString(), "-tracefrom", "2", "-traceto", "3")) {
            e.run();
        }
        List<String> lines = Files.readAllLines(verbose);
        assertThat(lines).containsExactly("#doom-trace v1 iwad=test.wad demo=none version=1.9", "t=0", "t=1", "t=2",
                "  a0=1", "t=3", "  a0=1", "t=4");
    }

    @Test
    void soundEventsReachTheSoundSystemWithTheOriginalChannelRules() {
        FakeSession fake = new FakeSession();
        fake.onTick = t -> {
            if (t == 3) {
                fake.events.startSound(SoundOrigins.NONE, SoundId.PISTOL);
            }
        };
        try (DoomEngine e = engine(fake, sources(GameMode.SHAREWARE), "-maxtics", "5")) {
            e.run();
            assertThat(e.sound().channelInUse(0)).isTrue();
            assertThat(e.sound().channelSfx(0)).isEqualTo(SoundId.PISTOL);
        }
    }

    @Test
    void missingIwadIsAFatalErrorWithTheOriginalMessage() {
        LaunchOptions options = LaunchOptions.parse("-headless", "-iwad", tmp.resolve("nothing.wad").toString());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> GameSources.load(options,
                DataDirs.resolve("Linux", Map.of("XDG_CONFIG_HOME", tmp.toString()), tmp.toString())))
                .hasMessageContaining("IWAD file").hasMessageContaining("not found");
    }

    @Test
    void frameSizingFollowsTheSpec() {
        FrameSizing auto = FrameSizing.compute(2560, 1080, true, 1.0, false);
        assertThat(auto.width()).isEqualTo(2560);
        assertThat(auto.height()).isEqualTo(900);
        assertThat(auto.pixelAspect()).isEqualTo(1.2);
        FrameSizing square = FrameSizing.compute(1920, 1080, false, 0.5, false);
        assertThat(square.width()).isEqualTo(960);
        assertThat(square.height()).isEqualTo(540);
        assertThat(FrameSizing.compute(3000, 2000, true, 1.0, true)).isEqualTo(new FrameSizing(320, 200, 1.2));
        assertThat(FrameSizing.compute(100, 100, true, 0.25, false).width()).isEqualTo(FrameSizing.MIN_WIDTH);
    }

    private static List<String> readGzip(Path file) throws Exception {
        try (var in = new GZIPInputStream(Files.newInputStream(file))) {
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n"));
        }
    }
}
