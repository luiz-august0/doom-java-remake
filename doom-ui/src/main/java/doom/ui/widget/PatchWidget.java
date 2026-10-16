package doom.ui.widget;

import doom.render.canvas.Canvas2D;
import doom.ui.UiAssets;
import doom.wad.gfx.Picture;

/** A fixed patch at a fixed virtual position; draws nothing when the lump is missing. */
public final class PatchWidget {

    private final String name;
    private final int x;
    private final int y;

    public PatchWidget(String name, int x, int y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public void draw(Canvas2D canvas, UiAssets assets) {
        Picture p = assets.patch(name);
        if (p != null) {
            canvas.drawPatch(p, x, y);
        }
    }

    /** Draws patch {@code name} at ({@code x}, {@code y}) if it exists. */
    public static void draw(Canvas2D canvas, UiAssets assets, String name, int x, int y) {
        Picture p = assets.patch(name);
        if (p != null) {
            canvas.drawPatch(p, x, y);
        }
    }
}
