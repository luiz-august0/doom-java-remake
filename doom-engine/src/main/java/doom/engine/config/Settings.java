package doom.engine.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue of the known configuration keys (spec 07 section 4), grouped in sections for the generated default file.
 * Unknown keys are tolerated by {@link Config} (with a warning) and kept when the file is written back.
 */
public final class Settings {

    // ---------------------------------------------------------------- video

    public static final Setting<String> VIDEO_MODE = Setting.choice("video.mode", "windowed",
            "Window mode", "windowed", "borderless", "fullscreen");
    public static final Setting<Integer> VIDEO_WIDTH = Setting.integer("video.width", 1280, 160, 16384,
            "Window width in screen coordinates");
    public static final Setting<Integer> VIDEO_HEIGHT = Setting.integer("video.height", 960, 100, 16384,
            "Window height in screen coordinates");
    public static final Setting<Boolean> VIDEO_ASPECT_CORRECTION = Setting.bool("video.aspectCorrection", true,
            "Stretch pixels to the original 5:6 shape (4:3 picture at 320x200)");
    public static final Setting<Double> VIDEO_RENDER_SCALE = Setting.decimal("video.renderScale", 1.0, 0.25, 2.0,
            "Internal resolution factor");
    public static final Setting<Boolean> VIDEO_VSYNC = Setting.bool("video.vsync", true, "Wait for vertical sync");
    public static final Setting<Integer> VIDEO_MAX_FPS = Setting.integer("video.maxFps", 0, 0, 1000,
            "Frame limiter without vsync, 0 = unlimited");
    public static final Setting<Boolean> VIDEO_INTERPOLATION = Setting.bool("video.interpolation", true,
            "Draw frames between tics (uncapped frame rate)");
    public static final Setting<String> VIDEO_SCALE_FILTER = Setting.choice("video.scaleFilter", "sharp-bilinear",
            "Filter used to scale the frame", "nearest", "sharp-bilinear");
    public static final Setting<String> VIDEO_SCALE_MODE = Setting.choice("video.scaleMode", "fit",
            "How the frame is fitted to the window", "fit", "integer", "stretch");
    public static final Setting<Boolean> RENDER_PRECISION_FIXES = Setting.bool("render.precisionFixes", true,
            "High resolution precision fixes");
    public static final Setting<Boolean> RENDER_VANILLA_MODE = Setting.bool("render.vanillaMode", false,
            "Vanilla presentation: 320x200, 4:3, no interpolation, no corrections");
    public static final Setting<String> HUD_FULLSCREEN = Setting.choice("hud.fullscreenHud", "off",
            "Minimal heads-up display with the status bar hidden", "off", "minimal");
    public static final Setting<Integer> GAME_SCREEN_BLOCKS = Setting.integer("game.screenBlocks", 10, 3, 11,
            "Screen size (vanilla screenblocks)");
    public static final Setting<Integer> GAME_DETAIL = Setting.integer("game.detail", 0, 0, 1,
            "Graphic detail (0 high, 1 low)");
    public static final Setting<Integer> GAME_GAMMA = Setting.integer("game.gamma", 0, 0, 4,
            "Gamma correction level");
    public static final Setting<Boolean> GAME_SHOW_MESSAGES = Setting.bool("game.showMessages", true,
            "Show pick-up and status messages");

    // ---------------------------------------------------------------- audio

    public static final Setting<Integer> SOUND_SFX_VOLUME = Setting.integer("sound.sfxVolume", 8, 0, 15,
            "Sound effects volume");
    public static final Setting<Integer> SOUND_MUSIC_VOLUME = Setting.integer("sound.musicVolume", 8, 0, 15,
            "Music volume");
    public static final Setting<Integer> SOUND_CHANNELS = Setting.integer("sound.channels", 8, 1, 32,
            "Simultaneous sound effects");
    public static final Setting<String> SOUND_OPL_MODE = Setting.choice("sound.oplMode", "opl2",
            "Music synthesis: 9 voices like DOS DMX, or 18 voices", "opl2", "opl3");
    public static final Setting<String> SOUND_RESAMPLER = Setting.choice("sound.resampler", "linear",
            "Sound effect resampling", "nearest", "linear");
    public static final Setting<Integer> SOUND_SAMPLE_RATE = Setting.integer("sound.sampleRate", 48000, 8000, 192000,
            "Output sample rate in Hz");
    public static final Setting<Boolean> SOUND_PITCH_VARIATION = Setting.bool("sound.pitchVariation", false,
            "Random pitch variation of effects (not in DOOM v1.9)");
    public static final Setting<String> SOUND_DEVICE = Setting.text("sound.device", "",
            "Output device name, empty for the system default");
    public static final Setting<Integer> SOUND_DEVICE_BUFFER = Setting.integer("sound.deviceBufferFrames", 0, 0, 16384,
            "Device buffer in frames, 0 = two mixer blocks");

    // ---------------------------------------------------------------- input

    public static final Setting<Integer> INPUT_MOUSE_SENS_X = Setting.integer("input.mouseSensitivityX", 5, 0, 100,
            "Horizontal mouse sensitivity (vanilla scale, 5 = unscaled)");
    public static final Setting<Integer> INPUT_MOUSE_SENS_Y = Setting.integer("input.mouseSensitivityY", 5, 0, 100,
            "Vertical mouse sensitivity (vanilla scale, 5 = unscaled)");
    public static final Setting<String> INPUT_MOUSE_VERTICAL = Setting.choice("input.mouseVertical", "move",
            "What vertical mouse movement does", "move", "off");
    public static final Setting<Boolean> INPUT_ALWAYS_RUN = Setting.bool("input.alwaysRun", false,
            "Run by default; the run key walks");
    public static final Setting<Boolean> INPUT_MOUSE_CAPTURE = Setting.bool("input.mouseCapture", true,
            "Capture the mouse while playing a level");
    public static final Setting<Double> PAD_DEADZONE = Setting.decimal("pad.deadzone", 0.2, 0.0, 0.9,
            "Gamepad stick dead zone");
    public static final Setting<Integer> PAD_MOVE_SENS = Setting.integer("pad.moveSensitivity", 10, 0, 40,
            "Gamepad movement sensitivity (10 = full speed at full deflection)");
    public static final Setting<Integer> PAD_TURN_SENS = Setting.integer("pad.turnSensitivity", 10, 0, 40,
            "Gamepad turning sensitivity");

    // ---------------------------------------------------------------- misc

    public static final Setting<String> LOG_LEVEL = Setting.choice("log.level", "info",
            "Minimum severity written to the console and log file", "off", "error", "warning", "info", "debug", "trace");

    private static final Map<String, List<Setting<?>>> SECTIONS = new LinkedHashMap<>();
    private static final Map<String, Setting<?>> BY_KEY = new LinkedHashMap<>();

    static {
        section("Video", VIDEO_MODE, VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_ASPECT_CORRECTION, VIDEO_RENDER_SCALE,
                VIDEO_VSYNC, VIDEO_MAX_FPS, VIDEO_INTERPOLATION, VIDEO_SCALE_FILTER, VIDEO_SCALE_MODE,
                RENDER_PRECISION_FIXES, RENDER_VANILLA_MODE, HUD_FULLSCREEN);
        section("Game", GAME_SCREEN_BLOCKS, GAME_DETAIL, GAME_GAMMA, GAME_SHOW_MESSAGES);
        section("Audio", SOUND_SFX_VOLUME, SOUND_MUSIC_VOLUME, SOUND_CHANNELS, SOUND_OPL_MODE, SOUND_RESAMPLER,
                SOUND_SAMPLE_RATE, SOUND_PITCH_VARIATION, SOUND_DEVICE, SOUND_DEVICE_BUFFER);
        section("Controls", INPUT_MOUSE_SENS_X, INPUT_MOUSE_SENS_Y, INPUT_MOUSE_VERTICAL, INPUT_ALWAYS_RUN,
                INPUT_MOUSE_CAPTURE, PAD_DEADZONE, PAD_MOVE_SENS, PAD_TURN_SENS);
        List<Setting<?>> keys = new ArrayList<>();
        for (Action a : Action.values()) {
            keys.add(binding(a));
        }
        section("Key bindings (comma separated list of keys, MOUSEn, WHEELUP/WHEELDOWN, PAD_x)",
                keys.toArray(new Setting<?>[0]));
        section("Logging", LOG_LEVEL);
    }

    private Settings() {
    }

    private static void section(String title, Setting<?>... settings) {
        List<Setting<?>> list = new ArrayList<>(List.of(settings));
        SECTIONS.put(title, List.copyOf(list));
        for (Setting<?> s : settings) {
            BY_KEY.put(s.key(), s);
        }
    }

    /** The setting that holds the bindings of an action. */
    public static Setting<String> binding(Action action) {
        Setting<?> existing = BY_KEY.get(action.configKey());
        if (existing != null) {
            @SuppressWarnings("unchecked")
            Setting<String> s = (Setting<String>) existing;
            return s;
        }
        return Setting.text(action.configKey(), action.defaultInputs(), action.description());
    }

    /** The settings by section title, in file order. */
    public static Map<String, List<Setting<?>>> sections() {
        return java.util.Collections.unmodifiableMap(SECTIONS);
    }

    /** The setting for a key, or {@code null} when the key is not known. */
    public static Setting<?> find(String key) {
        return BY_KEY.get(key);
    }
}
