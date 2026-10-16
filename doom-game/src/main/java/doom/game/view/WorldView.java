package doom.game.view;

/**
 * Level geometry and the dynamic state of sectors, sides and lines. Indices are positions in the map lumps
 * (sector {@code s} is the {@code s}-th SECTORS entry, and so on). Values are those of the end of the last tic;
 * {@code prev*} values are those of the start of the last tic, for interpolation (ADR-011).
 */
public interface WorldView {

    /** Map lump name, e.g. {@code "E1M1"} or {@code "MAP01"}; empty when no level is loaded. */
    String mapName();

    // ------------------------------------------------------------------ vertices

    int vertexCount();

    /** Fixed point. */
    int vertexX(int v);

    /** Fixed point. */
    int vertexY(int v);

    // ------------------------------------------------------------------ sectors

    int sectorCount();

    int sectorFloorHeight(int s);

    int sectorCeilingHeight(int s);

    /** Floor height at the start of the last tic (interpolation). */
    int sectorPrevFloorHeight(int s);

    /** Ceiling height at the start of the last tic (interpolation). */
    int sectorPrevCeilingHeight(int s);

    /** Raw flat number; draw {@link #flatTranslation(int)} of it. */
    int sectorFloorPic(int s);

    /** Raw flat number; draw {@link #flatTranslation(int)} of it. */
    int sectorCeilingPic(int s);

    /** 0–255. */
    int sectorLightLevel(int s);

    int sectorSpecial(int s);

    int sectorTag(int s);

    /** Sound origin of the sector ({@code sec->soundorg}), fixed point. */
    int sectorSoundOriginX(int s);

    /** Sound origin of the sector ({@code sec->soundorg}), fixed point. */
    int sectorSoundOriginY(int s);

    /** First actor id of the sector's thing list ({@code sec->thinglist}), or -1; continue with {@link ActorView#nextInSector}. */
    int sectorFirstActor(int s);

    /** Number of lines bordering the sector ({@code sec->linecount}). */
    int sectorLineCount(int s);

    /** The {@code i}-th line bordering the sector ({@code sec->lines[i]}). */
    int sectorLine(int s, int i);

    // ------------------------------------------------------------------ sides

    int sideCount();

    /** Horizontal texture offset, fixed point. */
    int sideTextureOffset(int sd);

    /** Horizontal texture offset at the start of the last tic (scrolling walls, interpolation). */
    int sidePrevTextureOffset(int sd);

    /** Vertical texture offset, fixed point. */
    int sideRowOffset(int sd);

    /** Raw texture number, 0 = no texture; draw {@link #textureTranslation(int)} of it. */
    int sideTopTexture(int sd);

    /** Raw texture number, 0 = no texture. */
    int sideBottomTexture(int sd);

    /** Raw texture number, 0 = no texture. */
    int sideMidTexture(int sd);

    int sideSector(int sd);

    // ------------------------------------------------------------------ lines

    int lineCount();

    int lineV1(int l);

    int lineV2(int l);

    /** {@link LineFlags} bits (without the renderer's "mapped" state, which the renderer keeps itself). */
    int lineFlags(int l);

    int lineSpecial(int l);

    int lineTag(int l);

    /** Front side index ({@code sidenum[0]}). */
    int lineFrontSide(int l);

    /** Back side index ({@code sidenum[1]}), or -1. */
    int lineBackSide(int l);

    int lineFrontSector(int l);

    /** Back sector, or -1 for one-sided lines. */
    int lineBackSector(int l);

    // ------------------------------------------------------------------ segs

    int segCount();

    int segV1(int g);

    int segV2(int g);

    /** BAM angle of the seg. */
    int segAngle(int g);

    /** Distance along the line from its start to the seg start, fixed point. */
    int segOffset(int g);

    int segLine(int g);

    /** Side index ({@code seg->sidedef}). */
    int segSide(int g);

    int segFrontSector(int g);

    /** Back sector, or -1 when the line is one-sided. */
    int segBackSector(int g);

    // ------------------------------------------------------------------ subsectors

    int subsectorCount();

    int subsectorFirstSeg(int ss);

    int subsectorSegCount(int ss);

    int subsectorSector(int ss);

    // ------------------------------------------------------------------ BSP nodes

    /** Number of nodes; the root is {@code nodeCount() - 1}. A map with a single subsector has no nodes. */
    int nodeCount();

    /** Partition line start, fixed point. */
    int nodeX(int n);

    int nodeY(int n);

    /** Partition line direction, fixed point. */
    int nodeDx(int n);

    int nodeDy(int n);

    /**
     * Bounding box of a child.
     *
     * @param child 0 = right (front) child, 1 = left (back) child
     * @param coord {@code doom.math.BBox.BOXTOP}, {@code BOXBOTTOM}, {@code BOXLEFT} or {@code BOXRIGHT}
     */
    int nodeBBox(int n, int child, int coord);

    /** Child reference; when bit {@link #NF_SUBSECTOR} is set, the low bits are a subsector index. */
    int nodeChild(int n, int child);

    /** Flag in {@link #nodeChild} marking a subsector ({@code NF_SUBSECTOR}). */
    int NF_SUBSECTOR = 0x8000;

    // ------------------------------------------------------------------ blockmap (automap grid)

    /** Blockmap origin, fixed point. */
    int blockmapOriginX();

    int blockmapOriginY();

    int blockmapColumns();

    int blockmapRows();

    // ------------------------------------------------------------------ animation state

    /** Current texture to draw for raw texture number {@code texture} ({@code texturetranslation[]}). */
    int textureTranslation(int texture);

    /** Current flat to draw for raw flat number {@code flat} ({@code flattranslation[]}). */
    int flatTranslation(int flat);

    /** Sky texture number ({@code skytexture}). */
    int skyTexture();

    /** Flat number that means "sky" ({@code skyflatnum}, F_SKY1). */
    int skyFlat();
}
