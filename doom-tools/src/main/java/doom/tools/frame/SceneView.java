package doom.tools.frame;

import doom.game.view.ActorView;
import doom.game.view.PlayerState;
import doom.game.view.PlayerView;
import doom.game.view.WorldView;
import java.util.Arrays;

/**
 * Test fixture: the views of one oracle frame. Static map geometry comes from a {@link WorldView} loaded from the
 * WAD; the dynamic part (sector heights and light, side textures, translations, things per sector in {@code snext}
 * order, psprites, powers, sky) comes from the parsed {@link SceneSidecar}.
 *
 * <p>Implements {@link WorldView}, {@link ActorView} and {@link PlayerView} in one object. Actor ids are the positions
 * in the sidecar's {@code T} list.
 */
public final class SceneView implements WorldView, ActorView, PlayerView {

    private final WorldView geo;
    private final SceneSidecar sc;
    private final int[] firstInSector;
    private final int viewActor;

    public SceneView(WorldView geometry, SceneSidecar sidecar) {
        this.geo = geometry;
        this.sc = sidecar;
        this.firstInSector = new int[geometry.sectorCount()];
        Arrays.fill(firstInSector, -1);
        int actor = -1;
        for (int i = sc.thingSector.length - 1; i >= 0; i--) {
            firstInSector[sc.thingSector[i]] = i;
        }
        for (int i = 0; i < sc.thingPlayer.length; i++) {
            if (sc.thingPlayer[i] == sc.displayPlayer) {
                actor = i;
            }
        }
        this.viewActor = actor;
    }

    /** Actor id of the display player's body, or -1. */
    public int viewActor() {
        return viewActor;
    }

    public SceneSidecar sidecar() {
        return sc;
    }

    // ============================================================================================ WorldView

    @Override public String mapName() { return geo.mapName(); }
    @Override public int vertexCount() { return geo.vertexCount(); }
    @Override public int vertexX(int v) { return geo.vertexX(v); }
    @Override public int vertexY(int v) { return geo.vertexY(v); }
    @Override public int sectorCount() { return geo.sectorCount(); }
    @Override public int sectorFloorHeight(int s) { return sc.sectorFloor[s]; }
    @Override public int sectorCeilingHeight(int s) { return sc.sectorCeiling[s]; }
    @Override public int sectorPrevFloorHeight(int s) { return sc.sectorFloor[s]; }
    @Override public int sectorPrevCeilingHeight(int s) { return sc.sectorCeiling[s]; }
    @Override public int sectorFloorPic(int s) { return sc.sectorFloorPic[s]; }
    @Override public int sectorCeilingPic(int s) { return sc.sectorCeilingPic[s]; }
    @Override public int sectorLightLevel(int s) { return sc.sectorLight[s]; }
    @Override public int sectorSpecial(int s) { return geo.sectorSpecial(s); }
    @Override public int sectorTag(int s) { return geo.sectorTag(s); }
    @Override public int sectorSoundOriginX(int s) { return geo.sectorSoundOriginX(s); }
    @Override public int sectorSoundOriginY(int s) { return geo.sectorSoundOriginY(s); }
    @Override public int sectorFirstActor(int s) { return firstInSector[s]; }
    @Override public int sectorLineCount(int s) { return geo.sectorLineCount(s); }
    @Override public int sectorLine(int s, int i) { return geo.sectorLine(s, i); }

    @Override public int sideCount() { return geo.sideCount(); }
    @Override public int sideTextureOffset(int sd) { return sc.sideTextureOffset[sd]; }
    @Override public int sidePrevTextureOffset(int sd) { return sc.sideTextureOffset[sd]; }
    @Override public int sideRowOffset(int sd) { return sc.sideRowOffset[sd]; }
    @Override public int sideTopTexture(int sd) { return sc.sideTop[sd]; }
    @Override public int sideBottomTexture(int sd) { return sc.sideBottom[sd]; }
    @Override public int sideMidTexture(int sd) { return sc.sideMid[sd]; }
    @Override public int sideSector(int sd) { return geo.sideSector(sd); }

    @Override public int lineCount() { return geo.lineCount(); }
    @Override public int lineV1(int l) { return geo.lineV1(l); }
    @Override public int lineV2(int l) { return geo.lineV2(l); }
    @Override public int lineFlags(int l) { return geo.lineFlags(l); }
    @Override public int lineSpecial(int l) { return geo.lineSpecial(l); }
    @Override public int lineTag(int l) { return geo.lineTag(l); }
    @Override public int lineFrontSide(int l) { return geo.lineFrontSide(l); }
    @Override public int lineBackSide(int l) { return geo.lineBackSide(l); }
    @Override public int lineFrontSector(int l) { return geo.lineFrontSector(l); }
    @Override public int lineBackSector(int l) { return geo.lineBackSector(l); }

    @Override public int segCount() { return geo.segCount(); }
    @Override public int segV1(int g) { return geo.segV1(g); }
    @Override public int segV2(int g) { return geo.segV2(g); }
    @Override public int segAngle(int g) { return geo.segAngle(g); }
    @Override public int segOffset(int g) { return geo.segOffset(g); }
    @Override public int segLine(int g) { return geo.segLine(g); }
    @Override public int segSide(int g) { return geo.segSide(g); }
    @Override public int segFrontSector(int g) { return geo.segFrontSector(g); }
    @Override public int segBackSector(int g) { return geo.segBackSector(g); }

    @Override public int subsectorCount() { return geo.subsectorCount(); }
    @Override public int subsectorFirstSeg(int ss) { return geo.subsectorFirstSeg(ss); }
    @Override public int subsectorSegCount(int ss) { return geo.subsectorSegCount(ss); }
    @Override public int subsectorSector(int ss) { return geo.subsectorSector(ss); }

    @Override public int nodeCount() { return geo.nodeCount(); }
    @Override public int nodeX(int n) { return geo.nodeX(n); }
    @Override public int nodeY(int n) { return geo.nodeY(n); }
    @Override public int nodeDx(int n) { return geo.nodeDx(n); }
    @Override public int nodeDy(int n) { return geo.nodeDy(n); }
    @Override public int nodeBBox(int n, int child, int coord) { return geo.nodeBBox(n, child, coord); }
    @Override public int nodeChild(int n, int child) { return geo.nodeChild(n, child); }

    @Override public int blockmapOriginX() { return geo.blockmapOriginX(); }
    @Override public int blockmapOriginY() { return geo.blockmapOriginY(); }
    @Override public int blockmapColumns() { return geo.blockmapColumns(); }
    @Override public int blockmapRows() { return geo.blockmapRows(); }

    @Override public int textureTranslation(int texture) { return sc.textureTranslation[texture]; }
    @Override public int flatTranslation(int flat) { return sc.flatTranslation[flat]; }
    @Override public int skyTexture() { return sc.skyTexture; }
    @Override public int skyFlat() { return sc.skyFlat; }

    // ============================================================================================ ActorView

    @Override public int capacity() { return sc.thingSector.length; }
    @Override public boolean exists(int id) { return id >= 0 && id < sc.thingSector.length; }
    @Override public int type(int id) { return 0; }
    @Override public int x(int id) { return sc.thingX[id]; }
    @Override public int y(int id) { return sc.thingY[id]; }
    @Override public int z(int id) { return sc.thingZ[id]; }
    @Override public int angle(int id) { return sc.thingAngle[id]; }
    @Override public int prevX(int id) { return sc.thingX[id]; }
    @Override public int prevY(int id) { return sc.thingY[id]; }
    @Override public int prevZ(int id) { return sc.thingZ[id]; }
    @Override public int prevAngle(int id) { return sc.thingAngle[id]; }
    @Override public int sprite(int id) { return sc.thingSprite[id]; }
    @Override public int frame(int id) { return sc.thingFrame[id]; }
    @Override public int flags(int id) { return sc.thingFlags[id]; }
    @Override public int radius(int id) { return 0; }
    @Override public int height(int id) { return 0; }
    @Override public int health(int id) { return 1; }
    @Override public int floorZ(int id) { return sc.sectorFloor[sc.thingSector[id]]; }
    @Override public int ceilingZ(int id) { return sc.sectorCeiling[sc.thingSector[id]]; }
    @Override public int player(int id) { return sc.thingPlayer[id]; }
    @Override public int sector(int id) { return sc.thingSector[id]; }

    @Override
    public int nextInSector(int id) {
        int n = id + 1;
        return n < sc.thingSector.length && sc.thingSector[n] == sc.thingSector[id] ? n : -1;
    }

    @Override public int first() { return sc.thingSector.length > 0 ? 0 : -1; }
    @Override public int next(int id) { return id + 1 < sc.thingSector.length ? id + 1 : -1; }

    // ============================================================================================ PlayerView

    @Override public boolean inGame() { return true; }
    @Override public PlayerState state() { return PlayerState.LIVE; }
    @Override public int actor() { return viewActor; }
    @Override public int x() { return sc.viewX; }
    @Override public int y() { return sc.viewY; }
    @Override public int z() { return sc.viewZ; }
    @Override public int angle() { return sc.viewAngle; }
    @Override public int viewZ() { return sc.viewZ; }
    @Override public int prevViewZ() { return sc.viewZ; }
    @Override public int viewHeight() { return 0; }
    @Override public int deltaViewHeight() { return 0; }
    @Override public int bob() { return 0; }
    @Override public int health() { return 100; }
    @Override public int armorPoints() { return 0; }
    @Override public int armorType() { return 0; }
    @Override public int readyWeapon() { return 0; }
    @Override public int pendingWeapon() { return 0; }
    @Override public boolean weaponOwned(int weapon) { return false; }
    @Override public int ammo(int ammoType) { return 0; }
    @Override public int maxAmmo(int ammoType) { return 0; }
    @Override public boolean card(int card) { return false; }
    @Override public boolean backpack() { return false; }
    @Override public int power(int power) { return sc.powers[power]; }
    @Override public int cheats() { return 0; }
    @Override public int damageCount() { return 0; }
    @Override public int bonusCount() { return 0; }
    @Override public int extraLight() { return sc.extraLight; }
    @Override public int fixedColormap() { return sc.fixedColormap; }
    @Override public int colormap() { return 0; }
    @Override public int attacker() { return -1; }
    @Override public int killCount() { return 0; }
    @Override public int itemCount() { return 0; }
    @Override public int secretCount() { return 0; }
    @Override public int frags(int otherPlayer) { return 0; }
    @Override public boolean didSecret() { return false; }
    @Override public boolean pspriteActive(int which) { return sc.pspriteSprite[which] >= 0; }
    @Override public int pspriteSprite(int which) { return sc.pspriteSprite[which]; }
    @Override public int pspriteFrame(int which) { return sc.pspriteFrame[which]; }
    @Override public int pspriteSx(int which) { return sc.pspriteSx[which]; }
    @Override public int pspriteSy(int which) { return sc.pspriteSy[which]; }
    @Override public int pspritePrevSx(int which) { return sc.pspriteSx[which]; }
    @Override public int pspritePrevSy(int which) { return sc.pspriteSy[which]; }
}
