package doom.game.world;

import doom.game.view.WorldView;
import doom.math.annotation.VanillaRef;
import doom.wad.LumpDirectory;
import doom.wad.WadFormatException;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.IwadInfo;
import doom.wad.texture.FlatDirectory;
import doom.wad.texture.TextureDirectory;
import java.util.Locale;

/**
 * Loads map geometry into a {@link WorldView} without spawning anything (no actors, no thinkers), for viewers and
 * renderer tests.
 */
public final class LevelGeometryLoader {

    /** {@code SKYFLATNAME}. */
    public static final String SKY_FLAT_NAME = "F_SKY1";

    private LevelGeometryLoader() {
    }

    /**
     * Loads {@code mapName} ({@code E1M1}, {@code MAP01}, ...).
     *
     * @throws WadFormatException when the map or the texture/flat lumps it needs are missing or malformed
     */
    public static WorldView load(LumpDirectory lumps, IwadInfo iwad, String mapName) {
        TextureDirectory textures = TextureDirectory.load(lumps);
        FlatDirectory flats = FlatDirectory.load(lumps);
        Level level = new LevelLoader(textures, flats).load(lumps, mapName);
        int sky = textures.numForName(skyTextureName(iwad.mode(), level.name));
        int skyFlat = flats.numForName(SKY_FLAT_NAME);
        return new LevelWorldView(level, sky, skyFlat);
    }

    /**
     * The sky texture of a map. Vanilla picks it in {@code G_InitNew} from the first map of the game, so it never
     * changes inside a Doom II session ("sky never changes in Doom II"); for a single map this is the sky of its
     * episode or map number.
     */
    @VanillaRef("g_game.c:G_InitNew")
    public static String skyTextureName(GameMode mode, String mapName) {
        String name = mapName.toUpperCase(Locale.ROOT);
        if (mode == GameMode.COMMERCIAL) {
            int map = name.startsWith("MAP") ? parseNumber(name.substring(3)) : 1;
            if (map < 12) {
                return "SKY1";
            }
            return map < 21 ? "SKY2" : "SKY3";
        }
        int episode = name.length() == 4 && name.charAt(0) == 'E' && name.charAt(2) == 'M'
                ? name.charAt(1) - '0' : 1;
        return switch (episode) {
            case 2 -> "SKY2";
            case 3 -> "SKY3";
            case 4 -> "SKY4";
            default -> "SKY1";
        };
    }

    private static int parseNumber(String digits) {
        int n = 0;
        for (int i = 0; i < digits.length(); i++) {
            char ch = digits.charAt(i);
            if (ch < '0' || ch > '9') {
                return 1;
            }
            n = n * 10 + (ch - '0');
        }
        return n;
    }
}
