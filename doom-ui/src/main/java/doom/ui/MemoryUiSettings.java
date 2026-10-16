package doom.ui;

import java.util.List;

/** A plain in-memory {@link UiSettings} with the vanilla defaults; useful for tests, tools and as a starting point. */
public class MemoryUiSettings implements UiSettings {

    private int sfxVolume = 8;
    private int musicVolume = 8;
    private int mouseSensitivity = 5;
    private int screenBlocks = 10;
    private boolean showMessages = true;
    private int detailLevel;
    private int gamma;
    private List<SettingsPage> pages = List.of();
    private FullscreenHud fullscreenHud = FullscreenHud.OFF;
    private HudAlign hudAlign = HudAlign.CORNERS;

    @Override
    public int sfxVolume() {
        return sfxVolume;
    }

    @Override
    public void setSfxVolume(int volume) {
        sfxVolume = volume;
    }

    @Override
    public int musicVolume() {
        return musicVolume;
    }

    @Override
    public void setMusicVolume(int volume) {
        musicVolume = volume;
    }

    @Override
    public int mouseSensitivity() {
        return mouseSensitivity;
    }

    @Override
    public void setMouseSensitivity(int sensitivity) {
        mouseSensitivity = sensitivity;
    }

    @Override
    public int screenBlocks() {
        return screenBlocks;
    }

    @Override
    public void setScreenBlocks(int blocks) {
        screenBlocks = blocks;
    }

    @Override
    public boolean showMessages() {
        return showMessages;
    }

    @Override
    public void setShowMessages(boolean on) {
        showMessages = on;
    }

    @Override
    public int detailLevel() {
        return detailLevel;
    }

    @Override
    public void setDetailLevel(int level) {
        detailLevel = level;
    }

    @Override
    public int gamma() {
        return gamma;
    }

    @Override
    public void setGamma(int level) {
        gamma = level;
    }

    @Override
    public List<SettingsPage> extendedPages() {
        return pages;
    }

    public void setExtendedPages(List<SettingsPage> pages) {
        this.pages = List.copyOf(pages);
    }

    @Override
    public FullscreenHud fullscreenHud() {
        return fullscreenHud;
    }

    public void setFullscreenHud(FullscreenHud hud) {
        this.fullscreenHud = hud;
    }

    @Override
    public HudAlign hudAlign() {
        return hudAlign;
    }

    public void setHudAlign(HudAlign align) {
        this.hudAlign = align;
    }
}
