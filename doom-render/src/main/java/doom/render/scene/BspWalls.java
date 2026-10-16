package doom.render.scene;

import doom.game.view.LineFlags;
import doom.game.view.WorldView;
import doom.math.Angle;
import doom.math.BBox;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.Trig;
import doom.math.annotation.VanillaRef;
import doom.render.texture.WallTexture;
import java.util.Arrays;

/**
 * BSP traversal, segment clipping and wall drawing: {@code r_bsp.c} and {@code r_segs.c} (minus the masked-seg
 * drawing, which lives with the sprites). Drawsegs, solid-seg ranges and the opening pool grow on demand.
 */
final class BspWalls {

    static final int SIL_BOTTOM = 1;
    static final int SIL_TOP = 2;
    static final int SIL_BOTH = 3;

    private static final int HEIGHTBITS = 12;
    private static final int HEIGHTUNIT = 1 << HEIGHTBITS;

    /** {@code checkcoord[12][4]}. */
    private static final int[][] CHECKCOORD = {
        {3, 0, 2, 1},
        {3, 0, 2, 0},
        {3, 1, 2, 0},
        {0, 0, 0, 0},
        {2, 0, 2, 1},
        {0, 0, 0, 0},
        {3, 1, 3, 0},
        {0, 0, 0, 0},
        {2, 0, 3, 1},
        {2, 1, 3, 1},
        {2, 1, 3, 0}
    };

    private final RenderState rs;
    private final Draw draw;
    private final Planes planes;
    private final Things things;

    WorldView world;
    /** Lines seen so far ({@code ML_MAPPED}); kept by the renderer. */
    boolean[] lineMapped = new boolean[0];

    // drawsegs
    int numDrawSegs;
    int[] dsX1 = new int[256];
    int[] dsX2 = new int[256];
    int[] dsCurline = new int[256];
    int[] dsScale1 = new int[256];
    int[] dsScale2 = new int[256];
    int[] dsScaleStep = new int[256];
    /** Precise mode: exact scale at {@code dsX1} and per-column step (pixels per unit). */
    double[] dsScale1D = new double[256];
    double[] dsScaleStepD = new double[256];
    int[] dsSilhouette = new int[256];
    int[] dsBSilHeight = new int[256];
    int[] dsTSilHeight = new int[256];
    int[] dsSprTopClip = new int[256];
    int[] dsSprBottomClip = new int[256];
    int[] dsMaskedTexCol = new int[256];

    // solidsegs
    private int[] solidFirst = new int[16];
    private int[] solidLast = new int[16];
    private int newend;

    // current seg
    int curline;
    int frontsector;
    int backsector;
    private int sidedef;
    private int linedef;

    // r_segs.c state
    private boolean segtextured;
    private boolean markfloor;
    private boolean markceiling;
    private boolean maskedtexture;
    private int toptexture;
    private int bottomtexture;
    private int midtexture;
    private int rwNormalAngle;
    private int rwAngle1;
    private int rwX;
    private int rwStopX;
    private int rwCenterAngle;
    private int rwOffset;
    private int rwDistance;
    private int rwScale;
    private int rwScaleStep;
    private int rwMidTextureMid;
    private int rwTopTextureMid;
    private int rwBottomTextureMid;
    private int worldTop;
    private int worldBottom;
    private int worldHigh;
    private int worldLow;
    private int pixHigh;
    private int pixLow;
    private int pixHighStep;
    private int pixLowStep;
    private int topFrac;
    private int topStep;
    private int bottomFrac;
    private int bottomStep;
    /** {@code walllights}: the active scale light row. */
    int[] wallLights;
    /** Base of {@code maskedtexturecol} in the openings pool for the seg being stored. */
    private int maskedTextureCol;

    private final int[] bbox = new int[4];

    // precise mode (RenderOptions.precise): the current wall as exact line geometry, see preciseWallSetup
    /** Distance from the eye to the wall's line, in units. */
    private double pDist;
    /** Components of the wall normal and of the wall direction in view space (forward, left). */
    private double pNf;
    private double pNl;
    private double pTf;
    private double pTl;
    /** Texture position of the point of the line nearest to the eye, in texels. */
    private double pOffset;
    /** Unclamped scale at {@code rwX} and its per-column step, in pixels per unit. */
    private double pScale0;
    private double pScaleStep;
    /** World heights relative to the eye, in units. */
    private double pWt;
    private double pWb;
    private double pWh;
    private double pWl;

    BspWalls(RenderState rs, Draw draw, Planes planes, Things things) {
        this.rs = rs;
        this.draw = draw;
        this.planes = planes;
        this.things = things;
    }

    void resize() {
        int need = rs.viewWidth + 8;
        if (solidFirst.length < need) {
            solidFirst = new int[need];
            solidLast = new int[need];
        }
    }

    /** Makes {@link #lineMapped} match the level and forgets the marks. */
    void resetMapped(int lineCount) {
        if (lineMapped.length != lineCount) {
            lineMapped = new boolean[lineCount];
        } else {
            Arrays.fill(lineMapped, false);
        }
    }

    // ===================================================================================== helpers

    private int pointToAngle(int x, int y) {
        if (rs.precise) {
            // full precision: not through the 2048-entry tantoangle table
            long dx = (long) x - rs.viewX;
            long dy = (long) y - rs.viewY;
            if (dx == 0 && dy == 0) {
                return 0;
            }
            return RenderState.bamFromRadians(Math.atan2(dy, dx));
        }
        return Trig.pointToAngle2(rs.viewX, rs.viewY, x, y);
    }

    /**
     * First screen column right of the view-relative angle {@code rel} (BAM, within the clip angle): the
     * {@code viewangletox} lookup, or the exact tangent in precise mode.
     */
    private int columnOfAngle(int rel) {
        if (rs.precise) {
            // the clip angle is the screen edge exactly (also for odd widths, where it is not symmetric)
            if (rel >= rs.clipAngle) {
                return 0;
            }
            if (rel <= -rs.clipAngle) {
                return rs.viewWidth;
            }
            double x = Math.ceil(rs.centerX - Math.tan(rel * RenderState.BAM_TO_RAD) * rs.projD - 1e-3);
            if (x < 0) {
                return 0;
            }
            return x > rs.viewWidth ? rs.viewWidth : (int) x;
        }
        return rs.viewAngleToX[(rel + Angle.ANG90) >>> Angle.ANGLETOFINESHIFT];
    }

    /** {@code R_PointToDist}. */
    private int pointToDist(int x, int y) {
        int dx = Math.abs(x - rs.viewX);
        int dy = Math.abs(y - rs.viewY);
        if (dy > dx) {
            int t = dx;
            dx = dy;
            dy = t;
        }
        int frac = dx != 0 ? Fixed.div(dy, dx) : 0;
        int angle = (Tables.tanToAngle(frac >> Tables.DBITS) + Angle.ANG90) >>> Angle.ANGLETOFINESHIFT;
        return Fixed.div(dx, Tables.fineSine(angle));
    }

    /** {@code R_PointOnSide} against node {@code n}. */
    @VanillaRef("r_main.c:R_PointOnSide")
    private int pointOnSide(int x, int y, int n) {
        int nx = world.nodeX(n);
        int ny = world.nodeY(n);
        int ndx = world.nodeDx(n);
        int ndy = world.nodeDy(n);
        if (ndx == 0) {
            if (x <= nx) {
                return ndy > 0 ? 1 : 0;
            }
            return ndy < 0 ? 1 : 0;
        }
        if (ndy == 0) {
            if (y <= ny) {
                return ndx < 0 ? 1 : 0;
            }
            return ndx > 0 ? 1 : 0;
        }
        int dx = x - nx;
        int dy = y - ny;
        if (((ndy ^ ndx ^ dx ^ dy) & 0x80000000) != 0) {
            if (((ndy ^ dx) & 0x80000000) != 0) {
                return 1;
            }
            return 0;
        }
        int left = Fixed.mul(ndy >> Fixed.FRACBITS, dx);
        int right = Fixed.mul(dy, ndx >> Fixed.FRACBITS);
        return right < left ? 0 : 1;
    }

    /** {@code R_PointOnSegSide}. */
    @VanillaRef("r_main.c:R_PointOnSegSide")
    int pointOnSegSide(int x, int y, int seg) {
        int v1 = world.segV1(seg);
        int v2 = world.segV2(seg);
        int lx = world.vertexX(v1);
        int ly = world.vertexY(v1);
        int ldx = world.vertexX(v2) - lx;
        int ldy = world.vertexY(v2) - ly;
        if (ldx == 0) {
            if (x <= lx) {
                return ldy > 0 ? 1 : 0;
            }
            return ldy < 0 ? 1 : 0;
        }
        if (ldy == 0) {
            if (y <= ly) {
                return ldx < 0 ? 1 : 0;
            }
            return ldx > 0 ? 1 : 0;
        }
        int dx = x - lx;
        int dy = y - ly;
        if (((ldy ^ ldx ^ dx ^ dy) & 0x80000000) != 0) {
            if (((ldy ^ dx) & 0x80000000) != 0) {
                return 1;
            }
            return 0;
        }
        int left = Fixed.mul(ldy >> Fixed.FRACBITS, dx);
        int right = Fixed.mul(dy, ldx >> Fixed.FRACBITS);
        return right < left ? 0 : 1;
    }

    /**
     * {@code finetangent[angle]} for an index that may run past the table's 4096 entries (a wall seen at an extreme
     * angle): the original reads the next table in memory, {@code finesine}, from its start.
     */
    @VanillaRef("tables.c:finetangent")
    private static int fineTangentOverrun(int angle) {
        // VANILLA-BUG: reads past finetangent[] into finesine[]
        return angle < Angle.FINEANGLES / 2 ? Tables.fineTangent(angle) : Tables.fineSine(angle - Angle.FINEANGLES / 2);
    }

    /** {@code R_ScaleFromGlobalAngle}. */
    @VanillaRef("r_main.c:R_ScaleFromGlobalAngle")
    private int scaleFromGlobalAngle(int visangle) {
        int anglea = Angle.ANG90 + (visangle - rs.viewAngle);
        int angleb = Angle.ANG90 + (visangle - rwNormalAngle);
        int sinea = Tables.fineSine(anglea >>> Angle.ANGLETOFINESHIFT);
        int sineb = Tables.fineSine(angleb >>> Angle.ANGLETOFINESHIFT);
        int num = Fixed.mul(rs.projection, sineb) << rs.detailShift;
        int den = Fixed.mul(rwDistance, sinea);
        int scale;
        if (den > (num >> Fixed.FRACBITS)) {
            scale = Fixed.div(num, den);
            if (scale > rs.maxScale) {
                scale = rs.maxScale;
            } else if (scale < 256) {
                scale = 256;
            }
        } else {
            scale = rs.maxScale;
        }
        return scale;
    }

    // ===================================================================================== frame setup

    /** {@code R_ClearClipSegs} + {@code R_ClearDrawSegs}. */
    void clearFrame() {
        solidFirst[0] = -0x7fffffff;
        solidLast[0] = -1;
        solidFirst[1] = rs.viewWidth;
        solidLast[1] = 0x7fffffff;
        newend = 2;
        numDrawSegs = 0;
    }

    // ===================================================================================== BSP

    /** {@code R_PointInSubsector}, returning the sector. */
    int sectorAt(int x, int y) {
        int nodes = world.nodeCount();
        if (nodes == 0) {
            return world.subsectorSector(0);
        }
        int nodenum = nodes - 1;
        while ((nodenum & WorldView.NF_SUBSECTOR) == 0) {
            int side = pointOnSide(x, y, nodenum);
            nodenum = world.nodeChild(nodenum, side);
        }
        return world.subsectorSector(nodenum & ~WorldView.NF_SUBSECTOR);
    }

    /** {@code R_RenderBSPNode} from the root. */
    void renderBsp() {
        int nodes = world.nodeCount();
        if (nodes == 0) {
            subsector(0);
            return;
        }
        renderBspNode(nodes - 1);
    }

    private void renderBspNode(int bspnum) {
        if ((bspnum & WorldView.NF_SUBSECTOR) != 0) {
            if (bspnum == -1) {
                subsector(0);
            } else {
                subsector(bspnum & ~WorldView.NF_SUBSECTOR);
            }
            return;
        }
        int side = pointOnSide(rs.viewX, rs.viewY, bspnum);
        renderBspNode(world.nodeChild(bspnum, side));
        if (checkBBox(bspnum, side ^ 1)) {
            renderBspNode(world.nodeChild(bspnum, side ^ 1));
        }
    }

    /** {@code R_Subsector}. */
    @VanillaRef("r_bsp.c:R_Subsector")
    private void subsector(int num) {
        frontsector = world.subsectorSector(num);
        int count = world.subsectorSegCount(num);
        int line = world.subsectorFirstSeg(num);
        int floorH = world.sectorFloorHeight(frontsector);
        int ceilH = world.sectorCeilingHeight(frontsector);
        int light = world.sectorLightLevel(frontsector);
        if (floorH < rs.viewZ) {
            planes.floorplane = planes.findPlane(floorH, world.sectorFloorPic(frontsector), light);
        } else {
            planes.floorplane = null;
        }
        if (ceilH > rs.viewZ || world.sectorCeilingPic(frontsector) == rs.skyFlat) {
            planes.ceilingplane = planes.findPlane(ceilH, world.sectorCeilingPic(frontsector), light);
        } else {
            planes.ceilingplane = null;
        }
        things.addSprites(frontsector);
        while (count-- > 0) {
            addLine(line);
            line++;
        }
    }

    /** {@code R_CheckBBox} for child {@code side} of node {@code n}. */
    @VanillaRef("r_bsp.c:R_CheckBBox")
    private boolean checkBBox(int n, int side) {
        for (int i = 0; i < 4; i++) {
            bbox[i] = world.nodeBBox(n, side, i);
        }
        int boxx;
        int boxy;
        if (rs.viewX <= bbox[BBox.BOXLEFT]) {
            boxx = 0;
        } else if (rs.viewX < bbox[BBox.BOXRIGHT]) {
            boxx = 1;
        } else {
            boxx = 2;
        }
        if (rs.viewY >= bbox[BBox.BOXTOP]) {
            boxy = 0;
        } else if (rs.viewY > bbox[BBox.BOXBOTTOM]) {
            boxy = 1;
        } else {
            boxy = 2;
        }
        int boxpos = (boxy << 2) + boxx;
        if (boxpos == 5) {
            return true;
        }
        int[] cc = CHECKCOORD[boxpos];
        int x1 = bbox[cc[0]];
        int y1 = bbox[cc[1]];
        int x2 = bbox[cc[2]];
        int y2 = bbox[cc[3]];

        int angle1 = pointToAngle(x1, y1) - rs.viewAngle;
        int angle2 = pointToAngle(x2, y2) - rs.viewAngle;
        int span = angle1 - angle2;
        if (Integer.compareUnsigned(span, Angle.ANG180) >= 0) {
            return true;
        }
        final int clip = rs.clipAngle;
        int tspan = angle1 + clip;
        if (Integer.compareUnsigned(tspan, 2 * clip) > 0) {
            tspan -= 2 * clip;
            if (Integer.compareUnsigned(tspan, span) >= 0) {
                return false;
            }
            angle1 = clip;
        }
        tspan = clip - angle2;
        if (Integer.compareUnsigned(tspan, 2 * clip) > 0) {
            tspan -= 2 * clip;
            if (Integer.compareUnsigned(tspan, span) >= 0) {
                return false;
            }
            angle2 = -clip;
        }
        int sx1 = columnOfAngle(angle1);
        int sx2 = columnOfAngle(angle2);
        if (sx1 == sx2) {
            return false;
        }
        sx2--;
        int start = 0;
        while (solidLast[start] < sx2) {
            start++;
        }
        return !(sx1 >= solidFirst[start] && sx2 <= solidLast[start]);
    }

    /** {@code R_AddLine}. */
    @VanillaRef("r_bsp.c:R_AddLine")
    private void addLine(int line) {
        curline = line;
        int v1 = world.segV1(line);
        int v2 = world.segV2(line);
        int angle1 = pointToAngle(world.vertexX(v1), world.vertexY(v1));
        int angle2 = pointToAngle(world.vertexX(v2), world.vertexY(v2));
        int span = angle1 - angle2;
        if (Integer.compareUnsigned(span, Angle.ANG180) >= 0) {
            return;
        }
        rwAngle1 = angle1;
        angle1 -= rs.viewAngle;
        angle2 -= rs.viewAngle;
        final int clip = rs.clipAngle;
        int tspan = angle1 + clip;
        if (Integer.compareUnsigned(tspan, 2 * clip) > 0) {
            tspan -= 2 * clip;
            if (Integer.compareUnsigned(tspan, span) >= 0) {
                return;
            }
            angle1 = clip;
        }
        tspan = clip - angle2;
        if (Integer.compareUnsigned(tspan, 2 * clip) > 0) {
            tspan -= 2 * clip;
            if (Integer.compareUnsigned(tspan, span) >= 0) {
                return;
            }
            angle2 = -clip;
        }
        int x1 = columnOfAngle(angle1);
        int x2 = columnOfAngle(angle2);
        if (x1 == x2) {
            return;
        }
        backsector = world.segBackSector(line);
        if (backsector < 0) {
            clipSolidWallSegment(x1, x2 - 1);
            return;
        }
        int fFloor = world.sectorFloorHeight(frontsector);
        int fCeil = world.sectorCeilingHeight(frontsector);
        int bFloor = world.sectorFloorHeight(backsector);
        int bCeil = world.sectorCeilingHeight(backsector);
        if (bCeil <= fFloor || bFloor >= fCeil) {
            clipSolidWallSegment(x1, x2 - 1);
            return;
        }
        if (bCeil != fCeil || bFloor != fFloor) {
            clipPassWallSegment(x1, x2 - 1);
            return;
        }
        if (world.sectorCeilingPic(backsector) == world.sectorCeilingPic(frontsector)
                && world.sectorFloorPic(backsector) == world.sectorFloorPic(frontsector)
                && world.sectorLightLevel(backsector) == world.sectorLightLevel(frontsector)
                && world.sideMidTexture(world.segSide(line)) == 0) {
            return;
        }
        clipPassWallSegment(x1, x2 - 1);
    }

    // ===================================================================================== clipping

    /** {@code R_ClipSolidWallSegment}. */
    @VanillaRef("r_bsp.c:R_ClipSolidWallSegment")
    private void clipSolidWallSegment(int first, int last) {
        int start = 0;
        while (solidLast[start] < first - 1) {
            start++;
        }
        if (first < solidFirst[start]) {
            if (last < solidFirst[start] - 1) {
                storeWallRange(first, last);
                if (newend + 1 >= solidFirst.length) {
                    solidFirst = Arrays.copyOf(solidFirst, solidFirst.length * 2);
                    solidLast = Arrays.copyOf(solidLast, solidLast.length * 2);
                }
                int next = newend;
                newend++;
                while (next != start) {
                    solidFirst[next] = solidFirst[next - 1];
                    solidLast[next] = solidLast[next - 1];
                    next--;
                }
                solidFirst[next] = first;
                solidLast[next] = last;
                return;
            }
            storeWallRange(first, solidFirst[start] - 1);
            solidFirst[start] = first;
        }
        if (last <= solidLast[start]) {
            return;
        }
        int next = start;
        boolean crunch = false;
        while (last >= solidFirst[next + 1] - 1) {
            storeWallRange(solidLast[next] + 1, solidFirst[next + 1] - 1);
            next++;
            if (last <= solidLast[next]) {
                solidLast[start] = solidLast[next];
                crunch = true;
                break;
            }
        }
        if (!crunch) {
            storeWallRange(solidLast[next] + 1, last);
            solidLast[start] = last;
        }
        if (next == start) {
            return;
        }
        while (next++ != newend) {
            ++start;
            solidFirst[start] = solidFirst[next];
            solidLast[start] = solidLast[next];
        }
        newend = start + 1;
    }

    /** {@code R_ClipPassWallSegment}. */
    @VanillaRef("r_bsp.c:R_ClipPassWallSegment")
    private void clipPassWallSegment(int first, int last) {
        int start = 0;
        while (solidLast[start] < first - 1) {
            start++;
        }
        if (first < solidFirst[start]) {
            if (last < solidFirst[start] - 1) {
                storeWallRange(first, last);
                return;
            }
            storeWallRange(first, solidFirst[start] - 1);
        }
        if (last <= solidLast[start]) {
            return;
        }
        while (last >= solidFirst[start + 1] - 1) {
            storeWallRange(solidLast[start] + 1, solidFirst[start + 1] - 1);
            start++;
            if (last <= solidLast[start]) {
                return;
            }
        }
        storeWallRange(solidLast[start] + 1, last);
    }

    // ===================================================================================== wall range

    private void growDrawSegs() {
        int n = dsX1.length * 2;
        dsX1 = Arrays.copyOf(dsX1, n);
        dsX2 = Arrays.copyOf(dsX2, n);
        dsCurline = Arrays.copyOf(dsCurline, n);
        dsScale1 = Arrays.copyOf(dsScale1, n);
        dsScale2 = Arrays.copyOf(dsScale2, n);
        dsScaleStep = Arrays.copyOf(dsScaleStep, n);
        dsScale1D = Arrays.copyOf(dsScale1D, n);
        dsScaleStepD = Arrays.copyOf(dsScaleStepD, n);
        dsSilhouette = Arrays.copyOf(dsSilhouette, n);
        dsBSilHeight = Arrays.copyOf(dsBSilHeight, n);
        dsTSilHeight = Arrays.copyOf(dsTSilHeight, n);
        dsSprTopClip = Arrays.copyOf(dsSprTopClip, n);
        dsSprBottomClip = Arrays.copyOf(dsSprBottomClip, n);
        dsMaskedTexCol = Arrays.copyOf(dsMaskedTexCol, n);
    }

    /** {@code R_StoreWallRange}. */
    @VanillaRef("r_segs.c:R_StoreWallRange")
    private void storeWallRange(int start, int stop) {
        if (numDrawSegs == dsX1.length) {
            growDrawSegs();
        }
        final int ds = numDrawSegs;
        final WorldView w = world;
        sidedef = w.segSide(curline);
        linedef = w.segLine(curline);
        lineMapped[linedef] = true;

        rwNormalAngle = w.segAngle(curline) + Angle.ANG90;
        final boolean precise = rs.precise;
        int offsetangle = 0;
        int hyp = 0;
        int sineval;
        int v1 = w.segV1(curline);
        if (!precise) {
            offsetangle = Math.abs(rwNormalAngle - rwAngle1);
            if (Integer.compareUnsigned(offsetangle, Angle.ANG90) > 0) {
                offsetangle = Angle.ANG90;
            }
            int distangle = Angle.ANG90 - offsetangle;
            hyp = pointToDist(w.vertexX(v1), w.vertexY(v1));
            sineval = Tables.fineSine(distangle >>> Angle.ANGLETOFINESHIFT);
            rwDistance = Fixed.mul(hyp, sineval);
        }

        dsX1[ds] = rwX = start;
        dsX2[ds] = stop;
        dsCurline[ds] = curline;
        rwStopX = stop + 1;

        if (precise) {
            preciseWallSetup(w, v1, start, stop);
            double s1 = preciseScale(start);
            double s2 = preciseScale(stop);
            dsScale1[ds] = rwScale = fixedScale(s1);
            dsScale2[ds] = fixedScale(s2);
            dsScale1D[ds] = pScale0;
            dsScaleStepD[ds] = pScaleStep;
            rwScaleStep = stop > start ? (dsScale2[ds] - rwScale) / (stop - start) : 0;
            dsScaleStep[ds] = rwScaleStep;
        } else {
            dsScale1[ds] = rwScale = scaleFromGlobalAngle(rs.viewAngle + rs.xToViewAngle[start]);
            if (stop > start) {
                dsScale2[ds] = scaleFromGlobalAngle(rs.viewAngle + rs.xToViewAngle[stop]);
                dsScaleStep[ds] = rwScaleStep = (dsScale2[ds] - rwScale) / (stop - start);
            } else {
                dsScale2[ds] = dsScale1[ds];
                rwScaleStep = 0;
                dsScaleStep[ds] = 0;
            }
        }

        final int viewZ = rs.viewZ;
        final int fFloor = w.sectorFloorHeight(frontsector);
        final int fCeil = w.sectorCeilingHeight(frontsector);
        worldTop = fCeil - viewZ;
        worldBottom = fFloor - viewZ;

        midtexture = toptexture = bottomtexture = 0;
        maskedtexture = false;
        dsMaskedTexCol[ds] = RenderState.NOCLIP;
        final int lineFlags = w.lineFlags(linedef);
        final int rowOffset = w.sideRowOffset(sidedef);

        if (backsector < 0) {
            midtexture = w.textureTranslation(w.sideMidTexture(sidedef));
            markfloor = markceiling = true;
            if ((lineFlags & LineFlags.DONTPEGBOTTOM) != 0) {
                int vtop = fFloor + rs.textures.heightFixed(w.sideMidTexture(sidedef));
                rwMidTextureMid = vtop - viewZ;
            } else {
                rwMidTextureMid = worldTop;
            }
            rwMidTextureMid += rowOffset;
            dsSilhouette[ds] = SIL_BOTH;
            dsSprTopClip[ds] = rs.viewWidth; // screenheightarray
            dsSprBottomClip[ds] = 0; // negonearray
            dsBSilHeight[ds] = Integer.MAX_VALUE;
            dsTSilHeight[ds] = Integer.MIN_VALUE;
        } else {
            final int bFloor = w.sectorFloorHeight(backsector);
            final int bCeil = w.sectorCeilingHeight(backsector);
            final int bFloorPic = w.sectorFloorPic(backsector);
            final int bCeilPic = w.sectorCeilingPic(backsector);
            final int bLight = w.sectorLightLevel(backsector);
            dsSprTopClip[ds] = dsSprBottomClip[ds] = RenderState.NOCLIP;
            int sil = 0;
            if (fFloor > bFloor) {
                sil = SIL_BOTTOM;
                dsBSilHeight[ds] = fFloor;
            } else if (bFloor > viewZ) {
                sil = SIL_BOTTOM;
                dsBSilHeight[ds] = Integer.MAX_VALUE;
            }
            if (fCeil < bCeil) {
                sil |= SIL_TOP;
                dsTSilHeight[ds] = fCeil;
            } else if (bCeil < viewZ) {
                sil |= SIL_TOP;
                dsTSilHeight[ds] = Integer.MIN_VALUE;
            }
            if (bCeil <= fFloor) {
                dsSprBottomClip[ds] = 0; // negonearray
                dsBSilHeight[ds] = Integer.MAX_VALUE;
                sil |= SIL_BOTTOM;
            }
            if (bFloor >= fCeil) {
                dsSprTopClip[ds] = rs.viewWidth; // screenheightarray
                dsTSilHeight[ds] = Integer.MIN_VALUE;
                sil |= SIL_TOP;
            }
            dsSilhouette[ds] = sil;

            worldHigh = bCeil - viewZ;
            worldLow = bFloor - viewZ;
            final int fCeilPic = w.sectorCeilingPic(frontsector);
            final int fFloorPic = w.sectorFloorPic(frontsector);
            final int fLight = w.sectorLightLevel(frontsector);
            if (fCeilPic == rs.skyFlat && bCeilPic == rs.skyFlat) {
                worldTop = worldHigh;
            }
            markfloor = worldLow != worldBottom || bFloorPic != fFloorPic || bLight != fLight;
            markceiling = worldHigh != worldTop || bCeilPic != fCeilPic || bLight != fLight;
            if (bCeil <= fFloor || bFloor >= fCeil) {
                markceiling = markfloor = true;
            }
            if (worldHigh < worldTop) {
                toptexture = w.textureTranslation(w.sideTopTexture(sidedef));
                if ((lineFlags & LineFlags.DONTPEGTOP) != 0) {
                    rwTopTextureMid = worldTop;
                } else {
                    int vtop = bCeil + rs.textures.heightFixed(w.sideTopTexture(sidedef));
                    rwTopTextureMid = vtop - viewZ;
                }
            }
            if (worldLow > worldBottom) {
                bottomtexture = w.textureTranslation(w.sideBottomTexture(sidedef));
                if ((lineFlags & LineFlags.DONTPEGBOTTOM) != 0) {
                    rwBottomTextureMid = worldTop;
                } else {
                    rwBottomTextureMid = worldLow;
                }
            }
            rwTopTextureMid += rowOffset;
            rwBottomTextureMid += rowOffset;
            if (w.sideMidTexture(sidedef) != 0) {
                maskedtexture = true;
                rs.ensureOpenings(rwStopX - rwX);
                maskedTextureCol = rs.lastOpening - rwX;
                dsMaskedTexCol[ds] = maskedTextureCol;
                rs.lastOpening += rwStopX - rwX;
            }
        }

        segtextured = midtexture != 0 || toptexture != 0 || bottomtexture != 0 || maskedtexture;
        if (segtextured) {
            if (precise) {
                // texture position of the column's hit point: pOffset + dist * (r.T) / (r.n), see preciseWallSetup
                pOffset += (w.sideTextureOffset(sidedef) + (double) w.segOffset(curline)) / 65536.0;
            } else {
                offsetangle = rwNormalAngle - rwAngle1;
                if (Integer.compareUnsigned(offsetangle, Angle.ANG180) > 0) {
                    offsetangle = -offsetangle;
                }
                if (Integer.compareUnsigned(offsetangle, Angle.ANG90) > 0) {
                    offsetangle = Angle.ANG90;
                }
                sineval = Tables.fineSine(offsetangle >>> Angle.ANGLETOFINESHIFT);
                rwOffset = Fixed.mul(hyp, sineval);
                if (Integer.compareUnsigned(rwNormalAngle - rwAngle1, Angle.ANG180) < 0) {
                    rwOffset = -rwOffset;
                }
                rwOffset += w.sideTextureOffset(sidedef) + w.segOffset(curline);
                rwCenterAngle = Angle.ANG90 + rs.viewAngle - rwNormalAngle;
            }
            if (rs.fixedColormap < 0) {
                wallLights = scaleLightRow(w.sectorLightLevel(frontsector), curline);
            } else {
                wallLights = rs.scaleLightFixed;
            }
        }

        if (fFloor >= viewZ) {
            markfloor = false;
        }
        if (fCeil <= viewZ && w.sectorCeilingPic(frontsector) != rs.skyFlat) {
            markceiling = false;
        }

        if (precise) {
            preciseVertical();
            if (markceiling) {
                planes.ceilingplane = planes.checkPlane(planes.ceilingplane, rwX, rwStopX - 1);
            }
            if (markfloor) {
                planes.floorplane = planes.checkPlane(planes.floorplane, rwX, rwStopX - 1);
            }
            renderSegLoopPrecise();
            saveSpriteClips(ds, start);
            return;
        }
        worldTop >>= 4;
        worldBottom >>= 4;
        topStep = -Fixed.mul(rwScaleStep, worldTop);
        topFrac = (rs.centerYFrac >> 4) - Fixed.mul(worldTop, rwScale);
        bottomStep = -Fixed.mul(rwScaleStep, worldBottom);
        bottomFrac = (rs.centerYFrac >> 4) - Fixed.mul(worldBottom, rwScale);
        if (backsector >= 0) {
            worldHigh >>= 4;
            worldLow >>= 4;
            if (worldHigh < worldTop) {
                pixHigh = (rs.centerYFrac >> 4) - Fixed.mul(worldHigh, rwScale);
                pixHighStep = -Fixed.mul(rwScaleStep, worldHigh);
            }
            if (worldLow > worldBottom) {
                pixLow = (rs.centerYFrac >> 4) - Fixed.mul(worldLow, rwScale);
                pixLowStep = -Fixed.mul(rwScaleStep, worldLow);
            }
        }

        if (markceiling) {
            planes.ceilingplane = planes.checkPlane(planes.ceilingplane, rwX, rwStopX - 1);
        }
        if (markfloor) {
            planes.floorplane = planes.checkPlane(planes.floorplane, rwX, rwStopX - 1);
        }

        renderSegLoop();
        saveSpriteClips(ds, start);
    }

    /** The end of {@code R_StoreWallRange}: clip arrays saved for the sprites and masked textures. */
    private void saveSpriteClips(int ds, int start) {
        int sil = dsSilhouette[ds];
        if (((sil & SIL_TOP) != 0 || maskedtexture) && dsSprTopClip[ds] == RenderState.NOCLIP) {
            rs.ensureOpenings(rwStopX - start);
            System.arraycopy(planes.ceilingclip, start, rs.openings, rs.lastOpening, rwStopX - start);
            dsSprTopClip[ds] = rs.lastOpening - start;
            rs.lastOpening += rwStopX - start;
        }
        if (((sil & SIL_BOTTOM) != 0 || maskedtexture) && dsSprBottomClip[ds] == RenderState.NOCLIP) {
            rs.ensureOpenings(rwStopX - start);
            System.arraycopy(planes.floorclip, start, rs.openings, rs.lastOpening, rwStopX - start);
            dsSprBottomClip[ds] = rs.lastOpening - start;
            rs.lastOpening += rwStopX - start;
        }
        if (maskedtexture && (dsSilhouette[ds] & SIL_TOP) == 0) {
            dsSilhouette[ds] |= SIL_TOP;
            dsTSilHeight[ds] = Integer.MIN_VALUE;
        }
        if (maskedtexture && (dsSilhouette[ds] & SIL_BOTTOM) == 0) {
            dsSilhouette[ds] |= SIL_BOTTOM;
            dsBSilHeight[ds] = Integer.MAX_VALUE;
        }
        numDrawSegs++;
    }

    /**
     * The scale light row of a seg for sector light {@code lightLevel}: one level darker for horizontal walls, one
     * brighter for vertical ones ("fake contrast"), plus {@code extralight}.
     */
    int[] scaleLightRow(int lightLevel, int seg) {
        int lightnum = (lightLevel >> RenderState.LIGHTSEGSHIFT) + rs.extraLight;
        int v1 = world.segV1(seg);
        int v2 = world.segV2(seg);
        if (world.vertexY(v1) == world.vertexY(v2)) {
            lightnum--;
        } else if (world.vertexX(v1) == world.vertexX(v2)) {
            lightnum++;
        }
        if (lightnum < 0) {
            return rs.scaleLight[0];
        }
        if (lightnum >= RenderState.LIGHTLEVELS) {
            return rs.scaleLight[RenderState.LIGHTLEVELS - 1];
        }
        return rs.scaleLight[lightnum];
    }

    // ===================================================================================== precise walls

    /**
     * Exact geometry of the wall about to be drawn (spec 04 section 3: 64-bit wall scale, full-precision angles).
     *
     * <p>The wall lies on the line {@code p . n = const} of the level's line (not of the rounded seg vertices). For
     * the ray through screen column {@code x}, {@code r(x) = proj * forward + (cx - x) * left}, the hit point is
     * {@code eye + r * dist / (r . n)}: the scale {@code proj / depth = (r . n) / dist} is exactly linear in {@code x},
     * and the position along the wall is {@code (eye - v1) . T + dist * (r . T) / (r . n)}.
     */
    private void preciseWallSetup(WorldView w, int v1, int start, int stop) {
        final int line = linedef;
        final int sv2 = w.segV2(curline);
        final double ex = rs.viewX / 65536.0;
        final double ey = rs.viewY / 65536.0;
        final double v1x = w.vertexX(v1) / 65536.0;
        final double v1y = w.vertexY(v1) / 65536.0;
        // direction of the level's line, oriented like the seg
        final double lx1 = w.vertexX(w.lineV1(line)) / 65536.0;
        final double ly1 = w.vertexY(w.lineV1(line)) / 65536.0;
        final double lx2 = w.vertexX(w.lineV2(line)) / 65536.0;
        final double ly2 = w.vertexY(w.lineV2(line)) / 65536.0;
        double dx = lx2 - lx1;
        double dy = ly2 - ly1;
        final double sdx = w.vertexX(sv2) / 65536.0 - v1x;
        final double sdy = w.vertexY(sv2) / 65536.0 - v1y;
        double len = Math.hypot(dx, dy);
        double ax;
        double ay;
        if (len < 1e-9) {
            double a = (w.segAngle(curline) & 0xffffffffL) * RenderState.BAM_TO_RAD;
            dx = Math.cos(a);
            dy = Math.sin(a);
            len = 1;
            ax = v1x;
            ay = v1y;
        } else if (dx * sdx + dy * sdy >= 0) {
            ax = lx1;
            ay = ly1;
        } else {
            dx = -dx;
            dy = -dy;
            ax = lx2;
            ay = ly2;
        }
        final double tx = dx / len;
        final double ty = dy / len;
        final double nx = -ty;
        final double ny = tx;
        double dist = (ax - ex) * nx + (ay - ey) * ny;
        if (dist < 1.0 / 1024) {
            dist = 1.0 / 1024;
        }
        final double cosV = rs.viewCosD;
        final double sinV = rs.viewSinD;
        pDist = dist;
        pNf = nx * cosV + ny * sinV;
        pNl = -nx * sinV + ny * cosV;
        pTf = tx * cosV + ty * sinV;
        pTl = -tx * sinV + ty * cosV;
        pOffset = (ex - v1x) * tx + (ey - v1y) * ty;
        final double mul = 1 << rs.detailShift;
        double raw0 = (rs.projD * pNf + (rs.centerX - start) * pNl) * mul / dist;
        double raw1 = (rs.projD * pNf + (rs.centerX - stop) * pNl) * mul / dist;
        pScale0 = raw0;
        pScaleStep = stop > start ? (raw1 - raw0) / (stop - start) : 0;
    }

    private double clampScale(double sc) {
        if (sc > rs.maxScaleD) {
            return rs.maxScaleD;
        }
        return sc < 1.0 / 256 ? 1.0 / 256 : sc;
    }

    /** Clamped scale at column {@code x} of the wall being set up. */
    private double preciseScale(int x) {
        return clampScale(pScale0 + pScaleStep * (x - rwX));
    }

    private static int fixedScale(double sc) {
        return (int) Math.min(Integer.MAX_VALUE / 2, sc * 65536.0);
    }

    private void preciseVertical() {
        pWt = worldTop / 65536.0;
        pWb = worldBottom / 65536.0;
        pWh = worldHigh / 65536.0;
        pWl = worldLow / 65536.0;
    }

    /** {@code R_RenderSegLoop} with exact scale, vertical positions and texture columns. */
    private void renderSegLoopPrecise() {
        final short[] ceilingclip = planes.ceilingclip;
        final short[] floorclip = planes.floorclip;
        final Planes.Visplane ceilingplane = planes.ceilingplane;
        final Planes.Visplane floorplane = planes.floorplane;
        final boolean markceiling = this.markceiling;
        final boolean markfloor = this.markfloor;
        final boolean segtextured = this.segtextured;
        final int viewHeight = rs.viewHeight;
        final Draw draw = this.draw;
        final int midtexture = this.midtexture;
        final int toptexture = this.toptexture;
        final int bottomtexture = this.bottomtexture;
        final boolean maskedtexture = this.maskedtexture;
        final int[] walllights = wallLights;
        final int rwStopX = this.rwStopX;
        final short[] openings = rs.openings;
        final int maskedBase = maskedTextureCol;
        final int fixedColormap = rs.fixedColormap;
        final double cy = rs.centerY;
        final double cx = rs.centerX;
        final double projD = rs.projD;
        final double wt = pWt;
        final double wb = pWb;
        final double wh = pWh;
        final double wl = pWl;
        final double dist = pDist;
        final double nf = pNf;
        final double nl = pNl;
        final double tf = pTf;
        final double tl = pTl;
        final double offset = pOffset;
        final double s0 = pScale0;
        final double sStep = pScaleStep;
        final double maxScale = rs.maxScaleD;
        final WallTexture midTex = midtexture != 0 ? rs.textures.get(midtexture) : null;
        final WallTexture topTex = toptexture != 0 ? rs.textures.get(toptexture) : null;
        final WallTexture botTex = bottomtexture != 0 ? rs.textures.get(bottomtexture) : null;
        draw.colMode = Draw.COL_BASE;
        draw.dcWrapMode = Draw.WRAP_HEIGHT;
        draw.dcFine = true;

        for (int x = rwX; x < rwStopX; x++) {
            double sc = s0 + sStep * (x - rwX);
            if (sc > maxScale) {
                sc = maxScale;
            } else if (sc < 1.0 / 256) {
                sc = 1.0 / 256;
            }
            int yl = (int) Math.ceil(cy - wt * sc);
            if (yl < ceilingclip[x] + 1) {
                yl = ceilingclip[x] + 1;
            }
            if (markceiling) {
                int top = ceilingclip[x] + 1;
                int bottom = yl - 1;
                if (bottom >= floorclip[x]) {
                    bottom = floorclip[x] - 1;
                }
                if (top <= bottom) {
                    ceilingplane.top[x + 1] = (short) top;
                    ceilingplane.bottom[x + 1] = (short) bottom;
                }
            }
            int yh = (int) Math.floor(cy - wb * sc);
            if (yh >= floorclip[x]) {
                yh = floorclip[x] - 1;
            }
            if (markfloor) {
                int top = yh + 1;
                int bottom = floorclip[x] - 1;
                if (top <= ceilingclip[x]) {
                    top = ceilingclip[x] + 1;
                }
                if (top <= bottom) {
                    floorplane.top[x + 1] = (short) top;
                    floorplane.bottom[x + 1] = (short) bottom;
                }
            }

            int texturecolumn = 0;
            if (segtextured) {
                double rn = projD * nf + (cx - x) * nl;
                double rt = projD * tf + (cx - x) * tl;
                if (rn < 1e-9) {
                    rn = 1e-9;
                }
                texturecolumn = (int) Math.floor(offset + dist * rt / rn);
                if (fixedColormap < 0) {
                    draw.dcColormap = walllights[rs.lightIndex((int) (sc * 65536.0))];
                } else {
                    draw.dcColormap = fixedColormap;
                }
                draw.dcX = x;
                draw.dcIscaleP = (long) (4294967296.0 / sc);
            }

            if (midtexture != 0) {
                draw.dcYl = yl;
                draw.dcYh = yh;
                draw.dcTextureMid = rwMidTextureMid;
                int col = texturecolumn & midTex.widthMask;
                draw.dcSource = midTex.wallData[col];
                draw.dcSourceOfs = midTex.wallOfs[col];
                draw.dcWrapHeight = midTex.height;
                draw.drawColumn();
                ceilingclip[x] = (short) viewHeight;
                floorclip[x] = -1;
            } else {
                if (toptexture != 0) {
                    int mid = (int) Math.floor(cy - wh * sc);
                    if (mid >= floorclip[x]) {
                        mid = floorclip[x] - 1;
                    }
                    if (mid >= yl) {
                        draw.dcYl = yl;
                        draw.dcYh = mid;
                        draw.dcTextureMid = rwTopTextureMid;
                        int col = texturecolumn & topTex.widthMask;
                        draw.dcSource = topTex.wallData[col];
                        draw.dcSourceOfs = topTex.wallOfs[col];
                        draw.dcWrapHeight = topTex.height;
                        draw.drawColumn();
                        ceilingclip[x] = (short) mid;
                    } else {
                        ceilingclip[x] = (short) (yl - 1);
                    }
                } else if (markceiling) {
                    ceilingclip[x] = (short) (yl - 1);
                }

                if (bottomtexture != 0) {
                    int mid = (int) Math.ceil(cy - wl * sc);
                    if (mid <= ceilingclip[x]) {
                        mid = ceilingclip[x] + 1;
                    }
                    if (mid <= yh) {
                        draw.dcYl = mid;
                        draw.dcYh = yh;
                        draw.dcTextureMid = rwBottomTextureMid;
                        int col = texturecolumn & botTex.widthMask;
                        draw.dcSource = botTex.wallData[col];
                        draw.dcSourceOfs = botTex.wallOfs[col];
                        draw.dcWrapHeight = botTex.height;
                        draw.drawColumn();
                        floorclip[x] = (short) mid;
                    } else {
                        floorclip[x] = (short) (yh + 1);
                    }
                } else if (markfloor) {
                    floorclip[x] = (short) (yh + 1);
                }

                if (maskedtexture) {
                    openings[maskedBase + x] = (short) texturecolumn;
                }
            }
        }
        draw.dcFine = false;
    }

    /** {@code R_RenderSegLoop}. */
    @VanillaRef("r_segs.c:R_RenderSegLoop")
    private void renderSegLoop() {
        final short[] ceilingclip = planes.ceilingclip;
        final short[] floorclip = planes.floorclip;
        final Planes.Visplane ceilingplane = planes.ceilingplane;
        final Planes.Visplane floorplane = planes.floorplane;
        final boolean markceiling = this.markceiling;
        final boolean markfloor = this.markfloor;
        final boolean segtextured = this.segtextured;
        final int viewHeight = rs.viewHeight;
        final int[] xToViewAngle = rs.xToViewAngle;
        final boolean vanilla = rs.vanilla;
        final Draw draw = this.draw;
        final int midtexture = this.midtexture;
        final int toptexture = this.toptexture;
        final int bottomtexture = this.bottomtexture;
        final boolean maskedtexture = this.maskedtexture;
        final int[] walllights = wallLights;
        final int rwStopX = this.rwStopX;
        final int rwCenterAngle = this.rwCenterAngle;
        final int rwOffset = this.rwOffset;
        final int rwDistance = this.rwDistance;
        final int rwScaleStep = this.rwScaleStep;
        final int topStep = this.topStep;
        final int bottomStep = this.bottomStep;
        final int pixHighStep = this.pixHighStep;
        final int pixLowStep = this.pixLowStep;
        final short[] openings = rs.openings;
        final int maskedBase = maskedTextureCol;
        final int fixedColormap = rs.fixedColormap;
        int rwScale = this.rwScale;
        int topFrac = this.topFrac;
        int bottomFrac = this.bottomFrac;
        int pixHigh = this.pixHigh;
        int pixLow = this.pixLow;
        final WallTexture midTex = midtexture != 0 ? rs.textures.get(midtexture) : null;
        final WallTexture topTex = toptexture != 0 ? rs.textures.get(toptexture) : null;
        final WallTexture botTex = bottomtexture != 0 ? rs.textures.get(bottomtexture) : null;
        draw.colMode = Draw.COL_BASE;
        draw.dcWrapMode = vanilla ? Draw.WRAP_VANILLA : Draw.WRAP_HEIGHT;

        for (int x = rwX; x < rwStopX; x++) {
            int yl = (topFrac + HEIGHTUNIT - 1) >> HEIGHTBITS;
            if (yl < ceilingclip[x] + 1) {
                yl = ceilingclip[x] + 1;
            }
            if (markceiling) {
                int top = ceilingclip[x] + 1;
                int bottom = yl - 1;
                if (bottom >= floorclip[x]) {
                    bottom = floorclip[x] - 1;
                }
                if (top <= bottom) {
                    ceilingplane.top[x + 1] = (short) top;
                    ceilingplane.bottom[x + 1] = (short) bottom;
                }
            }
            int yh = bottomFrac >> HEIGHTBITS;
            if (yh >= floorclip[x]) {
                yh = floorclip[x] - 1;
            }
            if (markfloor) {
                int top = yh + 1;
                int bottom = floorclip[x] - 1;
                if (top <= ceilingclip[x]) {
                    top = ceilingclip[x] + 1;
                }
                if (top <= bottom) {
                    floorplane.top[x + 1] = (short) top;
                    floorplane.bottom[x + 1] = (short) bottom;
                }
            }

            int texturecolumn = 0;
            if (segtextured) {
                int angle = (rwCenterAngle + xToViewAngle[x]) >>> Angle.ANGLETOFINESHIFT;
                texturecolumn = rwOffset - Fixed.mul(fineTangentOverrun(angle), rwDistance);
                texturecolumn >>= Fixed.FRACBITS;
                if (fixedColormap < 0) {
                    int index = rs.lightIndex(rwScale);
                    draw.dcColormap = walllights[index];
                } else {
                    draw.dcColormap = fixedColormap;
                }
                draw.dcX = x;
                draw.dcIscale = (int) (0xffffffffL / (rwScale & 0xffffffffL));
            }

            if (midtexture != 0) {
                draw.dcYl = yl;
                draw.dcYh = yh;
                draw.dcTextureMid = rwMidTextureMid;
                int col = texturecolumn & midTex.widthMask;
                draw.dcSource = midTex.wallData[col];
                draw.dcSourceOfs = midTex.wallOfs[col];
                draw.dcWrapHeight = midTex.height;
                draw.drawColumn();
                ceilingclip[x] = (short) viewHeight;
                floorclip[x] = -1;
            } else {
                if (toptexture != 0) {
                    int mid = pixHigh >> HEIGHTBITS;
                    pixHigh += pixHighStep;
                    if (mid >= floorclip[x]) {
                        mid = floorclip[x] - 1;
                    }
                    if (mid >= yl) {
                        draw.dcYl = yl;
                        draw.dcYh = mid;
                        draw.dcTextureMid = rwTopTextureMid;
                        int col = texturecolumn & topTex.widthMask;
                        draw.dcSource = topTex.wallData[col];
                        draw.dcSourceOfs = topTex.wallOfs[col];
                        draw.dcWrapHeight = topTex.height;
                        draw.drawColumn();
                        ceilingclip[x] = (short) mid;
                    } else {
                        ceilingclip[x] = (short) (yl - 1);
                    }
                } else if (markceiling) {
                    ceilingclip[x] = (short) (yl - 1);
                }

                if (bottomtexture != 0) {
                    int mid = (pixLow + HEIGHTUNIT - 1) >> HEIGHTBITS;
                    pixLow += pixLowStep;
                    if (mid <= ceilingclip[x]) {
                        mid = ceilingclip[x] + 1;
                    }
                    if (mid <= yh) {
                        draw.dcYl = mid;
                        draw.dcYh = yh;
                        draw.dcTextureMid = rwBottomTextureMid;
                        int col = texturecolumn & botTex.widthMask;
                        draw.dcSource = botTex.wallData[col];
                        draw.dcSourceOfs = botTex.wallOfs[col];
                        draw.dcWrapHeight = botTex.height;
                        draw.drawColumn();
                        floorclip[x] = (short) mid;
                    } else {
                        floorclip[x] = (short) (yh + 1);
                    }
                } else if (markfloor) {
                    floorclip[x] = (short) (yh + 1);
                }

                if (maskedtexture) {
                    openings[maskedBase + x] = (short) texturecolumn;
                }
            }
            rwScale += rwScaleStep;
            topFrac += topStep;
            bottomFrac += bottomStep;
        }
        this.rwScale = rwScale;
        this.topFrac = topFrac;
        this.bottomFrac = bottomFrac;
        this.pixHigh = pixHigh;
        this.pixLow = pixLow;
    }
}
