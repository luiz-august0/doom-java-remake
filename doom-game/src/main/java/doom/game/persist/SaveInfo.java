package doom.game.persist;

import java.util.List;

/**
 * What the header of a save game says, read without restoring anything.
 *
 * @param formatVersion DJSV format version of the file
 * @param gameVersion   executable version the game emulated ({@code 1.9}, {@code ultimate}, ...)
 * @param gameMode      {@code GameMode} name of the IWAD the save was made with
 * @param wads          file name of each WAD, in load order
 * @param wadHashes     SHA-256 (lower-case hex) of each WAD, in the same order
 * @param description   the player's description (at most 24 characters)
 * @param timestamp     epoch milliseconds when the snapshot was taken
 * @param payloadOffset index of the first byte after the header (the sections start here)
 */
public record SaveInfo(int formatVersion, String gameVersion, String gameMode, List<String> wads,
                       List<String> wadHashes, String description, long timestamp, int payloadOffset) {

    static SaveInfo of(SaveFormat.Header h) {
        ByteSink probe = new ByteSink(256);
        SaveFormat.writeHeader(probe, h);
        List<String> names = h.wads().stream().map(SaveFormat.WadId::name).toList();
        List<String> hashes = h.wads().stream().map(SaveFormat.WadId::sha256).toList();
        return new SaveInfo(h.formatVersion(), h.gameVersion(), h.gameMode(), names, hashes, h.description(),
                h.timestamp(), probe.size());
    }
}
