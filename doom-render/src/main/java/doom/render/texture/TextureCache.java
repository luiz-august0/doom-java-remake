package doom.render.texture;

import doom.math.annotation.VanillaRef;
import doom.wad.LumpDirectory;
import doom.wad.gfx.Picture;
import doom.wad.texture.TextureDefs.TextureDef;
import doom.wad.texture.TextureDefs.TexturePatch;
import doom.wad.texture.TextureDirectory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Composes the wall textures of TEXTURE1/TEXTURE2 + PNAMES on first use ({@code R_GetColumn} /
 * {@code R_GenerateComposite} / {@code R_GenerateLookup}), or all at once with {@link #composeAll()} (virtual threads).
 *
 * <p>Vanilla mode keeps the original memory layout (see {@link WallTexture}); otherwise every column is composed to
 * exactly the texture height and patches are decoded with the DeePsea tall-patch rule.
 */
public final class TextureCache {

    private final LumpDirectory lumps;
    private final TextureDirectory directory;
    private final boolean vanilla;
    private final WallTexture[] cache;
    private final Map<Integer, Picture> patches = new HashMap<>();

    public TextureCache(LumpDirectory lumps, boolean vanilla) {
        this.lumps = lumps;
        this.directory = TextureDirectory.load(lumps);
        this.vanilla = vanilla;
        this.cache = new WallTexture[directory.count()];
    }

    public TextureDirectory directory() {
        return directory;
    }

    /** {@code numtextures}. */
    public int count() {
        return cache.length;
    }

    /** {@code textureheight[tex]} (fixed point). */
    public int heightFixed(int tex) {
        return directory.height(tex);
    }

    /** The composed texture, built on first use. */
    public WallTexture get(int tex) {
        WallTexture t = cache[tex];
        if (t == null) {
            loadPatches(tex);
            t = build(tex);
            cache[tex] = t;
        }
        return t;
    }

    /** Composes every texture, using virtual threads for the pixel work. */
    public void composeAll() {
        int n = cache.length;
        for (int i = 0; i < n; i++) {
            if (cache[i] == null) {
                loadPatches(i);
            }
        }
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<WallTexture>> futures = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                final int tex = i;
                futures.add(cache[i] != null ? null : pool.submit(() -> build(tex)));
            }
            for (int i = 0; i < n; i++) {
                Future<WallTexture> f = futures.get(i);
                if (f != null) {
                    cache[i] = f.get();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("texture composition interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException r) {
                throw r;
            }
            throw new IllegalStateException(c);
        }
    }

    private void loadPatches(int tex) {
        for (TexturePatch p : directory.def(tex).patches()) {
            patches.computeIfAbsent(p.lump(), l -> Picture.read(lumps, l, !vanilla));
        }
    }

    /** Growable int list for the build step. */
    private static final class Ints {
        int[] a = new int[64];
        int n;

        void add(int v) {
            if (n == a.length) {
                a = java.util.Arrays.copyOf(a, n * 2);
            }
            a[n++] = v;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(a, n);
        }
    }

    @VanillaRef({"r_data.c:R_GenerateLookup", "r_data.c:R_GenerateComposite"})
    private WallTexture build(int tex) {
        TextureDef def = directory.def(tex);
        int w = def.width();
        int h = def.height();
        TexturePatch[] ps = def.patches();
        Picture[] pics = new Picture[ps.length];
        int[] count = new int[w];
        Picture[] colPic = new Picture[w];
        int[] colPicCol = new int[w];
        for (int i = 0; i < ps.length; i++) {
            Picture pic = patches.get(ps[i].lump());
            pics[i] = pic;
            int x1 = ps[i].originX();
            int x2 = Math.min(x1 + pic.width(), w);
            for (int x = Math.max(x1, 0); x < x2; x++) {
                count[x]++;
                colPic[x] = pic;
                colPicCol[x] = x - x1;
            }
        }
        boolean[] composite = new boolean[w];
        int[] compOfs = new int[w];
        int compCols = 0;
        for (int x = 0; x < w; x++) {
            composite[x] = !vanilla || count[x] != 1;
            if (composite[x]) {
                compOfs[x] = vanilla ? compCols * h : x * h;
                compCols++;
            }
        }
        int compSize = (vanilla ? compCols : w) * h;
        byte[] comp = new byte[compSize + 128];
        boolean[] cov = new boolean[compSize + 1];
        // R_DrawColumnInCache for every column that has more than one patch
        for (int i = 0; i < ps.length; i++) {
            Picture pic = pics[i];
            int x1 = ps[i].originX();
            int originY = ps[i].originY();
            int x2 = Math.min(x1 + pic.width(), w);
            byte[] src = pic.data();
            for (int x = Math.max(x1, 0); x < x2; x++) {
                if (!composite[x]) {
                    continue;
                }
                int cx = x - x1;
                int base = compOfs[x];
                for (int p = pic.firstPost(cx), e = pic.endPost(cx); p < e; p++) {
                    int position = originY + pic.postTop(p);
                    int n = pic.postLength(p);
                    if (position < 0) {
                        n += position;
                        position = 0;
                    }
                    if (position + n > h) {
                        n = h - position;
                    }
                    if (n > 0) {
                        System.arraycopy(src, pic.postDataOffset(p), comp, base + position, n);
                        java.util.Arrays.fill(cov, base + position, base + position + n, true);
                    }
                }
            }
        }
        byte[][] wallData = new byte[w][];
        int[] wallOfs = new int[w];
        byte[][] maskData = new byte[w][];
        Ints first = new Ints();
        Ints top = new Ints();
        Ints len = new Ints();
        Ints ofs = new Ints();
        for (int x = 0; x < w; x++) {
            first.add(top.n);
            if (composite[x]) {
                wallData[x] = comp;
                wallOfs[x] = compOfs[x];
                maskData[x] = comp;
                int base = compOfs[x];
                int y = 0;
                while (y < h) {
                    if (!cov[base + y]) {
                        y++;
                        continue;
                    }
                    int start = y;
                    while (y < h && cov[base + y]) {
                        y++;
                    }
                    top.add(start);
                    len.add(y - start);
                    ofs.add(base + start);
                }
            } else {
                Picture pic = colPic[x];
                int cx = colPicCol[x];
                wallData[x] = pic.data();
                wallOfs[x] = pic.columnOffset(cx) + 3;
                maskData[x] = pic.data();
                for (int p = pic.firstPost(cx), e = pic.endPost(cx); p < e; p++) {
                    top.add(pic.postTop(p));
                    len.add(pic.postLength(p));
                    ofs.add(pic.postDataOffset(p));
                }
            }
        }
        first.add(top.n);
        PostImage posts = new PostImage(w, h, null, maskData, first.toArray(), top.toArray(), len.toArray(),
                ofs.toArray());
        return new WallTexture(w, h, directory.widthMask(tex), wallData, wallOfs, posts);
    }
}
