package doom.engine.cli;

import static org.assertj.core.api.Assertions.assertThat;

import doom.wad.iwad.GameVersion;
import org.junit.jupiter.api.Test;

class LaunchOptionsTest {

    @Test
    void emptyCommandLineMeansTheTitleLoop() {
        LaunchOptions o = LaunchOptions.parse();
        assertThat(o.iwad()).isNull();
        assertThat(o.skill()).isEqualTo(-1);
        assertThat(o.warp()).isEmpty();
        assertThat(o.demoRequested()).isFalse();
        assertThat(o.problems()).isEmpty();
        assertThat(o.traceTo()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void valuesRunToTheNextParameter() {
        CommandLine cl = CommandLine.of("-file", "a.wad", "b.wad", "-warp", "2", "3", "-nomonsters");
        assertThat(cl.values("-file")).containsExactly("a.wad", "b.wad");
        assertThat(cl.values("-warp")).containsExactly("2", "3");
        assertThat(cl.has("-nomonsters")).isTrue();
        assertThat(cl.values("-nomonsters")).isEmpty();
        assertThat(cl.has("-FILE")).isTrue(); // case-insensitive like M_CheckParm
    }

    @Test
    void negativeNumbersAreValuesNotParameters() {
        CommandLine cl = CommandLine.of("-maxtics", "-1");
        assertThat(cl.intValue("-maxtics", 5)).isEqualTo(-1);
    }

    @Test
    void interpretsTheDocumentedFlags() {
        LaunchOptions o = LaunchOptions.parse("-iwad", "doom1.wad", "-skill", "4", "-episode", "2", "-warp", "2", "5",
                "-nomonsters", "-respawn", "-fast", "-altdeath", "-timedemo", "demo1", "-gameversion", "final",
                "-config", "x.cfg", "-window", "-width", "800", "-height", "600", "-vanilla", "-headless",
                "-trace", "t.gz", "-maxtics", "100", "-deh", "a.deh", "-nodeh");
        assertThat(o.iwad()).isEqualTo("doom1.wad");
        assertThat(o.skill()).isEqualTo(3);
        assertThat(o.episode()).isEqualTo(2);
        assertThat(o.warp()).containsExactly(2, 5);
        assertThat(o.noMonsters() && o.respawn() && o.fast()).isTrue();
        assertThat(o.deathmatch()).isEqualTo(2);
        assertThat(o.timeDemo()).isEqualTo("demo1");
        assertThat(o.playDemo()).isNull();
        assertThat(o.gameVersion()).isEqualTo(GameVersion.FINAL);
        assertThat(o.config()).isEqualTo("x.cfg");
        assertThat(o.window()).isTrue();
        assertThat(o.width()).isEqualTo(800);
        assertThat(o.height()).isEqualTo(600);
        assertThat(o.vanilla() && o.headless()).isTrue();
        assertThat(o.trace()).isEqualTo("t.gz");
        assertThat(o.maxTics()).isEqualTo(100);
        assertThat(o.deh()).containsExactly("a.deh");
        assertThat(o.noDeh()).isTrue();
        assertThat(o.problems()).isEmpty();
    }

    @Test
    void problemsAreReportedNotFatal() {
        LaunchOptions o = LaunchOptions.parse("-bogus", "-skill", "9", "-gameversion", "2.0", "-warp", "x");
        assertThat(o.problems()).anyMatch(p -> p.contains("-bogus"));
        assertThat(o.problems()).anyMatch(p -> p.contains("-skill"));
        assertThat(o.problems()).anyMatch(p -> p.contains("-gameversion"));
        assertThat(o.problems()).anyMatch(p -> p.contains("-warp"));
        assertThat(o.skill()).isEqualTo(-1);
        assertThat(o.gameVersion()).isNull();
    }

    @Test
    void helpFlags() {
        assertThat(LaunchOptions.parse("-help").help()).isTrue();
        assertThat(LaunchOptions.parse("--help").help()).isTrue();
        assertThat(LaunchOptions.usage()).contains("-timedemo").contains("-headless").contains("-trace");
    }
}
