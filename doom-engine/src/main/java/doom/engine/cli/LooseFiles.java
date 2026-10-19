package doom.engine.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Files given without a parameter ({@code doom-java freedoom2.wad}), as when a file is opened from the desktop: a file
 * association, "Open with" or dropping it on the launcher (Chocolate Doom's {@code M_AddLooseFiles}). The arguments
 * before the first parameter are turned into parameters appended at the end, so an explicit one still wins (the first
 * occurrence of a parameter is the one read):
 *
 * <ul>
 *   <li>a {@code .wad} whose header says {@code IWAD} (the first one only) → {@code -iwad}; other {@code .wad} → {@code -merge}</li>
 *   <li>{@code .deh}, {@code .bex} → {@code -deh}</li>
 *   <li>{@code .lmp} → {@code -playdemo} (the first one only)</li>
 * </ul>
 *
 * Arguments of any other kind are left where they are (and ignored, as before).
 */
public final class LooseFiles {

    private LooseFiles() {
    }

    /** The command line with the loose files turned into parameters. */
    public static List<String> expand(List<String> args) {
        String iwad = null;
        String demo = null;
        List<String> merge = new ArrayList<>();
        List<String> deh = new ArrayList<>();
        List<String> out = new ArrayList<>();
        int i = 0;
        for (; i < args.size() && !CommandLine.isParameter(args.get(i)); i++) {
            String arg = args.get(i);
            String lower = arg.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".wad")) {
                // DIVERGE: Chocolate picks the IWAD by file name, and only for absolute Windows paths (docs/divergences.md)
                if (iwad == null && isIwad(arg)) {
                    iwad = arg;
                } else {
                    merge.add(arg);
                }
            } else if (lower.endsWith(".deh") || lower.endsWith(".bex")) {
                deh.add(arg);
            } else if (lower.endsWith(".lmp") && demo == null) {
                demo = arg;
            } else {
                out.add(arg);
            }
        }
        out.addAll(args.subList(i, args.size()));
        if (iwad != null) {
            out.add("-iwad");
            out.add(iwad);
        }
        if (!merge.isEmpty()) {
            out.add("-merge");
            out.addAll(merge);
        }
        if (!deh.isEmpty()) {
            out.add("-deh");
            out.addAll(deh);
        }
        if (demo != null) {
            out.add("-playdemo");
            out.add(demo);
        }
        return out;
    }

    /** Whether the file starts with the {@code IWAD} identification (false when it cannot be read). */
    private static boolean isIwad(String file) {
        try (InputStream in = Files.newInputStream(Path.of(file))) {
            return new String(in.readNBytes(4), StandardCharsets.US_ASCII).equals("IWAD");
        } catch (IOException | InvalidPathException e) {
            return false;
        }
    }
}
