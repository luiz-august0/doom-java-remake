package doom.render.scene;

import doom.math.Angle;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.annotation.VanillaRef;
import doom.render.texture.WallTexture;
import java.util.Arrays;

/**
 * Visplanes: collection while walls are drawn, and the final span drawing ({@code r_plane.c}). The pool grows on demand
 * (the original dies at 128 visplanes).
 */
final class Planes {

    /** An unset column in {@code top[]} (the original's 0xff). */
    static final short UNSET = Short.MAX_VALUE;

    /** {@code visplane_t}. {@code top}/{@code bottom} are indexed {@code x + 1} so that {@code minx - 1} is valid. */
    static final class Visplane {
        int height;
        int picnum;
        int lightlevel;
        int minx;
        int maxx;
        short[] top;
        short[] bottom;
    }

    private final RenderState rs;
    private final Draw draw;

    Visplane[] planes = new Visplane[64];
    int numPlanes;
    Visplane floorplane;
    Visplane ceilingplane;

    short[] floorclip = new short[0];
    short[] ceilingclip = new short[0];

    private int[] spanstart = new int[0];
    private int[] cachedheight = new int[0];
    private int[] cacheddistance = new int[0];
    private int[] cachedxstep = new int[0];
    private int[] cachedystep = new int[0];

    private int planeheight;
    // precise mode: per-row depth and 32.32 steps of the current plane height
    private double[] cachedDepthD = new double[0];
    private long[] cachedXstepP = new long[0];
    private long[] cachedYstepP = new long[0];
    private int[] planezlight;
    private int basexscale;
    private int baseyscale;
    private int arrayWidth;

    Planes(RenderState rs, Draw draw) {
        this.rs = rs;
        this.draw = draw;
    }

    /** Called when the view size changed. */
    void resize() {
        int w = rs.viewWidth;
        int h = rs.viewHeight;
        if (floorclip.length < w) {
            floorclip = new short[w];
            ceilingclip = new short[w];
        }
        if (spanstart.length < h + 1) {
            spanstart = new int[h + 1];
            cachedheight = new int[h + 1];
            cacheddistance = new int[h + 1];
            cachedxstep = new int[h + 1];
            cachedystep = new int[h + 1];
            cachedDepthD = new double[h + 1];
            cachedXstepP = new long[h + 1];
            cachedYstepP = new long[h + 1];
        }
        if (arrayWidth != w + 3) {
            arrayWidth = w + 3;
            for (int i = 0; i < planes.length; i++) {
                planes[i] = null;
            }
        }
        numPlanes = 0;
    }

    /** {@code R_ClearPlanes}. */
    @VanillaRef("r_plane.c:R_ClearPlanes")
    void clearPlanes() {
        int w = rs.viewWidth;
        short vh = (short) rs.viewHeight;
        for (int i = 0; i < w; i++) {
            floorclip[i] = vh;
            ceilingclip[i] = -1;
        }
        numPlanes = 0;
        rs.lastOpening = rs.openingsConstEnd;
        Arrays.fill(cachedheight, 0, rs.viewHeight + 1, 0);
        int angle = (rs.viewAngle - Angle.ANG90) >>> Angle.ANGLETOFINESHIFT;
        basexscale = Fixed.div(Tables.fineCosine(angle), rs.projection);
        baseyscale = -Fixed.div(Tables.fineSine(angle), rs.projection);
    }

    private Visplane allocPlane() {
        if (numPlanes == planes.length) {
            planes = Arrays.copyOf(planes, numPlanes * 2);
        }
        Visplane p = planes[numPlanes];
        if (p == null) {
            p = new Visplane();
            p.top = new short[arrayWidth];
            p.bottom = new short[arrayWidth];
            planes[numPlanes] = p;
        }
        numPlanes++;
        return p;
    }

    /** {@code R_FindPlane}. */
    @VanillaRef("r_plane.c:R_FindPlane")
    Visplane findPlane(int height, int picnum, int lightlevel) {
        if (picnum == rs.skyFlat) {
            height = 0;
            lightlevel = 0;
        }
        for (int i = 0; i < numPlanes; i++) {
            Visplane check = planes[i];
            if (height == check.height && picnum == check.picnum && lightlevel == check.lightlevel) {
                return check;
            }
        }
        Visplane check = allocPlane();
        check.height = height;
        check.picnum = picnum;
        check.lightlevel = lightlevel;
        check.minx = rs.viewWidth;
        check.maxx = -1;
        Arrays.fill(check.top, UNSET);
        return check;
    }

    /** {@code R_CheckPlane}. */
    @VanillaRef("r_plane.c:R_CheckPlane")
    Visplane checkPlane(Visplane pl, int start, int stop) {
        int intrl;
        int intrh;
        int unionl;
        int unionh;
        if (start < pl.minx) {
            intrl = pl.minx;
            unionl = start;
        } else {
            unionl = pl.minx;
            intrl = start;
        }
        if (stop > pl.maxx) {
            intrh = pl.maxx;
            unionh = stop;
        } else {
            unionh = pl.maxx;
            intrh = stop;
        }
        int x;
        final short[] top = pl.top;
        for (x = intrl; x <= intrh; x++) {
            if (top[x + 1] != UNSET) {
                break;
            }
        }
        if (x > intrh) {
            pl.minx = unionl;
            pl.maxx = unionh;
            return pl;
        }
        Visplane n = allocPlane();
        n.height = pl.height;
        n.picnum = pl.picnum;
        n.lightlevel = pl.lightlevel;
        n.minx = start;
        n.maxx = stop;
        Arrays.fill(n.top, UNSET);
        return n;
    }

    // ===================================================================================== drawing

    /**
     * {@code R_MapPlane} with exact geometry (spec 04 section 3, finer span steps): the floor point under column
     * {@code x} of a row at depth {@code d} is {@code eye + d * (forward + (cx - x)/proj * left)}, so the start of the
     * span and the per-column step follow from the view direction alone, with 32 fractional bits.
     */
    private void mapPlanePrecise(int y, int x1, int x2) {
        final double depth;
        final long xs;
        final long ys;
        if (planeheight != cachedheight[y]) {
            cachedheight[y] = planeheight;
            depth = cachedDepthD[y] = Math.abs(planeheight) / 65536.0 * rs.yslopeD[y];
            xs = cachedXstepP[y] = (long) (depth * rs.viewSinD / rs.projD * 4294967296.0);
            ys = cachedYstepP[y] = (long) (depth * rs.viewCosD / rs.projD * 4294967296.0);
        } else {
            depth = cachedDepthD[y];
            xs = cachedXstepP[y];
            ys = cachedYstepP[y];
        }
        final double t = (rs.centerX - x1) / rs.projD;
        final double px = rs.viewX / 65536.0 + depth * (rs.viewCosD - t * rs.viewSinD);
        final double py = rs.viewY / 65536.0 + depth * (rs.viewSinD + t * rs.viewCosD);
        draw.dsXfracP = (long) (px * 4294967296.0);
        draw.dsYfracP = (long) (-py * 4294967296.0);
        draw.dsXstepP = xs;
        draw.dsYstepP = ys;
        if (rs.fixedColormap >= 0) {
            draw.dsColormap = rs.fixedColormap;
        } else {
            int index = (int) Math.min(depth / 16.0, RenderState.MAXLIGHTZ - 1);
            draw.dsColormap = planezlight[index];
        }
        draw.dsY = y;
        draw.dsX1 = x1;
        draw.dsX2 = x2;
        draw.drawSpanFine();
    }

    /** {@code R_MapPlane}. */
    private void mapPlane(int y, int x1, int x2) {
        if (rs.precise) {
            mapPlanePrecise(y, x1, x2);
            return;
        }
        int distance;
        int xstep;
        int ystep;
        if (planeheight != cachedheight[y]) {
            cachedheight[y] = planeheight;
            distance = cacheddistance[y] = Fixed.mul(planeheight, rs.yslope[y]);
            xstep = cachedxstep[y] = Fixed.mul(distance, basexscale);
            ystep = cachedystep[y] = Fixed.mul(distance, baseyscale);
        } else {
            distance = cacheddistance[y];
            xstep = cachedxstep[y];
            ystep = cachedystep[y];
        }
        int length = Fixed.mul(distance, rs.distScale[x1]);
        int angle = (rs.viewAngle + rs.xToViewAngle[x1]) >>> Angle.ANGLETOFINESHIFT;
        draw.dsXfrac = rs.viewX + Fixed.mul(Tables.fineCosine(angle), length);
        draw.dsYfrac = -rs.viewY - Fixed.mul(Tables.fineSine(angle), length);
        draw.dsXstep = xstep;
        draw.dsYstep = ystep;
        if (rs.fixedColormap >= 0) {
            draw.dsColormap = rs.fixedColormap;
        } else {
            int index = distance >>> RenderState.LIGHTZSHIFT;
            if (index >= RenderState.MAXLIGHTZ || index < 0) {
                index = RenderState.MAXLIGHTZ - 1;
            }
            draw.dsColormap = planezlight[index];
        }
        draw.dsY = y;
        draw.dsX1 = x1;
        draw.dsX2 = x2;
        draw.drawSpan();
    }

    /** {@code R_MakeSpans}. */
    private void makeSpans(int x, int t1, int b1, int t2, int b2) {
        while (t1 < t2 && t1 <= b1) {
            mapPlane(t1, spanstart[t1], x - 1);
            t1++;
        }
        while (b1 > b2 && b1 >= t1) {
            mapPlane(b1, spanstart[b1], x - 1);
            b1--;
        }
        while (t2 < t1 && t2 <= b2) {
            spanstart[t2] = x;
            t2++;
        }
        while (b2 > b1 && b2 >= t2) {
            spanstart[b2] = x;
            b2--;
        }
    }

    /** {@code R_DrawPlanes}. */
    @VanillaRef("r_plane.c:R_DrawPlanes")
    void drawPlanes() {
        for (int n = 0; n < numPlanes; n++) {
            Visplane pl = planes[n];
            if (pl.minx > pl.maxx) {
                continue;
            }
            if (pl.picnum == rs.skyFlat) {
                drawSky(pl);
                continue;
            }
            int flat = rs.world.flatTranslation(pl.picnum);
            draw.dsSource = rs.flats.pixels(flat);
            planeheight = Math.abs(pl.height - rs.viewZ);
            int light = (pl.lightlevel >> RenderState.LIGHTSEGSHIFT) + rs.extraLight;
            if (light >= RenderState.LIGHTLEVELS) {
                light = RenderState.LIGHTLEVELS - 1;
            }
            if (light < 0) {
                light = 0;
            }
            planezlight = rs.zLight[light];
            final short[] top = pl.top;
            final short[] bottom = pl.bottom;
            top[pl.maxx + 2] = UNSET;
            top[pl.minx] = UNSET;
            int stop = pl.maxx + 1;
            for (int x = pl.minx; x <= stop; x++) {
                makeSpans(x, top[x], bottom[x], top[x + 1], bottom[x + 1]);
            }
        }
    }

    private void drawSky(Visplane pl) {
        draw.dcIscale = rs.pspriteIScale >> rs.detailShift;
        draw.dcColormap = 0;
        draw.dcTextureMid = 100 * Fixed.FRACUNIT;
        draw.colMode = Draw.COL_BASE;
        WallTexture tex = rs.textures.get(rs.skyTexture);
        if (rs.vanilla) {
            draw.dcWrapMode = Draw.WRAP_VANILLA;
        } else {
            draw.dcWrapMode = Draw.WRAP_HEIGHT;
            draw.dcWrapHeight = tex.height;
        }
        final short[] top = pl.top;
        final short[] bottom = pl.bottom;
        for (int x = pl.minx; x <= pl.maxx; x++) {
            int yl = top[x + 1];
            int yh = bottom[x + 1];
            if (yl <= yh) {
                int angle = (rs.viewAngle + rs.xToViewAngle[x]) >>> RenderState.ANGLETOSKYSHIFT;
                draw.dcYl = yl;
                draw.dcYh = yh;
                draw.dcX = x;
                int col = angle & tex.widthMask;
                draw.dcSource = tex.wallData[col];
                draw.dcSourceOfs = tex.wallOfs[col];
                draw.colfunc();
            }
        }
    }
}
