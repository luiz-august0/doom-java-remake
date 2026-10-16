package doom.ui;

/**
 * Static presentation options of the UI.
 *
 * @param sideFill         what fills the pillarbox bars of full-screen pages (menus over the game keep the game)
 * @param vanillaMode      disables every optional presentation feature (extended options, minimal HUD, debug overlay)
 * @param automapLineScale automap line thickness in physical pixels per virtual row: thickness = max(1, round(s * this))
 */
public record UiConfig(SideFill sideFill, boolean vanillaMode, double automapLineScale) {

    /** Fill of the left and right bars of full-screen pages. */
    public enum SideFill {
        /** Black bars. */
        BLACK,
        /** The game's border flat ({@code FLOOR7_2} / {@code GRNROCK}). */
        FLAT
    }

    public static UiConfig defaults() {
        return new UiConfig(SideFill.BLACK, false, 0.6);
    }

    public UiConfig withSideFill(SideFill fill) {
        return new UiConfig(fill, vanillaMode, automapLineScale);
    }

    public UiConfig withVanillaMode(boolean vanilla) {
        return new UiConfig(sideFill, vanilla, automapLineScale);
    }
}
