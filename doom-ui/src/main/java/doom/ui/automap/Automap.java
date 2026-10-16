package doom.ui.automap;

import doom.game.view.ActorView;
import doom.game.view.GameView;
import doom.game.view.LineFlags;
import doom.game.view.PlayerView;
import doom.game.view.Power;
import doom.game.view.WorldView;
import doom.math.Angle;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.annotation.VanillaRef;
import doom.render.FrameBuffer;
import doom.render.canvas.Canvas2D;
import doom.ui.MappedLines;
import doom.ui.UiAssets;
import doom.ui.UiKeys;
import doom.ui.menu.MessageSink;
import doom.wad.gfx.Picture;

/**
 * The automap ({@code am_map.c}): follow mode, pan, zoom, grid, marks, wall colours by line type, the {@code iddt}
 * cheat and the player arrow. Unlike the other screens it is drawn at the native frame resolution: the map window is
 * {@code W × (H - status bar)} pixels and lines are {@code max(1, round(s * lineScale))} pixels thick.
 * All map arithmetic is the original fixed point; only the view window is interpolated between tics.
 */
public final class Automap {

    private static final int PLAYERRADIUS = 16 * Fixed.FRACUNIT;
    private static final int MAPBLOCKUNITS = 128;

    // colours (palette indices)
    private static final int REDS = 256 - 5 * 16;
    private static final int REDRANGE = 16;
    private static final int GREENS = 7 * 16;
    private static final int GRAYS = 6 * 16;
    private static final int BROWNS = 4 * 16;
    private static final int YELLOWS = 256 - 32 + 7;
    private static final int BLACK = 0;
    private static final int WHITE = 256 - 47;
    private static final int WALLCOLORS = REDS;
    private static final int WALLRANGE = REDRANGE;
    private static final int TSWALLCOLORS = GRAYS;
    private static final int FDWALLCOLORS = BROWNS;
    private static final int CDWALLCOLORS = YELLOWS;
    private static final int THINGCOLORS = GREENS;
    private static final int SECRETWALLCOLORS = WALLCOLORS;
    private static final int GRIDCOLORS = GRAYS + 16 / 2;
    private static final int XHAIRCOLORS = GRAYS;

    private static final int AM_NUMMARKPOINTS = 10;
    private static final int INITSCALEMTOF = (int) (.2 * Fixed.FRACUNIT);
    private static final int F_PANINC = 4;
    private static final int M_ZOOMIN = (int) (1.02 * Fixed.FRACUNIT);
    private static final int M_ZOOMOUT = (int) (Fixed.FRACUNIT / 1.02);

    private static final int R = (8 * PLAYERRADIUS) / 7;
    private static final int[][] PLAYER_ARROW = {
        {-R + R / 8, 0, R, 0},
        {R, 0, R - R / 2, R / 4},
        {R, 0, R - R / 2, -R / 4},
        {-R + R / 8, 0, -R - R / 8, R / 4},
        {-R + R / 8, 0, -R - R / 8, -R / 4},
        {-R + 3 * R / 8, 0, -R + R / 8, R / 4},
        {-R + 3 * R / 8, 0, -R + R / 8, -R / 4}};
    private static final int[][] CHEAT_PLAYER_ARROW = {
        {-R + R / 8, 0, R, 0},
        {R, 0, R - R / 2, R / 6},
        {R, 0, R - R / 2, -R / 6},
        {-R + R / 8, 0, -R - R / 8, R / 6},
        {-R + R / 8, 0, -R - R / 8, -R / 6},
        {-R + 3 * R / 8, 0, -R + R / 8, R / 6},
        {-R + 3 * R / 8, 0, -R + R / 8, -R / 6},
        {-R / 2, 0, -R / 2, -R / 6},
        {-R / 2, -R / 6, -R / 2 + R / 6, -R / 6},
        {-R / 2 + R / 6, -R / 6, -R / 2 + R / 6, R / 4},
        {-R / 6, 0, -R / 6, -R / 6},
        {-R / 6, -R / 6, 0, -R / 6},
        {0, -R / 6, 0, R / 4},
        {R / 6, R / 4, R / 6, -R / 7},
        {R / 6, -R / 7, R / 6 + R / 32, -R / 7 - R / 32},
        {R / 6 + R / 32, -R / 7 - R / 32, R / 6 + R / 10, -R / 7}};
    private static final int[][] THIN_TRIANGLE_GUY = {
        {(int) (-.5 * Fixed.FRACUNIT), (int) (-.7 * Fixed.FRACUNIT), Fixed.FRACUNIT, 0},
        {Fixed.FRACUNIT, 0, (int) (-.5 * Fixed.FRACUNIT), (int) (.7 * Fixed.FRACUNIT)},
        {(int) (-.5 * Fixed.FRACUNIT), (int) (.7 * Fixed.FRACUNIT), (int) (-.5 * Fixed.FRACUNIT),
            (int) (-.7 * Fixed.FRACUNIT)}};

    private static final char[] CHEAT_IDDT = {'i', 'd', 'd', 't'};

    private final UiAssets assets;
    private final GameView view;
    private final MessageSink messages;
    private MappedLines mapped = MappedLines.NONE;
    private double lineScale = 0.6;

    private boolean active;
    private boolean stopped = true;
    private int cheating;
    private boolean grid;
    private boolean followPlayer = true;
    private boolean bigState;
    private int cheatProgress;

    // window on the screen (f_*), in physical pixels
    private int fX;
    private int fY;
    private int fW = 320;
    private int fH = 168;
    private int statusBarPixels = 32;

    private int mPanIncX;
    private int mPanIncY;
    private int mtofZoomMul = Fixed.FRACUNIT;
    private int ftomZoomMul = Fixed.FRACUNIT;
    private int mX;
    private int mY;
    private int mX2;
    private int mY2;
    private int mW;
    private int mH;
    private int minX;
    private int minY;
    private int maxX;
    private int maxY;
    private int maxW;
    private int maxH;
    private int minScaleMtof;
    private int maxScaleMtof;
    private int oldMW;
    private int oldMH;
    private int oldMX;
    private int oldMY;
    private int fOldLocX = Integer.MAX_VALUE;
    private int fOldLocY;
    private int scaleMtof = INITSCALEMTOF;
    private int scaleFtom;
    private int lastLevel = -1;
    private int lastEpisode = -1;
    private final int[] markX = new int[AM_NUMMARKPOINTS];
    private final int[] markY = new int[AM_NUMMARKPOINTS];
    private int markPointNum;
    private final Picture[] markNums = new Picture[10];
    private int ampClock;

    // state at the start of the last tic, for interpolation
    private int pmX;
    private int pmY;
    private int pScaleMtof;

    // view used by one draw call
    private int vX;
    private int vY;
    private int vX2;
    private int vY2;
    private int vScale;
    private int thickness = 1;
    private double alpha = 1;

    public Automap(UiAssets assets, GameView view, MessageSink messages) {
        this.assets = assets;
        this.view = view;
        this.messages = messages;
        scaleFtom = Fixed.div(Fixed.FRACUNIT, scaleMtof);
        clearMarks();
    }

    public void setMappedLines(MappedLines mapped) {
        this.mapped = mapped;
    }

    public void setLineScale(double lineScale) {
        this.lineScale = lineScale;
    }

    public boolean active() {
        return active;
    }

    /** 0 off, 1 all lines, 2 all lines and things ({@code cheating}). */
    public int cheating() {
        return cheating;
    }

    public boolean followPlayer() {
        return followPlayer;
    }

    public boolean gridOn() {
        return grid;
    }

    /** Map window width in pixels ({@code f_w}). */
    public int windowWidth() {
        return fW;
    }

    public int windowHeight() {
        return fH;
    }

    /** Fixed-point view window (for tests). */
    public int viewX() {
        return mX;
    }

    public int viewY() {
        return mY;
    }

    public int scale() {
        return scaleMtof;
    }

    public int markCount() {
        int n = 0;
        for (int i = 0; i < AM_NUMMARKPOINTS; i++) {
            if (markX[i] != -1) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ player and world helpers

    private PlayerView player() {
        int me = view.consolePlayer();
        if (view.playerInGame(me)) {
            return view.player(me);
        }
        for (int i = 0; i < 4; i++) {
            if (view.playerInGame(i)) {
                return view.player(i);
            }
        }
        return view.player(0);
    }

    private int plrX() {
        return player().x();
    }

    private int plrY() {
        return player().y();
    }

    private int ftom(int x) {
        return Fixed.mul(x << Fixed.FRACBITS, scaleFtom);
    }

    private int mtof(int x) {
        return Fixed.mul(x, scaleMtof) >> Fixed.FRACBITS;
    }

    // ------------------------------------------------------------------ setup

    /**
     * Tells the automap how big the frame is. The map window is the whole width and everything above the status bar.
     * Re-fits the zoom limits when the size changed.
     */
    public void setFrame(int width, int height, int statusBarPixels) {
        int nh = Math.max(1, height - statusBarPixels);
        if (width == fW && nh == fH) {
            return;
        }
        fW = width;
        fH = nh;
        this.statusBarPixels = statusBarPixels;
        if (!stopped) {
            findMinMaxBoundaries();
            if (scaleMtof < minScaleMtof) {
                scaleMtof = minScaleMtof;
            } else if (scaleMtof > maxScaleMtof) {
                scaleMtof = maxScaleMtof;
            }
            scaleFtom = Fixed.div(Fixed.FRACUNIT, scaleMtof);
            activateNewScale();
        }
    }

    @VanillaRef("am_map.c:AM_findMinMaxBoundaries")
    private void findMinMaxBoundaries() {
        WorldView w = view.world();
        minX = Integer.MAX_VALUE;
        minY = Integer.MAX_VALUE;
        maxX = -Integer.MAX_VALUE;
        maxY = -Integer.MAX_VALUE;
        for (int i = 0; i < w.vertexCount(); i++) {
            int x = w.vertexX(i);
            int y = w.vertexY(i);
            if (x < minX) {
                minX = x;
            } else if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            } else if (y > maxY) {
                maxY = y;
            }
        }
        if (w.vertexCount() == 0) {
            minX = minY = 0;
            maxX = maxY = 1 << Fixed.FRACBITS;
        }
        maxW = Math.max(1, maxX - minX);
        maxH = Math.max(1, maxY - minY);
        int a = Fixed.div(fW << Fixed.FRACBITS, maxW);
        int b = Fixed.div(fH << Fixed.FRACBITS, maxH);
        minScaleMtof = a < b ? a : b;
        maxScaleMtof = Fixed.div(fH << Fixed.FRACBITS, 2 * PLAYERRADIUS);
    }

    @VanillaRef("am_map.c:AM_clearMarks")
    private void clearMarks() {
        for (int i = 0; i < AM_NUMMARKPOINTS; i++) {
            markX[i] = -1;
        }
        markPointNum = 0;
    }

    @VanillaRef("am_map.c:AM_LevelInit")
    private void levelInit() {
        fX = 0;
        fY = 0;
        clearMarks();
        findMinMaxBoundaries();
        scaleMtof = Fixed.div(minScaleMtof, (int) (0.7 * Fixed.FRACUNIT));
        if (scaleMtof > maxScaleMtof) {
            scaleMtof = minScaleMtof;
        }
        scaleFtom = Fixed.div(Fixed.FRACUNIT, scaleMtof);
    }

    @VanillaRef("am_map.c:AM_initVariables")
    private void initVariables() {
        active = true;
        fOldLocX = Integer.MAX_VALUE;
        ampClock = 0;
        mPanIncX = 0;
        mPanIncY = 0;
        ftomZoomMul = Fixed.FRACUNIT;
        mtofZoomMul = Fixed.FRACUNIT;
        mW = ftom(fW);
        mH = ftom(fH);
        mX = plrX() - mW / 2;
        mY = plrY() - mH / 2;
        changeWindowLoc();
        oldMX = mX;
        oldMY = mY;
        oldMW = mW;
        oldMH = mH;
        pmX = mX;
        pmY = mY;
        pScaleMtof = scaleMtof;
    }

    /** {@code AM_Start}. */
    @VanillaRef("am_map.c:AM_Start")
    public void start() {
        if (!stopped) {
            stop();
        }
        stopped = false;
        if (lastLevel != view.map() || lastEpisode != view.episode()) {
            levelInit();
            lastLevel = view.map();
            lastEpisode = view.episode();
        }
        initVariables();
        for (int i = 0; i < 10; i++) {
            markNums[i] = assets.patch("AMMNUM" + i);
        }
    }

    /** {@code AM_Stop}. */
    @VanillaRef("am_map.c:AM_Stop")
    public void stop() {
        active = false;
        stopped = true;
        bigState = false;
    }

    /** Sets {@code automapactive = false} without {@code AM_Stop}, as {@code G_InitNew} does. */
    public void deactivate() {
        active = false;
    }

    // ------------------------------------------------------------------ scale and location

    private void activateNewScale() {
        mX += mW / 2;
        mY += mH / 2;
        mW = ftom(fW);
        mH = ftom(fH);
        mX -= mW / 2;
        mY -= mH / 2;
        mX2 = mX + mW;
        mY2 = mY + mH;
    }

    private void saveScaleAndLoc() {
        oldMX = mX;
        oldMY = mY;
        oldMW = mW;
        oldMH = mH;
    }

    private void restoreScaleAndLoc() {
        mW = oldMW;
        mH = oldMH;
        if (!followPlayer) {
            mX = oldMX;
            mY = oldMY;
        } else {
            mX = plrX() - mW / 2;
            mY = plrY() - mH / 2;
        }
        mX2 = mX + mW;
        mY2 = mY + mH;
        scaleMtof = Fixed.div(fW << Fixed.FRACBITS, mW);
        scaleFtom = Fixed.div(Fixed.FRACUNIT, scaleMtof);
    }

    private void minOutWindowScale() {
        scaleMtof = minScaleMtof;
        scaleFtom = Fixed.div(Fixed.FRACUNIT, scaleMtof);
        activateNewScale();
    }

    private void maxOutWindowScale() {
        scaleMtof = maxScaleMtof;
        scaleFtom = Fixed.div(Fixed.FRACUNIT, scaleMtof);
        activateNewScale();
    }

    private void changeWindowLoc() {
        if (mPanIncX != 0 || mPanIncY != 0) {
            followPlayer = false;
            fOldLocX = Integer.MAX_VALUE;
        }
        mX += mPanIncX;
        mY += mPanIncY;
        if (mX + mW / 2 > maxX) {
            mX = maxX - mW / 2;
        } else if (mX + mW / 2 < minX) {
            mX = minX - mW / 2;
        }
        if (mY + mH / 2 > maxY) {
            mY = maxY - mH / 2;
        } else if (mY + mH / 2 < minY) {
            mY = minY - mH / 2;
        }
        mX2 = mX + mW;
        mY2 = mY + mH;
    }

    private void changeWindowScale() {
        scaleMtof = Fixed.mul(scaleMtof, mtofZoomMul);
        scaleFtom = Fixed.div(Fixed.FRACUNIT, scaleMtof);
        if (scaleMtof < minScaleMtof) {
            minOutWindowScale();
        } else if (scaleMtof > maxScaleMtof) {
            maxOutWindowScale();
        } else {
            activateNewScale();
        }
    }

    private void doFollowPlayer() {
        int px = plrX();
        int py = plrY();
        if (fOldLocX != px || fOldLocY != py) {
            mX = ftom(mtof(px)) - mW / 2;
            mY = ftom(mtof(py)) - mH / 2;
            mX2 = mX + mW;
            mY2 = mY + mH;
            fOldLocX = px;
            fOldLocY = py;
        }
    }

    private void addMark() {
        markX[markPointNum] = mX + mW / 2;
        markY[markPointNum] = mY + mH / 2;
        markPointNum = (markPointNum + 1) % AM_NUMMARKPOINTS;
    }

    /** {@code AM_Ticker}. */
    @VanillaRef("am_map.c:AM_Ticker")
    public void tick() {
        if (!active) {
            return;
        }
        pmX = mX;
        pmY = mY;
        pScaleMtof = scaleMtof;
        ampClock++;
        if (followPlayer) {
            doFollowPlayer();
        }
        if (ftomZoomMul != Fixed.FRACUNIT) {
            changeWindowScale();
        }
        if (mPanIncX != 0 || mPanIncY != 0) {
            changeWindowLoc();
        }
    }

    // ------------------------------------------------------------------ input

    /**
     * {@code AM_Responder} for a key press. Returns true when the key was used (so the game must not see it).
     */
    @VanillaRef("am_map.c:AM_Responder")
    public boolean keyDown(int key) {
        boolean rc = false;
        if (!active) {
            if (key == UiKeys.TAB) {
                start();
                rc = true;
            }
            return rc;
        }
        rc = true;
        if (key == UiKeys.RIGHTARROW) {
            if (!followPlayer) {
                mPanIncX = ftom(F_PANINC);
            } else {
                rc = false;
            }
        } else if (key == UiKeys.LEFTARROW) {
            if (!followPlayer) {
                mPanIncX = -ftom(F_PANINC);
            } else {
                rc = false;
            }
        } else if (key == UiKeys.UPARROW) {
            if (!followPlayer) {
                mPanIncY = ftom(F_PANINC);
            } else {
                rc = false;
            }
        } else if (key == UiKeys.DOWNARROW) {
            if (!followPlayer) {
                mPanIncY = -ftom(F_PANINC);
            } else {
                rc = false;
            }
        } else if (key == UiKeys.MINUS) {
            mtofZoomMul = M_ZOOMOUT;
            ftomZoomMul = M_ZOOMIN;
        } else if (key == UiKeys.EQUALS) {
            mtofZoomMul = M_ZOOMIN;
            ftomZoomMul = M_ZOOMOUT;
        } else if (key == UiKeys.TAB) {
            bigState = false;
            stop();
        } else if (key == '0') {
            bigState = !bigState;
            if (bigState) {
                saveScaleAndLoc();
                minOutWindowScale();
            } else {
                restoreScaleAndLoc();
            }
        } else if (key == 'f') {
            followPlayer = !followPlayer;
            fOldLocX = Integer.MAX_VALUE;
            messages.post(text(followPlayer ? "AMSTR_FOLLOWON" : "AMSTR_FOLLOWOFF"), false);
        } else if (key == 'g') {
            grid = !grid;
            messages.post(text(grid ? "AMSTR_GRIDON" : "AMSTR_GRIDOFF"), false);
        } else if (key == 'm') {
            messages.post(text("AMSTR_MARKEDSPOT") + " " + markPointNum, false);
            addMark();
        } else if (key == 'c') {
            clearMarks();
            messages.post(text("AMSTR_MARKSCLEARED"), false);
        } else {
            rc = false;
        }

        // cht_CheckCheat(&cheat_amap, ev->data2)
        if (view.deathmatch() == 0 && key >= 32 && key < 127) {
            if (key == CHEAT_IDDT[cheatProgress]) {
                cheatProgress++;
                if (cheatProgress == CHEAT_IDDT.length) {
                    cheatProgress = 0;
                    rc = false;
                    cheating = (cheating + 1) % 3;
                }
            } else {
                cheatProgress = 0;
            }
        }
        return rc;
    }

    /** {@code AM_Responder} for a key release. Always false. */
    public boolean keyUp(int key) {
        if (!active) {
            return false;
        }
        if (key == UiKeys.RIGHTARROW || key == UiKeys.LEFTARROW) {
            if (!followPlayer) {
                mPanIncX = 0;
            }
        } else if (key == UiKeys.UPARROW || key == UiKeys.DOWNARROW) {
            if (!followPlayer) {
                mPanIncY = 0;
            }
        } else if (key == UiKeys.MINUS || key == UiKeys.EQUALS) {
            mtofZoomMul = Fixed.FRACUNIT;
            ftomZoomMul = Fixed.FRACUNIT;
        }
        return false;
    }

    private String text(String key) {
        try {
            return view.defs().text(key);
        } catch (RuntimeException e) {
            return key;
        }
    }

    // ------------------------------------------------------------------ drawing

    private int lerp(int a, int b) {
        return (int) (a + (long) ((b - (long) a) * alpha));
    }

    /**
     * {@code AM_Drawer}: draws the map into the top {@code H - statusBarPixels} rows of the frame.
     *
     * @param alpha interpolation between the previous tic (0) and the current one (1)
     */
    @VanillaRef("am_map.c:AM_Drawer")
    public void draw(Canvas2D canvas, float alpha) {
        if (!active) {
            return;
        }
        FrameBuffer fb = canvas.frame();
        int sb = canvas.frame().height() - canvas.physicalY(168);
        setFrame(fb.width(), fb.height(), Math.max(0, sb));
        this.alpha = Math.max(0f, Math.min(1f, alpha));

        // interpolated window
        vScale = lerp(pScaleMtof, scaleMtof);
        vX = lerp(pmX, mX);
        vY = lerp(pmY, mY);
        int invScale = Fixed.div(Fixed.FRACUNIT, vScale);
        int vw = Fixed.mul(fW << Fixed.FRACBITS, invScale);
        int vh = Fixed.mul(fH << Fixed.FRACBITS, invScale);
        // keep the centre of the interpolated window; the size follows the interpolated scale
        int cx = vX + (mW / 2);
        int cy = vY + (mH / 2);
        vX = cx - vw / 2;
        vY = cy - vh / 2;
        vX2 = vX + vw;
        vY2 = vY + vh;
        thickness = Math.max(1, (int) Math.round(canvas.scale() * lineScale));

        canvas.fillPixels(0, 0, fW, fH, BLACK);
        if (grid) {
            drawGrid(canvas, GRIDCOLORS);
        }
        drawWalls(canvas);
        drawPlayers(canvas);
        if (cheating == 2) {
            drawThings(canvas);
        }
        stamp(canvas, fW / 2, fH / 2, XHAIRCOLORS, thickness);
        drawMarks(canvas);
    }

    // ---- coordinate transforms (CXMTOF, CYMTOF) with the interpolated view

    private int cxmtof(int x) {
        return fX + (Fixed.mul(x - vX, vScale) >> Fixed.FRACBITS);
    }

    private int cymtof(int y) {
        return fY + (fH - (Fixed.mul(y - vY, vScale) >> Fixed.FRACBITS));
    }

    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int BOTTOM = 4;
    private static final int TOP = 8;

    private int outcode(int mx, int my) {
        int oc = 0;
        if (my < 0) {
            oc |= TOP;
        } else if (my >= fH) {
            oc |= BOTTOM;
        }
        if (mx < 0) {
            oc |= LEFT;
        } else if (mx >= fW) {
            oc |= RIGHT;
        }
        return oc;
    }

    // result of clipMline
    private int flAX;
    private int flAY;
    private int flBX;
    private int flBY;

    @VanillaRef("am_map.c:AM_clipMline")
    private boolean clipMline(int ax, int ay, int bx, int by) {
        int outcode1 = 0;
        int outcode2 = 0;
        if (ay > vY2) {
            outcode1 = TOP;
        } else if (ay < vY) {
            outcode1 = BOTTOM;
        }
        if (by > vY2) {
            outcode2 = TOP;
        } else if (by < vY) {
            outcode2 = BOTTOM;
        }
        if ((outcode1 & outcode2) != 0) {
            return false;
        }
        if (ax < vX) {
            outcode1 |= LEFT;
        } else if (ax > vX2) {
            outcode1 |= RIGHT;
        }
        if (bx < vX) {
            outcode2 |= LEFT;
        } else if (bx > vX2) {
            outcode2 |= RIGHT;
        }
        if ((outcode1 & outcode2) != 0) {
            return false;
        }
        flAX = cxmtof(ax);
        flAY = cymtof(ay);
        flBX = cxmtof(bx);
        flBY = cymtof(by);
        outcode1 = outcode(flAX, flAY);
        outcode2 = outcode(flBX, flBY);
        if ((outcode1 & outcode2) != 0) {
            return false;
        }
        while ((outcode1 | outcode2) != 0) {
            int outside = outcode1 != 0 ? outcode1 : outcode2;
            int tx;
            int ty;
            int dx;
            int dy;
            if ((outside & TOP) != 0) {
                dy = flAY - flBY;
                dx = flBX - flAX;
                tx = flAX + (int) (((long) dx * flAY) / dy);
                ty = 0;
            } else if ((outside & BOTTOM) != 0) {
                dy = flAY - flBY;
                dx = flBX - flAX;
                tx = flAX + (int) (((long) dx * (flAY - fH)) / dy);
                ty = fH - 1;
            } else if ((outside & RIGHT) != 0) {
                dy = flBY - flAY;
                dx = flBX - flAX;
                ty = flAY + (int) (((long) dy * (fW - 1 - flAX)) / dx);
                tx = fW - 1;
            } else if ((outside & LEFT) != 0) {
                dy = flBY - flAY;
                dx = flBX - flAX;
                ty = flAY + (int) (((long) dy * (-flAX)) / dx);
                tx = 0;
            } else {
                tx = 0;
                ty = 0;
            }
            if (outside == outcode1) {
                flAX = tx;
                flAY = ty;
                outcode1 = outcode(flAX, flAY);
            } else {
                flBX = tx;
                flBY = ty;
                outcode2 = outcode(flBX, flBY);
            }
            if ((outcode1 & outcode2) != 0) {
                return false;
            }
        }
        return true;
    }

    /** Puts a {@code t × t} square of colour centred on (x, y), clipped to the map window. */
    private void stamp(Canvas2D canvas, int x, int y, int colour, int t) {
        int x0 = x - (t - 1) / 2;
        int y0 = y - (t - 1) / 2;
        canvas.fillPixels(Math.max(0, x0), Math.max(0, y0), Math.min(fW, x0 + t), Math.min(fH, y0 + t), colour);
    }

    @VanillaRef("am_map.c:AM_drawFline")
    private void drawFline(Canvas2D canvas, int colour) {
        FrameBuffer fb = canvas.frame();
        byte[] px = fb.pixels();
        int colStride = fb.columnStride();
        int rowStride = fb.rowStride();
        int t = thickness;
        int lo = -((t - 1) / 2);
        int hi = t / 2;
        byte c = (byte) colour;

        int dx = flBX - flAX;
        int ax = 2 * (dx < 0 ? -dx : dx);
        int sx = dx < 0 ? -1 : 1;
        int dy = flBY - flAY;
        int ay = 2 * (dy < 0 ? -dy : dy);
        int sy = dy < 0 ? -1 : 1;
        int x = flAX;
        int y = flAY;
        int d;
        if (ax > ay) {
            d = ay - ax / 2;
            while (true) {
                dot(px, colStride, rowStride, x, y, lo, hi, c);
                if (x == flBX) {
                    return;
                }
                if (d >= 0) {
                    y += sy;
                    d -= ax;
                }
                x += sx;
                d += ay;
            }
        } else {
            d = ax - ay / 2;
            while (true) {
                dot(px, colStride, rowStride, x, y, lo, hi, c);
                if (y == flBY) {
                    return;
                }
                if (d >= 0) {
                    x += sx;
                    d -= ay;
                }
                y += sy;
                d += ax;
            }
        }
    }

    private void dot(byte[] px, int colStride, int rowStride, int x, int y, int lo, int hi, byte c) {
        if (lo == 0 && hi == 0) {
            if (x >= 0 && x < fW && y >= 0 && y < fH) {
                px[x * colStride + y * rowStride] = c;
            }
            return;
        }
        int x0 = Math.max(0, x + lo);
        int x1 = Math.min(fW - 1, x + hi);
        int y0 = Math.max(0, y + lo);
        int y1 = Math.min(fH - 1, y + hi);
        for (int yy = y0; yy <= y1; yy++) {
            for (int xx = x0; xx <= x1; xx++) {
                px[xx * colStride + yy * rowStride] = c;
            }
        }
    }

    private void drawMline(Canvas2D canvas, int ax, int ay, int bx, int by, int colour) {
        if (clipMline(ax, ay, bx, by)) {
            drawFline(canvas, colour);
        }
    }

    @VanillaRef("am_map.c:AM_drawGrid")
    private void drawGrid(Canvas2D canvas, int colour) {
        WorldView w = view.world();
        int unit = MAPBLOCKUNITS << Fixed.FRACBITS;
        int start = vX;
        int rem = (start - w.blockmapOriginX()) % unit;
        if (rem != 0) {
            start += unit - rem;
        }
        int end = vX + (vX2 - vX);
        for (int x = start; x < end; x += unit) {
            drawMline(canvas, x, vY, x, vY2, colour);
        }
        start = vY;
        rem = (start - w.blockmapOriginY()) % unit;
        if (rem != 0) {
            start += unit - rem;
        }
        end = vY2;
        for (int y = start; y < end; y += unit) {
            drawMline(canvas, vX, y, vX2, y, colour);
        }
    }

    @VanillaRef("am_map.c:AM_drawWalls")
    private void drawWalls(Canvas2D canvas) {
        WorldView w = view.world();
        PlayerView plr = player();
        boolean allMap = plr.power(Power.ALLMAP) != 0;
        boolean cheat = cheating != 0;
        int n = w.lineCount();
        for (int i = 0; i < n; i++) {
            int v1 = w.lineV1(i);
            int v2 = w.lineV2(i);
            int ax = w.vertexX(v1);
            int ay = w.vertexY(v1);
            int bx = w.vertexX(v2);
            int by = w.vertexY(v2);
            int flags = w.lineFlags(i);
            if (cheat || mapped.isMapped(i)) {
                if ((flags & LineFlags.DONTDRAW) != 0 && !cheat) {
                    continue;
                }
                int back = w.lineBackSector(i);
                if (back < 0) {
                    drawMline(canvas, ax, ay, bx, by, WALLCOLORS);
                } else {
                    int front = w.lineFrontSector(i);
                    if (w.lineSpecial(i) == 39) {
                        // teleporters
                        drawMline(canvas, ax, ay, bx, by, WALLCOLORS + WALLRANGE / 2);
                    } else if ((flags & LineFlags.SECRET) != 0) {
                        if (cheat) {
                            drawMline(canvas, ax, ay, bx, by, SECRETWALLCOLORS);
                        } else {
                            drawMline(canvas, ax, ay, bx, by, WALLCOLORS);
                        }
                    } else if (w.sectorFloorHeight(back) != w.sectorFloorHeight(front)) {
                        drawMline(canvas, ax, ay, bx, by, FDWALLCOLORS);
                    } else if (w.sectorCeilingHeight(back) != w.sectorCeilingHeight(front)) {
                        drawMline(canvas, ax, ay, bx, by, CDWALLCOLORS);
                    } else if (cheat) {
                        drawMline(canvas, ax, ay, bx, by, TSWALLCOLORS);
                    }
                }
            } else if (allMap) {
                if ((flags & LineFlags.DONTDRAW) == 0) {
                    drawMline(canvas, ax, ay, bx, by, GRAYS + 3);
                }
            }
        }
    }

    /** {@code AM_rotate} into ({@code rx}, {@code ry}). */
    private int rotX;
    private int rotY;

    private void rotate(int x, int y, int a) {
        int fine = a >>> Angle.ANGLETOFINESHIFT;
        int cos = Tables.fineCosine(fine);
        int sin = Tables.fineSine(fine);
        rotX = Fixed.mul(x, cos) - Fixed.mul(y, sin);
        rotY = Fixed.mul(x, sin) + Fixed.mul(y, cos);
    }

    @VanillaRef("am_map.c:AM_drawLineCharacter")
    private void drawLineCharacter(Canvas2D canvas, int[][] guy, int scale, int angle, int colour, int x, int y) {
        for (int[] l : guy) {
            int ax = l[0];
            int ay = l[1];
            if (scale != 0) {
                ax = Fixed.mul(scale, ax);
                ay = Fixed.mul(scale, ay);
            }
            if (angle != 0) {
                rotate(ax, ay, angle);
                ax = rotX;
                ay = rotY;
            }
            ax += x;
            ay += y;
            int bx = l[2];
            int by = l[3];
            if (scale != 0) {
                bx = Fixed.mul(scale, bx);
                by = Fixed.mul(scale, by);
            }
            if (angle != 0) {
                rotate(bx, by, angle);
                bx = rotX;
                by = rotY;
            }
            bx += x;
            by += y;
            drawMline(canvas, ax, ay, bx, by, colour);
        }
    }

    private int interpAngle(int prev, int cur) {
        return prev + (int) ((long) (cur - prev) * alpha);
    }

    @VanillaRef("am_map.c:AM_drawPlayers")
    private void drawPlayers(Canvas2D canvas) {
        ActorView actors = view.actors();
        int[] colours = {GREENS, GRAYS, BROWNS, REDS};
        if (!view.netGame()) {
            PlayerView p = player();
            int[] pos = playerPos(p, actors);
            drawLineCharacter(canvas, cheating != 0 ? CHEAT_PLAYER_ARROW : PLAYER_ARROW, 0, pos[2], WHITE,
                pos[0], pos[1]);
            return;
        }
        PlayerView me = player();
        for (int i = 0; i < 4; i++) {
            if (!view.playerInGame(i)) {
                continue;
            }
            PlayerView p = view.player(i);
            if (view.deathmatch() != 0 && !view.demoPlayback() && p != me) {
                continue;
            }
            int colour = p.power(Power.INVISIBILITY) != 0 ? 246 : colours[i];
            int[] pos = playerPos(p, actors);
            drawLineCharacter(canvas, PLAYER_ARROW, 0, pos[2], colour, pos[0], pos[1]);
        }
    }

    private int[] playerPos(PlayerView p, ActorView actors) {
        int x = p.x();
        int y = p.y();
        int a = p.angle();
        int id = p.actor();
        if (id >= 0 && actors.exists(id)) {
            x = lerp(actors.prevX(id), actors.x(id));
            y = lerp(actors.prevY(id), actors.y(id));
            a = interpAngle(actors.prevAngle(id), actors.angle(id));
        }
        return new int[] {x, y, a};
    }

    @VanillaRef("am_map.c:AM_drawThings")
    private void drawThings(Canvas2D canvas) {
        WorldView w = view.world();
        ActorView actors = view.actors();
        for (int s = 0; s < w.sectorCount(); s++) {
            for (int t = w.sectorFirstActor(s); t >= 0; t = actors.nextInSector(t)) {
                int x = lerp(actors.prevX(t), actors.x(t));
                int y = lerp(actors.prevY(t), actors.y(t));
                drawLineCharacter(canvas, THIN_TRIANGLE_GUY, 16 << Fixed.FRACBITS, actors.angle(t),
                    THINGCOLORS, x, y);
            }
        }
    }

    @VanillaRef("am_map.c:AM_drawMarks")
    private void drawMarks(Canvas2D canvas) {
        int sc = Math.max(1, (int) Math.round(canvas.scale()));
        for (int i = 0; i < AM_NUMMARKPOINTS; i++) {
            if (markX[i] == -1 || markNums[i] == null) {
                continue;
            }
            int w = 5 * sc;
            int h = 6 * sc;
            int fx = cxmtof(markX[i]);
            int fy = cymtof(markY[i]);
            if (fx >= fX && fx <= fW - w && fy >= fY && fy <= fH - h) {
                blit(canvas, markNums[i], fx, fy, sc);
            }
        }
    }

    /** Draws a patch with integer pixel scaling at native coordinates, clipped to the map window. */
    private void blit(Canvas2D canvas, Picture p, int x, int y, int sc) {
        FrameBuffer fb = canvas.frame();
        byte[] px = fb.pixels();
        byte[] data = p.data();
        x -= p.leftOffset() * sc;
        y -= p.topOffset() * sc;
        for (int col = 0; col < p.width(); col++) {
            for (int post = p.firstPost(col), end = p.endPost(col); post < end; post++) {
                int top = p.postTop(post);
                int len = p.postLength(post);
                int src = p.postDataOffset(post);
                for (int r = 0; r < len; r++) {
                    byte c = data[src + r];
                    for (int dy = 0; dy < sc; dy++) {
                        for (int dx = 0; dx < sc; dx++) {
                            int xx = x + col * sc + dx;
                            int yy = y + (top + r) * sc + dy;
                            if (xx >= 0 && xx < fW && yy >= 0 && yy < fH) {
                                px[xx * fb.columnStride() + yy * fb.rowStride()] = c;
                            }
                        }
                    }
                }
            }
        }
    }
}
