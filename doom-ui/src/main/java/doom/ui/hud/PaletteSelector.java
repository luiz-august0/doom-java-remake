package doom.ui.hud;

import doom.game.view.PlayerView;
import doom.game.view.Power;
import doom.math.annotation.VanillaRef;
import doom.wad.gfx.Palettes;

/** The PLAYPAL palette the player should see ({@code ST_doPaletteStuff}). */
public final class PaletteSelector {

    private PaletteSelector() {
    }

    /**
     * @param player the console player
     * @return 0 normal, 1..8 damage (and berserk), 9..12 item pickup, 13 radiation suit
     */
    @VanillaRef("st_stuff.c:ST_doPaletteStuff")
    public static int select(PlayerView player) {
        int cnt = player.damageCount();
        int strength = player.power(Power.STRENGTH);
        if (strength != 0) {
            // slowly fade the berserk out
            int bzc = 12 - (strength >> 6);
            if (bzc > cnt) {
                cnt = bzc;
            }
        }
        int palette;
        if (cnt != 0) {
            palette = (cnt + 7) >> 3;
            if (palette >= Palettes.DAMAGE_COUNT) {
                palette = Palettes.DAMAGE_COUNT - 1;
            }
            palette += Palettes.FIRST_DAMAGE;
        } else if (player.bonusCount() != 0) {
            palette = (player.bonusCount() + 7) >> 3;
            if (palette >= Palettes.BONUS_COUNT) {
                palette = Palettes.BONUS_COUNT - 1;
            }
            palette += Palettes.FIRST_BONUS;
        } else if (player.power(Power.IRONFEET) > 4 * 32 || (player.power(Power.IRONFEET) & 8) != 0) {
            palette = Palettes.RADIATION;
        } else {
            palette = 0;
        }
        return palette;
    }
}
