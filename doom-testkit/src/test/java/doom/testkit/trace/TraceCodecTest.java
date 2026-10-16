package doom.testkit.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceCodecTest {

    @TempDir
    Path tmp;

    @Test
    void fnv1aMatchesKnownVectors() {
        assertThat(Fnv1a64.INIT).isEqualTo(0xcbf29ce484222325L);
        assertThat(Fnv1a64.format(Fnv1a64.INIT)).isEqualTo("cbf29ce484222325");
        // FNV-1a 64 of the four bytes 00 00 00 00 (int32 0, little endian), computed independently by hand:
        long h = TraceFormat.FNV_OFFSET_BASIS;
        for (int i = 0; i < 4; i++) {
            h ^= 0;
            h *= TraceFormat.FNV_PRIME;
        }
        assertThat(Fnv1a64.of(0)).isEqualTo(h);
        // Little-endian order: 0x01020304 feeds 04 03 02 01.
        long le = TraceFormat.FNV_OFFSET_BASIS;
        for (int b : new int[] {4, 3, 2, 1}) {
            le ^= b;
            le *= TraceFormat.FNV_PRIME;
        }
        assertThat(Fnv1a64.of(0x01020304)).isEqualTo(le);
        assertThat(Fnv1a64.of(1, 2)).isEqualTo(Fnv1a64.add(Fnv1a64.add(Fnv1a64.INIT, 1), 2));
        assertThat(Fnv1a64.parse(Fnv1a64.format(0x00000000000000abL))).isEqualTo(0xabL);
        assertThat(Fnv1a64.format(0xabL)).isEqualTo("00000000000000ab");
    }

    @Test
    void headerRoundTrips() {
        TraceHeader h = TraceHeader.of("freedoom2.wad", "f2m03a", "final2");
        assertThat(h.raw()).isEqualTo("#doom-trace v1 iwad=freedoom2.wad demo=f2m03a version=final2");
        TraceHeader parsed = TraceHeader.parse(h.raw());
        assertThat(parsed.iwad()).isEqualTo("freedoom2.wad");
        assertThat(parsed.demo()).isEqualTo("f2m03a");
        assertThat(parsed.version()).isEqualTo("final2");
        assertThatThrownBy(() -> TraceHeader.parse("#doom-trace v2 iwad=x")).isInstanceOf(TraceFormatException.class);
    }

    @Test
    void lineParsesKeysAndPlayerFields() {
        TraceLine l = TraceLine.parse(TraceTestSupport.line(7, 33, TraceTestSupport.player(1000), "9"), 2);
        assertThat(l.tic()).isEqualTo(7);
        assertThat(l.keys()).containsExactly("t", "gs", "r", "lt", "map", "tot", "p0", "a", "s", "l", "d", "x", "m");
        assertThat(l.intPart("map", 1)).isEqualTo(5);
        assertThat(l.players()).containsExactly(0);
        assertThat(l.parts("p0")).hasSize(TraceFormat.PLAYER_FIELD_COUNT);
        assertThat(l.get("s")).matches("[0-9a-f]{16}");
        assertThat(l.get("a")).startsWith("12,");
    }

    @Test
    void malformedLinesAreRejected() {
        assertThatThrownBy(() -> TraceLine.parse("gs=0;t=1", 5)).isInstanceOf(TraceFormatException.class);
        assertThatThrownBy(() -> TraceLine.parse("t=1;t=2", 5)).isInstanceOf(TraceFormatException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> TraceLine.parse("t=1;oops", 5)).isInstanceOf(TraceFormatException.class)
                .hasMessageContaining("line 5");
        assertThatThrownBy(() -> TraceLine.parse("t=abc", 5)).isInstanceOf(TraceFormatException.class);
    }

    @Test
    void builderRejectsBadKeys() {
        assertThatThrownBy(() -> new TraceLineBuilder().put("a;b", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceLineBuilder().put("", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void playerFieldTableHasSixtyOneNames() {
        assertThat(TraceFormat.PLAYER_FIELDS).hasSize(TraceFormat.PLAYER_FIELD_COUNT).doesNotHaveDuplicates();
        assertThat(TraceFormat.PLAYER_FIELDS.get(0)).isEqualTo("mo->x");
        assertThat(TraceFormat.PLAYER_FIELDS.get(7)).isEqualTo("mo->health");
        assertThat(TraceFormat.PLAYER_FIELDS.get(18)).isEqualTo("weaponowned");
        assertThat(TraceFormat.PLAYER_FIELDS.get(45)).isEqualTo("attacker");
        assertThat(TraceFormat.PLAYER_FIELDS.get(60)).isEqualTo("didsecret");
        assertThat(TraceFormat.MOBJ_FIELDS).hasSize(TraceFormat.MOBJ_FIELD_COUNT);
        assertThat(TraceFormat.fieldName("p2", 13)).isEqualTo("health");
        assertThat(TraceFormat.fieldName("map", 2)).isEqualTo("gameskill");
        assertThat(TraceFormat.fieldName("r", 0)).isNull();
    }

    @Test
    void plainAndGzipFilesRoundTrip() throws IOException {
        Trace original = TraceTestSupport.trace(40);
        for (String name : List.of("plain.trace", "packed.trace.gz")) {
            Path file = tmp.resolve(name);
            try (TraceWriter w = TraceWriter.create(file, original.header())) {
                for (TraceLine l : original.lines()) {
                    w.write(l);
                }
            }
            Trace read = TraceReader.readAll(file);
            assertThat(read.header()).isEqualTo(original.header());
            assertThat(read.lines()).hasSize(40);
            assertThat(read.lines().stream().map(TraceLine::raw).toList())
                    .isEqualTo(original.lines().stream().map(TraceLine::raw).toList());
        }
        byte[] gz = Files.readAllBytes(tmp.resolve("packed.trace.gz"));
        assertThat(gz[0] & 0xff).isEqualTo(0x1f);
        assertThat(gz[1] & 0xff).isEqualTo(0x8b);
        byte[] plain = Files.readAllBytes(tmp.resolve("plain.trace"));
        assertThat(new String(plain, StandardCharsets.UTF_8)).startsWith("#doom-trace v1 ").doesNotContain("\r");
    }

    @Test
    void gzipIsDetectedFromContentNotName() throws IOException {
        Path gz = tmp.resolve("a.trace.gz");
        try (TraceWriter w = TraceWriter.create(gz, TraceTestSupport.header())) {
            w.writeLine(TraceTestSupport.line(0, 0, TraceTestSupport.player(0), null));
        }
        Path renamed = tmp.resolve("renamed.txt");
        Files.copy(gz, renamed);
        assertThat(TraceReader.readAll(renamed).lines()).hasSize(1);
    }

    @Test
    void commentsAndBlankLinesAreSkippedAndCrlfTolerated() throws IOException {
        String text = "#doom-trace v1 iwad=doom1.wad demo=d version=1.9\r\n# a comment\r\n\r\n"
                + TraceTestSupport.line(0, 0, TraceTestSupport.player(0), "1") + "\r\n"
                + TraceTestSupport.line(1, 1, TraceTestSupport.player(0), "2") + "\r\n";
        try (TraceReader r = TraceReader.of(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "mem")) {
            assertThat(r.next().tic()).isEqualTo(0);
            assertThat(r.next().tic()).isEqualTo(1);
            assertThat(r.next()).isNull();
        }
    }

    @Test
    void emptyAndHeaderlessFilesAreErrors() {
        assertThatThrownBy(() -> TraceReader.of(new ByteArrayInputStream(new byte[0]), "empty"))
                .isInstanceOf(TraceFormatException.class);
        assertThatThrownBy(() -> TraceReader.of(new ByteArrayInputStream("t=0\n".getBytes(StandardCharsets.UTF_8)), "x"))
                .isInstanceOf(TraceFormatException.class);
    }

    @Test
    void verboseLinesAttachToTheirTicAndRoundTrip() throws IOException {
        int[] mobj = new int[TraceFormat.MOBJ_FIELD_COUNT];
        for (int i = 0; i < mobj.length; i++) {
            mobj[i] = i - 3;
        }
        TraceLine base = TraceLine.parse(TraceTestSupport.line(5, 1, TraceTestSupport.player(0), "0"), 0);
        TraceLine verbose = base.withVerbose(List.of(new VerboseEntry('a', 0, mobj, 0),
                new VerboseEntry('a', 1, mobj, 0), new VerboseEntry('x', 0, new int[] {3, 7, 1, 0, 2, 0, 0, 0, 0}, 0)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TraceWriter w = TraceWriter.of(bytes, false, TraceTestSupport.header())) {
            w.write(verbose);
            w.write(TraceLine.parse(TraceTestSupport.line(6, 1, TraceTestSupport.player(0), "0"), 0));
        }
        String text = bytes.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("\n  a0=-3,-2,").contains("\n  x0=3,7,1,");
        try (TraceReader r = TraceReader.of(new ByteArrayInputStream(bytes.toByteArray()), "mem")) {
            TraceLine first = r.next();
            assertThat(first.verbose()).hasSize(3);
            assertThat(first.verbose('a')).hasSize(2);
            assertThat(first.verbose('x').getFirst().fieldName(0)).isEqualTo("code");
            assertThat(first.verbose('x').getFirst().fieldName(1)).isEqualTo("type");
            assertThat(first.verbose('a').getFirst().fieldName(1)).isEqualTo("x");
            assertThat(first.verbose('a').getFirst().values()).isEqualTo(mobj);
            TraceLine second = r.next();
            assertThat(second.verbose()).isEmpty();
            assertThat(r.next()).isNull();
        }
    }

    @Test
    void malformedVerboseLinesAreRejected() {
        assertThatThrownBy(() -> VerboseEntry.parse("  q0=1,2", 3)).isInstanceOf(TraceFormatException.class);
        assertThatThrownBy(() -> VerboseEntry.parse("  a0=1,x", 3)).isInstanceOf(TraceFormatException.class);
        assertThatThrownBy(() -> VerboseEntry.parse("  a=1", 3)).isInstanceOf(TraceFormatException.class);
        assertThat(VerboseEntry.parse("  a12=1,-2,3", 3).index()).isEqualTo(12);
    }
}
