package doom.game.persist;

import doom.game.api.GameState;
import doom.game.session.Game;
import doom.game.session.SaveGameHandler;
import doom.game.world.Level;
import doom.game.world.LevelLoader;
import doom.game.world.World;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameMode;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * The persistence layer of a session ({@code G_DoSaveGame} / {@code G_DoLoadGame}): custom DJSV format (ADR-013, see
 * {@link SaveFormat}). Saving takes the snapshot synchronously and returns the bytes, which the engine writes to disk
 * on a virtual thread. Loading rebuilds the level from the map lumps (a full {@code G_InitNew}) and overlays the saved
 * state.
 *
 * <p>All checks that can reject a file (signature, format version, checksum, game mode, map, level size) happen before
 * the running game is touched; a file that fails them leaves the session as it was.
 */
public final class SaveGameCodec implements SaveGameHandler {

    private static final System.Logger LOG = System.getLogger(SaveGameCodec.class.getName());

    private final World w;
    private final Game game;
    private final Clock clock;
    /** Computed on first use: hashing the WADs takes a moment. */
    private List<SaveFormat.WadId> wads;
    private String lastWarning;

    public SaveGameCodec(World world, Game game, Clock clock) {
        this.w = world;
        this.game = game;
        this.clock = clock;
    }

    /**
     * Reads the header of a save game (description, timestamp, WADs) after the same checks as a load: signature,
     * format version and checksum.
     *
     * @throws SaveGameException if the data is not a readable save game
     */
    public static SaveInfo inspect(byte[] data) {
        return SaveInfo.of(SaveFormat.readHeader(data));
    }

    /** The warning of the last {@link #load}, or null (different WADs or game version). */
    public String lastWarning() {
        return lastWarning;
    }

    private List<SaveFormat.WadId> wads() {
        if (wads == null) {
            wads = WadFingerprint.of(w.lumps);
        }
        return wads;
    }

    // ------------------------------------------------------------------ save

    @Override
    @VanillaRef("g_game.c:G_DoSaveGame")
    public byte[] save(int slot, String description) {
        if (game.gameState != GameState.LEVEL || w.level == null) {
            throw new SaveGameException("There is no game in progress to save");
        }
        String text = description == null ? "" : description;
        if (text.length() > SaveFormat.DESCRIPTION_LENGTH) {
            text = text.substring(0, SaveFormat.DESCRIPTION_LENGTH);
        }
        SaveFormat.Header header = new SaveFormat.Header(SaveFormat.FORMAT_VERSION, w.version.cliName(),
                w.mode.name(), wads(), text, clock.millis());
        return new SnapshotWriter(w, game).write(header);
    }

    // ------------------------------------------------------------------ load

    @Override
    @VanillaRef("g_game.c:G_DoLoadGame")
    public void load(byte[] data) {
        lastWarning = null;
        SaveFormat.Parsed parsed = SaveFormat.parse(data);
        SaveFormat.Header header = parsed.header();

        if (!w.mode.name().equals(header.gameMode())) {
            throw new SaveGameException("This save game belongs to another game (" + header.gameMode()
                    + "), not " + w.mode.name());
        }
        StringBuilder warning = new StringBuilder();
        String wadDifference = WadFingerprint.difference(header.wads(), wads());
        if (wadDifference != null) {
            warning.append("different WADs: ").append(wadDifference);
        }
        if (!w.version.cliName().equals(header.gameVersion())) {
            if (warning.length() > 0) {
                warning.append("; ");
            }
            warning.append("game version ").append(header.gameVersion()).append(" vs ").append(w.version.cliName());
        }

        SessionData session = SessionData.read(parsed.require(SaveFormat.SESSION));
        checkMap(parsed, session);

        // Nothing above changed the running game. From here on it is replaced.
        w.netGame = session.netGame;
        w.deathmatch = session.deathmatch;
        w.respawnParm = session.respawnParm;
        w.fastParm = session.fastParm;
        w.noMonsters = session.noMonsters;
        w.consolePlayer = session.consolePlayer;
        System.arraycopy(session.playerInGame, 0, w.playerInGame, 0, World.MAXPLAYERS);
        try {
            game.initNew(session.skill, session.episode, session.map);
            new SnapshotReader(w, parsed, session).apply();
            game.levelStartTic = session.levelStartTic;
        } catch (SaveGameException e) {
            throw new SaveGameException(e.getMessage(), true, e);
        } catch (RuntimeException e) {
            throw new SaveGameException("Save game could not be restored (" + e + ")", true, e);
        }

        if (warning.length() > 0) {
            lastWarning = warning.toString();
            LOG.log(System.Logger.Level.WARNING, "loaded a save game with {0}", lastWarning);
            String message = "WARNING: save made with different WADs";
            if (wadDifference == null) {
                message = "WARNING: save made with another game version";
            }
            w.players[w.consolePlayer].message = message;
            w.events.playerMessage(w.consolePlayer, message);
        }
    }

    /** The map must exist in the running WADs and have the geometry the snapshot was made on. */
    private void checkMap(SaveFormat.Parsed parsed, SessionData session) {
        String lump = w.mode == GameMode.COMMERCIAL
                ? (session.map < 10 ? "MAP0" + session.map : "MAP" + session.map)
                : "E" + session.episode + "M" + session.map;
        if (w.lumps.find(lump) < 0) {
            throw new SaveGameException("The map " + lump + " of this save game is not in the loaded WADs");
        }
        Level level;
        try {
            level = new LevelLoader(w.textures, w.flats).load(w.lumps, lump);
        } catch (RuntimeException e) {
            throw new SaveGameException("The map " + lump + " cannot be loaded (" + e.getMessage() + ")", e);
        }
        int[] dims = SnapshotReader.worldDimensions(parsed);
        int[] have = {level.sectors.length, level.lines.length, level.sides.length, level.blockmap.columns,
            level.blockmap.rows};
        if (!Arrays.equals(dims, have)) {
            throw new SaveGameException("The save game does not match the map " + lump + " (the WADs are different)");
        }
    }
}
