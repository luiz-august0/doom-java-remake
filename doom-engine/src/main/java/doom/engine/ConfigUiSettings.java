package doom.engine;

import doom.engine.config.Action;
import doom.engine.config.Config;
import doom.engine.config.InputNames;
import doom.engine.config.KeyBindings;
import doom.engine.config.Setting;
import doom.engine.config.Settings;
import doom.ui.SettingsItem;
import doom.ui.SettingsPage;
import doom.ui.UiSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@link UiSettings} on top of the configuration file: the menus read and write {@link Config} directly, and the
 * {@link Applier} makes the engine act on a change (volumes, view size, gamma, window).
 */
final class ConfigUiSettings implements UiSettings {

    /** What the engine does when a setting changes. */
    interface Applier {
        void sfxVolumeChanged(int level);

        void musicVolumeChanged(int level);

        void screenBlocksChanged(int blocks);

        void gammaChanged(int level);

        void videoChanged();

        void inputChanged();

        void configChanged();
    }

    private final Config config;
    private final Applier applier;
    private final boolean vanilla;
    private final KeyBindings bindings;
    private final String versionInfo;
    private final List<SettingsPage> pages;

    ConfigUiSettings(Config config, Applier applier, boolean vanilla, KeyBindings bindings, String versionInfo) {
        this.config = config;
        this.applier = applier;
        this.vanilla = vanilla;
        this.bindings = bindings;
        this.versionInfo = versionInfo;
        this.pages = vanilla ? List.of() : List.of(videoPage(), controlsPage(), audioPage(), compatibilityPage());
    }

    // ------------------------------------------------------------------ the original options

    @Override
    public int sfxVolume() {
        return config.get(Settings.SOUND_SFX_VOLUME);
    }

    @Override
    public void setSfxVolume(int volume) {
        config.set(Settings.SOUND_SFX_VOLUME, clamp(volume, 0, 15));
        applier.sfxVolumeChanged(sfxVolume());
        applier.configChanged();
    }

    @Override
    public int musicVolume() {
        return config.get(Settings.SOUND_MUSIC_VOLUME);
    }

    @Override
    public void setMusicVolume(int volume) {
        config.set(Settings.SOUND_MUSIC_VOLUME, clamp(volume, 0, 15));
        applier.musicVolumeChanged(musicVolume());
        applier.configChanged();
    }

    @Override
    public int mouseSensitivity() {
        return clamp(config.get(Settings.INPUT_MOUSE_SENS_X), 0, 9);
    }

    @Override
    public void setMouseSensitivity(int sensitivity) {
        int s = clamp(sensitivity, 0, 9);
        config.set(Settings.INPUT_MOUSE_SENS_X, s);
        config.set(Settings.INPUT_MOUSE_SENS_Y, s);
        applier.inputChanged();
        applier.configChanged();
    }

    @Override
    public int screenBlocks() {
        return config.get(Settings.GAME_SCREEN_BLOCKS);
    }

    @Override
    public void setScreenBlocks(int blocks) {
        config.set(Settings.GAME_SCREEN_BLOCKS, clamp(blocks, 3, 11));
        applier.screenBlocksChanged(screenBlocks());
        applier.configChanged();
    }

    @Override
    public boolean showMessages() {
        return config.get(Settings.GAME_SHOW_MESSAGES);
    }

    @Override
    public void setShowMessages(boolean on) {
        config.set(Settings.GAME_SHOW_MESSAGES, on);
        applier.configChanged();
    }

    @Override
    public int detailLevel() {
        return config.get(Settings.GAME_DETAIL);
    }

    @Override
    public void setDetailLevel(int level) {
        config.set(Settings.GAME_DETAIL, clamp(level, 0, 1));
        applier.videoChanged();
        applier.configChanged();
    }

    @Override
    public int gamma() {
        return config.get(Settings.GAME_GAMMA);
    }

    @Override
    public void setGamma(int level) {
        config.set(Settings.GAME_GAMMA, clamp(level, 0, 4));
        applier.gammaChanged(gamma());
        applier.configChanged();
    }

    @Override
    public FullscreenHud fullscreenHud() {
        return config.get(Settings.HUD_FULLSCREEN).equals("minimal") ? FullscreenHud.MINIMAL : FullscreenHud.OFF;
    }

    @Override
    public List<SettingsPage> extendedPages() {
        return pages;
    }

    // ------------------------------------------------------------------ extended pages

    private SettingsPage videoPage() {
        List<SettingsItem> items = new ArrayList<>();
        items.add(choice("Window mode", Settings.VIDEO_MODE, "windowed", "borderless", "fullscreen"));
        items.add(toggle("Vertical sync", Settings.VIDEO_VSYNC, true));
        items.add(toggle("Interpolation", Settings.VIDEO_INTERPOLATION, false));
        items.add(toggle("Aspect correction", Settings.VIDEO_ASPECT_CORRECTION, true));
        items.add(choice("Scale filter", Settings.VIDEO_SCALE_FILTER, "nearest", "sharp-bilinear"));
        items.add(choice("Scale mode", Settings.VIDEO_SCALE_MODE, "fit", "integer", "stretch"));
        items.add(new SettingsItem.Slider("Frame limit", 0, 300, 10,
                () -> config.get(Settings.VIDEO_MAX_FPS),
                v -> {
                    config.set(Settings.VIDEO_MAX_FPS, v);
                    applier.videoChanged();
                    applier.configChanged();
                }, v -> v == 0 ? "off" : Integer.toString(v)));
        items.add(new SettingsItem.Slider("Render scale", 25, 200, 25,
                () -> (int) Math.round(config.get(Settings.VIDEO_RENDER_SCALE) * 100),
                v -> {
                    config.set(Settings.VIDEO_RENDER_SCALE, v / 100.0);
                    applier.videoChanged();
                    applier.configChanged();
                }, v -> v + "%"));
        items.add(choice("Fullscreen hud", Settings.HUD_FULLSCREEN, "off", "minimal"));
        return new SettingsPage("Video", List.copyOf(items));
    }

    private SettingsPage controlsPage() {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem.Toggle("Always run", () -> config.get(Settings.INPUT_ALWAYS_RUN), v -> {
            config.set(Settings.INPUT_ALWAYS_RUN, v);
            applier.inputChanged();
            applier.configChanged();
        }));
        items.add(sensitivity("Mouse x", Settings.INPUT_MOUSE_SENS_X));
        items.add(sensitivity("Mouse y", Settings.INPUT_MOUSE_SENS_Y));
        items.add(choice("Mouse vertical", Settings.INPUT_MOUSE_VERTICAL, "move", "off"));
        for (Action a : new Action[] {Action.FORWARD, Action.BACK, Action.TURN_LEFT, Action.TURN_RIGHT,
            Action.STRAFE_LEFT, Action.STRAFE_RIGHT, Action.FIRE, Action.USE, Action.RUN, Action.STRAFE}) {
            items.add(new SettingsItem.Info(a.description().split(" \\(")[0], () -> bound(a)));
        }
        return new SettingsPage("Controls", List.copyOf(items));
    }

    private SettingsPage audioPage() {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem.Slider("Channels", 1, 32, 1, () -> config.get(Settings.SOUND_CHANNELS), v -> {
            config.set(Settings.SOUND_CHANNELS, v);
            applier.configChanged();
        }, null));
        items.add(choice("Resampler", Settings.SOUND_RESAMPLER, "nearest", "linear"));
        items.add(choice("Music chip", Settings.SOUND_OPL_MODE, "opl2", "opl3"));
        items.add(new SettingsItem.Toggle("Pitch variation", () -> config.get(Settings.SOUND_PITCH_VARIATION), v -> {
            config.set(Settings.SOUND_PITCH_VARIATION, v);
            applier.configChanged();
        }));
        items.add(new SettingsItem.Info("Applies after restart", () -> ""));
        return new SettingsPage("Audio", List.copyOf(items));
    }

    private SettingsPage compatibilityPage() {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem.Toggle("Vanilla mode", () -> config.get(Settings.RENDER_VANILLA_MODE), v -> {
            config.set(Settings.RENDER_VANILLA_MODE, v);
            applier.configChanged();
        }));
        items.add(new SettingsItem.Info("Executable", () -> versionInfo));
        items.add(new SettingsItem.Info("Applies after restart", () -> ""));
        return new SettingsPage("Compatibility", List.copyOf(items));
    }

    private String bound(Action action) {
        StringBuilder sb = new StringBuilder();
        for (int id : bindings.inputs(action)) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(InputNames.name(id));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private SettingsItem sensitivity(String label, Setting<Integer> setting) {
        return new SettingsItem.Slider(label, 0, 20, 1, () -> config.get(setting), v -> {
            config.set(setting, v);
            applier.inputChanged();
            applier.configChanged();
        }, null);
    }

    private SettingsItem toggle(String label, Setting<Boolean> setting, boolean video) {
        return new SettingsItem.Toggle(label, () -> config.get(setting), v -> {
            config.set(setting, v);
            if (video) {
                applier.videoChanged();
            }
            applier.configChanged();
        });
    }

    private SettingsItem choice(String label, Setting<String> setting, String... options) {
        List<String> list = List.of(options);
        return new SettingsItem.Choice(label, list, () -> Math.max(0, list.indexOf(config.get(setting))), i -> {
            config.set(setting, list.get(i));
            applier.videoChanged();
            applier.inputChanged();
            applier.configChanged();
        });
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
