package doom.engine;

import doom.math.Tables;
import doom.wad.gfx.Palettes;

/** The PLAYPAL palettes as 768-byte RGB arrays with the gamma level applied ({@code I_SetPalette}), built lazily. */
final class GammaPalettes {

    private final Palettes palettes;
    private final byte[][] cache;
    private int gamma;

    GammaPalettes(Palettes palettes) {
        this.palettes = palettes;
        this.cache = new byte[palettes.count()][];
    }

    int gamma() {
        return gamma;
    }

    void setGamma(int level) {
        int g = Math.max(0, Math.min(Tables.GAMMA_LEVELS - 1, level));
        if (g != gamma) {
            gamma = g;
            java.util.Arrays.fill(cache, null);
        }
    }

    /** The palette (do not modify); the same array is returned until the gamma changes. */
    byte[] get(int index) {
        int i = Math.max(0, Math.min(cache.length - 1, index));
        byte[] p = cache[i];
        if (p == null) {
            p = new byte[Palettes.BYTES];
            for (int c = 0; c < 256; c++) {
                p[c * 3] = (byte) Tables.gamma(gamma, palettes.red(i, c));
                p[c * 3 + 1] = (byte) Tables.gamma(gamma, palettes.green(i, c));
                p[c * 3 + 2] = (byte) Tables.gamma(gamma, palettes.blue(i, c));
            }
            cache[i] = p;
        }
        return p;
    }
}
