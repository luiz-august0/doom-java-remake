package doom.math;

import doom.math.annotation.VanillaRef;

/**
 * Bounding boxes stored as {@code int[4]} in the original order: top, bottom, left, right ({@code m_bbox.c}).
 */
public final class BBox {

    public static final int BOXTOP = 0;
    public static final int BOXBOTTOM = 1;
    public static final int BOXLEFT = 2;
    public static final int BOXRIGHT = 3;

    private BBox() {
    }

    /** Resets the box so that the next {@link #add} defines it ({@code M_ClearBox}). */
    @VanillaRef("m_bbox.c:M_ClearBox")
    public static void clear(int[] box) {
        box[BOXTOP] = box[BOXRIGHT] = Integer.MIN_VALUE;
        box[BOXBOTTOM] = box[BOXLEFT] = Integer.MAX_VALUE;
    }

    /** Grows the box to contain the point ({@code M_AddToBox}), including its {@code else if} quirk. */
    @VanillaRef("m_bbox.c:M_AddToBox")
    public static void add(int[] box, int x, int y) {
        if (x < box[BOXLEFT]) {
            box[BOXLEFT] = x;
        } else if (x > box[BOXRIGHT]) {
            box[BOXRIGHT] = x;
        }
        if (y < box[BOXBOTTOM]) {
            box[BOXBOTTOM] = y;
        } else if (y > box[BOXTOP]) {
            box[BOXTOP] = y;
        }
    }
}
