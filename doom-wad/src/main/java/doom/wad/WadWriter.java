package doom.wad;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a WAD file in memory: lumps are stored in the order they are added, followed by the directory. Used by
 * tests (synthetic maps, malformed data) and tools.
 */
public final class WadWriter {

    private final String identification;
    private final List<String> names = new ArrayList<>();
    private final List<byte[]> contents = new ArrayList<>();

    private WadWriter(String identification) {
        this.identification = identification;
    }

    /** A writer for a {@code PWAD}. */
    public static WadWriter pwad() {
        return new WadWriter("PWAD");
    }

    /** A writer for an {@code IWAD}. */
    public static WadWriter iwad() {
        return new WadWriter("IWAD");
    }

    /** Appends a lump; the name is stored upper case and must be at most 8 ASCII characters. */
    public WadWriter add(String name, byte[] data) {
        if (name.length() > LumpNames.MAX_LENGTH) {
            throw new IllegalArgumentException("lump name longer than 8 characters: " + name);
        }
        names.add(LumpNames.upper(name));
        contents.add(data.clone());
        return this;
    }

    /** Appends an empty lump (a marker). */
    public WadWriter marker(String name) {
        return add(name, new byte[0]);
    }

    /** The WAD bytes. */
    public byte[] toBytes() {
        int dataSize = 0;
        for (byte[] c : contents) {
            dataSize += c.length;
        }
        int dirOffset = 12 + dataSize;
        ByteBuffer b = ByteBuffer.allocate(dirOffset + names.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
        b.put(identification.getBytes(StandardCharsets.US_ASCII)).putInt(names.size()).putInt(dirOffset);
        for (byte[] c : contents) {
            b.put(c);
        }
        int offset = 12;
        for (int i = 0; i < names.size(); i++) {
            byte[] c = contents.get(i);
            b.putInt(c.length == 0 ? 0 : offset).putInt(c.length);
            byte[] name = new byte[LumpNames.MAX_LENGTH];
            byte[] src = names.get(i).getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(src, 0, name, 0, src.length);
            b.put(name);
            offset += c.length;
        }
        return b.array();
    }

    /** The WAD as an in-memory {@link WadFile}. */
    public WadFile toWadFile(String fileName) {
        return WadFile.of(fileName, ByteBuffer.wrap(toBytes()));
    }

    /** Writes the WAD to {@code path}. */
    public void write(Path path) throws IOException {
        Files.write(path, toBytes());
    }
}
