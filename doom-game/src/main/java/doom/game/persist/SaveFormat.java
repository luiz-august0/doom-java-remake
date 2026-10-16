package doom.game.persist;

import java.util.ArrayList;
import java.util.List;

/**
 * The DJSV container (spec 08 section 2): a header, then tagged sections (tag, length, payload) and a trailing CRC-32.
 * Everything is little-endian.
 *
 * <pre>
 * "DJSV" u16 formatVersion
 * string gameVersion, string gameMode
 * u8 wadCount, wadCount x (string name, 32 bytes SHA-256)
 * string description, i64 timestamp (epoch millis)
 * "SESS" session parameters      "PLYR" players       "WRLD" sectors, lines, sides, thing links, queues
 * "THNK" thinkers (map objects and sector effects, in thinker-list order, then objects only referenced)
 * "SPEC" active ceilings / platforms, buttons, boss brain
 * "CRC " CRC-32 of every byte before this section
 * </pre>
 *
 * <p>Unknown sections are skipped, so a later writer may add sections without breaking this reader; any change to the
 * layout of an existing section raises {@link #FORMAT_VERSION}.
 */
final class SaveFormat {

    static final String MAGIC = "DJSV";
    static final int FORMAT_VERSION = 1;
    /** {@code SAVESTRINGSIZE}. */
    static final int DESCRIPTION_LENGTH = 24;

    static final String SESSION = "SESS";
    static final String PLAYERS = "PLYR";
    static final String WORLD = "WRLD";
    static final String THINKERS = "THNK";
    static final String SPECIALS = "SPEC";
    static final String CRC = "CRC ";

    private SaveFormat() {
    }

    /** One WAD of the load order: file name and the SHA-256 of its content, lower-case hex. */
    record WadId(String name, String sha256) {
    }

    /** The header of a save game. */
    record Header(int formatVersion, String gameVersion, String gameMode, List<WadId> wads, String description,
                  long timestamp) {
    }

    /** A named section of the file. */
    record Section(String tag, ByteSource data) {
    }

    /** A save game split into its header and sections; nothing has been interpreted yet. */
    record Parsed(Header header, List<Section> sections) {

        /** The section with this tag. */
        ByteSource require(String tag) {
            for (Section s : sections) {
                if (s.tag().equals(tag)) {
                    return s.data().copy();
                }
            }
            throw new SaveGameException("Save game is incomplete (no " + tag.trim() + " section)");
        }
    }

    static void writeHeader(ByteSink out, Header h) {
        out.putTag(MAGIC);
        out.putShort(h.formatVersion());
        out.putString(h.gameVersion());
        out.putString(h.gameMode());
        out.putByte(h.wads().size());
        for (WadId w : h.wads()) {
            out.putString(w.name());
            out.putBytes(hexToBytes(w.sha256()));
        }
        out.putString(h.description());
        out.putLong(h.timestamp());
    }

    /**
     * Checks the magic, the format version and the checksum, and splits the file into sections.
     *
     * @throws SaveGameException if the data is not a readable DJSV file
     */
    static Parsed parse(byte[] data) {
        if (data == null || data.length < 8) {
            throw new SaveGameException("Not a DOOM Java save game (file too short)");
        }
        ByteSource in = new ByteSource(data, 0, data.length, "header");
        if (!MAGIC.equals(in.getTag())) {
            throw new SaveGameException("Not a DOOM Java save game (bad signature)");
        }
        int version = in.getShort();
        if (version > FORMAT_VERSION) {
            throw new SaveGameException("Save game was made by a newer version of the game (format " + version
                    + ", this game reads up to " + FORMAT_VERSION + ")");
        }
        if (version < 1) {
            throw new SaveGameException("Unsupported save game format " + version);
        }
        String gameVersion = in.getString();
        String gameMode = in.getString();
        int wadCount = in.getByte();
        List<WadId> wads = new ArrayList<>(wadCount);
        for (int i = 0; i < wadCount; i++) {
            String name = in.getString();
            wads.add(new WadId(name, bytesToHex(in.getBytes(32))));
        }
        String description = in.getString();
        long timestamp = in.getLong();
        Header header = new Header(version, gameVersion, gameMode, List.copyOf(wads), description, timestamp);

        List<Section> sections = new ArrayList<>();
        boolean sawCrc = false;
        while (in.remaining() > 0) {
            int sectionStart = in.position();
            String tag = in.getTag();
            int length = in.getInt();
            if (length < 0 || length > in.remaining()) {
                throw new SaveGameException("Save game is truncated or corrupt (section " + tag.trim() + ")");
            }
            ByteSource payload = in.slice(length, "section " + tag.trim());
            if (CRC.equals(tag)) {
                if (length != 4) {
                    throw new SaveGameException("Save game is corrupt (bad checksum section)");
                }
                int stored = payload.getInt();
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                crc.update(data, 0, sectionStart);
                if ((int) crc.getValue() != stored) {
                    throw new SaveGameException("Save game is corrupt (checksum mismatch)");
                }
                sawCrc = true;
                if (in.remaining() != 0) {
                    throw new SaveGameException("Save game is corrupt (data after the checksum)");
                }
                break;
            }
            sections.add(new Section(tag, payload));
        }
        if (!sawCrc) {
            throw new SaveGameException("Save game is truncated (no checksum)");
        }
        return new Parsed(header, sections);
    }

    /** Reads only the header (description, WADs, version) of a save game, after the same checks as a load. */
    static Header readHeader(byte[] data) {
        return parse(data).header();
    }

    static String bytesToHex(byte[] b) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            out[i * 2] = digits[(b[i] >> 4) & 15];
            out[i * 2 + 1] = digits[b[i] & 15];
        }
        return new String(out);
    }

    static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
