package doom.render.texture;

import doom.wad.gfx.Picture;

/**
 * A column-based image made of posts ({@code column_t} runs of opaque pixels), the form masked drawing needs:
 * sprites, psprites and masked middle textures.
 *
 * <p>For column {@code c} the posts are {@code first[c] .. first[c + 1] - 1}. Post {@code p} covers rows
 * {@code top[p] .. top[p] + length[p] - 1} and its pixels start at {@code data[ofs[p]]}; the array is the shared
 * {@link #data} or, when {@link #columnData} is not {@code null}, the array of that column.
 *
 * <p>Immutable by contract; the arrays are shared with the drawing loops.
 */
public final class PostImage {

    public final int width;
    public final int height;
    /** Backing array for every column, or {@code null} when {@link #columnData} is used. */
    public final byte[] data;
    /** Backing array per column, or {@code null} when {@link #data} is used. */
    public final byte[][] columnData;
    public final int[] first;
    public final int[] top;
    public final int[] length;
    public final int[] ofs;

    public PostImage(int width, int height, byte[] data, byte[][] columnData, int[] first, int[] top, int[] length,
                     int[] ofs) {
        this.width = width;
        this.height = height;
        this.data = data;
        this.columnData = columnData;
        this.first = first;
        this.top = top;
        this.length = length;
        this.ofs = ofs;
    }

    /** The posts of a decoded {@link Picture}; pixels are read from {@link Picture#data()}. */
    public static PostImage of(Picture p) {
        int w = p.width();
        int posts = p.postCount();
        int[] first = new int[w + 1];
        int[] top = new int[posts];
        int[] len = new int[posts];
        int[] ofs = new int[posts];
        for (int c = 0; c <= w; c++) {
            first[c] = c == w ? posts : p.firstPost(c);
        }
        for (int i = 0; i < posts; i++) {
            top[i] = p.postTop(i);
            len[i] = p.postLength(i);
            ofs[i] = p.postDataOffset(i);
        }
        return new PostImage(w, p.height(), p.data(), null, first, top, len, ofs);
    }

    /** Backing array of column {@code col}. */
    public byte[] source(int col) {
        return columnData != null ? columnData[col] : data;
    }
}
