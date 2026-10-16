package doom.engine;

import doom.engine.cli.LaunchOptions;
import doom.engine.config.DataDirs;
import doom.game.api.DehackedPatch;
import doom.math.DoomFatalException;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.iwad.IwadIdentifier;
import doom.wad.iwad.IwadInfo;
import doom.wad.iwad.IwadLocator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The game data the command line asks for ({@code D_DoomMain}): the IWAD found with {@link IwadLocator}, the
 * {@code -merge} and {@code -file} WADs on top of it, and the DEHACKED patches from {@code -deh} and from {@code -file}
 * arguments ending in {@code .deh} or {@code .bex}.
 *
 * @param lumps   all loaded lumps (IWAD first)
 * @param iwad    identification of the IWAD, with the {@code -gameversion} override applied
 * @param patches DEHACKED patches in the order they are applied
 */
public record GameSources(LumpDirectory lumps, IwadInfo iwad, List<DehackedPatch> patches) {

    private static final System.Logger LOG = System.getLogger(GameSources.class.getName());

    public GameSources {
        patches = List.copyOf(patches);
    }

    /**
     * Finds and opens the WADs.
     *
     * @throws DoomFatalException with the original message when no IWAD or a listed file is found
     */
    public static GameSources load(LaunchOptions options, DataDirs dirs) {
        IwadLocator locator = IwadLocator.fromEnvironment(options.iwad(), dirs.root());
        Path iwadPath = locator.require();
        try {
            WadFile iwadFile = WadFile.load(iwadPath);
            IwadInfo info = IwadIdentifier.identify(iwadFile, options.gameVersion());

            LumpDirectory.Builder builder = LumpDirectory.builder().add(iwadFile);
            List<DehackedPatch> patches = new ArrayList<>();
            // Chocolate Doom runs every -merge before -file
            for (String name : options.merge()) {
                builder.merge(WadFile.load(find(locator, name)));
            }
            for (String name : options.deh()) {
                patches.add(readPatch(find(locator, name)));
            }
            for (String name : options.files()) {
                Path p = find(locator, name);
                String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".deh") || lower.endsWith(".bex")) {
                    patches.add(readPatch(p));
                } else {
                    builder.add(WadFile.load(p));
                }
            }
            return new GameSources(builder.build(), info, patches);
        } catch (IOException e) {
            throw new DoomFatalException("Cannot read WAD file: " + e.getMessage());
        }
    }

    private static Path find(IwadLocator locator, String name) {
        return locator.findWadByName(name)
                .orElseThrow(() -> new DoomFatalException("File '" + name + "' not found!"));
    }

    private static DehackedPatch readPatch(Path file) throws IOException {
        LOG.log(System.Logger.Level.INFO, "loading DEHACKED patch {0}", file);
        return new DehackedPatch(file.getFileName().toString(),
                new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
    }
}
