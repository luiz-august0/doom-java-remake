package doom.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {

    @TempDir
    Path tmp;

    @Test
    void absentKeysGiveDefaults() {
        Config c = Config.empty();
        assertThat(c.get(Settings.SOUND_SFX_VOLUME)).isEqualTo(8);
        assertThat(c.get(Settings.VIDEO_MODE)).isEqualTo("windowed");
        assertThat(c.get(Settings.VIDEO_RENDER_SCALE)).isEqualTo(1.0);
        assertThat(c.warnings()).isEmpty();
    }

    @Test
    void parsesKeyValueWithComments() {
        Config c = Config.parse("""
                # a comment
                video.mode = Borderless   # windowed | borderless
                sound.sfxVolume=3
                video.vsync = off

                input.alwaysRun = yes
                """);
        assertThat(c.get(Settings.VIDEO_MODE)).isEqualTo("borderless");
        assertThat(c.get(Settings.SOUND_SFX_VOLUME)).isEqualTo(3);
        assertThat(c.get(Settings.VIDEO_VSYNC)).isFalse();
        assertThat(c.get(Settings.INPUT_ALWAYS_RUN)).isTrue();
        assertThat(c.warnings()).isEmpty();
    }

    @Test
    void lenientParsingWarnsAndKeepsGoing() {
        Config c = Config.parse("""
                this line is nonsense
                bogus.key = 1
                sound.sfxVolume = loud
                sound.musicVolume = 99
                sound.sfxVolume = 2
                """);
        assertThat(c.get(Settings.SOUND_MUSIC_VOLUME)).isEqualTo(8); // out of range: default
        assertThat(c.get(Settings.SOUND_SFX_VOLUME)).isEqualTo(2); // the last one wins
        assertThat(c.warnings()).anyMatch(w -> w.contains("line 1") && w.contains("expected"));
        assertThat(c.warnings()).anyMatch(w -> w.contains("unknown key 'bogus.key'"));
        assertThat(c.warnings()).anyMatch(w -> w.contains("duplicate key 'sound.sfxVolume'"));
        assertThat(c.warnings()).anyMatch(w -> w.contains("invalid value '99' for sound.musicVolume"));
    }

    @Test
    void writingPreservesCommentsOrderAndUnknownKeys() {
        String text = """
                # header
                video.mode = windowed   # keep this comment
                custom.thing = 42

                # audio
                sound.sfxVolume = 8
                """;
        Config c = Config.parse(text);
        assertThat(c.toText()).isEqualTo(text);
        c.set(Settings.VIDEO_MODE, "fullscreen");
        c.set(Settings.SOUND_MUSIC_VOLUME, 5);
        assertThat(c.toText()).isEqualTo("""
                # header
                video.mode = fullscreen  # keep this comment
                custom.thing = 42

                # audio
                sound.sfxVolume = 8
                sound.musicVolume = 5
                """);
    }

    @Test
    void defaultFileRoundTripsToTheSameValues() {
        Config defaults = Config.defaults();
        Config back = Config.parse(defaults.toText());
        assertThat(back.warnings()).isEmpty();
        assertThat(back.toText()).isEqualTo(defaults.toText());
        for (var section : Settings.sections().values()) {
            for (Setting<?> s : section) {
                assertThat(back.get(s)).as(s.key()).isEqualTo(s.defaultValue());
            }
        }
    }

    @Test
    void saveAndLoadAreAtomicAndRoundTrip() throws Exception {
        Path file = tmp.resolve("sub").resolve("doom-java.cfg");
        Config c = Config.load(file); // missing: defaults
        assertThat(c.isDirty()).isTrue();
        c.set(Settings.SOUND_CHANNELS, 16);
        c.saveAsync(file).get();
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.exists(file.resolveSibling("doom-java.cfg.tmp"))).isFalse();
        Config again = Config.load(file);
        assertThat(again.get(Settings.SOUND_CHANNELS)).isEqualTo(16);
        assertThat(again.warnings()).isEmpty();
    }

    @Test
    void awaitSavesWaitsForTheWriter() throws IOException {
        Path file = tmp.resolve("a.cfg");
        Config c = Config.defaults();
        c.saveAsync(file);
        c.awaitSaves();
        assertThat(Files.readString(file)).isEqualTo(c.toText());
    }

    @Test
    void bindingsParseAndReportUnknownNames() {
        Config c = Config.parse("key.fire = SPACE, MOUSE2, nonsense\nkey.use =\n");
        KeyBindings b = KeyBindings.fromConfig(c);
        assertThat(b.inputs(Action.FIRE)).containsExactly(' ', InputNames.MOUSE_BASE + 1);
        assertThat(b.inputs(Action.USE)).isEmpty();
        assertThat(c.warnings()).anyMatch(w -> w.contains("unknown input 'nonsense'"));
        assertThat(b.actionsFor(' ')).containsExactly(Action.FIRE);
    }

    @Test
    void defaultBindingsAreVanillaLike() {
        KeyBindings b = KeyBindings.defaults();
        assertThat(b.inputs(Action.FORWARD)).contains('w', doom.platform.input.KeyCode.UPARROW);
        assertThat(b.inputs(Action.FIRE)).contains(doom.platform.input.KeyCode.RCTRL, InputNames.MOUSE_BASE);
        assertThat(b.inputs(Action.MOUSE_FORWARD)).containsExactly(InputNames.MOUSE_BASE + 2);
        assertThat(InputNames.parse("F5")).isEqualTo(doom.platform.input.KeyCode.F5);
        assertThat(InputNames.name(doom.platform.input.KeyCode.UPARROW)).isEqualTo("UP");
    }

    @Test
    void bindingsWriteBack() {
        Config c = Config.empty();
        KeyBindings.defaults().with(Action.USE, 'f').writeTo(c);
        assertThat(c.getRaw("key.use")).isEqualTo("F");
        assertThat(KeyBindings.fromConfig(c).inputs(Action.USE)).containsExactly('f');
    }

    @Test
    void dataDirectoriesPerOs() {
        assertThat(DataDirs.resolve("Mac OS X", java.util.Map.of(), "/Users/me").root())
                .isEqualTo(Path.of("/Users/me/Library/Application Support/doom-java"));
        assertThat(DataDirs.resolve("Linux", java.util.Map.of("XDG_CONFIG_HOME", "/x/cfg"), "/home/me").root())
                .isEqualTo(Path.of("/x/cfg/doom-java"));
        assertThat(DataDirs.resolve("Linux", java.util.Map.of(), "/home/me").root())
                .isEqualTo(Path.of("/home/me/.config/doom-java"));
        assertThat(DataDirs.resolve("Windows 11", java.util.Map.of("APPDATA", "C:\\Users\\me\\AppData\\Roaming"),
                "C:\\Users\\me").root().getFileName().toString()).isEqualTo("doom-java");
        DataDirs d = DataDirs.resolve("Linux", java.util.Map.of(), "/home/me");
        assertThat(d.saveDir("doom1")).isEqualTo(Path.of("/home/me/.config/doom-java/saves/doom1"));
        assertThat(d.configFile().getFileName().toString()).isEqualTo("doom-java.cfg");
    }

    @Test
    void importsVanillaDefaultCfg() {
        String vanilla = """
                mouse_sensitivity             7
                sfx_volume                    12
                music_volume                  3
                screenblocks                  9
                usegamma                      2
                show_messages                 0
                snd_channels                  16
                key_up                        72
                key_fire                      29
                key_use                       57
                key_speed                     54
                mouseb_fire                   0
                mouseb_strafe                 1
                player_name                   "Hero"
                """;
        Config c = Config.empty();
        var messages = VanillaCfgImport.apply(vanilla, c);
        assertThat(c.get(Settings.INPUT_MOUSE_SENS_X)).isEqualTo(7);
        assertThat(c.get(Settings.SOUND_SFX_VOLUME)).isEqualTo(12);
        assertThat(c.get(Settings.SOUND_MUSIC_VOLUME)).isEqualTo(3);
        assertThat(c.get(Settings.GAME_SCREEN_BLOCKS)).isEqualTo(9);
        assertThat(c.get(Settings.GAME_GAMMA)).isEqualTo(2);
        assertThat(c.get(Settings.GAME_SHOW_MESSAGES)).isFalse();
        assertThat(c.get(Settings.SOUND_CHANNELS)).isEqualTo(16);
        KeyBindings b = KeyBindings.fromConfig(c);
        assertThat(b.inputs(Action.FORWARD)).containsExactly(doom.platform.input.KeyCode.UPARROW);
        assertThat(b.inputs(Action.FIRE)).containsExactly(doom.platform.input.KeyCode.RCTRL, InputNames.MOUSE_BASE);
        assertThat(b.inputs(Action.USE)).containsExactly(' ');
        assertThat(b.inputs(Action.RUN)).containsExactly(doom.platform.input.KeyCode.RSHIFT);
        assertThat(messages).anyMatch(m -> m.startsWith("imported"));
    }
}
