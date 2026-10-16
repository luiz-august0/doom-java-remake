package doom.game.data.dehacked;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.api.DehackedPatch;
import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.data.OracleDefsDump;
import doom.wad.WadFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Applies the DEHACKED lumps of the Freedoom IWADs and compares every table with the dump of the patched
 * Chocolate Doom ({@code -dumpdefs}, cached under {@code tools/oracle/work/cache/defs}). Skipped when the dumps or
 * the IWADs are missing.
 */
class DehackedOracleTest {

    /** Finds {@code DOOM_HOME}: the environment variable, or the ancestor of the working directory that has the cache. */
    static Path oracleDefsDir() {
        String env = System.getenv("DOOM_HOME");
        List<Path> candidates = new ArrayList<>();
        if (env != null && !env.isEmpty()) {
            candidates.add(Path.of(env));
        }
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            candidates.add(p);
        }
        for (Path c : candidates) {
            Path dir = c.resolve("tools/oracle/work/cache/defs");
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        return null;
    }

    static Path iwadDir() {
        String env = System.getenv("DOOM_IWAD_DIR");
        if (env != null && !env.isEmpty()) {
            return Path.of(env);
        }
        Path defs = oracleDefsDir();
        // <home>/tools/oracle/work/cache/defs -> <home>/.iwads
        return defs == null ? null : defs.getParent().getParent().getParent().getParent().getParent().resolve(".iwads");
    }

    private static List<String> readDump(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
    }

    /** The dump lines our rendering is compared with: header, {@code nodeh}, {@code strings N}, string lines, end. */
    private static List<String> tableLines(List<String> dump) {
        List<String> out = new ArrayList<>();
        for (String line : dump) {
            if (line.startsWith("#") || line.startsWith("nodeh ") || line.startsWith("strings ")
                    || line.startsWith("string ") || line.equals("end")) {
                continue;
            }
            out.add(line);
        }
        return out;
    }

    private static List<String> stringLines(List<String> dump) {
        return dump.stream().filter(l -> l.startsWith("string ")).sorted().toList();
    }

    /** Without DEHACKED the built-in tables are exactly the tables of the real game. */
    @ParameterizedTest
    @ValueSource(strings = {"doom1", "doom1-nodeh", "freedoom1-nodeh", "freedoom2-nodeh", "freedm-nodeh"})
    void builtInTablesEqualTheVanillaTables(String name) throws IOException {
        Path dir = oracleDefsDir();
        Assumptions.assumeTrue(dir != null && Files.isRegularFile(dir.resolve(name + ".txt")),
                "oracle dump " + name + " not available");
        List<String> dump = readDump(dir.resolve(name + ".txt"));
        // doom1.wad has no DEHACKED lump, so its dump is the vanilla one too.
        GameDefs defs = GameDefs.createDefault();
        GameStrings strings = GameStrings.createDefault();
        assertThat(OracleDefsDump.tables(defs, strings)).containsExactlyElementsOf(tableLines(dump));
        assertThat(stringLines(dump)).isEmpty();
    }

    /** Applying the IWAD's lump the way Chocolate Doom does gives the same tables and string replacements. */
    @ParameterizedTest
    @ValueSource(strings = {"freedoom1", "freedoom2", "freedm"})
    void iwadDehackedLumpGivesTheSameTablesAsChocolateDoom(String iwad) throws IOException {
        Path dir = oracleDefsDir();
        Path wads = iwadDir();
        Assumptions.assumeTrue(dir != null && Files.isRegularFile(dir.resolve(iwad + ".txt")),
                "oracle dump " + iwad + " not available");
        Assumptions.assumeTrue(wads != null && Files.isRegularFile(wads.resolve(iwad + ".wad")),
                iwad + ".wad not available");

        String lump = dehackedLump(wads.resolve(iwad + ".wad"));
        GameDefs defs = GameDefs.createDefault();
        GameStrings strings = GameStrings.createDefault();
        Dehacked.Result result = Dehacked.apply(defs, strings, new DehackedPatch(iwad + ".wad:DEHACKED", lump),
                Dehacked.Options.iwadLump());

        List<String> dump = readDump(dir.resolve(iwad + ".txt"));
        assertThat(result.errors()).isEmpty();
        assertThat(OracleDefsDump.tables(defs, strings)).containsExactlyElementsOf(tableLines(dump));
        assertThat(OracleDefsDump.substitutions(strings).stream().sorted().toList()).containsExactlyElementsOf(stringLines(dump));
        assertThat(OracleDefsDump.substitutions(strings)).isNotEmpty();
    }

    private static String dehackedLump(Path wad) throws IOException {
        WadFile file = WadFile.open(wad);
        for (int i = 0; i < file.lumpCount(); i++) {
            if (file.lumpName(i).equals("DEHACKED")) {
                ByteBuffer data = file.lumpData(i);
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        }
        throw new IllegalStateException(wad + " has no DEHACKED lump");
    }
}
