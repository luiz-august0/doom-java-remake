package doom.ui.screens;

import doom.game.view.GameView;
import doom.game.view.IntermissionView;
import doom.math.annotation.VanillaRef;
import doom.render.canvas.Canvas2D;
import doom.ui.UiAssets;
import doom.ui.widget.NumberWidget;
import doom.wad.gfx.Picture;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVersion;

/**
 * Draws the intermission ({@code WI_Drawer}) from {@link IntermissionView}. All logic (counting, sounds, animation
 * timers) lives in the simulation.
 */
public final class IntermissionScreen {

    private static final int WI_TITLEY = 2;
    private static final int WI_SPACINGY = 33;
    private static final int SP_STATSX = 50;
    private static final int SP_STATSY = 50;
    private static final int SP_TIMEX = 16;
    private static final int SP_TIMEY = 200 - 32;
    private static final int NG_STATSY = 50;
    private static final int NG_SPACINGX = 64;
    private static final int DM_MATRIXX = 42;
    private static final int DM_MATRIXY = 68;
    private static final int DM_SPACINGX = 40;
    private static final int DM_TOTALSX = 269;
    private static final int DM_KILLERSX = 10;
    private static final int DM_KILLERSY = 100;
    private static final int DM_VICTIMSX = 5;
    private static final int DM_VICTIMSY = 50;
    private static final int NUMCMAPS = 32;

    /** {@code lnodes[episode][level]}: the "you are here" locations of the world maps. */
    private static final int[][][] LNODES = {
        {{185, 164}, {148, 143}, {69, 122}, {209, 102}, {116, 89}, {166, 55}, {71, 56}, {135, 29}, {71, 24}},
        {{254, 25}, {97, 50}, {188, 64}, {128, 78}, {214, 92}, {133, 130}, {208, 136}, {148, 140}, {235, 158}},
        {{156, 168}, {48, 154}, {174, 95}, {265, 75}, {130, 48}, {279, 23}, {198, 48}, {140, 25}, {281, 136}}};

    /** Animation locations per episode ({@code epsd?animinfo}): x, y, number of frames. */
    private static final int[][][] ANIMS = {
        {{224, 104, 3}, {184, 160, 3}, {112, 136, 3}, {72, 112, 3}, {88, 96, 3}, {64, 48, 3}, {192, 40, 3},
            {136, 16, 3}, {80, 16, 3}, {64, 24, 3}},
        {{128, 136, 1}, {128, 136, 1}, {128, 136, 1}, {128, 136, 1}, {128, 136, 1}, {128, 136, 1}, {128, 136, 1},
            {192, 144, 3}, {128, 136, 1}},
        {{104, 168, 3}, {40, 136, 3}, {160, 96, 3}, {104, 80, 3}, {120, 32, 3}, {40, 0, 3}}};

    private final UiAssets assets;
    private final GameView view;

    private final Picture[] num = new Picture[10];
    private boolean loaded;
    private Picture wiMinus;
    private Picture percent;
    private Picture colon;
    private NumberWidget numbers;

    public IntermissionScreen(UiAssets assets, GameView view) {
        this.assets = assets;
        this.view = view;
    }

    private void load() {
        loaded = true;
        for (int i = 0; i < 10; i++) {
            num[i] = assets.patch("WINUM" + i);
        }
        wiMinus = assets.patch("WIMINUS");
        percent = assets.patch("WIPCNT");
        colon = assets.patch("WICOLON");
        numbers = new NumberWidget(num, wiMinus);
    }

    private int episode(IntermissionView wi) {
        int e = wi.episode();
        if (view.version().ordinal() < GameVersion.ULTIMATE.ordinal() && e > 2) {
            e -= 3;
        }
        return e;
    }

    private boolean commercial() {
        return view.mode() == GameMode.COMMERCIAL;
    }

    /** The name of the background lump. */
    String backgroundName(int epsd) {
        if (commercial()) {
            return "INTERPIC";
        }
        if (view.version().isAtLeast(GameVersion.ULTIMATE) && epsd == 3) {
            return "INTERPIC";
        }
        return "WIMAP" + epsd;
    }

    /** {@code WI_Drawer}. */
    @VanillaRef("wi_stuff.c:WI_Drawer")
    public void draw(Canvas2D c) {
        if (!loaded) {
            load();
        }
        IntermissionView wi = view.intermission();
        switch (wi.stage()) {
            case 0 -> {
                if (view.deathmatch() != 0) {
                    drawDeathmatchStats(c, wi);
                } else if (view.netGame()) {
                    drawNetgameStats(c, wi);
                } else {
                    drawStats(c, wi);
                }
            }
            case 1 -> drawShowNextLoc(c, wi, wi.pointerOn());
            default -> drawShowNextLoc(c, wi, true);
        }
    }

    private void patch(Canvas2D c, String name, int x, int y) {
        Picture p = assets.patch(name);
        if (p != null) {
            c.drawPatch(p, x, y);
        }
    }

    private void slamBackground(Canvas2D c, IntermissionView wi) {
        Picture bg = assets.patch(backgroundName(episode(wi)));
        if (bg != null) {
            c.drawPatchCentred(bg, 0);
        }
    }

    private void drawAnimatedBack(Canvas2D c, IntermissionView wi) {
        if (commercial()) {
            return;
        }
        int epsd = episode(wi);
        if (epsd > 2) {
            return;
        }
        int n = Math.min(wi.animCount(), ANIMS[epsd].length);
        for (int i = 0; i < n; i++) {
            int frame = wi.animFrame(i);
            if (frame >= 0) {
                // "MONDO HACK": episode 1 animation 8 shares the graphics of animation 4
                int src = epsd == 1 && i == 8 ? 4 : i;
                patch(c, String.format("WIA%d%02d%02d", epsd, src, frame), ANIMS[epsd][i][0], ANIMS[epsd][i][1]);
            }
        }
    }

    private void drawLF(Canvas2D c, IntermissionView wi) {
        int y = WI_TITLEY;
        int last = wi.lastMap();
        Picture fin = assets.patch("WIF");
        if (!commercial() || last < NUMCMAPS) {
            Picture name = assets.patch(levelNameLump(wi, last));
            if (name != null) {
                c.drawPatch(name, (320 - name.width()) / 2, y);
                y += (5 * name.height()) / 4;
            }
            if (fin != null) {
                c.drawPatch(fin, (320 - fin.width()) / 2, y);
            }
        } else if (last == NUMCMAPS && fin != null) {
            // MAP33: "Finished!" only
            c.drawPatch(fin, (320 - fin.width()) / 2, y);
        }
    }

    private String levelNameLump(IntermissionView wi, int level) {
        if (commercial()) {
            return String.format("CWILV%02d", level);
        }
        return "WILV" + episode(wi) + level;
    }

    private void drawEL(Canvas2D c, IntermissionView wi) {
        int y = WI_TITLEY;
        Picture entering = assets.patch("WIENTER");
        if (entering != null) {
            c.drawPatch(entering, (320 - entering.width()) / 2, y);
        }
        Picture name = assets.patch(levelNameLump(wi, wi.nextMap()));
        if (name != null) {
            y += (5 * name.height()) / 4;
            c.drawPatch(name, (320 - name.width()) / 2, y);
        }
    }

    private void drawOnLnode(Canvas2D c, IntermissionView wi, int n, Picture... choices) {
        int epsd = episode(wi);
        if (epsd < 0 || epsd > 2 || n < 0 || n > 8) {
            return;
        }
        int nx = LNODES[epsd][n][0];
        int ny = LNODES[epsd][n][1];
        for (int i = 0; i < choices.length && i < 2; i++) {
            Picture p = choices[i];
            if (p == null) {
                return;
            }
            int left = nx - p.leftOffset();
            int top = ny - p.topOffset();
            int right = left + p.width();
            int bottom = top + p.height();
            if (left >= 0 && right < 320 && top >= 0 && bottom < 200) {
                c.drawPatch(p, nx, ny);
                return;
            }
        }
    }

    @VanillaRef("wi_stuff.c:WI_drawShowNextLoc")
    private void drawShowNextLoc(Canvas2D c, IntermissionView wi, boolean pointerOn) {
        slamBackground(c, wi);
        drawAnimatedBack(c, wi);
        if (!commercial()) {
            if (episode(wi) > 2) {
                drawEL(c, wi);
                return;
            }
            int last = wi.lastMap() == 8 ? wi.nextMap() - 1 : wi.lastMap();
            Picture splat = assets.patch("WISPLAT");
            for (int i = 0; i <= last; i++) {
                drawOnLnode(c, wi, i, splat);
            }
            if (wi.didSecret()) {
                drawOnLnode(c, wi, 8, splat);
            }
            if (pointerOn) {
                drawOnLnode(c, wi, wi.nextMap(), assets.patch("WIURH0"), assets.patch("WIURH1"));
            }
        }
        if (!commercial() || wi.nextMap() != 30) {
            drawEL(c, wi);
        }
    }

    private void drawPercent(Canvas2D c, int x, int y, int p) {
        if (p < 0) {
            return;
        }
        if (percent != null) {
            c.drawPatch(percent, x, y);
        }
        numbers.drawVariable(c, x, y, p, -1);
    }

    @VanillaRef("wi_stuff.c:WI_drawTime")
    private void drawTime(Canvas2D c, int x, int y, int t) {
        if (t < 0) {
            return;
        }
        if (t <= 61 * 59) {
            int div = 1;
            int cw = colon != null ? colon.width() : 0;
            do {
                int n = (t / div) % 60;
                x = numbers.drawVariable(c, x, y, n, 2) - cw;
                div *= 60;
                if ((div == 60 || t / div != 0) && colon != null) {
                    c.drawPatch(colon, x, y);
                }
            } while (t / div != 0);
        } else {
            Picture sucks = assets.patch("WISUCKS");
            if (sucks != null) {
                c.drawPatch(sucks, x - sucks.width(), y);
            }
        }
    }

    @VanillaRef("wi_stuff.c:WI_drawStats")
    private void drawStats(Canvas2D c, IntermissionView wi) {
        int lh = (3 * (num[0] != null ? num[0].height() : 12)) / 2;
        slamBackground(c, wi);
        drawAnimatedBack(c, wi);
        drawLF(c, wi);
        patch(c, "WIOSTK", SP_STATSX, SP_STATSY);
        drawPercent(c, 320 - SP_STATSX, SP_STATSY, wi.countKills(0));
        patch(c, "WIOSTI", SP_STATSX, SP_STATSY + lh);
        drawPercent(c, 320 - SP_STATSX, SP_STATSY + lh, wi.countItems(0));
        patch(c, "WISCRT2", SP_STATSX, SP_STATSY + 2 * lh);
        drawPercent(c, 320 - SP_STATSX, SP_STATSY + 2 * lh, wi.countSecret(0));
        patch(c, "WITIME", SP_TIMEX, SP_TIMEY);
        drawTime(c, 320 / 2 - SP_TIMEX, SP_TIMEY, wi.countTime());
        if (episode(wi) < 3) {
            patch(c, "WIPAR", 320 / 2 + SP_TIMEX, SP_TIMEY);
            drawTime(c, 320 - SP_TIMEX, SP_TIMEY, wi.countPar());
        }
    }

    @VanillaRef("wi_stuff.c:WI_drawNetgameStats")
    private void drawNetgameStats(Canvas2D c, IntermissionView wi) {
        Picture star = assets.patch("STFST01");
        Picture kills = assets.patch("WIOSTK");
        Picture items = assets.patch(assets.has("WIOBJ") && view.deathmatch() == 0 ? "WIOBJ" : "WIOSTI");
        Picture secret = assets.patch("WIOSTS");
        Picture frags = assets.patch("WIFRGS");
        boolean doFrags = wi.doFrags();
        int ngStatsX = 32 + (star != null ? star.width() : 0) / 2 + (doFrags ? 0 : 32);
        int pwidth = percent != null ? percent.width() : 0;
        slamBackground(c, wi);
        drawAnimatedBack(c, wi);
        drawLF(c, wi);

        if (kills != null) {
            c.drawPatch(kills, ngStatsX + NG_SPACINGX - kills.width(), NG_STATSY);
        }
        if (items != null) {
            c.drawPatch(items, ngStatsX + 2 * NG_SPACINGX - items.width(), NG_STATSY);
        }
        if (secret != null) {
            c.drawPatch(secret, ngStatsX + 3 * NG_SPACINGX - secret.width(), NG_STATSY);
        }
        if (doFrags && frags != null) {
            c.drawPatch(frags, ngStatsX + 4 * NG_SPACINGX - frags.width(), NG_STATSY);
        }

        int y = NG_STATSY + (kills != null ? kills.height() : 0);
        for (int i = 0; i < 4; i++) {
            if (!wi.playerPresent(i)) {
                continue;
            }
            int x = ngStatsX;
            Picture p = assets.patch("STPB" + i);
            if (p != null) {
                c.drawPatch(p, x - p.width(), y);
                if (i == wi.me() && star != null) {
                    c.drawPatch(star, x - p.width(), y);
                }
            }
            x += NG_SPACINGX;
            drawPercent(c, x - pwidth, y + 10, wi.countKills(i));
            x += NG_SPACINGX;
            drawPercent(c, x - pwidth, y + 10, wi.countItems(i));
            x += NG_SPACINGX;
            drawPercent(c, x - pwidth, y + 10, wi.countSecret(i));
            x += NG_SPACINGX;
            if (doFrags) {
                numbers.drawVariable(c, x, y + 10, wi.countFrags(i), -1);
            }
            y += WI_SPACINGY;
        }
    }

    @VanillaRef("wi_stuff.c:WI_drawDeathmatchStats")
    private void drawDeathmatchStats(Canvas2D c, IntermissionView wi) {
        Picture total = assets.patch("WIMSTT");
        Picture star = assets.patch("STFST01");
        Picture bstar = assets.patch("STFDEAD0");
        slamBackground(c, wi);
        drawAnimatedBack(c, wi);
        drawLF(c, wi);

        if (total != null) {
            c.drawPatch(total, DM_TOTALSX - total.width() / 2, DM_MATRIXY - WI_SPACINGY + 10);
        }
        patch(c, "WIKILRS", DM_KILLERSX, DM_KILLERSY);
        patch(c, "WIVCTMS", DM_VICTIMSX, DM_VICTIMSY);

        int x = DM_MATRIXX + DM_SPACINGX;
        int y = DM_MATRIXY;
        for (int i = 0; i < 4; i++) {
            if (wi.playerPresent(i)) {
                Picture p = assets.patch("STPB" + i);
                if (p != null) {
                    c.drawPatch(p, x - p.width() / 2, DM_MATRIXY - WI_SPACINGY);
                    c.drawPatch(p, DM_MATRIXX - p.width() / 2, y);
                    if (i == wi.me()) {
                        if (bstar != null) {
                            c.drawPatch(bstar, x - p.width() / 2, DM_MATRIXY - WI_SPACINGY);
                        }
                        if (star != null) {
                            c.drawPatch(star, DM_MATRIXX - p.width() / 2, y);
                        }
                    }
                }
            }
            x += DM_SPACINGX;
            y += WI_SPACINGY;
        }

        y = DM_MATRIXY + 10;
        int w = num[0] != null ? num[0].width() : 0;
        for (int i = 0; i < 4; i++) {
            x = DM_MATRIXX + DM_SPACINGX;
            if (wi.playerPresent(i)) {
                for (int j = 0; j < 4; j++) {
                    if (wi.playerPresent(j)) {
                        numbers.drawVariable(c, x + w, y, wi.dmFrags(i, j), 2);
                    }
                    x += DM_SPACINGX;
                }
                numbers.drawVariable(c, DM_TOTALSX + w, y, wi.dmTotal(i), 2);
            }
            y += WI_SPACINGY;
        }
    }
}
