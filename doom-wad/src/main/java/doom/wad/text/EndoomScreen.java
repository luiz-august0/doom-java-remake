package doom.wad.text;

import doom.wad.LumpDirectory;
import doom.wad.LumpReader;

/**
 * The ENDOOM text screen: 80×25 cells of character + attribute (DOS text mode, code page 437). The attribute byte
 * holds the foreground colour in bits 0-3, the background in bits 4-6 and the blink flag in bit 7.
 */
public final class EndoomScreen {
    public static final int COLUMNS = 80;
    public static final int ROWS = 25;
    /** Size of the lump in bytes ({@code ENDOOM_W * ENDOOM_H * 2}). */
    public static final int SIZE = COLUMNS * ROWS * 2;

    /** Unicode characters of code page 437 (the glyphs of the first 32 codes, as displayed by the PC BIOS). */
    private static final String CP437 =
            "\u0000☺☻♥♦♣♠•◘○◙♂♀♪♫☼"
            + "►◄↕‼¶§▬↨↑↓→←∟↔▲▼"
            + " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
            + "⌂"
            + "ÇüéâäàåçêëèïîìÄÅ"
            + "ÉæÆôöòûùÿÖÜ¢£¥₧ƒ"
            + "áíóúñÑªº¿⌐¬½¼¡«»"
            + "░▒▓│┤╡╢╖╕╣║╗╝╜╛┐"
            + "└┴┬├─┼╞╟╚╔╩╦╠═╬╧"
            + "╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀"
            + "αßΓπΣσµτΦΘΩδ∞φε∩"
            + "≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ ";

    private final byte[] cells;

    private EndoomScreen(byte[] cells) {
        this.cells = cells;
    }

    /**
     * Decodes the ENDOOM lump.
     *
     * @throws doom.wad.WadFormatException when the lump holds fewer than {@link #SIZE} bytes
     */
    public static EndoomScreen read(LumpReader r) {
        r.require(0, SIZE, "ENDOOM screen");
        return new EndoomScreen(r.bytes(0, SIZE));
    }

    /** Decodes the {@code ENDOOM} lump of a directory. */
    public static EndoomScreen read(LumpDirectory lumps) {
        return read(LumpReader.of(lumps, lumps.get("ENDOOM")));
    }

    private int index(int column, int row) {
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            throw new IllegalArgumentException("cell " + column + "," + row);
        }
        return (row * COLUMNS + column) * 2;
    }

    /** Code page 437 character code of a cell (0-255). */
    public int code(int column, int row) {
        return cells[index(column, row)] & 0xFF;
    }

    /** Unicode character for the cell. */
    public char character(int column, int row) {
        return CP437.charAt(code(column, row));
    }

    /** Raw attribute byte (0-255). */
    public int attribute(int column, int row) {
        return cells[index(column, row) + 1] & 0xFF;
    }

    /** Foreground colour, 0-15 (CGA palette index). */
    public int foreground(int column, int row) {
        return attribute(column, row) & 0x0F;
    }

    /** Background colour, 0-7. */
    public int background(int column, int row) {
        return (attribute(column, row) >> 4) & 0x07;
    }

    /** True when the cell blinks. */
    public boolean blink(int column, int row) {
        return (attribute(column, row) & 0x80) != 0;
    }

    /** The characters of one row as Unicode text (80 characters). */
    public String rowText(int row) {
        char[] out = new char[COLUMNS];
        for (int c = 0; c < COLUMNS; c++) {
            out[c] = character(c, row);
        }
        return new String(out);
    }

    /** A copy of the raw cells: character, attribute, character, attribute, ... (4000 bytes). */
    public byte[] toBytes() {
        return cells.clone();
    }
}
