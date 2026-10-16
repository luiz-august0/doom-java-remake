package doom.game.impl;

import doom.game.actor.Actor;
import doom.game.view.WorldView;
import doom.game.world.Level;
import doom.game.world.Sector;
import doom.game.world.World;

/**
 * The session's {@link WorldView}: the level currently in memory, with its dynamic state (heights, light, textures,
 * thing lists, interpolation values, animation translations). Counts are 0 when no level is loaded.
 */
final class SimWorldView implements WorldView {

    private final World w;

    SimWorldView(World world) {
        this.w = world;
    }

    private Level level() {
        return w.level;
    }

    @Override
    public String mapName() {
        return level() == null ? "" : level().name;
    }

    // vertices

    @Override
    public int vertexCount() {
        return level() == null ? 0 : level().vertexes.length;
    }

    @Override
    public int vertexX(int v) {
        return level().vertexes[v].x;
    }

    @Override
    public int vertexY(int v) {
        return level().vertexes[v].y;
    }

    // sectors

    @Override
    public int sectorCount() {
        return level() == null ? 0 : level().sectors.length;
    }

    @Override
    public int sectorFloorHeight(int s) {
        return level().sectors[s].floorHeight;
    }

    @Override
    public int sectorCeilingHeight(int s) {
        return level().sectors[s].ceilingHeight;
    }

    @Override
    public int sectorPrevFloorHeight(int s) {
        return level().sectors[s].prevFloorHeight;
    }

    @Override
    public int sectorPrevCeilingHeight(int s) {
        return level().sectors[s].prevCeilingHeight;
    }

    @Override
    public int sectorFloorPic(int s) {
        return level().sectors[s].floorPic;
    }

    @Override
    public int sectorCeilingPic(int s) {
        return level().sectors[s].ceilingPic;
    }

    @Override
    public int sectorLightLevel(int s) {
        return level().sectors[s].lightLevel;
    }

    @Override
    public int sectorSpecial(int s) {
        return level().sectors[s].special;
    }

    @Override
    public int sectorTag(int s) {
        return level().sectors[s].tag;
    }

    @Override
    public int sectorSoundOriginX(int s) {
        return level().sectors[s].soundOrgX;
    }

    @Override
    public int sectorSoundOriginY(int s) {
        return level().sectors[s].soundOrgY;
    }

    @Override
    public int sectorFirstActor(int s) {
        Actor a = level().sectors[s].thingList;
        return a == null ? -1 : a.id;
    }

    @Override
    public int sectorLineCount(int s) {
        return level().sectors[s].lineCount;
    }

    @Override
    public int sectorLine(int s, int i) {
        return level().sectors[s].lines[i].index;
    }

    // sides

    @Override
    public int sideCount() {
        return level() == null ? 0 : level().sides.length;
    }

    @Override
    public int sideTextureOffset(int sd) {
        return level().sides[sd].textureOffset;
    }

    @Override
    public int sidePrevTextureOffset(int sd) {
        return level().sides[sd].prevTextureOffset;
    }

    @Override
    public int sideRowOffset(int sd) {
        return level().sides[sd].rowOffset;
    }

    @Override
    public int sideTopTexture(int sd) {
        return level().sides[sd].topTexture;
    }

    @Override
    public int sideBottomTexture(int sd) {
        return level().sides[sd].bottomTexture;
    }

    @Override
    public int sideMidTexture(int sd) {
        return level().sides[sd].midTexture;
    }

    @Override
    public int sideSector(int sd) {
        return level().sides[sd].sector.index;
    }

    // lines

    @Override
    public int lineCount() {
        return level() == null ? 0 : level().lines.length;
    }

    @Override
    public int lineV1(int l) {
        return level().lines[l].v1.index;
    }

    @Override
    public int lineV2(int l) {
        return level().lines[l].v2.index;
    }

    @Override
    public int lineFlags(int l) {
        return level().lines[l].flags;
    }

    @Override
    public int lineSpecial(int l) {
        return level().lines[l].special;
    }

    @Override
    public int lineTag(int l) {
        return level().lines[l].tag;
    }

    @Override
    public int lineFrontSide(int l) {
        return level().lines[l].sideNum[0];
    }

    @Override
    public int lineBackSide(int l) {
        return level().lines[l].sideNum[1];
    }

    @Override
    public int lineFrontSector(int l) {
        return level().lines[l].frontSector.index;
    }

    @Override
    public int lineBackSector(int l) {
        Sector s = level().lines[l].backSector;
        return s == null ? -1 : s.index;
    }

    // segs

    @Override
    public int segCount() {
        return level() == null ? 0 : level().segs.length;
    }

    @Override
    public int segV1(int g) {
        return level().segs[g].v1.index;
    }

    @Override
    public int segV2(int g) {
        return level().segs[g].v2.index;
    }

    @Override
    public int segAngle(int g) {
        return level().segs[g].angle;
    }

    @Override
    public int segOffset(int g) {
        return level().segs[g].offset;
    }

    @Override
    public int segLine(int g) {
        return level().segs[g].lineDef.index;
    }

    @Override
    public int segSide(int g) {
        return level().segs[g].sideDef.index;
    }

    @Override
    public int segFrontSector(int g) {
        return level().segs[g].frontSector.index;
    }

    /** -1 for one-sided lines and for the null sector of the "glass hack". */
    @Override
    public int segBackSector(int g) {
        Sector s = level().segs[g].backSector;
        return s == null ? -1 : s.index;
    }

    // subsectors

    @Override
    public int subsectorCount() {
        return level() == null ? 0 : level().subsectors.length;
    }

    @Override
    public int subsectorFirstSeg(int ss) {
        return level().subsectors[ss].firstLine;
    }

    @Override
    public int subsectorSegCount(int ss) {
        return level().subsectors[ss].numLines;
    }

    @Override
    public int subsectorSector(int ss) {
        return level().subsectors[ss].sector.index;
    }

    // nodes

    @Override
    public int nodeCount() {
        return level() == null ? 0 : level().nodes.length;
    }

    @Override
    public int nodeX(int n) {
        return level().nodes[n].x;
    }

    @Override
    public int nodeY(int n) {
        return level().nodes[n].y;
    }

    @Override
    public int nodeDx(int n) {
        return level().nodes[n].dx;
    }

    @Override
    public int nodeDy(int n) {
        return level().nodes[n].dy;
    }

    @Override
    public int nodeBBox(int n, int child, int coord) {
        return level().nodes[n].bbox[child][coord];
    }

    @Override
    public int nodeChild(int n, int child) {
        return level().nodes[n].children[child];
    }

    // blockmap

    @Override
    public int blockmapOriginX() {
        return level() == null ? 0 : level().blockmap.originX;
    }

    @Override
    public int blockmapOriginY() {
        return level() == null ? 0 : level().blockmap.originY;
    }

    @Override
    public int blockmapColumns() {
        return level() == null ? 0 : level().blockmap.columns;
    }

    @Override
    public int blockmapRows() {
        return level() == null ? 0 : level().blockmap.rows;
    }

    // animation state

    @Override
    public int textureTranslation(int texture) {
        return w.specials.textureTranslation(texture);
    }

    @Override
    public int flatTranslation(int flat) {
        return w.specials.flatTranslation(flat);
    }

    @Override
    public int skyTexture() {
        return w.skyTexture;
    }

    @Override
    public int skyFlat() {
        return w.skyFlatNum;
    }
}
