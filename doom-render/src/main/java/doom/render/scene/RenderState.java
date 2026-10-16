package doom.render.scene;

import doom.game.view.ActorView;
import doom.game.view.PlayerView;
import doom.game.view.WorldView;
import doom.math.Angle;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.annotation.VanillaRef;
import doom.render.Camera;
import doom.render.FrameBuffer;
import doom.render.RenderOptions;
import doom.render.texture.FlatCache;
import doom.render.texture.SpriteSet;
import doom.render.texture.TextureCache;
import java.util.Arrays;

/**
 * State shared by the parts of the 3D renderer: the view-size dependent tables of {@code R_ExecuteSetViewSize}, the
 * per-frame view of {@code R_SetupFrame}, the framebuffer binding and the clipping/opening pool.
 *
 * <p>Generalisation to any {@code W x H} (spec 04 §2): the window is {@code winW x winH} pixels inside the framebuffer;
 * {@code projection} is that of a 4:3 view of the same height ({@code W43/2}), so the vertical field of view stays the
 * original's and the horizontal one grows with the width (Hor+). At 320x200 every value equals the original's.
 */
final class RenderState {

    static final int LIGHTLEVELS = 16;
    static final int LIGHTSEGSHIFT = 4;
    static final int MAXLIGHTSCALE = 48;
    static final int LIGHTSCALESHIFT = 12;
    static final int MAXLIGHTZ = 128;
    static final int LIGHTZSHIFT = 20;
    static final int NUMCOLORMAPS = 32;
    static final int DISTMAP = 2;
    static final int ANGLETOSKYSHIFT = 22;
    static final int FIELDOFVIEW = 2048;
    static final int SBARHEIGHT = 32;
    /** Radians per BAM unit. */
    static final double BAM_TO_RAD = 2.0 * Math.PI / 4294967296.0;
    /** BAM units per radian. */
    static final double RAD_TO_BAM = 4294967296.0 / (2.0 * Math.PI);
    /** Marks a missing clip pointer / masked column table. */
    static final int NOCLIP = Integer.MIN_VALUE;

    // ------------------------------------------------------------------------------------------------ assets
    TextureCache textures;
    FlatCache flats;
    SpriteSet sprites;
    final byte[] colormaps;
    /** {@code translationtables}: three 256-entry tables (green to gray, brown, red). */
    final byte[] translations = new byte[256 * 3];

    // ------------------------------------------------------------------------------------------------ options
    RenderOptions options;
    boolean vanilla;
    int fbW;
    int fbH;

    // ------------------------------------------------------------------------------------------------ view size
    /** Window position and size in framebuffer pixels ({@code viewwindowx/y}, {@code scaledviewwidth}, {@code viewheight}). */
    int winX;
    int winY;
    int winW;
    int viewHeight;
    int detailShift;
    /** Columns actually rendered: {@code winW >> detailShift}. */
    int viewWidth;
    int centerX;
    int centerY;
    int centerXFrac;
    int centerYFrac;
    /** {@code projection} in render columns (fixed point). */
    int projection;
    /** Projection in full-resolution pixels, the numerator of {@code yslope} and of the scale. */
    int projFull;
    int maxScale;
    /** Precise paths in use ({@link RenderOptions#precise()}). */
    boolean precise;
    /** {@link #projection} in pixels. */
    double projD;
    /** {@link #maxScale} as pixels per unit. */
    double maxScaleD;
    /** Sine and cosine of the view angle at full precision (set by {@link #setupFrame}). */
    double viewSinD;
    double viewCosD;
    /** 65536 * 200 / H: normalises scales before they index the light tables. */
    int lightNorm;
    int pspriteScale;
    int pspriteIScale;
    int clipAngle;
    final int[] viewAngleToX = new int[Angle.FINEANGLES / 2];
    int[] xToViewAngle = new int[0];
    int[] yslope = new int[0];
    /** Precise mode: {@code yslope} as pixels per unit of depth. */
    double[] yslopeD = new double[0];
    int[] distScale = new int[0];
    /** Offsets into {@link #colormaps}. */
    final int[][] scaleLight = new int[LIGHTLEVELS][MAXLIGHTSCALE];
    final int[] scaleLightFixed = new int[MAXLIGHTSCALE];
    final int[][] zLight = new int[LIGHTLEVELS][MAXLIGHTZ];

    // ------------------------------------------------------------------------------------------------ frame binding
    byte[] pixels;
    int rowStride;
    int colStride;
    /** Index of the window's top-left pixel. */
    int base;

    // ------------------------------------------------------------------------------------------------ per frame
    WorldView world;
    ActorView actors;
    PlayerView player;
    int viewX;
    int viewY;
    int viewZ;
    int viewAngle;
    int viewSin;
    int viewCos;
    int extraLight;
    /** Offset of the fixed colormap in {@link #colormaps}, or -1. */
    int fixedColormap;
    int viewActor;
    int validCount;
    int skyFlat;
    int skyTexture;
    int fuzzPos;

    // ------------------------------------------------------------------------------------------------ openings pool
    /**
     * {@code openings[]}: [0, W) = {@code negonearray} (-1), [W, 2W) = {@code screenheightarray}, then the per-frame pool.
     * A "pointer" is an int base so that column {@code x} lives at {@code openings[base + x]}.
     */
    short[] openings = new short[0];
    int openingsConstEnd;
    int lastOpening;

    RenderState(TextureCache textures, FlatCache flats, SpriteSet sprites, byte[] colormaps) {
        this.textures = textures;
        this.flats = flats;
        this.sprites = sprites;
        this.colormaps = colormaps;
        initTranslationTables();
    }

    @VanillaRef("r_draw.c:R_InitTranslationTables")
    private void initTranslationTables() {
        for (int i = 0; i < 256; i++) {
            if (i >= 0x70 && i <= 0x7f) {
                translations[i] = (byte) (0x60 + (i & 0xf));
                translations[i + 256] = (byte) (0x40 + (i & 0xf));
                translations[i + 512] = (byte) (0x20 + (i & 0xf));
            } else {
                translations[i] = translations[i + 256] = translations[i + 512] = (byte) i;
            }
        }
    }

    // =================================================================================================== view size

    /** {@code R_ExecuteSetViewSize}. */
    @VanillaRef({"r_main.c:R_ExecuteSetViewSize", "r_main.c:R_InitTextureMapping", "r_draw.c:R_InitBuffer"})
    void setViewSize(RenderOptions o) {
        options = o;
        vanilla = o.vanilla();
        fbW = o.width();
        fbH = o.height();
        int blocks = o.screenBlocks();
        int vw0;
        int vh0;
        if (blocks == 11) {
            vw0 = 320;
            vh0 = 200;
        } else {
            vw0 = blocks * 32;
            vh0 = (blocks * 168 / 10) & ~7;
        }
        winW = blocks >= 10 ? fbW : (int) (((long) vw0 * fbH + 100) / 200);
        viewHeight = blocks == 11 ? fbH : (int) (((long) vh0 * fbH + 100) / 200);
        winW = Math.max(2, winW);
        viewHeight = Math.max(2, viewHeight);
        winX = (fbW - winW) >> 1;
        if (winW == fbW) {
            winY = 0;
        } else {
            int sbar = (int) (((long) SBARHEIGHT * fbH + 100) / 200);
            winY = Math.max(0, (fbH - sbar - viewHeight) >> 1);
        }
        detailShift = o.detail();
        viewWidth = winW >> detailShift;
        centerY = viewHeight / 2;
        centerX = viewWidth / 2;
        centerXFrac = centerX << Fixed.FRACBITS;
        centerYFrac = centerY << Fixed.FRACBITS;
        // Square pixels: the 4:3 view of the full-width window is H x 4/3 wide instead of H x 1.6 (factor 5/6)
        final long kn = o.squarePixels() && blocks >= 10 ? 5 : 1;
        final long kd = o.squarePixels() && blocks >= 10 ? 6 : 1;
        projFull = (int) ((long) vw0 * fbH * 65536L * kn / (400L * kd));
        projection = projFull >> detailShift;
        maxScale = (int) Math.min(Integer.MAX_VALUE / 2, 64L * Fixed.FRACUNIT * fbH * kn / (200 * kd));
        lightNorm = (int) ((65536L * 200 * kd + fbH * kn / 2) / (fbH * kn));
        precise = o.precise();
        projD = projection / 65536.0;
        maxScaleD = maxScale / 65536.0;

        initTextureMapping();

        pspriteScale = projection / 160;
        pspriteIScale = (int) ((320L << 32) / (2L * projection));

        // openings: constant arrays
        int needed = 4 * viewWidth + 64;
        if (openings.length < needed) {
            openings = new short[Math.max(needed, 8192)];
        }
        Arrays.fill(openings, 0, viewWidth, (short) -1);
        Arrays.fill(openings, viewWidth, 2 * viewWidth, (short) viewHeight);
        openingsConstEnd = 2 * viewWidth;
        lastOpening = openingsConstEnd;

        if (yslope.length < viewHeight) {
            yslope = new int[viewHeight];
        }
        if (yslopeD.length < viewHeight) {
            yslopeD = new double[viewHeight];
        }
        for (int i = 0; i < viewHeight; i++) {
            int dy = ((i - viewHeight / 2) << Fixed.FRACBITS) + Fixed.FRACUNIT / 2;
            dy = Math.abs(dy);
            yslope[i] = Fixed.div(projFull, dy);
            yslopeD[i] = (projFull / 65536.0) / (dy / 65536.0);
        }
        if (distScale.length < viewWidth) {
            distScale = new int[viewWidth];
        }
        for (int i = 0; i < viewWidth; i++) {
            int cosadj = Math.abs(Tables.fineCosine(xToViewAngle[i] >>> Angle.ANGLETOFINESHIFT));
            distScale[i] = Fixed.div(Fixed.FRACUNIT, cosadj);
        }

        // scalelight depends on the view size
        for (int i = 0; i < LIGHTLEVELS; i++) {
            int startmap = ((LIGHTLEVELS - 1 - i) * 2) * NUMCOLORMAPS / LIGHTLEVELS;
            for (int j = 0; j < MAXLIGHTSCALE; j++) {
                int level = startmap - j * 320 / vw0 / DISTMAP;
                if (level < 0) {
                    level = 0;
                }
                if (level >= NUMCOLORMAPS) {
                    level = NUMCOLORMAPS - 1;
                }
                scaleLight[i][j] = level * 256;
            }
        }
        initLightTables();
    }

    /** {@code R_InitLightTables}: the {@code zlight} table. */
    @VanillaRef("r_main.c:R_InitLightTables")
    private void initLightTables() {
        for (int i = 0; i < LIGHTLEVELS; i++) {
            int startmap = ((LIGHTLEVELS - 1 - i) * 2) * NUMCOLORMAPS / LIGHTLEVELS;
            for (int j = 0; j < MAXLIGHTZ; j++) {
                int scale = Fixed.div((320 / 2) * Fixed.FRACUNIT, (j + 1) << LIGHTZSHIFT);
                scale >>= LIGHTSCALESHIFT;
                int level = startmap - scale / DISTMAP;
                if (level < 0) {
                    level = 0;
                }
                if (level >= NUMCOLORMAPS) {
                    level = NUMCOLORMAPS - 1;
                }
                zLight[i][j] = level * 256;
            }
        }
    }

    @VanillaRef("r_main.c:R_InitTextureMapping")
    private void initTextureMapping() {
        if (xToViewAngle.length < viewWidth + 1) {
            xToViewAngle = new int[viewWidth + 1];
        }
        int focallength = Fixed.div(projection, Tables.fineTangent(Angle.FINEANGLES / 4 + FIELDOFVIEW / 2));
        int limit = Fixed.FRACUNIT * 2;
        if (!vanilla && projection > 0) {
            int k = (centerXFrac + projection - 1) / projection + 1;
            limit = Fixed.FRACUNIT * Math.max(2, k);
        }
        for (int i = 0; i < Angle.FINEANGLES / 2; i++) {
            int t;
            int tan = Tables.fineTangent(i);
            if (tan > limit) {
                t = -1;
            } else if (tan < -limit) {
                t = viewWidth + 1;
            } else {
                t = Fixed.mul(tan, focallength);
                t = (centerXFrac - t + Fixed.FRACUNIT - 1) >> Fixed.FRACBITS;
                if (t < -1) {
                    t = -1;
                } else if (t > viewWidth + 1) {
                    t = viewWidth + 1;
                }
            }
            viewAngleToX[i] = t;
        }
        for (int x = 0; x <= viewWidth; x++) {
            if (precise) {
                // full precision: the angle of the left edge of column x, not rounded to the 8192-step fine grid
                xToViewAngle[x] = bamFromRadians(Math.atan2(centerX - x, projD));
                continue;
            }
            int i = 0;
            while (viewAngleToX[i] > x) {
                i++;
            }
            xToViewAngle[x] = (i << Angle.ANGLETOFINESHIFT) - Angle.ANG90;
        }
        for (int i = 0; i < Angle.FINEANGLES / 2; i++) {
            if (viewAngleToX[i] == -1) {
                viewAngleToX[i] = 0;
            } else if (viewAngleToX[i] == viewWidth + 1) {
                viewAngleToX[i] = viewWidth;
            }
        }
        clipAngle = xToViewAngle[0];
    }

    /** Radians to a BAM angle (wraps modulo 2 pi). */
    static int bamFromRadians(double radians) {
        return (int) (long) Math.rint(radians * RAD_TO_BAM);
    }

    // =================================================================================================== frame

    void bindFrame(FrameBuffer fb) {
        if (fb.width() != fbW || fb.height() != fbH) {
            throw new IllegalArgumentException("frame is " + fb.width() + "x" + fb.height() + " but the renderer is set up for "
                    + fbW + "x" + fbH);
        }
        pixels = fb.pixels();
        rowStride = fb.rowStride();
        colStride = fb.columnStride();
        base = winY * rowStride + winX * colStride;
    }

    /** {@code R_SetupFrame}. */
    @VanillaRef("r_main.c:R_SetupFrame")
    void setupFrame(Camera cam) {
        viewX = cam.x;
        viewY = cam.y;
        viewZ = cam.z;
        viewAngle = cam.angle;
        extraLight = cam.extraLight;
        viewActor = cam.viewActor;
        if (precise) {
            double a = (viewAngle & 0xffffffffL) * BAM_TO_RAD;
            viewSinD = Math.sin(a);
            viewCosD = Math.cos(a);
        }
        viewSin = Tables.fineSine(viewAngle >>> Angle.ANGLETOFINESHIFT);
        viewCos = Tables.fineCosine(viewAngle >>> Angle.ANGLETOFINESHIFT);
        if (cam.fixedColormap != 0) {
            fixedColormap = cam.fixedColormap * 256;
            Arrays.fill(scaleLightFixed, fixedColormap);
        } else {
            fixedColormap = -1;
        }
        skyFlat = world.skyFlat();
        skyTexture = world.skyTexture();
        validCount++;
    }

    // =================================================================================================== helpers

    /** Index into the scale light tables for a full-resolution scale, clamped like {@code index >= MAXLIGHTSCALE}. */
    int lightIndex(int scale) {
        long n = ((long) scale * lightNorm) >> 16;
        int idx = (int) (n >> LIGHTSCALESHIFT);
        if (idx < 0 || idx >= MAXLIGHTSCALE) {
            return MAXLIGHTSCALE - 1;
        }
        return idx;
    }

    /** Makes room for {@code n} more shorts in the openings pool. */
    void ensureOpenings(int n) {
        int need = lastOpening + n;
        if (need > openings.length) {
            openings = Arrays.copyOf(openings, Math.max(need, openings.length * 2));
        }
    }
}
