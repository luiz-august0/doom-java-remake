package doom.it.wad;

import static org.assertj.core.api.Assertions.assertThat;

import doom.game.api.GameFactory;
import doom.game.view.WorldView;
import doom.math.BBox;
import doom.wad.LumpDirectory;
import doom.wad.iwad.IwadInfo;
import doom.wad.map.MapLump;
import doom.wad.texture.FlatDirectory;
import doom.wad.texture.TextureDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Every map of every IWAD loads through {@link GameFactory#loadLevelGeometry} with consistent indices. */
class LevelGeometryIT {

    private static final Pattern MAP_NAME = Pattern.compile("E\\dM\\d|MAP\\d\\d");

    @TestFactory
    Stream<DynamicTest> everyMapLoads() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String file : IwadFiles.NAMES) {
            tests.add(DynamicTest.dynamicTest(file, () -> checkAllMaps(file)));
        }
        return tests.stream();
    }

    private static void checkAllMaps(String file) throws Exception {
        LumpDirectory lumps = IwadFiles.open(file);
        IwadInfo info = IwadFiles.identify(file);
        TextureDirectory textures = TextureDirectory.load(lumps);
        FlatDirectory flats = FlatDirectory.load(lumps);
        int maps = 0;
        for (int i = 0; i < lumps.size(); i++) {
            String name = lumps.name(i);
            if (MAP_NAME.matcher(name).matches() && i + MapLump.BLOCKMAP.offsetFromMarker() < lumps.size()
                    && lumps.name(i + 1).equals("THINGS")) {
                WorldView w = GameFactory.loadLevelGeometry(lumps, info, name);
                check(name, w, textures, flats);
                maps++;
            }
        }
        assertThat(maps).as("maps in %s", file).isGreaterThan(0);
    }

    private static void check(String map, WorldView w, TextureDirectory textures, FlatDirectory flats) {
        assertThat(w.mapName()).isEqualTo(map);
        int sectors = w.sectorCount();
        int sides = w.sideCount();
        int lines = w.lineCount();
        int verts = w.vertexCount();
        int segs = w.segCount();
        int subs = w.subsectorCount();
        assertThat(sectors).as(map + " sectors").isPositive();
        assertThat(subs).as(map + " subsectors").isPositive();
        int references = 0;
        for (int s = 0; s < sectors; s++) {
            assertThat(w.sectorFloorPic(s)).isBetween(0, flats.count() - 1);
            assertThat(w.sectorCeilingPic(s)).isBetween(0, flats.count() - 1);
            assertThat(w.sectorFirstActor(s)).isEqualTo(-1);
            int n = w.sectorLineCount(s);
            references += n;
            for (int i = 0; i < n; i++) {
                int l = w.sectorLine(s, i);
                assertThat(l).isBetween(0, lines - 1);
                assertThat(w.lineFrontSector(l) == s || w.lineBackSector(l) == s)
                        .as("%s sector %d line %d borders it", map, s, l).isTrue();
            }
        }
        int expectedReferences = 0;
        for (int l = 0; l < lines; l++) {
            assertThat(w.lineV1(l)).isBetween(0, verts - 1);
            assertThat(w.lineV2(l)).isBetween(0, verts - 1);
            int front = w.lineFrontSide(l);
            int back = w.lineBackSide(l);
            assertThat(front).isBetween(0, sides - 1);
            assertThat(back).isBetween(-1, sides - 1);
            assertThat(w.lineFrontSector(l)).isEqualTo(w.sideSector(front));
            expectedReferences++;
            if (back >= 0) {
                assertThat(w.lineBackSector(l)).isEqualTo(w.sideSector(back));
                if (w.lineBackSector(l) != w.lineFrontSector(l)) {
                    expectedReferences++;
                }
            } else {
                assertThat(w.lineBackSector(l)).isEqualTo(-1);
            }
        }
        assertThat(references).as(map + " sector line references").isEqualTo(expectedReferences);
        for (int sd = 0; sd < sides; sd++) {
            assertThat(w.sideSector(sd)).isBetween(0, sectors - 1);
            assertThat(w.sideTopTexture(sd)).isBetween(0, textures.count() - 1);
            assertThat(w.sideBottomTexture(sd)).isBetween(0, textures.count() - 1);
            assertThat(w.sideMidTexture(sd)).isBetween(0, textures.count() - 1);
        }
        for (int g = 0; g < segs; g++) {
            assertThat(w.segV1(g)).isBetween(0, verts - 1);
            assertThat(w.segV2(g)).isBetween(0, verts - 1);
            int line = w.segLine(g);
            assertThat(line).isBetween(0, lines - 1);
            int side = w.segSide(g);
            assertThat(side == w.lineFrontSide(line) || side == w.lineBackSide(line)).isTrue();
            assertThat(w.segFrontSector(g)).isEqualTo(w.sideSector(side));
        }
        for (int ss = 0; ss < subs; ss++) {
            assertThat(w.subsectorSector(ss)).isBetween(0, sectors - 1);
            assertThat(w.subsectorFirstSeg(ss)).isBetween(0, segs - 1);
            assertThat(w.subsectorFirstSeg(ss) + w.subsectorSegCount(ss)).isLessThanOrEqualTo(segs);
            assertThat(w.subsectorSegCount(ss)).isPositive();
        }
        int nodes = w.nodeCount();
        for (int n = 0; n < nodes; n++) {
            for (int c = 0; c < 2; c++) {
                int child = w.nodeChild(n, c);
                if ((child & WorldView.NF_SUBSECTOR) != 0) {
                    assertThat(child & ~WorldView.NF_SUBSECTOR).isBetween(0, subs - 1);
                } else {
                    assertThat(child).isBetween(0, nodes - 1);
                }
                assertThat(w.nodeBBox(n, c, BBox.BOXTOP)).isGreaterThanOrEqualTo(w.nodeBBox(n, c, BBox.BOXBOTTOM));
                assertThat(w.nodeBBox(n, c, BBox.BOXRIGHT)).isGreaterThanOrEqualTo(w.nodeBBox(n, c, BBox.BOXLEFT));
            }
        }
        assertThat(w.blockmapColumns()).isPositive();
        assertThat(w.blockmapRows()).isPositive();
        assertThat(w.skyTexture()).isBetween(1, textures.count() - 1);
        assertThat(flats.name(w.skyFlat())).isEqualTo("F_SKY1");
    }
}
