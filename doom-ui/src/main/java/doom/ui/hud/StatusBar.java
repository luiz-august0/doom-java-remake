package doom.ui.hud;

import doom.game.api.MiscRandom;
import doom.game.view.AmmoType;
import doom.game.view.Card;
import doom.game.view.GameView;
import doom.game.view.PlayerView;
import doom.game.view.Weapon;
import doom.math.annotation.VanillaRef;
import doom.render.canvas.Canvas2D;
import doom.ui.UiAssets;
import doom.ui.UiConfig;
import doom.ui.ViewWindow;
import doom.ui.widget.NumberWidget;
import doom.wad.gfx.Flat;
import doom.wad.gfx.Picture;
import doom.wad.iwad.GameMode;
import java.util.function.IntPredicate;

/**
 * The status bar ({@code st_stuff.c}, {@code st_lib.c}): STBAR, ammo, health, arms, face, armor, keys and the ammo
 * table at the original positions relative to the bar. The bar is redrawn completely each frame (the original's
 * dirty-rectangle tricks do not change the result).
 */
public final class StatusBar {

    /** {@code ST_Y}. */
    public static final int ST_Y = 168;

    private static final int ST_FX = 143;
    private static final int ST_FACESX = 143;
    private static final int ST_FACESY = 168;
    private static final int ST_AMMOX = 44;
    private static final int ST_AMMOY = 171;
    private static final int ST_HEALTHX = 90;
    private static final int ST_HEALTHY = 171;
    private static final int ST_ARMSX = 111;
    private static final int ST_ARMSY = 172;
    private static final int ST_ARMSBGX = 104;
    private static final int ST_ARMSBGY = 168;
    private static final int ST_ARMSXSPACE = 12;
    private static final int ST_ARMSYSPACE = 10;
    private static final int ST_FRAGSX = 138;
    private static final int ST_FRAGSY = 171;
    private static final int ST_ARMORX = 221;
    private static final int ST_ARMORY = 171;
    private static final int ST_KEYX = 239;
    private static final int[] ST_KEYY = {171, 181, 191};
    private static final int ST_AMMOX_TABLE = 288;
    private static final int[] ST_AMMOY_TABLE = {173, 179, 191, 185};
    private static final int ST_MAXAMMOX = 314;
    private static final int[] ST_MAXAMMOY_TABLE = {173, 179, 191, 185};

    private final UiAssets assets;
    private final GameView view;
    private final FaceLogic face;
    private final UiConfig config;

    private final Picture[] tallNum = new Picture[10];
    private final Picture[] shortNum = new Picture[10];
    private final Picture[] keys = new Picture[Card.COUNT];
    private final Picture[] faces = new Picture[FaceLogic.NUM_FACES];
    private final Picture[] armsGray = new Picture[6];
    private Picture tallPercent;
    private Picture armsBg;
    private Picture sbar;
    private Picture sbarRight;
    private Picture faceBack;
    private NumberWidget tall;
    private NumberWidget small;
    private int loadedConsolePlayer = -1;
    private boolean loaded;
    private Flat borderFlat;

    public StatusBar(UiAssets assets, GameView view, MiscRandom rnd, UiConfig config) {
        this.assets = assets;
        this.view = view;
        this.face = new FaceLogic(view, rnd);
        this.config = config;
    }

    public FaceLogic face() {
        return face;
    }

    public void setAttackDown(IntPredicate attackDown) {
        face.setAttackDown(attackDown);
    }

    /** {@code ST_Start}. */
    public void start() {
        face.start();
        loadGraphics();
    }

    /** {@code ST_Ticker}: one misc RNG call. */
    public void tick() {
        face.tick();
    }

    /** {@code ST_doPaletteStuff} for the console player. */
    public int paletteIndex() {
        return PaletteSelector.select(view.player(view.consolePlayer()));
    }

    @VanillaRef("st_stuff.c:ST_loadGraphics")
    private void loadGraphics() {
        loaded = true;
        loadedConsolePlayer = view.consolePlayer();
        for (int i = 0; i < 10; i++) {
            tallNum[i] = assets.patch("STTNUM" + i);
            shortNum[i] = assets.patch("STYSNUM" + i);
        }
        tallPercent = assets.patch("STTPRCNT");
        for (int i = 0; i < Card.COUNT; i++) {
            keys[i] = assets.patch("STKEYS" + i);
        }
        armsBg = assets.patch("STARMS");
        for (int i = 0; i < 6; i++) {
            armsGray[i] = assets.patch("STGNUM" + (i + 2));
        }
        faceBack = assets.patch("STFB" + view.consolePlayer());
        if (assets.has("STBAR")) {
            sbar = assets.patch("STBAR");
            sbarRight = null;
        } else {
            sbar = assets.patch("STMBARL");
            sbarRight = assets.patch("STMBARR");
        }
        int n = 0;
        for (int i = 0; i < FaceLogic.NUM_PAIN_FACES; i++) {
            for (int j = 0; j < FaceLogic.NUM_STRAIGHT_FACES; j++) {
                faces[n++] = assets.patch("STFST" + i + j);
            }
            faces[n++] = assets.patch("STFTR" + i + "0");
            faces[n++] = assets.patch("STFTL" + i + "0");
            faces[n++] = assets.patch("STFOUCH" + i);
            faces[n++] = assets.patch("STFEVL" + i);
            faces[n++] = assets.patch("STFKILL" + i);
        }
        faces[n++] = assets.patch("STFGOD0");
        faces[n] = assets.patch("STFDEAD0");
        tall = new NumberWidget(tallNum, assets.patch("STTMINUS"));
        small = new NumberWidget(shortNum, null);
        borderFlat = assets.flat(view.mode() == GameMode.COMMERCIAL ? "GRNROCK" : "FLOOR7_2");
    }

    /** The border flat used to fill the sides ({@code R_FillBackScreen}), or null. */
    public Flat borderFlat() {
        if (!loaded) {
            loadGraphics();
        }
        return borderFlat;
    }

    /** Fills the area left and right of the 320-wide bar. */
    private void drawSides(Canvas2D canvas) {
        int top = canvas.physicalY(ST_Y);
        int h = canvas.frame().height();
        int w = canvas.frame().width();
        int lw = canvas.leftBarWidth();
        int rs = canvas.rightBarStart();
        if (lw <= 0 && rs >= w) {
            return;
        }
        Flat flat = borderFlat;
        if (flat != null) {
            canvas.tileFlatPixels(flat, 0, top, lw, h);
            canvas.tileFlatPixels(flat, rs, top, w, h);
        } else {
            canvas.fillPixels(0, top, lw, h, 0);
            canvas.fillPixels(rs, top, w, h, 0);
        }
    }

    /** Draws the bar and all its widgets. */
    public void draw(Canvas2D canvas) {
        if (!loaded || loadedConsolePlayer != view.consolePlayer()) {
            loadGraphics();
        }
        int me = view.consolePlayer();
        PlayerView plyr = view.player(me);
        boolean deathmatch = view.deathmatch() != 0;

        drawSides(canvas);
        if (sbar != null) {
            if (sbar.width() > 320) {
                canvas.drawPatchCentred(sbar, ST_Y);
            } else {
                canvas.drawPatch(sbar, 0, ST_Y);
            }
        }
        if (sbarRight != null) {
            canvas.drawPatch(sbarRight, ST_ARMSBGX, ST_Y);
        }
        if (view.netGame() && faceBack != null) {
            canvas.drawPatch(faceBack, ST_FX, ST_Y);
        }

        // ready weapon ammo
        int ready = plyr.readyWeapon();
        int ammoType = view.defs().weaponAmmo(ready);
        int readyAmmo = ammoType == AmmoType.NO_AMMO ? NumberWidget.NOT_APPLICABLE : plyr.ammo(ammoType);
        tall.drawStatus(canvas, ST_AMMOX, ST_AMMOY, readyAmmo, 3);

        // health and armor
        drawPercent(canvas, ST_HEALTHX, ST_HEALTHY, plyr.health());
        drawPercent(canvas, ST_ARMORX, ST_ARMORY, plyr.armorPoints());

        // arms
        if (!deathmatch) {
            if (armsBg != null) {
                canvas.drawPatch(armsBg, ST_ARMSBGX, ST_ARMSBGY);
            }
            for (int i = 0; i < 6; i++) {
                int x = ST_ARMSX + (i % 3) * ST_ARMSXSPACE;
                int y = ST_ARMSY + (i / 3) * ST_ARMSYSPACE;
                Picture p = plyr.weaponOwned(i + Weapon.PISTOL) ? shortNum[i + 2] : armsGray[i];
                if (p != null) {
                    canvas.drawPatch(p, x, y);
                }
            }
        } else {
            int frags = 0;
            for (int i = 0; i < 4; i++) {
                if (i != me) {
                    frags += plyr.frags(i);
                } else {
                    frags -= plyr.frags(i);
                }
            }
            tall.drawStatus(canvas, ST_FRAGSX, ST_FRAGSY, frags, 2);
        }

        // face
        int fi = face.faceIndex();
        if (fi >= 0 && fi < faces.length && faces[fi] != null) {
            canvas.drawPatch(faces[fi], ST_FACESX, ST_FACESY);
        }

        // keys
        for (int i = 0; i < 3; i++) {
            int k = -1;
            if (plyr.card(i)) {
                k = i;
            }
            if (plyr.card(i + 3)) {
                k = i + 3;
            }
            if (k >= 0 && keys[k] != null) {
                canvas.drawPatch(keys[k], ST_KEYX, ST_KEYY[i]);
            }
        }

        // ammo table
        for (int i = 0; i < AmmoType.COUNT; i++) {
            small.drawStatus(canvas, ST_AMMOX_TABLE, ST_AMMOY_TABLE[i], plyr.ammo(i), 3);
            small.drawStatus(canvas, ST_MAXAMMOX, ST_MAXAMMOY_TABLE[i], plyr.maxAmmo(i), 3);
        }
    }

    /** {@code STlib_updatePercent}: the percent sign at x, the number ending at x. */
    private void drawPercent(Canvas2D canvas, int x, int y, int value) {
        if (tallPercent != null) {
            canvas.drawPatch(tallPercent, x, y);
        }
        tall.drawStatus(canvas, x, y, value, 3);
    }

    /** Height of the bar in virtual rows. */
    public static int height() {
        return ViewWindow.STATUS_BAR_HEIGHT;
    }
}
