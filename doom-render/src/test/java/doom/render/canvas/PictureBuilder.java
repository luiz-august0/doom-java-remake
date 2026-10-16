package doom.render.canvas;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Builds picture lumps for tests: columns of posts {@code (topdelta, pixels...)}, with configurable pad bytes. */
final class PictureBuilder {

    private final int width;
    private final int height;
    private int leftOffset;
    private int topOffset;
    private final List<List<int[]>> columns = new ArrayList<>();
    private int padBefore = 0;
    private int padAfter = 0;

    PictureBuilder(int width, int height) {
        this.width = width;
        this.height = height;
        for (int i = 0; i < width; i++) {
            columns.add(new ArrayList<>());
        }
    }

    PictureBuilder offsets(int left, int top) {
        this.leftOffset = left;
        this.topOffset = top;
        return this;
    }

    PictureBuilder pads(int before, int after) {
        this.padBefore = before;
        this.padAfter = after;
        return this;
    }

    /** Adds a post to column {@code col}: {@code post[0]} is the topdelta, the rest are pixels. */
    PictureBuilder post(int col, int topDelta, int... pixels) {
        int[] p = new int[pixels.length + 1];
        p[0] = topDelta;
        System.arraycopy(pixels, 0, p, 1, pixels.length);
        columns.get(col).add(p);
        return this;
    }

    byte[] build() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int[] offsets = new int[width];
        int base = 8 + 4 * width;
        for (int c = 0; c < width; c++) {
            offsets[c] = base + body.size();
            for (int[] p : columns.get(c)) {
                body.write(p[0]);
                body.write(p.length - 1);
                body.write(padBefore);
                for (int i = 1; i < p.length; i++) {
                    body.write(p[i]);
                }
                body.write(padAfter);
            }
            body.write(0xFF);
        }
        ByteBuffer b = ByteBuffer.allocate(base + body.size()).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) width).putShort((short) height).putShort((short) leftOffset).putShort((short) topOffset);
        for (int o : offsets) {
            b.putInt(o);
        }
        b.put(body.toByteArray());
        return b.array();
    }

    ByteBuffer buffer() {
        return ByteBuffer.wrap(build()).order(ByteOrder.LITTLE_ENDIAN);
    }
}
