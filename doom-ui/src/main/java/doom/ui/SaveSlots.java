package doom.ui;

/** Read access to the save files, for the Load/Save menus ({@code M_ReadSaveStrings}). */
public interface SaveSlots {

    /** Number of slots shown by the menus. The original has 6. */
    int COUNT = 6;

    /**
     * The 24-character description stored in the save file of {@code slot} (0..5), or {@code null} when the slot is
     * empty or the file is unreadable. Called whenever the Load or Save menu opens.
     */
    String description(int slot);

    /** No saves at all. */
    SaveSlots EMPTY = slot -> null;
}
