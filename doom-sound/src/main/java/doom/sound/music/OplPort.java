package doom.sound.music;

/** Destination of the OPL register writes made by {@link OplMusicDriver}. */
public interface OplPort {

    /**
     * Writes an OPL register.
     *
     * @param reg   register number; bit 8 selects the second register array (OPL3 mode)
     * @param value 8-bit value
     */
    void writeRegister(int reg, int value);
}
