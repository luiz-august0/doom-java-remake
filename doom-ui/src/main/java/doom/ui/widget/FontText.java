package doom.ui.widget;

import doom.render.canvas.Canvas2D;
import doom.ui.UiAssets;
import doom.wad.gfx.Picture;

/** Text in the STCFN heads-up font: {@code M_StringWidth}, {@code M_StringHeight}, {@code M_WriteText}. */
public final class FontText {

    /** {@code SCREENWIDTH}: {@code M_WriteText} stops a line that would pass it. */
    public static final int LIMIT_X = 320;

    private FontText() {
    }

    /** {@code M_StringWidth}: unknown characters are 4 pixels wide. */
    public static int width(UiAssets assets, CharSequence s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            Picture g = assets.glyph(s.charAt(i));
            w += g == null ? 4 : g.width();
        }
        return w;
    }

    /** {@code M_StringHeight}: one font height per line. */
    public static int height(UiAssets assets, CharSequence s) {
        int height = assets.fontHeight();
        int h = height;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                h += height;
            }
        }
        return h;
    }

    /** {@code M_WriteText}: newlines advance 12 rows. */
    public static void draw(Canvas2D canvas, UiAssets assets, int x, int y, CharSequence s) {
        draw(canvas, assets, x, y, s, LIMIT_X);
    }

    /** Like {@link #draw(Canvas2D, UiAssets, int, int, CharSequence)} with another right limit. */
    public static void draw(Canvas2D canvas, UiAssets assets, int x, int y, CharSequence s, int limitX) {
        int cx = x;
        int cy = y;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                cx = x;
                cy += 12;
                continue;
            }
            Picture g = assets.glyph(c);
            if (g == null) {
                cx += 4;
                continue;
            }
            int w = g.width();
            if (cx + w > limitX) {
                break;
            }
            canvas.drawPatch(g, cx, cy);
            cx += w;
        }
    }

    /** Draws each line of {@code s} centred on x = 160 starting at {@code y}, advancing by the font height (menu messages). */
    public static void drawCentredLines(Canvas2D canvas, UiAssets assets, int y, CharSequence s) {
        int start = 0;
        int fh = assets.fontHeight();
        String str = s.toString();
        while (start < str.length()) {
            int nl = str.indexOf('\n', start);
            String line = nl < 0 ? str.substring(start) : str.substring(start, nl);
            start = nl < 0 ? str.length() : nl + 1;
            draw(canvas, assets, 160 - width(assets, line) / 2, y, line);
            y += fh;
        }
    }

    /** Draws {@code s} so that it ends at {@code rightX}. */
    public static void drawRight(Canvas2D canvas, UiAssets assets, int rightX, int y, CharSequence s) {
        draw(canvas, assets, rightX - width(assets, s), y, s, Integer.MAX_VALUE);
    }
}
