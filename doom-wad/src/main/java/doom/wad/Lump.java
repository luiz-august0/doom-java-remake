package doom.wad;

/**
 * Description of one lump of a {@link LumpDirectory}, for listings and tools. The data itself is read through
 * {@link LumpDirectory#data(int)}.
 *
 * @param number    lump number in the directory ({@code lumpnum})
 * @param name      upper-case name
 * @param size      size in bytes
 * @param offset    byte offset inside its file
 * @param wad       the file that contains it
 * @param indexInWad position in that file's own directory
 * @param namespace marker section inside its file
 */
public record Lump(int number, String name, int size, int offset, WadFile wad, int indexInWad,
                   LumpNamespace namespace) {
}
