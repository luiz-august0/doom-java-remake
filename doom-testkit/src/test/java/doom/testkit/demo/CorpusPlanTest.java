package doom.testkit.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.testkit.Iwads;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusPlanTest {

    @TempDir
    Path tmp;

    @Test
    void coversEveryMapAtLeastThreeTimesWithinTheTicRange() {
        List<ManifestEntry> all = CorpusPlan.build();
        Map<String, List<ManifestEntry>> byMap = all.stream()
                .collect(Collectors.groupingBy(e -> e.iwad() + ":" + e.spec().map()));
        for (String iwad : List.of(Iwads.DOOM1, Iwads.FREEDOOM1, Iwads.FREEDOOM2)) {
            for (String map : CorpusPlan.maps(iwad)) {
                assertThat(byMap.get(iwad + ":" + map)).as(iwad + " " + map).hasSizeGreaterThanOrEqualTo(3);
            }
        }
        for (String map : CorpusPlan.FREEDM_MAPS) {
            assertThat(byMap.get(Iwads.FREEDM + ":" + map)).hasSizeGreaterThanOrEqualTo(3);
        }
        assertThat(CorpusPlan.maps(Iwads.DOOM1)).hasSize(9);
        assertThat(CorpusPlan.maps(Iwads.FREEDOOM1)).hasSize(36);
        assertThat(CorpusPlan.maps(Iwads.FREEDOOM2)).hasSize(32);
        for (ManifestEntry e : all) {
            assertThat(e.spec().tics()).as(e.name()).isBetween(1000, 3000);
        }
    }

    @Test
    void namesAreUniqueValidLumpNames() {
        Set<String> seen = new HashSet<>();
        for (ManifestEntry e : CorpusPlan.build()) {
            assertThat(e.name()).hasSizeLessThanOrEqualTo(8);
            assertThat(seen.add(e.name().toLowerCase())).as(e.name()).isTrue();
        }
    }

    @Test
    void includesMovementOnlyNoMonstersSubsetSingleAndMultiplayer() {
        List<ManifestEntry> subset = CorpusPlan.build().stream()
                .filter(e -> e.spec().policy() == DemoPolicy.MOVEMENT_ONLY).toList();
        assertThat(subset).isNotEmpty().allMatch(e -> e.spec().noMonsters());
        assertThat(subset).anyMatch(e -> e.spec().players() == 1);
        assertThat(subset).anyMatch(e -> e.spec().players() > 1 && e.spec().deathmatch() == 0);
        assertThat(subset).anyMatch(e -> e.spec().players() > 1 && e.spec().deathmatch() > 0);
    }

    @Test
    void exercisesPlayersModesAndOptions() {
        List<ManifestEntry> all = CorpusPlan.build();
        for (int p = 1; p <= 4; p++) {
            int players = p;
            assertThat(all).anyMatch(e -> e.spec().players() == players);
        }
        assertThat(all).anyMatch(e -> e.spec().deathmatch() == 1);
        assertThat(all).anyMatch(e -> e.spec().deathmatch() == 2);
        assertThat(all).anyMatch(e -> e.spec().respawn());
        assertThat(all).anyMatch(e -> e.spec().fast());
        for (int s = 0; s <= 4; s++) {
            int skill = s;
            assertThat(all).anyMatch(e -> e.spec().skill() == skill);
        }
        // FreeDM is deathmatch only, except for the single-player movement probe
        assertThat(all).filteredOn(e -> e.iwad().equals(Iwads.FREEDM) && e.spec().players() > 1)
                .allMatch(e -> e.spec().deathmatch() > 0);
    }

    @Test
    void planIsDeterministic() {
        assertThat(CorpusPlan.build()).isEqualTo(CorpusPlan.build());
    }

    @Test
    void manifestRoundTripsAndFilters() throws IOException {
        List<ManifestEntry> all = CorpusPlan.build();
        Path file = tmp.resolve("sub/manifest.tsv");
        DemoManifest.write(file, all);
        List<ManifestEntry> read = DemoManifest.read(file);
        assertThat(read).isEqualTo(all);
        assertThat(DemoManifest.filter(read, "^d1e1m1")).extracting(ManifestEntry::name)
                .contains("d1e1m1a", "d1e1m1b", "d1e1m1c", "d1e1m1m");
        assertThat(DemoManifest.filter(read, "freedm.wad:MAP01")).isNotEmpty();
        assertThat(DemoManifest.filter(read, null)).isSameAs(read);
    }

    @Test
    void manifestLineFormat() {
        ManifestEntry e = new ManifestEntry("d1e1m1a", "doom1.wad",
                new DemoSpec(2, "E1M1", 2, 0, true, false, true, 1500, 42, DemoPolicy.MOVEMENT_ONLY));
        assertThat(e.toLine()).isEqualTo("d1e1m1a\tdoom1.wad\tE1M1\t2\t2\t0\trespawn,nomonsters\t1500\t42\tmovement-only");
        assertThat(ManifestEntry.parse(e.toLine())).isEqualTo(e);
        assertThatThrownBy(() -> ManifestEntry.parse("a\tb")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ManifestEntry.parse(e.toLine().replace("respawn", "bogus")))
                .hasMessageContaining("bogus");
        assertThatThrownBy(() -> new ManifestEntry("toolongname", "doom1.wad", e.spec()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkedInManifestMatchesThePlan() throws IOException {
        Path manifest = findTestdata();
        org.junit.jupiter.api.Assumptions.assumeTrue(manifest != null, "testdata/demos/manifest.tsv not found");
        assertThat(DemoManifest.read(manifest)).isEqualTo(CorpusPlan.build());
        Path dir = manifest.getParent();
        for (ManifestEntry e : DemoManifest.read(manifest)) {
            byte[] expected = DemoGenerator.generate(e.spec()).toBytes();
            assertThat(java.nio.file.Files.readAllBytes(dir.resolve(e.fileName()))).as(e.name()).isEqualTo(expected);
        }
    }

    private static Path findTestdata() {
        Function<Path, Path> candidate = p -> p.resolve("testdata/demos/manifest.tsv");
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (java.nio.file.Files.isRegularFile(candidate.apply(dir))) {
                return candidate.apply(dir);
            }
        }
        return null;
    }
}
