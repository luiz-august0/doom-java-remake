package doom.render.scene;

import doom.game.view.ActorView;
import doom.game.view.FrameFlags;
import doom.game.view.LineFlags;
import doom.game.view.MobjFlags;
import doom.game.view.PlayerView;
import doom.game.view.Power;
import doom.game.view.WorldView;
import doom.math.Angle;
import doom.math.Fixed;
import doom.math.Trig;
import doom.math.annotation.VanillaRef;
import doom.render.texture.PostImage;
import doom.render.texture.SpriteSet;
import doom.render.texture.WallTexture;
import java.util.Arrays;

/**
 * Sprites, masked middle textures and player sprites ({@code r_things.c} and {@code R_RenderMaskedSegRange}).
 * Vissprites are stored as parallel arrays that grow on demand.
 */
final class Things {

    private static final int MINZ = Fixed.FRACUNIT * 4;
    private static final int BASEYCENTER = 100;
    /** Marks the shadow (fuzz) colormap in {@link #vsColormap}. */
    private static final int SHADOW = -1;

    private final RenderState rs;
    private final Draw draw;
    BspWalls walls;

    // vissprite pool
    private int numVis;
    private int[] vsX1 = new int[128];
    private int[] vsX2 = new int[128];
    private int[] vsGx = new int[128];
    private int[] vsGy = new int[128];
    private int[] vsGz = new int[128];
    private int[] vsGzt = new int[128];
    private int[] vsScale = new int[128];
    private int[] vsTexMid = new int[128];
    private int[] vsStartFrac = new int[128];
    private int[] vsXiScale = new int[128];
    private int[] vsPatch = new int[128];
    private int[] vsColormap = new int[128];
    private int[] vsMobjFlags = new int[128];
    // precise sprites (projectSpritePrecise): 32.32 steps and a flag selecting the precise drawer
    private boolean[] vsFine = new boolean[128];
    private long[] vsXiScaleP = new long[128];
    private long[] vsStartFracP = new long[128];
    private long[] vsIscaleVP = new long[128];
    private int[] sorted = new int[128];
    private int[] sortScratch = new int[128];

    private int[] sectorValid = new int[0];
    private int[] spriteLights;

    private short[] clipBot = new short[0];
    private short[] clipTop = new short[0];

    // mfloorclip / mceilingclip as (array, base)
    private short[] mfloor;
    private int mfloorBase;
    private short[] mceil;
    private int mceilBase;
    private int sprYScale;
    private int sprTopScreen;
    private long sprYScaleL;
    private long sprTopScreenL;

    Things(RenderState rs, Draw draw) {
        this.rs = rs;
        this.draw = draw;
    }

    void resize() {
        if (clipBot.length < rs.viewWidth) {
            clipBot = new short[rs.viewWidth];
            clipTop = new short[rs.viewWidth];
        }
    }

    void resetSectors(int count) {
        if (sectorValid.length < count) {
            sectorValid = new int[count];
        }
    }

    /** {@code R_ClearSprites}. */
    void clearFrame() {
        numVis = 0;
    }

    private void growVis() {
        int n = vsX1.length * 2;
        vsX1 = Arrays.copyOf(vsX1, n);
        vsX2 = Arrays.copyOf(vsX2, n);
        vsGx = Arrays.copyOf(vsGx, n);
        vsGy = Arrays.copyOf(vsGy, n);
        vsGz = Arrays.copyOf(vsGz, n);
        vsGzt = Arrays.copyOf(vsGzt, n);
        vsScale = Arrays.copyOf(vsScale, n);
        vsTexMid = Arrays.copyOf(vsTexMid, n);
        vsStartFrac = Arrays.copyOf(vsStartFrac, n);
        vsXiScale = Arrays.copyOf(vsXiScale, n);
        vsFine = Arrays.copyOf(vsFine, n);
        vsXiScaleP = Arrays.copyOf(vsXiScaleP, n);
        vsStartFracP = Arrays.copyOf(vsStartFracP, n);
        vsIscaleVP = Arrays.copyOf(vsIscaleVP, n);
        vsPatch = Arrays.copyOf(vsPatch, n);
        vsColormap = Arrays.copyOf(vsColormap, n);
        vsMobjFlags = Arrays.copyOf(vsMobjFlags, n);
        sorted = new int[n];
        sortScratch = new int[n];
    }

    // ===================================================================================== projection

    /** {@code R_AddSprites}: queue the sprites of a sector once per frame. */
    @VanillaRef("r_things.c:R_AddSprites")
    void addSprites(int sec) {
        if (sectorValid[sec] == rs.validCount) {
            return;
        }
        sectorValid[sec] = rs.validCount;
        spriteLights = lightRow(rs.world.sectorLightLevel(sec) >> RenderState.LIGHTSEGSHIFT);
        final ActorView actors = rs.actors;
        for (int id = rs.world.sectorFirstActor(sec); id >= 0; id = actors.nextInSector(id)) {
            projectSprite(id);
        }
    }

    private int[] lightRow(int lightnum) {
        lightnum += rs.extraLight;
        if (lightnum < 0) {
            return rs.scaleLight[0];
        }
        if (lightnum >= RenderState.LIGHTLEVELS) {
            return rs.scaleLight[RenderState.LIGHTLEVELS - 1];
        }
        return rs.scaleLight[lightnum];
    }

    /** {@code R_ProjectSprite}. */
    @VanillaRef("r_things.c:R_ProjectSprite")
    private void projectSprite(int id) {
        final ActorView actors = rs.actors;
        if (id == rs.viewActor) {
            return;
        }
        if (rs.precise) {
            projectSpritePrecise(id);
            return;
        }
        int thingX = actors.x(id);
        int thingY = actors.y(id);
        int trX = thingX - rs.viewX;
        int trY = thingY - rs.viewY;
        int gxt = Fixed.mul(trX, rs.viewCos);
        int gyt = -Fixed.mul(trY, rs.viewSin);
        int tz = gxt - gyt;
        if (tz < MINZ) {
            return;
        }
        int xscale = Fixed.div(rs.projection, tz);
        gxt = -Fixed.mul(trX, rs.viewSin);
        gyt = Fixed.mul(trY, rs.viewCos);
        int tx = -(gyt + gxt);
        if (Math.abs(tx) > (tz << 2)) {
            return;
        }
        int sprite = actors.sprite(id);
        if (sprite < 0 || sprite >= rs.sprites.count()) {
            return;
        }
        SpriteSet.Frame[] frames = rs.sprites.frames(sprite);
        int frameNum = actors.frame(id);
        int f = frameNum & FrameFlags.FRAMEMASK;
        if (f >= frames.length) {
            return;
        }
        SpriteSet.Frame sprframe = frames[f];
        int lump;
        boolean flip;
        if (sprframe.rotate != 0) {
            int ang = Trig.pointToAngle2(rs.viewX, rs.viewY, thingX, thingY);
            int rot = (ang - actors.angle(id) + (Angle.ANG45 / 2) * 9) >>> 29;
            lump = sprframe.lump[rot];
            flip = sprframe.flip[rot];
        } else {
            lump = sprframe.lump[0];
            flip = sprframe.flip[0];
        }
        SpriteSet.Image img = rs.sprites.image(lump);
        tx -= img.offset;
        int x1 = (rs.centerXFrac + Fixed.mul(tx, xscale)) >> Fixed.FRACBITS;
        if (x1 > rs.viewWidth) {
            return;
        }
        tx += img.width;
        int x2 = ((rs.centerXFrac + Fixed.mul(tx, xscale)) >> Fixed.FRACBITS) - 1;
        if (x2 < 0) {
            return;
        }
        if (numVis == vsX1.length) {
            growVis();
        }
        final int v = numVis++;
        vsFine[v] = false;
        int flags = actors.flags(id);
        vsMobjFlags[v] = flags;
        vsScale[v] = xscale << rs.detailShift;
        vsGx[v] = thingX;
        vsGy[v] = thingY;
        int z = actors.z(id);
        vsGz[v] = z;
        vsGzt[v] = z + img.topOffset;
        vsTexMid[v] = vsGzt[v] - rs.viewZ;
        vsX1[v] = x1 < 0 ? 0 : x1;
        vsX2[v] = x2 >= rs.viewWidth ? rs.viewWidth - 1 : x2;
        int iscale = Fixed.div(Fixed.FRACUNIT, xscale);
        if (flip) {
            vsStartFrac[v] = img.width - 1;
            vsXiScale[v] = -iscale;
        } else {
            vsStartFrac[v] = 0;
            vsXiScale[v] = iscale;
        }
        if (vsX1[v] > x1) {
            vsStartFrac[v] += vsXiScale[v] * (vsX1[v] - x1);
        }
        vsPatch[v] = lump;
        if ((flags & MobjFlags.SHADOW) != 0) {
            vsColormap[v] = SHADOW;
        } else if (rs.fixedColormap >= 0) {
            vsColormap[v] = rs.fixedColormap;
        } else if ((frameNum & FrameFlags.FULLBRIGHT) != 0) {
            vsColormap[v] = 0;
        } else {
            vsColormap[v] = spriteLights[rs.lightIndex(vsScale[v])];
        }
    }

    // ===================================================================================== precise sprites

    /**
     * {@code R_ProjectSprite} with the exact view angle and 64-bit limits (spec 04 section 3): the camera space
     * coordinates are not rounded to the 8192-step fine angle table, and the scale, screen columns and texture steps
     * cannot overflow or lose their low bits at high resolution.
     */
    private void projectSpritePrecise(int id) {
        final ActorView actors = rs.actors;
        final int thingX = actors.x(id);
        final int thingY = actors.y(id);
        final double trX = ((long) thingX - rs.viewX) / 65536.0;
        final double trY = ((long) thingY - rs.viewY) / 65536.0;
        final double tz = trX * rs.viewCosD + trY * rs.viewSinD;
        if (tz < MINZ / 65536.0) {
            return;
        }
        double tx = trX * rs.viewSinD - trY * rs.viewCosD;
        if (Math.abs(tx) > tz * 4) {
            return;
        }
        final int sprite = actors.sprite(id);
        if (sprite < 0 || sprite >= rs.sprites.count()) {
            return;
        }
        final SpriteSet.Frame[] frames = rs.sprites.frames(sprite);
        final int frameNum = actors.frame(id);
        final int f = frameNum & FrameFlags.FRAMEMASK;
        if (f >= frames.length) {
            return;
        }
        final SpriteSet.Frame sprframe = frames[f];
        final int lump;
        final boolean flip;
        if (sprframe.rotate != 0) {
            int ang = RenderState.bamFromRadians(Math.atan2(trY, trX));
            int rot = (ang - actors.angle(id) + (Angle.ANG45 / 2) * 9) >>> 29;
            lump = sprframe.lump[rot];
            flip = sprframe.flip[rot];
        } else {
            lump = sprframe.lump[0];
            flip = sprframe.flip[0];
        }
        final SpriteSet.Image img = rs.sprites.image(lump);
        // pixels per unit: render columns (horizontal) and full resolution (vertical)
        final double xscale = rs.projD / tz;
        final double fullScale = xscale * (1 << rs.detailShift);
        tx -= img.offset / 65536.0;
        final long x1 = (long) Math.floor(rs.centerX + tx * xscale);
        if (x1 > rs.viewWidth) {
            return;
        }
        tx += img.width / 65536.0;
        final long x2 = (long) Math.floor(rs.centerX + tx * xscale) - 1;
        if (x2 < 0) {
            return;
        }
        if (numVis == vsX1.length) {
            growVis();
        }
        final int v = numVis++;
        final int flags = actors.flags(id);
        vsFine[v] = true;
        vsMobjFlags[v] = flags;
        vsScale[v] = (int) Math.min(Integer.MAX_VALUE / 2, fullScale * 65536.0);
        vsGx[v] = thingX;
        vsGy[v] = thingY;
        final int z = actors.z(id);
        vsGz[v] = z;
        vsGzt[v] = z + img.topOffset;
        vsTexMid[v] = vsGzt[v] - rs.viewZ;
        vsX1[v] = (int) Math.max(0, x1);
        vsX2[v] = (int) Math.min(rs.viewWidth - 1, x2);
        final long iscale = (long) (4294967296.0 / xscale);
        long start;
        if (flip) {
            // the original starts at the fixed-point width minus one fixed-point unit
            start = ((long) img.width << 16) - 1;
            vsXiScaleP[v] = -iscale;
        } else {
            start = 0;
            vsXiScaleP[v] = iscale;
        }
        if (vsX1[v] > x1) {
            start += vsXiScaleP[v] * (vsX1[v] - x1);
        }
        vsStartFracP[v] = start;
        vsIscaleVP[v] = (long) (4294967296.0 / fullScale);
        vsPatch[v] = lump;
        if ((flags & MobjFlags.SHADOW) != 0) {
            vsColormap[v] = SHADOW;
        } else if (rs.fixedColormap >= 0) {
            vsColormap[v] = rs.fixedColormap;
        } else if ((frameNum & FrameFlags.FULLBRIGHT) != 0) {
            vsColormap[v] = 0;
        } else {
            vsColormap[v] = spriteLights[rs.lightIndex(vsScale[v])];
        }
    }

    /** {@code R_DrawMaskedColumn} with 64-bit screen positions. */
    private void drawMaskedColumnFine(PostImage img, int col) {
        final int basetexturemid = draw.dcTextureMid;
        final byte[] src = img.source(col);
        final int dcX = draw.dcX;
        final int floorClip = mfloor[mfloorBase + dcX];
        final int ceilClip = mceil[mceilBase + dcX];
        for (int p = img.first[col], e = img.first[col + 1]; p < e; p++) {
            int top = img.top[p];
            int len = img.length[p];
            long topscreen = sprTopScreenL + sprYScaleL * top;
            long bottomscreen = topscreen + sprYScaleL * len;
            long ylL = (topscreen + 0xffff) >> 16;
            long yhL = (bottomscreen - 1) >> 16;
            int yl = (int) Math.max(Integer.MIN_VALUE / 2, Math.min(Integer.MAX_VALUE / 2, ylL));
            int yh = (int) Math.max(Integer.MIN_VALUE / 2, Math.min(Integer.MAX_VALUE / 2, yhL));
            if (yh >= floorClip) {
                yh = floorClip - 1;
            }
            if (yl <= ceilClip) {
                yl = ceilClip + 1;
            }
            if (yl <= yh) {
                draw.dcYl = yl;
                draw.dcYh = yh;
                draw.dcSource = src;
                draw.dcSourceOfs = img.ofs[p];
                draw.dcLength = len;
                draw.dcTextureMid = basetexturemid - (top << Fixed.FRACBITS);
                draw.colfunc();
            }
        }
        draw.dcTextureMid = basetexturemid;
    }

    /** {@code R_DrawVisSprite} for a sprite queued by {@link #projectSpritePrecise}. */
    private void drawVisSpriteFine(int v) {
        SpriteSet.Image img = rs.sprites.image(vsPatch[v]);
        int cmap = vsColormap[v];
        draw.dcWrapMode = Draw.WRAP_CLAMP;
        if (cmap == SHADOW) {
            draw.colMode = Draw.COL_FUZZ;
            draw.dcColormap = 0;
        } else {
            draw.dcColormap = cmap;
            int flags = vsMobjFlags[v];
            if ((flags & MobjFlags.TRANSLATION) != 0) {
                draw.colMode = Draw.COL_TRANSLATED;
                draw.dcTranslation = ((flags & MobjFlags.TRANSLATION) >> (MobjFlags.TRANSSHIFT - 8)) - 256;
            } else {
                draw.colMode = Draw.COL_BASE;
            }
        }
        draw.dcIscale = (int) Math.min(Integer.MAX_VALUE, vsIscaleVP[v] >> 16);
        draw.dcIscaleP = vsIscaleVP[v];
        draw.dcFine = true;
        draw.dcTextureMid = vsTexMid[v];
        long frac = vsStartFracP[v];
        sprYScaleL = vsScale[v];
        sprTopScreenL = rs.centerYFrac - (((long) draw.dcTextureMid * sprYScaleL) >> 16);
        final PostImage posts = img.posts;
        final long xi = vsXiScaleP[v];
        final int last = posts.width - 1;
        for (int x = vsX1[v], x2 = vsX2[v]; x <= x2; x++, frac += xi) {
            draw.dcX = x;
            int texturecolumn = (int) (frac >> 32);
            if (texturecolumn < 0) {
                texturecolumn = 0;
            } else if (texturecolumn > last) {
                texturecolumn = last;
            }
            drawMaskedColumnFine(posts, texturecolumn);
        }
        draw.dcFine = false;
        draw.colMode = Draw.COL_BASE;
    }

    /** {@code R_RenderMaskedSegRange} with the exact wall scale. */
    private void renderMaskedSegRangeFine(int ds, int x1, int x2) {
        final BspWalls w = walls;
        final WorldView world = rs.world;
        final int curline = w.dsCurline[ds];
        final int front = world.segFrontSector(curline);
        final int back = world.segBackSector(curline);
        final int side = world.segSide(curline);
        final int texnum = world.textureTranslation(world.sideMidTexture(side));
        final int[] walllights = w.scaleLightRow(world.sectorLightLevel(front), curline);
        final int maskedBase = w.dsMaskedTexCol[ds];
        final double scalestep = w.dsScaleStepD[ds];
        double spryscale = w.dsScale1D[ds] + (x1 - w.dsX1[ds]) * scalestep;
        mfloor = rs.openings;
        mfloorBase = w.dsSprBottomClip[ds];
        mceil = rs.openings;
        mceilBase = w.dsSprTopClip[ds];

        final WallTexture tex = rs.textures.get(texnum);
        int textureMid;
        if ((world.lineFlags(world.segLine(curline)) & LineFlags.DONTPEGBOTTOM) != 0) {
            int fl = world.sectorFloorHeight(front);
            int bl = world.sectorFloorHeight(back);
            textureMid = fl > bl ? fl : bl;
            textureMid = textureMid + rs.textures.heightFixed(texnum) - rs.viewZ;
        } else {
            int fc = world.sectorCeilingHeight(front);
            int bc = world.sectorCeilingHeight(back);
            textureMid = fc < bc ? fc : bc;
            textureMid = textureMid - rs.viewZ;
        }
        textureMid += world.sideRowOffset(side);

        draw.colMode = Draw.COL_BASE;
        draw.dcWrapMode = Draw.WRAP_CLAMP;
        draw.dcFine = true;
        if (rs.fixedColormap >= 0) {
            draw.dcColormap = rs.fixedColormap;
        }
        final short[] openings = rs.openings;
        final PostImage posts = tex.posts();
        final double maxScale = rs.maxScaleD;
        for (int x = x1; x <= x2; x++) {
            if (openings[maskedBase + x] != Short.MAX_VALUE) {
                double sc = spryscale;
                if (sc > maxScale) {
                    sc = maxScale;
                } else if (sc < 1.0 / 256) {
                    sc = 1.0 / 256;
                }
                int fixed = (int) (sc * 65536.0);
                if (rs.fixedColormap < 0) {
                    draw.dcColormap = walllights[rs.lightIndex(fixed)];
                }
                draw.dcX = x;
                draw.dcTextureMid = textureMid;
                sprYScaleL = fixed;
                sprTopScreenL = rs.centerYFrac - (((long) textureMid * sprYScaleL) >> 16);
                draw.dcIscaleP = (long) (4294967296.0 / sc);
                int col = openings[maskedBase + x] & tex.widthMask;
                drawMaskedColumnFine(posts, col);
                openings[maskedBase + x] = Short.MAX_VALUE;
            }
            spryscale += scalestep;
        }
        draw.dcFine = false;
    }

    // ===================================================================================== masked columns

    /** {@code R_DrawMaskedColumn}: draws the posts of column {@code col} of {@code img}. */
    @VanillaRef("r_things.c:R_DrawMaskedColumn")
    private void drawMaskedColumn(PostImage img, int col) {
        final int basetexturemid = draw.dcTextureMid;
        final byte[] src = img.source(col);
        final int dcX = draw.dcX;
        final int floorClip = mfloor[mfloorBase + dcX];
        final int ceilClip = mceil[mceilBase + dcX];
        for (int p = img.first[col], e = img.first[col + 1]; p < e; p++) {
            int top = img.top[p];
            int len = img.length[p];
            int topscreen = sprTopScreen + sprYScale * top;
            int bottomscreen = topscreen + sprYScale * len;
            int yl = (topscreen + Fixed.FRACUNIT - 1) >> Fixed.FRACBITS;
            int yh = (bottomscreen - 1) >> Fixed.FRACBITS;
            if (yh >= floorClip) {
                yh = floorClip - 1;
            }
            if (yl <= ceilClip) {
                yl = ceilClip + 1;
            }
            if (yl <= yh) {
                draw.dcYl = yl;
                draw.dcYh = yh;
                draw.dcSource = src;
                draw.dcSourceOfs = img.ofs[p];
                draw.dcLength = len;
                draw.dcTextureMid = basetexturemid - (top << Fixed.FRACBITS);
                draw.colfunc();
            }
        }
        draw.dcTextureMid = basetexturemid;
    }

    /** {@code R_DrawVisSprite}. */
    @VanillaRef("r_things.c:R_DrawVisSprite")
    private void drawVisSprite(int v) {
        if (vsFine[v]) {
            drawVisSpriteFine(v);
            return;
        }
        SpriteSet.Image img = rs.sprites.image(vsPatch[v]);
        int cmap = vsColormap[v];
        draw.dcWrapMode = rs.vanilla ? Draw.WRAP_VANILLA : Draw.WRAP_CLAMP;
        if (cmap == SHADOW) {
            draw.colMode = Draw.COL_FUZZ;
            draw.dcColormap = 0;
        } else {
            draw.dcColormap = cmap;
            int flags = vsMobjFlags[v];
            if ((flags & MobjFlags.TRANSLATION) != 0) {
                draw.colMode = Draw.COL_TRANSLATED;
                draw.dcTranslation = ((flags & MobjFlags.TRANSLATION) >> (MobjFlags.TRANSSHIFT - 8)) - 256;
            } else {
                draw.colMode = Draw.COL_BASE;
            }
        }
        draw.dcIscale = Math.abs(vsXiScale[v]) >> rs.detailShift;
        draw.dcTextureMid = vsTexMid[v];
        int frac = vsStartFrac[v];
        sprYScale = vsScale[v];
        sprTopScreen = rs.centerYFrac - Fixed.mul(draw.dcTextureMid, sprYScale);
        final PostImage posts = img.posts;
        final int xi = vsXiScale[v];
        final int last = posts.width - 1;
        for (int x = vsX1[v], x2 = vsX2[v]; x <= x2; x++, frac += xi) {
            draw.dcX = x;
            int texturecolumn = frac >> Fixed.FRACBITS;
            if (texturecolumn < 0) {
                texturecolumn = 0;
            } else if (texturecolumn > last) {
                texturecolumn = last;
            }
            drawMaskedColumn(posts, texturecolumn);
        }
        draw.colMode = Draw.COL_BASE;
    }

    // ===================================================================================== player sprites

    /** {@code R_DrawPSprite}. */
    @VanillaRef("r_things.c:R_DrawPSprite")
    private void drawPSprite(int which) {
        final PlayerView pl = rs.player;
        int sprite = pl.pspriteSprite(which);
        if (sprite < 0 || sprite >= rs.sprites.count()) {
            return;
        }
        SpriteSet.Frame[] frames = rs.sprites.frames(sprite);
        int frameNum = pl.pspriteFrame(which);
        int f = frameNum & FrameFlags.FRAMEMASK;
        if (f >= frames.length) {
            return;
        }
        SpriteSet.Frame sprframe = frames[f];
        int lump = sprframe.lump[0];
        boolean flip = sprframe.flip[0];
        SpriteSet.Image img = rs.sprites.image(lump);
        int tx = pl.pspriteSx(which) - (320 / 2) * Fixed.FRACUNIT;
        tx -= img.offset;
        int x1 = (rs.centerXFrac + Fixed.mul(tx, rs.pspriteScale)) >> Fixed.FRACBITS;
        if (x1 > rs.viewWidth) {
            return;
        }
        tx += img.width;
        int x2 = ((rs.centerXFrac + Fixed.mul(tx, rs.pspriteScale)) >> Fixed.FRACBITS) - 1;
        if (x2 < 0) {
            return;
        }
        if (numVis == vsX1.length) {
            growVis();
        }
        final int v = numVis; // scratch slot, not counted
        vsMobjFlags[v] = 0;
        vsFine[v] = false;
        vsTexMid[v] = (BASEYCENTER << Fixed.FRACBITS) + Fixed.FRACUNIT / 2 - (pl.pspriteSy(which) - img.topOffset);
        vsX1[v] = x1 < 0 ? 0 : x1;
        vsX2[v] = x2 >= rs.viewWidth ? rs.viewWidth - 1 : x2;
        vsScale[v] = rs.pspriteScale << rs.detailShift;
        if (flip) {
            vsXiScale[v] = -rs.pspriteIScale;
            vsStartFrac[v] = img.width - 1;
        } else {
            vsXiScale[v] = rs.pspriteIScale;
            vsStartFrac[v] = 0;
        }
        if (vsX1[v] > x1) {
            vsStartFrac[v] += vsXiScale[v] * (vsX1[v] - x1);
        }
        vsPatch[v] = lump;
        int inv = pl.power(Power.INVISIBILITY);
        if (inv > 4 * 32 || (inv & 8) != 0) {
            vsColormap[v] = SHADOW;
        } else if (rs.fixedColormap >= 0) {
            vsColormap[v] = rs.fixedColormap;
        } else if ((frameNum & FrameFlags.FULLBRIGHT) != 0) {
            vsColormap[v] = 0;
        } else {
            vsColormap[v] = spriteLights[RenderState.MAXLIGHTSCALE - 1];
        }
        drawVisSprite(v);
    }

    /** {@code R_DrawPlayerSprites}. */
    @VanillaRef("r_things.c:R_DrawPlayerSprites")
    private void drawPlayerSprites() {
        final PlayerView pl = rs.player;
        int actor = pl.actor();
        int sector = actor >= 0 ? rs.actors.sector(actor) : walls.sectorAt(rs.viewX, rs.viewY);
        spriteLights = lightRow(rs.world.sectorLightLevel(sector) >> RenderState.LIGHTSEGSHIFT);
        mfloor = rs.openings;
        mfloorBase = rs.viewWidth; // screenheightarray
        mceil = rs.openings;
        mceilBase = 0; // negonearray
        for (int i = 0; i < 2; i++) {
            if (pl.pspriteActive(i)) {
                drawPSprite(i);
            }
        }
    }

    // ===================================================================================== sort + draw

    /** {@code R_SortVisSprites}: back to front (ascending scale), ties in the order the sprites were queued. */
    private void sortVisSprites() {
        final int n = numVis;
        final int[] order = sorted;
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        if (n < 2) {
            return;
        }
        final int[] scale = vsScale;
        if (n <= 64) {
            for (int i = 1; i < n; i++) {
                int key = order[i];
                int ks = scale[key];
                int j = i - 1;
                while (j >= 0 && scale[order[j]] > ks) {
                    order[j + 1] = order[j];
                    j--;
                }
                order[j + 1] = key;
            }
            return;
        }
        // stable bottom-up merge sort
        int[] src = order;
        int[] dst = sortScratch;
        for (int width = 1; width < n; width <<= 1) {
            for (int lo = 0; lo < n; lo += 2 * width) {
                int mid = Math.min(lo + width, n);
                int hi = Math.min(lo + 2 * width, n);
                int a = lo;
                int b = mid;
                int k = lo;
                while (a < mid && b < hi) {
                    if (scale[src[b]] < scale[src[a]]) {
                        dst[k++] = src[b++];
                    } else {
                        dst[k++] = src[a++];
                    }
                }
                while (a < mid) {
                    dst[k++] = src[a++];
                }
                while (b < hi) {
                    dst[k++] = src[b++];
                }
            }
            int[] t = src;
            src = dst;
            dst = t;
        }
        if (src != order) {
            System.arraycopy(src, 0, order, 0, n);
        }
    }

    /** {@code R_DrawSprite}. */
    @VanillaRef("r_things.c:R_DrawSprite")
    private void drawSprite(int v) {
        final BspWalls w = walls;
        final int x1 = vsX1[v];
        final int x2 = vsX2[v];
        final short[] clipbot = clipBot;
        final short[] cliptop = clipTop;
        for (int x = x1; x <= x2; x++) {
            clipbot[x] = cliptop[x] = -2;
        }
        final short[] openings = rs.openings;
        for (int ds = w.numDrawSegs - 1; ds >= 0; ds--) {
            if (w.dsX1[ds] > x2 || w.dsX2[ds] < x1 || (w.dsSilhouette[ds] == 0
                    && w.dsMaskedTexCol[ds] == RenderState.NOCLIP)) {
                continue;
            }
            int r1 = w.dsX1[ds] < x1 ? x1 : w.dsX1[ds];
            int r2 = w.dsX2[ds] > x2 ? x2 : w.dsX2[ds];
            int lowscale;
            int scale;
            if (w.dsScale1[ds] > w.dsScale2[ds]) {
                lowscale = w.dsScale2[ds];
                scale = w.dsScale1[ds];
            } else {
                lowscale = w.dsScale1[ds];
                scale = w.dsScale2[ds];
            }
            if (scale < vsScale[v] || (lowscale < vsScale[v]
                    && w.pointOnSegSide(vsGx[v], vsGy[v], w.dsCurline[ds]) == 0)) {
                if (w.dsMaskedTexCol[ds] != RenderState.NOCLIP) {
                    renderMaskedSegRange(ds, r1, r2);
                }
                continue;
            }
            int silhouette = w.dsSilhouette[ds];
            if (vsGz[v] >= w.dsBSilHeight[ds]) {
                silhouette &= ~BspWalls.SIL_BOTTOM;
            }
            if (vsGzt[v] <= w.dsTSilHeight[ds]) {
                silhouette &= ~BspWalls.SIL_TOP;
            }
            if (silhouette == 1) {
                int b = w.dsSprBottomClip[ds];
                for (int x = r1; x <= r2; x++) {
                    if (clipbot[x] == -2) {
                        clipbot[x] = openings[b + x];
                    }
                }
            } else if (silhouette == 2) {
                int t = w.dsSprTopClip[ds];
                for (int x = r1; x <= r2; x++) {
                    if (cliptop[x] == -2) {
                        cliptop[x] = openings[t + x];
                    }
                }
            } else if (silhouette == 3) {
                int b = w.dsSprBottomClip[ds];
                int t = w.dsSprTopClip[ds];
                for (int x = r1; x <= r2; x++) {
                    if (clipbot[x] == -2) {
                        clipbot[x] = openings[b + x];
                    }
                    if (cliptop[x] == -2) {
                        cliptop[x] = openings[t + x];
                    }
                }
            }
        }
        final short vh = (short) rs.viewHeight;
        for (int x = x1; x <= x2; x++) {
            if (clipbot[x] == -2) {
                clipbot[x] = vh;
            }
            if (cliptop[x] == -2) {
                cliptop[x] = -1;
            }
        }
        mfloor = clipbot;
        mfloorBase = 0;
        mceil = cliptop;
        mceilBase = 0;
        drawVisSprite(v);
    }

    /** {@code R_DrawMasked}. */
    @VanillaRef("r_things.c:R_DrawMasked")
    void drawMasked() {
        sortVisSprites();
        for (int i = 0; i < numVis; i++) {
            drawSprite(sorted[i]);
        }
        final BspWalls w = walls;
        for (int ds = w.numDrawSegs - 1; ds >= 0; ds--) {
            if (w.dsMaskedTexCol[ds] != RenderState.NOCLIP) {
                renderMaskedSegRange(ds, w.dsX1[ds], w.dsX2[ds]);
            }
        }
        drawPlayerSprites();
    }

    // ===================================================================================== masked segs

    /** {@code R_RenderMaskedSegRange}. */
    @VanillaRef("r_segs.c:R_RenderMaskedSegRange")
    private void renderMaskedSegRange(int ds, int x1, int x2) {
        if (rs.precise) {
            renderMaskedSegRangeFine(ds, x1, x2);
            return;
        }
        final BspWalls w = walls;
        final WorldView world = rs.world;
        final int curline = w.dsCurline[ds];
        final int front = world.segFrontSector(curline);
        final int back = world.segBackSector(curline);
        final int side = world.segSide(curline);
        final int texnum = world.textureTranslation(world.sideMidTexture(side));
        final int[] walllights = w.scaleLightRow(world.sectorLightLevel(front), curline);
        final int maskedBase = w.dsMaskedTexCol[ds];
        final int scalestep = w.dsScaleStep[ds];
        int spryscale = w.dsScale1[ds] + (x1 - w.dsX1[ds]) * scalestep;
        mfloor = rs.openings;
        mfloorBase = w.dsSprBottomClip[ds];
        mceil = rs.openings;
        mceilBase = w.dsSprTopClip[ds];

        final WallTexture tex = rs.textures.get(texnum);
        int textureMid;
        if ((world.lineFlags(world.segLine(curline)) & LineFlags.DONTPEGBOTTOM) != 0) {
            int fl = world.sectorFloorHeight(front);
            int bl = world.sectorFloorHeight(back);
            textureMid = fl > bl ? fl : bl;
            textureMid = textureMid + rs.textures.heightFixed(texnum) - rs.viewZ;
        } else {
            int fc = world.sectorCeilingHeight(front);
            int bc = world.sectorCeilingHeight(back);
            textureMid = fc < bc ? fc : bc;
            textureMid = textureMid - rs.viewZ;
        }
        textureMid += world.sideRowOffset(side);

        draw.colMode = Draw.COL_BASE;
        draw.dcWrapMode = rs.vanilla ? Draw.WRAP_VANILLA : Draw.WRAP_CLAMP;
        if (rs.fixedColormap >= 0) {
            draw.dcColormap = rs.fixedColormap;
        }
        final short[] openings = rs.openings;
        final PostImage posts = tex.posts();
        for (int x = x1; x <= x2; x++) {
            if (openings[maskedBase + x] != Short.MAX_VALUE) {
                if (rs.fixedColormap < 0) {
                    draw.dcColormap = walllights[rs.lightIndex(spryscale)];
                }
                draw.dcX = x;
                draw.dcTextureMid = textureMid;
                sprYScale = spryscale;
                sprTopScreen = rs.centerYFrac - Fixed.mul(textureMid, spryscale);
                draw.dcIscale = (int) (0xffffffffL / (spryscale & 0xffffffffL));
                int col = openings[maskedBase + x] & tex.widthMask;
                drawMaskedColumn(posts, col);
                openings[maskedBase + x] = Short.MAX_VALUE;
            }
            spryscale += scalestep;
        }
    }
}
