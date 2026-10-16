package doom.wad;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * One WAD file, memory-mapped read-only (spec 05 §1). Lump data is exposed as little-endian slices without copying.
 *
 * <p>Header: {@code char[4]} identification ({@code IWAD}/{@code PWAD}), {@code int32} lump count,
 * {@code int32} directory offset. Directory entries: {@code int32} offset, {@code int32} size, {@code char[8]} name.
 *
 * <p>A file whose name does not end in {@code .wad} can also be loaded as a single lump named after the file
 * ({@link #load(Path)}), exactly like {@code W_AddFile} does for {@code -file foo.lmp} and {@code -playdemo}.
 */
public final class WadFile {

    private final String fileName;
    private final Path path;
    private final String identification;
    private final ByteBuffer data;
    private final String[] names;
    private final int[] offsets;
    private final int[] sizes;
    private final LumpNamespace[] namespaces;

    private WadFile(String fileName, Path path, ByteBuffer data) {
        this.fileName = fileName;
        this.path = path;
        this.data = data.order(ByteOrder.LITTLE_ENDIAN);
        if (data.capacity() < 12) {
            throw new WadFormatException(fileName, null, 0, "file too small for a WAD header (" + data.capacity()
                    + " bytes)");
        }
        String id = new String(new byte[] {data.get(0), data.get(1), data.get(2), data.get(3)},
                StandardCharsets.ISO_8859_1);
        if (!id.equals("IWAD") && !id.equals("PWAD")) {
            // Original message: W_AddFile "Wad file %s doesn't have IWAD or PWAD id".
            throw new WadFormatException(fileName, null, 0, "Wad file " + fileName
                    + " doesn't have IWAD or PWAD id (found '" + printable(id) + "')");
        }
        this.identification = id;
        int count = this.data.getInt(4);
        int dirOffset = this.data.getInt(8);
        if (count < 0 || dirOffset < 0 || (long) dirOffset + (long) count * 16 > data.capacity()) {
            throw new WadFormatException(fileName, null, 4, "directory outside the file (" + count + " lumps at "
                    + dirOffset + ", file has " + data.capacity() + " bytes)");
        }
        names = new String[count];
        offsets = new int[count];
        sizes = new int[count];
        for (int i = 0; i < count; i++) {
            int entry = dirOffset + i * 16;
            int offset = this.data.getInt(entry);
            int size = this.data.getInt(entry + 4);
            String name = LumpNames.upper(LumpNames.read(this.data, entry + 8));
            // Zero-length lumps (markers) may carry any offset: they are never read.
            if (size < 0 || (size > 0 && (offset < 0 || (long) offset + size > data.capacity()))) {
                throw new WadFormatException(fileName, name, entry, "lump " + i + " data outside the file (offset "
                        + offset + ", size " + size + ", file has " + data.capacity() + " bytes)");
            }
            names[i] = name;
            offsets[i] = offset;
            sizes[i] = size;
        }
        namespaces = computeNamespaces(names);
    }

    private WadFile(String fileName, Path path, String lumpName, ByteBuffer data) {
        this.fileName = fileName;
        this.path = path;
        this.identification = "PWAD";
        this.data = data.order(ByteOrder.LITTLE_ENDIAN);
        this.names = new String[] {LumpNames.upper(lumpName)};
        this.offsets = new int[] {0};
        this.sizes = new int[] {data.capacity()};
        this.namespaces = new LumpNamespace[] {LumpNamespace.GLOBAL};
    }

    /** Opens and memory-maps a WAD file. */
    public static WadFile open(Path path) throws IOException {
        return new WadFile(path.getFileName().toString(), path, map(path));
    }

    /**
     * Opens a file with {@code W_AddFile} semantics: a name ending in {@code .wad} (any case) is a WAD file; any
     * other file becomes a single lump named after the file's base name ({@link #lumpNameForFile}).
     */
    public static WadFile load(Path path) throws IOException {
        String name = path.getFileName().toString();
        if (name.length() >= 3 && name.substring(name.length() - 3).equalsIgnoreCase("wad")) {
            return open(path);
        }
        return new WadFile(name, path, lumpNameForFile(name), map(path));
    }

    /** Wraps WAD bytes held in memory (tests, synthetic maps). */
    public static WadFile of(String name, ByteBuffer bytes) {
        return new WadFile(name, null, bytes.duplicate());
    }

    /** Wraps one lump held in memory as a single-lump file (as {@link #load} does for non-WAD files). */
    public static WadFile ofLump(String fileName, String lumpName, ByteBuffer bytes) {
        return new WadFile(fileName, null, LumpNames.key(lumpName), bytes.duplicate());
    }

    /**
     * Lump name used for a single-lump file ({@code M_ExtractFileBase}): the base name up to the first dot,
     * upper case, truncated to 8 characters.
     */
    public static String lumpNameForFile(String fileName) {
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = fileName.substring(slash + 1);
        int dot = base.indexOf('.');
        if (dot >= 0) {
            base = base.substring(0, dot);
        }
        return LumpNames.key(base);
    }

    private static ByteBuffer map(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }

    /** Lump names are compared in upper case and are at most 8 characters, stopping at the first NUL. */
    static String readName(ByteBuffer buf, int at) {
        return LumpNames.upper(LumpNames.read(buf, at));
    }

    private static LumpNamespace[] computeNamespaces(String[] names) {
        LumpNamespace[] out = new LumpNamespace[names.length];
        LumpNamespace current = LumpNamespace.GLOBAL;
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            LumpNamespace open = LumpNamespace.opening(n);
            LumpNamespace close = LumpNamespace.closing(n);
            if (open != null) {
                current = open;
                out[i] = LumpNamespace.MARKER;
            } else if (close != null) {
                if (close == current) {
                    current = LumpNamespace.GLOBAL;
                }
                out[i] = LumpNamespace.MARKER;
            } else if (current != LumpNamespace.GLOBAL && LumpNamespace.isSubMarker(n)) {
                out[i] = LumpNamespace.MARKER;
            } else {
                out[i] = current;
            }
        }
        return out;
    }

    private static String printable(String id) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            sb.append(c >= 32 && c < 127 ? c : '?');
        }
        return sb.toString();
    }

    public String fileName() {
        return fileName;
    }

    /** The file on disk, or null for WADs held in memory. */
    public Path path() {
        return path;
    }

    public boolean isIwad() {
        return identification.equals("IWAD");
    }

    /** {@code "IWAD"} or {@code "PWAD"} (single-lump files report {@code "PWAD"}). */
    public String identification() {
        return identification;
    }

    public int lumpCount() {
        return names.length;
    }

    /** Upper-case name of lump {@code i}. */
    public String lumpName(int i) {
        return names[i];
    }

    public int lumpSize(int i) {
        return sizes[i];
    }

    public int lumpOffset(int i) {
        return offsets[i];
    }

    /** Marker section of lump {@code i} inside this file. */
    public LumpNamespace namespace(int i) {
        return namespaces[i];
    }

    /** Read-only little-endian view of lump {@code i}, positioned at 0. No copy. */
    public ByteBuffer lumpData(int i) {
        int size = sizes[i];
        int offset = size == 0 ? 0 : offsets[i];
        return data.slice(offset, size).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public String toString() {
        return fileName;
    }
}
