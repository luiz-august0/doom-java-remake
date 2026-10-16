package doom.ui.widget;

import doom.render.canvas.Canvas2D;
import doom.wad.gfx.Picture;

/**
 * Right-aligned numbers made of patches: the status bar fonts ({@code STlib_drawNum}) and the intermission font
 * ({@code WI_drawNum}).
 */
public final class NumberWidget {

    /** Value that means "not applicable" and draws nothing. */
    public static final int NOT_APPLICABLE = 1994;

    private final Picture[] digits;
    private final Picture minus;

    /**
     * @param digits ten patches for 0..9 (entries may be null: nothing is drawn for them)
     * @param minus  the minus sign patch, or null
     */
    public NumberWidget(Picture[] digits, Picture minus) {
        this.digits = digits;
        this.minus = minus;
    }

    private int digitWidth() {
        return digits[0] != null ? digits[0].width() : 0;
    }

    /**
     * {@code STlib_drawNum}: draws {@code value} right-aligned at {@code x}, at most {@code width} digits, with the
     * original's clamping of negative values and its minus sign at {@code x - 8}.
     */
    public void drawStatus(Canvas2D canvas, int x, int y, int value, int width) {
        int numdigits = width;
        int num = value;
        int w = digitWidth();
        boolean neg = num < 0;
        if (neg) {
            if (numdigits == 2 && num < -9) {
                num = -9;
            } else if (numdigits == 3 && num < -99) {
                num = -99;
            }
            num = -num;
        }
        if (num == NOT_APPLICABLE) {
            return;
        }
        int cx = x;
        if (num == 0) {
            put(canvas, 0, cx - w, y);
        }
        while (num != 0 && numdigits-- > 0) {
            cx -= w;
            put(canvas, num % 10, cx, y);
            num /= 10;
        }
        if (neg && minus != null) {
            canvas.drawPatch(minus, cx - 8, y);
        }
    }

    /**
     * {@code WI_drawNum}: {@code digits < 0} uses as many digits as needed (at least 1). Returns the new x
     * (0 for the "not applicable" value).
     */
    public int drawVariable(Canvas2D canvas, int x, int y, int n, int digitCount) {
        int fontwidth = digitWidth();
        if (digitCount < 0) {
            if (n == 0) {
                digitCount = 1;
            } else {
                digitCount = 0;
                int temp = n;
                while (temp != 0) {
                    temp /= 10;
                    digitCount++;
                }
            }
        }
        boolean neg = n < 0;
        if (neg) {
            n = -n;
        }
        if (n == NOT_APPLICABLE) {
            return 0;
        }
        while (digitCount-- > 0) {
            x -= fontwidth;
            put(canvas, n % 10, x, y);
            n /= 10;
        }
        if (neg && minus != null) {
            x -= 8;
            canvas.drawPatch(minus, x, y);
        }
        return x;
    }

    private void put(Canvas2D canvas, int d, int x, int y) {
        Picture p = digits[d];
        if (p != null) {
            canvas.drawPatch(p, x, y);
        }
    }
}
