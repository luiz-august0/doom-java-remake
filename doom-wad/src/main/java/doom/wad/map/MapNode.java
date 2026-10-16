package doom.wad.map;

/**
 * One NODES entry ({@code mapnode_t}, 28 bytes): partition line and the bounding boxes of both children as signed
 * 16-bit map units, and the two children as <b>unsigned</b> 16-bit values (bit 15 = {@link #NF_SUBSECTOR}).
 * Child 0 is the right (front) side of the partition, child 1 the left (back) side.
 */
public record MapNode(int x, int y, int dx, int dy,
                      int rightTop, int rightBottom, int rightLeft, int rightRight,
                      int leftTop, int leftBottom, int leftLeft, int leftRight,
                      int rightChild, int leftChild) {

    /** Bytes per entry. */
    public static final int SIZE = 28;

    /** Child flag: the low 15 bits are a subsector number. */
    public static final int NF_SUBSECTOR = 0x8000;

    /**
     * Bounding box coordinate of a child.
     *
     * @param child 0 = right, 1 = left
     * @param coord 0 = top, 1 = bottom, 2 = left, 3 = right ({@code BOXTOP} … {@code BOXRIGHT})
     */
    public int bbox(int child, int coord) {
        return switch (child * 4 + coord) {
            case 0 -> rightTop;
            case 1 -> rightBottom;
            case 2 -> rightLeft;
            case 3 -> rightRight;
            case 4 -> leftTop;
            case 5 -> leftBottom;
            case 6 -> leftLeft;
            case 7 -> leftRight;
            default -> throw new IllegalArgumentException("child " + child + " coord " + coord);
        };
    }

    /** {@code children[child]}: 0 = right, 1 = left. */
    public int child(int child) {
        return child == 0 ? rightChild : leftChild;
    }
}
