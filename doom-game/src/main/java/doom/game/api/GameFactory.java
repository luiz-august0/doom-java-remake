package doom.game.api;

import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.data.SessionDefsView;
import doom.game.data.dehacked.Dehacked;
import doom.game.impl.SessionFactory;
import doom.game.view.DefsView;
import doom.game.view.WorldView;
import doom.game.world.LevelGeometryLoader;
import doom.wad.LumpDirectory;
import doom.wad.iwad.GameVariant;
import doom.wad.iwad.IwadInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Entry points of the simulation module. */
public final class GameFactory {

    private GameFactory() {
    }

    /** Creates a session (definitions copied and DEHACKED applied once, here). */
    public static GameSession createSession(GameSetup setup) {
        return SessionFactory.create(setup);
    }

    /**
     * Loads only the geometry of a map (no actors, no thinkers), for viewers and renderer tests.
     *
     * @param mapName map lump name, e.g. {@code "E1M1"} or {@code "MAP01"}
     */
    public static WorldView loadLevelGeometry(LumpDirectory lumps, IwadInfo iwad, String mapName) {
        return LevelGeometryLoader.load(lumps, iwad, mapName);
    }

    /**
     * Definitions of a session without starting one (renderer and UI tools): the built-in tables, the IWAD's own
     * DEHACKED lump for Freedoom variants (as Chocolate Doom does), then {@code patches} in order.
     */
    public static DefsView loadDefs(LumpDirectory lumps, IwadInfo iwad, List<DehackedPatch> patches) {
        GameDefs defs = GameDefs.createDefault();
        GameStrings strings = GameStrings.createDefault();
        if (iwad != null && (iwad.variant() == GameVariant.FREEDOOM || iwad.variant() == GameVariant.FREEDM)) {
            int lump = lumps.find("DEHACKED");
            if (lump >= 0) {
                String text = new String(lumps.bytes(lump), StandardCharsets.ISO_8859_1);
                Dehacked.apply(defs, strings, new DehackedPatch("IWAD:DEHACKED", text), Dehacked.Options.iwadLump());
            }
        }
        for (DehackedPatch p : patches) {
            Dehacked.apply(defs, strings, p, Dehacked.Options.file());
        }
        return new SessionDefsView(defs, strings);
    }
}
