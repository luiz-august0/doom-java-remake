package doom.game.impl;

import doom.game.actor.ActionDispatcher;
import doom.game.actor.ActorRegistry;
import doom.game.actor.Mobjs;
import doom.game.api.DehackedPatch;
import doom.game.api.GameSession;
import doom.game.api.GameSetup;
import doom.game.data.GameDefs;
import doom.game.data.GameStrings;
import doom.game.data.dehacked.Dehacked;
import doom.game.persist.SaveGameCodec;
import doom.game.physics.Physics;
import doom.game.physics.Sight;
import doom.game.player.Pickups;
import doom.game.player.PlayerSim;
import doom.game.player.Weapons;
import doom.game.session.Game;
import doom.game.world.World;
import doom.math.annotation.VanillaRef;
import doom.wad.LumpDirectory;
import doom.wad.iwad.GameVariant;
import doom.wad.texture.FlatDirectory;
import doom.wad.texture.TextureDirectory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Builds {@link GameSession} implementations: copies the definitions once, applies DEHACKED (the IWAD's own lump for
 * Freedoom / FreeDM, then the given patches) and wires the rule areas. The Wave 3 areas replace the stubs here.
 */
public final class SessionFactory {

    private SessionFactory() {
    }

    public static GameSession create(GameSetup setup) {
        return createImpl(setup);
    }

    /** Same as {@link #create}, typed for tests and tools. */
    @VanillaRef("d_main.c:LoadIwadDeh")
    public static SessionImpl createImpl(GameSetup setup) {
        LumpDirectory lumps = setup.lumps();
        GameDefs defs = GameDefs.createDefault();
        GameStrings strings = GameStrings.createDefault();

        // The Freedoom IWADs have DEHACKED lumps that must be loaded (errors are ignored), unless -nodeh.
        GameVariant variant = setup.iwad().variant();
        if (!setup.noIwadDehacked() && (variant == GameVariant.FREEDOOM || variant == GameVariant.FREEDM)) {
            int lump = lumps.find("DEHACKED");
            if (lump >= 0) {
                String text = new String(lumps.bytes(lump), StandardCharsets.ISO_8859_1);
                String source = lumps.wadOf(lump).fileName() + ":DEHACKED";
                Dehacked.apply(defs, strings, new DehackedPatch(source, text), Dehacked.Options.iwadLump());
            }
        }
        for (DehackedPatch patch : setup.dehacked()) {
            Dehacked.apply(defs, strings, patch);
        }

        World w = new World(lumps, setup.iwad().mode(), setup.iwad().mission(), variant, setup.version(), defs,
                strings, TextureDirectory.load(lumps), FlatDirectory.load(lumps), setup.events());
        w.actorIds = new ActorRegistry();
        w.mobjs = new Mobjs(w);
        w.actions = new ActionDispatcher(w);
        w.physics = new Physics(w);
        w.sight = new Sight(w);
        w.playerSim = new PlayerSim(w);
        w.weapons = new Weapons(w);
        w.pickups = new Pickups(w);
        w.ai = doom.game.ai.AiFactory.create(w);
        w.combat = doom.game.combat.CombatFactory.create(w);
        w.specials = doom.game.mapspecial.SpecialsFactory.create(w);
        Game game = new Game(w);
        w.flow = game;
        w.specials.init();
        game.setSaveGameHandler(new SaveGameCodec(w, game, Clock.systemUTC()));
        return new SessionImpl(w, game);
    }
}
