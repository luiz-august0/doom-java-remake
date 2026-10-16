package doom.it.engine;

import static org.assertj.core.api.Assertions.assertThat;

import doom.engine.DoomEngine;
import doom.engine.EngineParams;
import doom.engine.cli.LaunchOptions;
import doom.engine.config.Config;
import doom.engine.config.DataDirs;
import doom.game.api.GameState;
import doom.it.engine.FakePlatform.ManualClock;
import doom.it.engine.FakePlatform.RecordingVideo;
import doom.it.engine.FakePlatform.ScriptedInput;
import doom.platform.input.InputEvent;
import doom.platform.input.InputEventQueue;
import doom.platform.input.KeyCode;
import doom.testkit.Iwads;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the engine with the real renderer, UI, wipe and sound system against a recording window: the title page, a level
 * started from the command line and a level started through the menu with scripted key presses. Pictures go to
 * {@code target/engine-shots/} for a look with the eyes.
 */
class EngineWindowIT {

    private static final Path SHOTS = Path.of("target", "engine-shots");

    @TempDir
    Path tmp;

    private Path iwad;

    @BeforeEach
    void iwad() {
        iwad = Iwads.assumeAvailable(Iwads.DOOM1);
    }

    @SafeVarargs
    private DoomEngine engine(RecordingVideo video, String[] args, Consumer<InputEventQueue>... script) {
        LaunchOptions options = LaunchOptions.parse(args);
        EngineParams params = new EngineParams(options).config(Config.defaults())
                .dirs(DataDirs.resolve("Linux", java.util.Map.of("XDG_CONFIG_HOME", tmp.toString()), tmp.toString()))
                .clock(new ManualClock())
                .platform(FakePlatform.platform(video, new ScriptedInput(script)));
        return new DoomEngine(params);
    }

    private static Consumer<InputEventQueue> key(int code) {
        return q -> {
            q.add(InputEvent.KEY_DOWN, 0).code = code;
            q.add(InputEvent.KEY_UP, 0).code = code;
        };
    }

    @Test
    void titlePageIsDrawn() throws Exception {
        RecordingVideo video = new RecordingVideo(1280, 800);
        try (DoomEngine engine = engine(video, new String[] {"-iwad", iwad.toString(), "-maxtics", "100"})) {
            engine.run();
            assertThat(engine.session().view().state()).isEqualTo(GameState.DEMOSCREEN);
            assertThat(engine.session().view().pageName()).isEqualTo("TITLEPIC");
            assertThat(engine.sound().musicPlaying()).as("title music").isNotZero();
        }
        assertThat(video.presented).isGreaterThan(10);
        assertThat(video.distinctColours()).isGreaterThan(40);
        video.writePng(SHOTS.resolve("title.png"));
    }

    @Test
    void warpStartsALevelWithTheWorldAndStatusBar() throws Exception {
        RecordingVideo video = new RecordingVideo(1280, 800);
        try (DoomEngine engine = engine(video,
                new String[] {"-iwad", iwad.toString(), "-warp", "1", "1", "-skill", "3", "-maxtics", "200"})) {
            engine.run();
            assertThat(engine.session().view().state()).isEqualTo(GameState.LEVEL);
            assertThat(engine.ranTics()).isEqualTo(200);
        }
        assertThat(video.distinctColours()).isGreaterThan(40);
        video.writePng(SHOTS.resolve("e1m1.png"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void newGameFromTheMenuMeltsIntoTheLevel() throws Exception {
        RecordingVideo video = new RecordingVideo(1280, 800);
        Consumer<InputEventQueue>[] script = new Consumer[40];
        script[5] = key(KeyCode.ESCAPE); // open the menu over the title
        script[8] = key(KeyCode.ENTER); // New Game
        script[11] = key(KeyCode.ENTER); // Knee-Deep in the Dead (shareware has one episode)
        script[14] = key(KeyCode.ENTER); // Hurt Me Plenty
        try (DoomEngine engine = engine(video, new String[] {"-iwad", iwad.toString(), "-maxtics", "400"}, script)) {
            engine.run();
            assertThat(engine.session().view().state()).isEqualTo(GameState.LEVEL);
            assertThat(engine.session().view().map()).isEqualTo(1);
        }
        video.writePng(SHOTS.resolve("menu-newgame.png"));
    }
}
