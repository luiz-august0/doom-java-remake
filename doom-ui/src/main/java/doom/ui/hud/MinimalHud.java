package doom.ui.hud;

import doom.game.view.AmmoType;
import doom.game.view.Card;
import doom.game.view.GameView;
import doom.game.view.PlayerView;
import doom.render.canvas.Canvas2D;
import doom.ui.UiAssets;
import doom.ui.UiSettings;
import doom.ui.widget.NumberWidget;
import doom.wad.gfx.Picture;

/**
 * Optional minimal full-screen HUD (spec 04 §6): health, armor, ammo and keys with the WAD's own graphics, anchored to
 * the screen corners or to the 4:3 area. A new presentation feature, never used in vanilla mode.
 */
public final class MinimalHud {

    private static final int MARGIN = 4;

    private final UiAssets assets;
    private final GameView view;
    private NumberWidget tall;
    private final Picture[] tallNum = new Picture[10];
    private final Picture[] keys = new Picture[Card.COUNT];
    private Picture percent;
    private boolean loaded;

    public MinimalHud(UiAssets assets, GameView view) {
        this.assets = assets;
        this.view = view;
    }

    private void load() {
        loaded = true;
        for (int i = 0; i < 10; i++) {
            tallNum[i] = assets.patch("STTNUM" + i);
        }
        for (int i = 0; i < Card.COUNT; i++) {
            keys[i] = assets.patch("STKEYS" + i);
        }
        percent = assets.patch("STTPRCNT");
        tall = new NumberWidget(tallNum, assets.patch("STTMINUS"));
    }

    public void draw(Canvas2D canvas, UiSettings.HudAlign align) {
        if (!loaded) {
            load();
        }
        PlayerView p = view.player(view.consolePlayer());
        int left = align == UiSettings.HudAlign.CORNERS ? canvas.virtualX(0) : 0;
        int right = align == UiSettings.HudAlign.CORNERS ? canvas.virtualX(canvas.frame().width() - 1) + 1 : 320;
        int w = tallNum[0] != null ? tallNum[0].width() : 14;
        int pw = percent != null ? percent.width() : 14;
        int y = 200 - MARGIN - 16;

        // health, then armor to its right
        int x = left + MARGIN + 3 * w;
        tall.drawStatus(canvas, x, y, p.health(), 3);
        if (percent != null) {
            canvas.drawPatch(percent, x, y);
        }
        x += pw + MARGIN + 3 * w;
        if (p.armorPoints() > 0) {
            tall.drawStatus(canvas, x, y, p.armorPoints(), 3);
            if (percent != null) {
                canvas.drawPatch(percent, x, y);
            }
        }

        // ammo of the ready weapon at the bottom right
        int ammoType = view.defs().weaponAmmo(p.readyWeapon());
        if (ammoType != AmmoType.NO_AMMO) {
            tall.drawStatus(canvas, right - MARGIN, y, p.ammo(ammoType), 3);
        }

        // keys, stacked above the ammo
        int ky = y - 12;
        for (int i = 0; i < 3; i++) {
            int k = p.card(i + 3) ? i + 3 : p.card(i) ? i : -1;
            if (k >= 0 && keys[k] != null) {
                canvas.drawPatch(keys[k], right - MARGIN - keys[k].width(), ky);
                ky -= keys[k].height() + 2;
            }
        }
    }
}
