package doom.game.persist;

import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Identifies the WADs of a session: file name and a SHA-256 over the lump names, sizes and contents. */
final class WadFingerprint {

    private WadFingerprint() {
    }

    static List<SaveFormat.WadId> of(LumpDirectory lumps) {
        List<SaveFormat.WadId> out = new ArrayList<>();
        for (WadFile wad : lumps.wads()) {
            out.add(new SaveFormat.WadId(wad.fileName(), hash(wad)));
        }
        return out;
    }

    private static String hash(WadFile wad) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        byte[] size = new byte[4];
        for (int i = 0; i < wad.lumpCount(); i++) {
            md.update(wad.lumpName(i).getBytes(StandardCharsets.ISO_8859_1));
            int n = wad.lumpSize(i);
            size[0] = (byte) n;
            size[1] = (byte) (n >> 8);
            size[2] = (byte) (n >> 16);
            size[3] = (byte) (n >> 24);
            md.update(size);
            ByteBuffer data = wad.lumpData(i);
            md.update(data);
        }
        return SaveFormat.bytesToHex(md.digest());
    }

    /** A human-readable list of the differences between the WADs of a save and the running game, or null if equal. */
    static String difference(List<SaveFormat.WadId> saved, List<SaveFormat.WadId> current) {
        if (saved.equals(current)) {
            return null;
        }
        // same content under another file name is not a difference
        if (saved.size() == current.size()) {
            boolean same = true;
            for (int i = 0; i < saved.size(); i++) {
                if (!saved.get(i).sha256().equals(current.get(i).sha256())) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return null;
            }
        }
        return "saved with " + names(saved) + ", running with " + names(current);
    }

    private static String names(List<SaveFormat.WadId> wads) {
        StringBuilder sb = new StringBuilder();
        for (SaveFormat.WadId w : wads) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(w.name());
        }
        return sb.toString();
    }
}
