package doom.game.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.game.api.GameFactory;
import doom.game.view.WorldView;
import doom.math.BBox;
import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.GameVersion;
import doom.wad.iwad.IwadInfo;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LevelGeometryLoaderTest {

    private static final int FU = 1 << 16;
    private static final IwadInfo SHAREWARE = new IwadInfo(Path.of("doom1.wad"), GameMode.SHAREWARE,
            GameMission.DOOM, GameVariant.VANILLA, GameVersion.V1_9);

    @Test
    void worldViewExposesTheLevel() {
        LumpDirectory d = new TwoRoomMap().directory();
        WorldView w = GameFactory.loadLevelGeometry(d, SHAREWARE, "e1m1"); // names are case-insensitive
        assertThat(w.mapName()).isEqualTo("E1M1");
        assertThat(w.vertexCount()).isEqualTo(6);
        assertThat(w.vertexX(3)).isEqualTo(128 * FU);
        assertThat(w.vertexY(3)).isEqualTo(64 * FU);

        assertThat(w.sectorCount()).isEqualTo(2);
        assertThat(w.sectorFloorHeight(1)).isEqualTo(16 * FU);
        assertThat(w.sectorCeilingHeight(1)).isEqualTo(96 * FU);
        assertThat(w.sectorPrevFloorHeight(1)).isEqualTo(16 * FU);
        assertThat(w.sectorPrevCeilingHeight(1)).isEqualTo(96 * FU);
        assertThat(w.sectorFloorPic(1)).isEqualTo(2);
        assertThat(w.sectorCeilingPic(1)).isEqualTo(3);
        assertThat(w.sectorLightLevel(1)).isEqualTo(200);
        assertThat(w.sectorSpecial(1)).isEqualTo(9);
        assertThat(w.sectorTag(1)).isEqualTo(3);
        assertThat(w.sectorSoundOriginX(0)).isEqualTo(32 * FU);
        assertThat(w.sectorSoundOriginY(0)).isEqualTo(32 * FU);
        assertThat(w.sectorFirstActor(0)).isEqualTo(-1);
        assertThat(w.sectorLineCount(0)).isEqualTo(4);
        assertThat(w.sectorLine(0, 3)).isEqualTo(6);

        assertThat(w.sideCount()).isEqualTo(8);
        assertThat(w.sideTextureOffset(2)).isEqualTo(16 * FU);
        assertThat(w.sidePrevTextureOffset(2)).isEqualTo(16 * FU);
        assertThat(w.sideRowOffset(2)).isEqualTo(-16 * FU);
        assertThat(w.sideTopTexture(2)).isZero();
        assertThat(w.sideMidTexture(2)).isEqualTo(1);
        assertThat(w.sideSector(2)).isEqualTo(1);

        assertThat(w.lineCount()).isEqualTo(7);
        assertThat(w.lineV1(6)).isEqualTo(1);
        assertThat(w.lineV2(6)).isEqualTo(4);
        assertThat(w.lineFlags(6)).isEqualTo(4);
        assertThat(w.lineSpecial(6)).isEqualTo(11);
        assertThat(w.lineTag(6)).isEqualTo(5);
        assertThat(w.lineFrontSide(6)).isEqualTo(6);
        assertThat(w.lineBackSide(6)).isEqualTo(7);
        assertThat(w.lineFrontSector(6)).isZero();
        assertThat(w.lineBackSector(6)).isEqualTo(1);
        assertThat(w.lineBackSide(0)).isEqualTo(-1);
        assertThat(w.lineBackSector(0)).isEqualTo(-1);

        assertThat(w.segCount()).isEqualTo(8);
        assertThat(w.segV1(1)).isEqualTo(1);
        assertThat(w.segV2(1)).isEqualTo(4);
        assertThat(w.segAngle(1)).isEqualTo(0x4000 << 16);
        assertThat(w.segLine(1)).isEqualTo(6);
        assertThat(w.segSide(4)).isEqualTo(7);
        assertThat(w.segFrontSector(4)).isEqualTo(1);
        assertThat(w.segBackSector(4)).isZero();
        assertThat(w.segBackSector(0)).isEqualTo(-1);

        assertThat(w.subsectorCount()).isEqualTo(2);
        assertThat(w.subsectorFirstSeg(1)).isEqualTo(4);
        assertThat(w.subsectorSegCount(1)).isEqualTo(4);
        assertThat(w.subsectorSector(1)).isEqualTo(1);

        assertThat(w.nodeCount()).isEqualTo(1);
        assertThat(w.nodeX(0)).isEqualTo(64 * FU);
        assertThat(w.nodeDy(0)).isEqualTo(64 * FU);
        assertThat(w.nodeBBox(0, 0, BBox.BOXTOP)).isEqualTo(64 * FU);
        assertThat(w.nodeBBox(0, 1, BBox.BOXRIGHT)).isEqualTo(64 * FU);
        assertThat(w.nodeChild(0, 0) & WorldView.NF_SUBSECTOR).isNotZero();
        assertThat(w.nodeChild(0, 0) & ~WorldView.NF_SUBSECTOR).isEqualTo(1);

        assertThat(w.blockmapOriginX()).isEqualTo(-64 * FU);
        assertThat(w.blockmapColumns()).isEqualTo(2);
        assertThat(w.blockmapRows()).isEqualTo(2);

        assertThat(w.textureTranslation(1)).isEqualTo(1);
        assertThat(w.flatTranslation(3)).isEqualTo(3);
        assertThat(w.skyTexture()).isEqualTo(2); // SKY1
        assertThat(w.skyFlat()).isEqualTo(3); // F_SKY1
    }

    @Test
    void missingMapAndMissingLumpsFailClearly() {
        LumpDirectory d = new TwoRoomMap().directory();
        assertThatThrownBy(() -> GameFactory.loadLevelGeometry(d, SHAREWARE, "E1M2"))
                .isInstanceOf(WadFormatException.class).hasMessageContaining("E1M2");
    }

    @Test
    void skyTextureFollowsEpisodeAndMapNumber() {
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.SHAREWARE, "E1M1")).isEqualTo("SKY1");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.REGISTERED, "E2M5")).isEqualTo("SKY2");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.REGISTERED, "E3M9")).isEqualTo("SKY3");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.RETAIL, "E4M2")).isEqualTo("SKY4");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.COMMERCIAL, "MAP01")).isEqualTo("SKY1");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.COMMERCIAL, "MAP11")).isEqualTo("SKY1");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.COMMERCIAL, "MAP12")).isEqualTo("SKY2");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.COMMERCIAL, "MAP20")).isEqualTo("SKY2");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.COMMERCIAL, "MAP21")).isEqualTo("SKY3");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.COMMERCIAL, "map32")).isEqualTo("SKY3");
        assertThat(LevelGeometryLoader.skyTextureName(GameMode.SHAREWARE, "TEST")).isEqualTo("SKY1");
    }
}
