package doom.render.texture;

import doom.game.view.DefsView;
import doom.math.annotation.VanillaRef;
import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import doom.wad.gfx.Picture;
import doom.wad.texture.SpriteDirectory;
import java.util.Arrays;

/**
 * Sprite frame definitions ({@code R_InitSpriteDefs}: rotations and flips) and the sprite pictures, loaded on first use
 * ({@code R_InitSpriteLumps} reads only the headers up front in the original; here the whole picture is decoded when a
 * frame is first projected).
 */
public final class SpriteSet {

    /** {@code spriteframe_t}. */
    public static final class Frame {
        /** 0 = one picture for all rotations, 1 = eight rotations, -1 = not set (only while loading). */
        public int rotate = -1;
        /** Sprite lump index (lump number minus {@code firstspritelump}) per rotation. */
        public final int[] lump = new int[8];
        public final boolean[] flip = new boolean[8];

        Frame() {
            Arrays.fill(lump, -1);
        }
    }

    /** A decoded sprite picture with the fixed-point metrics of {@code spritewidth/offset/topoffset}. */
    public static final class Image {
        public final PostImage posts;
        /** {@code spritewidth}, fixed point. */
        public final int width;
        /** {@code spriteoffset}, fixed point. */
        public final int offset;
        /** {@code spritetopoffset}, fixed point. */
        public final int topOffset;

        Image(Picture p) {
            this.posts = PostImage.of(p);
            this.width = p.width() << 16;
            this.offset = p.leftOffset() << 16;
            this.topOffset = p.topOffset() << 16;
        }
    }

    private static final Frame[] NO_FRAMES = new Frame[0];

    private final LumpDirectory lumps;
    private final SpriteDirectory directory;
    private final Frame[][] sprites;
    private final Image[] images;
    private final boolean tallPatches;

    public SpriteSet(LumpDirectory lumps, DefsView defs, boolean vanilla) {
        this.lumps = lumps;
        this.directory = SpriteDirectory.load(lumps);
        this.images = new Image[directory.count()];
        this.tallPatches = !vanilla;
        int n = defs.spriteCount();
        this.sprites = new Frame[n][];
        for (int i = 0; i < n; i++) {
            sprites[i] = initSprite(defs.spriteName(i));
        }
    }

    /** {@code numsprites}. */
    public int count() {
        return sprites.length;
    }

    /** Frames of a sprite ({@code sprites[n].spriteframes}); empty when no lump matches the name. */
    public Frame[] frames(int sprite) {
        return sprites[sprite];
    }

    /** The picture of sprite lump {@code lump}. */
    public Image image(int lump) {
        Image im = images[lump];
        if (im == null) {
            im = new Image(Picture.read(lumps, directory.lump(lump), tallPatches));
            images[lump] = im;
        }
        return im;
    }

    /** Decodes every sprite picture now. */
    public void loadAll() {
        for (int i = 0; i < images.length; i++) {
            image(i);
        }
    }

    @VanillaRef({"r_things.c:R_InitSpriteDefs", "r_things.c:R_InstallSpriteLump"})
    private Frame[] initSprite(String spriteName) {
        Frame[] tmp = new Frame[29];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = new Frame();
        }
        int maxFrame = -1;
        String key = spriteName.toUpperCase(java.util.Locale.ROOT);
        for (int l = 0; l < directory.count(); l++) {
            String name = directory.name(l);
            if (name.length() < 4 || !name.regionMatches(true, 0, key, 0, 4)) {
                continue;
            }
            int frame = at(name, 4) - 'A';
            int rotation = at(name, 5) - '0';
            int patched = directory.find(name);
            maxFrame = Math.max(maxFrame, install(tmp, key, patched < 0 ? l : patched, frame, rotation, false));
            if (name.length() > 6) {
                frame = at(name, 6) - 'A';
                rotation = at(name, 7) - '0';
                maxFrame = Math.max(maxFrame, install(tmp, key, l, frame, rotation, true));
            }
        }
        if (maxFrame == -1) {
            return NO_FRAMES;
        }
        int frames = maxFrame + 1;
        for (int frame = 0; frame < frames; frame++) {
            switch (tmp[frame].rotate) {
                case -1 -> throw new WadFormatException(
                        "R_InitSprites: No patches found for " + key + " frame " + (char) (frame + 'A'));
                case 1 -> {
                    for (int rotation = 0; rotation < 8; rotation++) {
                        if (tmp[frame].lump[rotation] == -1) {
                            throw new WadFormatException("R_InitSprites: Sprite " + key + " frame "
                                    + (char) (frame + 'A') + " is missing rotations");
                        }
                    }
                }
                default -> {
                }
            }
        }
        return Arrays.copyOf(tmp, frames);
    }

    private static int at(String s, int i) {
        return i < s.length() ? s.charAt(i) : 0;
    }

    /** Returns the frame index installed (for the running maximum). */
    private static int install(Frame[] tmp, String spriteName, int lump, int frame, int rotation, boolean flipped) {
        if (frame < 0 || frame >= 29 || rotation < 0 || rotation > 8) {
            throw new WadFormatException("R_InstallSpriteLump: Bad frame characters in lump " + lump);
        }
        Frame f = tmp[frame];
        if (rotation == 0) {
            if (f.rotate == 0) {
                throw new WadFormatException("R_InitSprites: Sprite " + spriteName + " frame " + (char) ('A' + frame)
                        + " has multip rot=0 lump");
            }
            if (f.rotate == 1) {
                throw new WadFormatException("R_InitSprites: Sprite " + spriteName + " frame " + (char) ('A' + frame)
                        + " has rotations and a rot=0 lump");
            }
            f.rotate = 0;
            for (int r = 0; r < 8; r++) {
                f.lump[r] = lump;
                f.flip[r] = flipped;
            }
            return frame;
        }
        if (f.rotate == 0) {
            throw new WadFormatException("R_InitSprites: Sprite " + spriteName + " frame " + (char) ('A' + frame)
                    + " has rotations and a rot=0 lump");
        }
        f.rotate = 1;
        int r = rotation - 1;
        if (f.lump[r] != -1) {
            throw new WadFormatException("R_InitSprites: Sprite " + spriteName + " : " + (char) ('A' + frame) + " : "
                    + (char) ('1' + r) + " has two lumps mapped to it");
        }
        f.lump[r] = lump;
        f.flip[r] = flipped;
        return frame;
    }
}
