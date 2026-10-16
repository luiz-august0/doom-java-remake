package doom.ui;

import java.util.List;

/**
 * The configuration the menus read and write. Implemented by the engine on top of its configuration file. The
 * original options (Options and Sound Volume menus) use the first group; the extended pages are data
 * ({@link SettingsPage}) and need nothing else from the UI.
 *
 * <p>Setters are called from inside {@link Ui#keyDown}; they apply the change immediately (volume, view size, ...).
 */
public interface UiSettings {

    /** Where the minimal full-screen HUD is anchored. */
    enum HudAlign { CORNERS, CENTRE }

    /** The full-screen HUD (view size 11): vanilla shows only messages. */
    enum FullscreenHud { OFF, MINIMAL }

    /** Sound effects volume 0..15 ({@code sfxVolume}). */
    int sfxVolume();

    void setSfxVolume(int volume);

    /** Music volume 0..15 ({@code musicVolume}). */
    int musicVolume();

    void setMusicVolume(int volume);

    /** Mouse sensitivity 0..9 ({@code mouseSensitivity}). */
    int mouseSensitivity();

    void setMouseSensitivity(int sensitivity);

    /** {@code screenblocks}: 3..11 (the Options menu slider is {@code screenblocks - 3}, 0..8; 10 = full view, 11 = no status bar). */
    int screenBlocks();

    /** {@code M_SizeDisplay} result: the engine applies {@code R_SetViewSize}. */
    void setScreenBlocks(int blocks);

    boolean showMessages();

    void setShowMessages(boolean on);

    /** {@code detailLevel}: 0 = high, 1 = low. */
    int detailLevel();

    void setDetailLevel(int level);

    /** {@code usegamma}: 0..4. The engine re-uploads the palette. */
    int gamma();

    void setGamma(int level);

    /** The extended text submenus; empty hides the "Extended Options" entry. */
    default List<SettingsPage> extendedPages() {
        return List.of();
    }

    default FullscreenHud fullscreenHud() {
        return FullscreenHud.OFF;
    }

    default HudAlign hudAlign() {
        return HudAlign.CORNERS;
    }
}
