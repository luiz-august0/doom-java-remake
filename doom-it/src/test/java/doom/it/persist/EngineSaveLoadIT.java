package doom.it.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.engine.DoomEngine;
import doom.engine.EngineParams;
import doom.engine.cli.LaunchOptions;
import doom.engine.config.Config;
import doom.engine.config.DataDirs;
import doom.game.api.GameState;
import doom.game.persist.SaveGameCodec;
import doom.game.persist.SaveInfo;

import doom.it.persist.FakePlatform.ManualClock;
import doom.it.persist.FakePlatform.RecordingVideo;
import doom.it.persist.FakePlatform.ScriptedInput;
import doom.math.DoomFatalException;
import doom.platform.input.InputEvent;
import doom.platform.input.InputEventQueue;
import doom.platform.input.KeyCode;
import doom.testkit.Iwads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine wiring of save games: a game saved from the menu (F2) lands in the save directory as a DJSV file, and
 * both the menu (F3) and {@code -loadgame} bring it back; the tic that loads is the tic the session would run if the
 * snapshot were loaded directly.
 */
class EngineSaveLoadIT {

    @TempDir
    Path tmp;

    private Path iwad;

    @BeforeEach
    void iwad() {
        iwad = Iwads.assumeAvailable(Iwads.DOOM1);
    }

    @SafeVarargs
    private DoomEngine engine(String[] args, Consumer<InputEventQueue>... script) {
        LaunchOptions options = LaunchOptions.parse(args);
        EngineParams params = new EngineParams(options).config(Config.defaults())
                .dirs(DataDirs.resolve("Linux", Map.of("XDG_CONFIG_HOME", tmp.toString()), tmp.toString()))
                .clock(new ManualClock())
                .platform(FakePlatform.platform(new RecordingVideo(640, 400), new ScriptedInput(script)));
        return new DoomEngine(params);
    }

    private static Consumer<InputEventQueue> key(int code) {
        return q -> {
            q.add(InputEvent.KEY_DOWN, 0).code = code;
            q.add(InputEvent.KEY_UP, 0).code = code;
        };
    }

    private static Consumer<InputEventQueue> text(char c) {
        return q -> q.add(InputEvent.CHAR, 0).code = c;
    }

    private Path saveFile() throws IOException {
        try (Stream<Path> files = Files.walk(tmp)) {
            return files.filter(p -> p.getFileName().toString().equals("doomsav0.dsg")).findFirst().orElse(null);
        }
    }

    private static List<String> lines(Path trace) throws IOException {
        List<String> out = new ArrayList<>();
        for (String l : Files.readAllLines(trace)) {
            if (!l.startsWith("#") && !l.startsWith("  ")) {
                out.add(l);
            }
        }
        return out;
    }

    private static int field(String line, String key) {
        for (String f : line.split(";")) {
            if (f.startsWith(key + "=")) {
                return Integer.parseInt(f.substring(key.length() + 1));
            }
        }
        throw new IllegalArgumentException(key + " in " + line);
    }

    /** Index of the first trace line whose level time is smaller than the one before it: the tic that loaded. */
    private static int loadLine(List<String> trace) {
        for (int i = 1; i < trace.size(); i++) {
            if (field(trace.get(i), "lt") < field(trace.get(i - 1), "lt")) {
                return i;
            }
        }
        return -1;
    }

    /** The trace line the session runs when it loads the snapshot and ticks once with no input. */
    private String lineAfterDirectLoad(byte[] snapshot, int tics) throws IOException {
        SaveHarness h = new SaveHarness(SaveHarness.open(iwad), SaveHarness.identify(iwad));
        h.session.loadGame(snapshot);
        String last = null;
        for (int t = 0; t < tics; t++) {
            h.session.tick(h.cmds, false);
            StringBuilder sb = new StringBuilder();
            h.session.appendTraceLine(sb);
            last = sb.toString();
        }
        return last;
    }

    @Test
    @SuppressWarnings("unchecked")
    void menuSaveWritesTheFileAndMenuLoadRestoresIt() throws Exception {
        Path trace = tmp.resolve("menu.trace");
        Consumer<InputEventQueue>[] script = new Consumer[400];
        script[100] = key(KeyCode.F2); // the save menu
        script[103] = key(KeyCode.ENTER); // the first slot starts the description
        script[105] = text('h');
        script[106] = text('i');
        script[108] = key(KeyCode.ENTER); // saves
        script[250] = key(KeyCode.F3); // the load menu
        script[253] = key(KeyCode.ENTER); // the first slot
        try (DoomEngine engine = engine(new String[] {"-iwad", iwad.toString(), "-warp", "1", "1", "-skill", "3",
            "-maxtics", "120", "-trace", trace.toString()}, script)) {
            engine.run();
            assertThat(engine.session().view().state()).isEqualTo(GameState.LEVEL);
        }

        Path file = saveFile();
        assertThat(file).as("the save file").isNotNull();
        byte[] data = Files.readAllBytes(file);
        SaveInfo info = SaveGameCodec.inspect(data);
        assertThat(info.description()).isEqualTo("HI"); // the menu upper-cases the name, like DOOM
        assertThat(info.gameMode()).isEqualTo("SHAREWARE");
        assertThat(Files.readString(file.resolveSibling("doomsav0.txt"))).isEqualTo("HI");

        List<String> lines = lines(trace);
        int load = loadLine(lines);
        assertThat(load).as("the tic that loaded the game (level time goes back)").isGreaterThan(5);
        // the snapshot was taken a few tics after the keys; loading it and ticking once gives the line of that tic
        assertThat(VanillaTrace.gated(lines.get(load))).isEqualTo(VanillaTrace.gated(lineAfterDirectLoad(data, 1)));
        // and the game keeps running from there exactly like a session that loaded the file
        int following = Math.min(lines.size() - 1, load + 20);
        assertThat(VanillaTrace.gated(lines.get(following)))
                .isEqualTo(VanillaTrace.gated(lineAfterDirectLoad(data, following - load + 1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadGameOnTheCommandLineStartsFromTheFile() throws Exception {
        // first make a save file through the menu
        Consumer<InputEventQueue>[] script = new Consumer[200];
        script[60] = key(KeyCode.F2);
        script[63] = key(KeyCode.ENTER);
        script[65] = text('x');
        script[68] = key(KeyCode.ENTER);
        try (DoomEngine engine = engine(new String[] {"-iwad", iwad.toString(), "-warp", "1", "2", "-skill", "2",
            "-maxtics", "120"}, script)) {
            engine.run();
        }
        Path file = saveFile();
        assertThat(file).isNotNull();
        byte[] data = Files.readAllBytes(file);

        Path trace = tmp.resolve("loadgame.trace");
        try (DoomEngine engine = engine(new String[] {"-iwad", iwad.toString(), "-loadgame", "0", "-maxtics", "40",
            "-trace", trace.toString()})) {
            engine.run();
            assertThat(engine.session().view().state()).isEqualTo(GameState.LEVEL);
            assertThat(engine.session().view().map()).isEqualTo(2);
        }
        List<String> lines = lines(trace);
        assertThat(lines).hasSize(40);
        for (int n : new int[] {1, 10, 40}) {
            assertThat(VanillaTrace.gated(lines.get(n - 1))).as("tic %d after loading", n)
                    .isEqualTo(VanillaTrace.gated(lineAfterDirectLoad(data, n)));
        }
    }

    @Test
    void loadGameOfAnEmptySlotIsAnError() {
        assertThatThrownBy(() -> {
            try (DoomEngine engine = engine(new String[] {"-iwad", iwad.toString(), "-loadgame", "4", "-maxtics", "5"})) {
                engine.run();
            }
        }).isInstanceOf(DoomFatalException.class).hasMessageContaining("save game");
    }

    @Test
    void aCorruptFileOnTheCommandLineLandsInTheTitleLoop() throws Exception {
        Path dir = tmp.resolve("junk");
        Files.createDirectories(dir);
        // find where the engine keeps the saves by saving one first
        @SuppressWarnings("unchecked")
        Consumer<InputEventQueue>[] script = new Consumer[100];
        script[40] = key(KeyCode.F2);
        script[43] = key(KeyCode.ENTER);
        script[45] = text('z');
        script[48] = key(KeyCode.ENTER);
        try (DoomEngine engine = engine(new String[] {"-iwad", iwad.toString(), "-warp", "1", "1", "-maxtics", "80"},
                script)) {
            engine.run();
        }
        Path file = saveFile();
        assertThat(file).isNotNull();
        byte[] data = Files.readAllBytes(file);
        data[data.length / 2] ^= 0x55; // a flipped byte: the checksum catches it
        Files.write(file, data);

        try (DoomEngine engine = engine(new String[] {"-iwad", iwad.toString(), "-loadgame", "0", "-maxtics", "400"})) {
            engine.run();
            // the title loop is running (its demos are levels too, but not a user game)
            assertThat(engine.ranTics()).isEqualTo(400);
            assertThat(engine.session().view().userGame()).isFalse();
        }
    }

    /** The trace line without the informational misc RNG key. */
    private static final class VanillaTrace {
        static String gated(String line) {
            return SaveHarness.gated(line);
        }
    }
}
