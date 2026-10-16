package doom.engine;

import doom.engine.EngineParams.Platform;
import doom.engine.cli.LaunchOptions;
import doom.engine.config.Config;
import doom.engine.config.KeyBindings;
import doom.engine.config.Settings;
import doom.engine.input.InputSettings;
import doom.engine.input.LocalTicSource;
import doom.game.api.DemoPlayback;
import doom.game.api.GameFactory;
import doom.game.api.GameParams;
import doom.game.api.GameSession;
import doom.game.api.GameSetup;
import doom.game.api.GameState;
import doom.game.api.Skill;
import doom.game.api.SoundOrigins;
import doom.game.api.TicCmd;
import doom.game.view.DefsView;
import doom.game.view.GameView;
import doom.game.view.MusicId;
import doom.game.view.SoundId;
import doom.math.DoomFatalException;
import doom.platform.AudioConfig;
import doom.platform.AudioProvider;
import doom.platform.AudioSink;
import doom.platform.Clock;
import doom.platform.InputSource;
import doom.platform.PlatformProvider;
import doom.platform.ScaleFilter;
import doom.platform.ScaleMode;
import doom.platform.VideoConfig;
import doom.platform.VideoSink;
import doom.platform.WindowMode;
import doom.platform.input.InputEvent;
import doom.platform.input.InputEventQueue;
import doom.platform.input.KeyCode;
import doom.render.FrameBuffer;
import doom.render.FrameLayout;
import doom.render.canvas.Canvas2D;
import doom.render.image.PngWriter;
import doom.sound.AudioEngineBackend;
import doom.sound.LumpSoundData;
import doom.sound.NullSoundBackend;
import doom.sound.SoundBackend;
import doom.sound.SoundSystem;
import doom.sound.mixer.AudioEngine;
import doom.sound.mixer.AudioEngineConfig;
import doom.ui.Ui;
import doom.ui.UiActions;
import doom.ui.UiAssets;
import doom.ui.UiConfig;
import doom.ui.ViewWindow;
import doom.wad.LumpDirectory;
import doom.wad.audio.GenMidi;
import doom.wad.gfx.Palettes;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.IwadInfo;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.function.Function;

/**
 * Owns the session, the UI, the sound system and the platform backends, and runs the main loop (spec 02 sections 5
 * to 7): fixed 35 Hz game tics driven by a clock, at most {@link #MAX_TICS_PER_FRAME} tics per frame, interpolation
 * alpha for the renderer, the simulation paused while a screen wipe runs, and the menu pausing a single-player game
 * through {@code GameSession.tick(cmds, menuActive)}.
 *
 * <p>Special modes: {@code -timedemo} (one tic and one frame per iteration, no clock, prints tics/time/FPS) and
 * {@code -headless} (no window, no audio output, no drawing; the same logic including the sound system and its
 * misc RNG use, as fast as possible), optionally writing the per-tic trace ({@code -trace}).
 *
 * <p>Single-threaded: everything but the audio thread and the writer threads runs on the calling thread.
 */
public final class DoomEngine implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(DoomEngine.class.getName());

    /** Game tics per second. */
    public static final int TICRATE = 35;
    /** Most tics run for one frame; beyond it the accumulated lag is discarded (no spiral of death). */
    public static final int MAX_TICS_PER_FRAME = 10;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    /** The original waits this long after the quit sound ({@code I_WaitVBL(105)}). */
    private static final long QUIT_DELAY_NANOS = 1_500_000_000L;

    /** Outcome of a {@code -timedemo} run. */
    public record TimedemoResult(int gameTics, long nanos, long frames) {

        /** Real time in tics ({@code realtics}). */
        public long realTics() {
            return nanos * TICRATE / NANOS_PER_SECOND;
        }

        /** Game tics per second of real time, as the original prints it. */
        public double ticsPerSecond() {
            return nanos <= 0 ? 0 : gameTics * (double) NANOS_PER_SECOND / nanos;
        }

        /** Frames drawn per second of real time. */
        public double framesPerSecond() {
            return nanos <= 0 ? 0 : frames * (double) NANOS_PER_SECOND / nanos;
        }
    }

    private final LaunchOptions options;
    private final Config config;
    private final EngineParams params;
    private final Clock clock;
    private final PrintStream out;
    private final boolean headless;
    private final boolean timedemo;

    private final GameSources sources;
    private final LumpDirectory lumps;
    private final IwadInfo iwad;
    private final SaveStore saves;

    private final EngineEvents events = new EngineEvents();
    private final GameSession session;
    private final GameView view;
    private final SoundSystem sound;
    private AudioEngine audioEngine;

    private final TicCmd[] cmds = {new TicCmd(), new TicCmd(), new TicCmd(), new TicCmd()};
    private final InputEventQueue queue = new InputEventQueue();
    private final LocalTicSource localSource;
    private KeyBindings bindings;
    private TraceRecorder trace;

    // presentation (null when headless)
    private final VideoSink video;
    private final InputSource input;
    private final WorldRenderer renderer;
    private final ScreenWipe wipe;
    private final Ui ui;
    private final GammaPalettes palettes;
    private FrameBuffer frame;
    private Canvas2D canvas;

    private boolean videoDirty;
    private boolean interpolation;
    private boolean vanilla;
    private boolean mouseCaptured;
    private boolean focused = true;
    private GameState wipeGameState = GameState.DEMOSCREEN;
    private boolean forceWipe;
    private boolean wiping;
    private long wipeLastNanos;
    private long wipeBeganNanos;

    // loop state
    private boolean quit;
    private long quitAtNanos = Long.MAX_VALUE;
    private long startNanos;
    private int ranTics;
    private long frames;
    private TimedemoResult timedemoResult;
    private long timedemoStartNanos;
    private boolean demoEnded;
    private boolean recording;
    private String recordName;

    // debug overlay measurements
    private long fpsWindowStart;
    private int fpsWindowFrames;
    private long lastFrameNanos;

    /**
     * Builds everything: finds and opens the WADs, creates the session, the sound system, and (unless headless) the
     * window, the UI and the audio output.
     *
     * @throws DoomFatalException for the original fatal errors (no IWAD, unreadable WAD, bad demo ...)
     */
    public DoomEngine(EngineParams p) {
        this.params = p;
        this.options = p.options;
        this.config = p.config;
        this.clock = p.clock;
        this.out = p.out;
        this.headless = options.headless();
        this.timedemo = options.timeDemo() != null;
        this.vanilla = options.vanilla() || config.get(Settings.RENDER_VANILLA_MODE);
        this.interpolation = config.get(Settings.VIDEO_INTERPOLATION) && !vanilla && !timedemo && !headless;

        this.sources = p.sources != null ? p.sources : GameSources.load(options, p.dirs);
        this.lumps = sources.lumps();
        this.iwad = sources.iwad();
        this.saves = new SaveStore(p.dirs.saveDir(iwadId(iwad)));

        // ---- sound
        Platform platform = p.platform;
        if (platform == null && !headless) {
            platform = discoverPlatform(options.noSound());
        }
        SoundSystem.Settings soundSettings = new SoundSystem.Settings(config.get(Settings.SOUND_CHANNELS),
                config.get(Settings.SOUND_SFX_VOLUME), config.get(Settings.SOUND_MUSIC_VOLUME),
                config.get(Settings.SOUND_PITCH_VARIATION));
        SoundBackend backend = createSoundBackend(soundSettings.channels(),
                platform == null ? null : platform.audio());
        LumpSoundData soundData = new LumpSoundData(lumps);
        this.sound = new SoundSystem(backend, soundData, soundSettings);

        // ---- session
        Function<GameSetup, GameSession> factory = p.sessionFactory != null ? p.sessionFactory
                : GameFactory::createSession;
        this.session = factory.apply(new GameSetup(lumps, iwad, iwad.version(), sources.patches(), events,
                options.noDeh()));
        this.view = session.view();
        sound.bind(view, session.miscRandom());
        if (audioEngine != null) {
            precacheSounds(soundData, view.defs());
        }

        // ---- input
        this.bindings = KeyBindings.fromConfig(config);
        this.localSource = new LocalTicSource(view, bindings, InputSettings.from(config));

        // ---- presentation
        if (headless) {
            this.video = null;
            this.input = null;
            this.renderer = null;
            this.wipe = null;
            this.ui = null;
            this.palettes = null;
        } else {
            if (platform == null || platform.video() == null) {
                throw new DoomFatalException("No video backend available. Install a platform backend or use -headless.");
            }
            this.video = platform.video();
            this.palettes = new GammaPalettes(Palettes.read(lumps));
            this.palettes.setGamma(config.get(Settings.GAME_GAMMA));
            this.wipe = p.wipe != null ? p.wipe : new MeltWipe();
            openVideo();
            this.renderer = p.renderer != null ? p.renderer
                    : new RenderAdapter(lumps, view.defs(), frame.width(), frame.height(), () -> vanilla,
                            () -> config.get(Settings.GAME_SCREEN_BLOCKS), () -> config.get(Settings.GAME_DETAIL),
                            () -> config.get(Settings.RENDER_PRECISION_FIXES),
                            () -> !config.get(Settings.VIDEO_ASPECT_CORRECTION));
            renderer.precache();
            this.input = platform.input() != null ? platform.input() : lateInput.createInputSource(video);
            this.ui = buildUi();
        }
        events.bind(sound, ui, new EngineListener());

        // ---- trace
        if (options.trace() != null || options.traceVerbose() != null) {
            String header = TraceRecorder.header(iwad.path().getFileName().toString(), traceDemoName(),
                    iwad.version().cliName());
            this.trace = new TraceRecorder(options.trace() == null ? null : Path.of(options.trace()),
                    options.traceVerbose() == null ? null : Path.of(options.traceVerbose()), header,
                    options.traceFrom(), options.traceTo());
        }
        warnAboutConfig();
    }

    // ================================================================== construction helpers

    private String traceDemoName() {
        String demo = options.playDemo() != null ? options.playDemo() : options.timeDemo();
        if (demo == null) {
            return "none";
        }
        String name = Path.of(demo).getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".lmp")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.toUpperCase(Locale.ROOT);
    }

    private static String iwadId(IwadInfo info) {
        String n = info.path().getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private void warnAboutConfig() {
        for (String w : config.warnings()) {
            LOG.log(System.Logger.Level.WARNING, "config: {0}", w);
        }
        config.warnings().clear();
        for (String w : options.problems()) {
            LOG.log(System.Logger.Level.WARNING, "command line: {0}", w);
        }
    }

    /** The backend whose input source is created once the window is open (it needs the window). */
    private PlatformProvider lateInput;

    private Platform discoverPlatform(boolean noSound) {
        VideoSink video = null;
        InputSource input = null;
        List<PlatformProvider> providers = new ArrayList<>();
        ServiceLoader.load(PlatformProvider.class).forEach(providers::add);
        providers.sort(Comparator.comparingInt(PlatformProvider::priority).reversed());
        for (PlatformProvider pp : providers) {
            if (pp.isSupported()) {
                video = pp.createVideoSink();
                lateInput = pp;
                LOG.log(System.Logger.Level.INFO, "video backend: {0}", pp.name());
                break;
            }
        }
        AudioSink audio = null;
        if (!noSound) {
            audio = discoverAudio();
        }
        return video == null ? null : new Platform(video, input, audio);
    }

    private static AudioSink discoverAudio() {
        List<AudioProvider> providers = new ArrayList<>();
        ServiceLoader.load(AudioProvider.class).forEach(providers::add);
        providers.sort(Comparator.comparingInt(AudioProvider::priority).reversed());
        for (AudioProvider ap : providers) {
            if (ap.isSupported()) {
                LOG.log(System.Logger.Level.INFO, "audio backend: {0}", ap.name());
                return ap.createAudioSink();
            }
        }
        return null;
    }

    private SoundBackend createSoundBackend(int channels, AudioSink sink) {
        if (headless || options.noSound()) {
            return new NullSoundBackend(channels);
        }
        if (sink == null) {
            LOG.log(System.Logger.Level.WARNING, "no audio backend: running without sound");
            return new NullSoundBackend(channels);
        }
        try {
            GenMidi genMidi = null;
            int genLump = lumps.find("GENMIDI");
            if (genLump >= 0 && !options.noMusic()) {
                genMidi = GenMidi.parse(lumps.bytes(genLump));
            }
            String device = config.get(Settings.SOUND_DEVICE);
            AudioEngineConfig cfg = AudioEngineConfig.defaults()
                    .withSampleRate(config.get(Settings.SOUND_SAMPLE_RATE))
                    .withSfxChannels(channels)
                    .withLinearInterpolation(config.get(Settings.SOUND_RESAMPLER).equals("linear"))
                    .withOpl3Mode(config.get(Settings.SOUND_OPL_MODE).equals("opl3"))
                    .withDevice(device.isEmpty() ? null : device, config.get(Settings.SOUND_DEVICE_BUFFER));
            AudioEngine engine = new AudioEngine(cfg, genMidi);
            engine.start(sink);
            audioEngine = engine;
            return new AudioEngineBackend(engine, cfg.blockFrames());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "cannot start audio, running without sound", e);
            try {
                sink.close();
            } catch (RuntimeException ignored) {
                // nothing more to do
            }
            return new NullSoundBackend(channels);
        }
    }

    private static void precacheSounds(LumpSoundData data, DefsView defs) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i < SoundId.COUNT; i++) {
            names.add(defs.sfxName(i));
        }
        data.precache(names);
    }

    private VideoConfig videoConfig() {
        WindowMode mode = switch (config.get(Settings.VIDEO_MODE)) {
            case "borderless" -> WindowMode.BORDERLESS;
            case "fullscreen" -> WindowMode.FULLSCREEN;
            default -> WindowMode.WINDOWED;
        };
        if (options.fullscreen()) {
            mode = WindowMode.FULLSCREEN;
        } else if (options.window()) {
            mode = WindowMode.WINDOWED;
        }
        int width = options.width() > 0 ? options.width() : config.get(Settings.VIDEO_WIDTH);
        int height = options.height() > 0 ? options.height() : config.get(Settings.VIDEO_HEIGHT);
        ScaleMode scale = switch (config.get(Settings.VIDEO_SCALE_MODE)) {
            case "integer" -> ScaleMode.INTEGER;
            case "stretch" -> ScaleMode.STRETCH;
            default -> ScaleMode.FIT;
        };
        ScaleFilter filter = config.get(Settings.VIDEO_SCALE_FILTER).equals("nearest") ? ScaleFilter.NEAREST
                : ScaleFilter.SHARP_BILINEAR;
        double pixelAspect = vanilla || config.get(Settings.VIDEO_ASPECT_CORRECTION) ? 1.2 : 1.0;
        return new VideoConfig("DOOM", mode, width, height, config.get(Settings.VIDEO_VSYNC),
                config.get(Settings.VIDEO_MAX_FPS), scale, filter, pixelAspect);
    }

    private void openVideo() {
        video.open(videoConfig());
        applyFrameSize();
    }

    /** Chooses the internal resolution for the current output size and (re)allocates the frame. */
    private void applyFrameSize() {
        FrameSizing size = FrameSizing.compute(video.outputWidth(), video.outputHeight(),
                vanilla || config.get(Settings.VIDEO_ASPECT_CORRECTION), config.get(Settings.VIDEO_RENDER_SCALE),
                vanilla);
        if (frame == null) {
            frame = new FrameBuffer(size.width(), size.height(), FrameLayout.ROW_MAJOR);
            canvas = new Canvas2D(frame);
        } else {
            frame.resize(size.width(), size.height());
        }
        if (renderer != null) {
            renderer.resized(size.width(), size.height());
        }
        LOG.log(System.Logger.Level.DEBUG, "internal resolution {0}x{1}", size.width(), size.height());
    }

    private Ui buildUi() {
        ConfigUiSettings settings = new ConfigUiSettings(config, new SettingsApplier(), vanilla, bindings,
                iwad.version().cliName());
        return Ui.builder(new UiAssets(lumps), view)
                .sounds(sfx -> sound.startSound(SoundOrigins.NONE, sfx))
                .actions(new MenuActions())
                .settings(settings)
                .saves(saves)
                .mappedLines(renderer.mappedLines())
                .miscRandom(session.miscRandom())
                .config(UiConfig.defaults().withVanillaMode(vanilla))
                .build();
    }

    // ================================================================== callbacks

    /** What the engine does with the events that are neither sound nor UI. */
    private final class EngineListener implements EngineEvents.Listener {
        @Override
        public void forceWipe() {
            forceWipe = true;
        }

        @Override
        public void gameSaved(int slot, String description, byte[] data) {
            saves.writeAsync(slot, description, data);
        }

        @Override
        public void demoFinished(DemoPlayback mode, int gametics) {
            switch (mode) {
                case TIMEDEMO -> {
                    timedemoResult = new TimedemoResult(gametics, clock.nanoTime() - timedemoStartNanos, frames);
                    demoEnded = true;
                    quit = true;
                }
                case SINGLE -> {
                    demoEnded = true;
                    quit = true;
                }
                default -> {
                }
            }
        }

        @Override
        public void intermissionStarted() {
            // WI_Start
            sound.changeMusic(iwad.mode() == GameMode.COMMERCIAL ? MusicId.DM2INT : MusicId.INTER, true);
        }

        @Override
        public void finaleStarted() {
            // F_StartFinale
            sound.changeMusic(iwad.mode() == GameMode.COMMERCIAL ? MusicId.READ_M : MusicId.VICTOR, true);
        }

        @Override
        public void levelLoaded() {
            localSource.releaseAll();
            if (renderer != null) {
                renderer.levelLoaded(view.world().lineCount());
            }
        }
    }

    /** The menu's requests to the game. */
    private final class MenuActions implements UiActions {
        @Override
        public void newGame(Skill skill, int episode, int map) {
            session.newGame(new GameParams(skill, episode, map, options.deathmatch(), options.respawn(),
                    options.fast(), options.noMonsters(), 1, 0));
        }

        @Override
        public void loadGame(int slot) {
            byte[] data = saves.read(slot);
            if (data != null) {
                session.loadGame(data);
            }
        }

        @Override
        public void saveGame(int slot, String description) {
            session.requestSave(slot, description);
        }

        @Override
        public void endGame() {
            session.endGame();
        }

        @Override
        public void quit() {
            quitAtNanos = clock.nanoTime() + (audioEngine != null ? QUIT_DELAY_NANOS : 0);
        }

        @Override
        public void screenshot() {
            takeScreenshot();
        }

        @Override
        public void cheatKey(int ch) {
            session.cheatKey(ch);
        }

        @Override
        public void finaleKey() {
            session.finaleKey();
        }
    }

    /** Reacts to changes made in the options menus. */
    private final class SettingsApplier implements ConfigUiSettings.Applier {
        @Override
        public void sfxVolumeChanged(int level) {
            sound.setSfxVolume(level);
        }

        @Override
        public void musicVolumeChanged(int level) {
            sound.setMusicVolume(level);
        }

        @Override
        public void screenBlocksChanged(int blocks) {
            // the UI and the renderer read the view window from the settings each frame
        }

        @Override
        public void gammaChanged(int level) {
            palettes.setGamma(level);
        }

        @Override
        public void videoChanged() {
            videoDirty = true;
        }

        @Override
        public void inputChanged() {
            localSource.setSettings(InputSettings.from(config));
        }

        @Override
        public void configChanged() {
            // written when the engine closes
        }
    }

    // ================================================================== accessors

    public GameSession session() {
        return session;
    }

    public SoundSystem sound() {
        return sound;
    }

    /** Game tics run so far (calls of {@code GameSession.tick}). */
    public int ranTics() {
        return ranTics;
    }

    /** Frames drawn so far. */
    public long frames() {
        return frames;
    }

    /** The result of a finished {@code -timedemo}, or {@code null}. */
    public TimedemoResult timedemoResult() {
        return timedemoResult;
    }

    /** Whether the run ended because a demo reached its end. */
    public boolean demoEnded() {
        return demoEnded;
    }

    /** Asks the loop to end after the current iteration. */
    public void requestQuit() {
        quit = true;
    }

    // ================================================================== starting the game

    private byte[] demoBytes(String arg) {
        String name = arg;
        Path file = Path.of(name);
        if (!Files.isRegularFile(file) && !name.toLowerCase(Locale.ROOT).endsWith(".lmp")) {
            file = Path.of(name + ".lmp");
        }
        if (Files.isRegularFile(file)) {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                throw new DoomFatalException("Cannot read demo " + file + ": " + e.getMessage());
            }
        }
        String lump = file.getFileName().toString();
        if (lump.toLowerCase(Locale.ROOT).endsWith(".lmp")) {
            lump = lump.substring(0, lump.length() - 4);
        }
        int n = lumps.find(lump.toUpperCase(Locale.ROOT));
        if (n < 0) {
            throw new DoomFatalException("W_GetNumForName: " + lump + " not found!");
        }
        return lumps.bytes(n);
    }

    private GameParams launchParams() {
        boolean commercial = iwad.mode() == GameMode.COMMERCIAL;
        int episode = 1;
        int map = 1;
        if (options.episode() >= 0) {
            episode = options.episode();
        }
        List<Integer> warp = options.warp();
        if (!warp.isEmpty()) {
            if (commercial) {
                map = warp.get(0);
            } else if (warp.size() >= 2) {
                episode = warp.get(0);
                map = warp.get(1);
            } else if (warp.get(0) >= 10) {
                episode = warp.get(0) / 10;
                map = warp.get(0) % 10;
            } else {
                episode = warp.get(0);
            }
        }
        Skill skill = options.skill() >= 0 ? Skill.of(options.skill()) : Skill.MEDIUM;
        return new GameParams(skill, episode, map, options.deathmatch(), options.respawn(), options.fast(),
                options.noMonsters(), 1, 0);
    }

    private boolean autostart() {
        return options.skill() >= 0 || options.episode() >= 0 || !options.warp().isEmpty();
    }

    /** {@code D_DoomMain}'s last part: what to run first. */
    private void startGame() {
        if (options.playDemo() != null) {
            session.playDemo(traceDemoNameOr(options.playDemo()), demoBytes(options.playDemo()), DemoPlayback.SINGLE);
        } else if (options.timeDemo() != null) {
            session.playDemo(traceDemoNameOr(options.timeDemo()), demoBytes(options.timeDemo()),
                    DemoPlayback.TIMEDEMO);
        } else if (options.record() != null) {
            recordName = options.record();
            recording = true;
            localSource.setLowResTurn(true);
            session.recordDemo(recordName, launchParams());
        } else if (options.loadGame() >= 0) {
            byte[] data = saves.read(options.loadGame());
            if (data == null) {
                throw new DoomFatalException("Couldn't read save game in slot " + options.loadGame());
            }
            session.loadGame(data);
        } else if (autostart()) {
            session.newGame(launchParams());
        } else {
            session.startTitle();
        }
    }

    private static String traceDemoNameOr(String arg) {
        String name = Path.of(arg).getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".lmp") ? name.substring(0, name.length() - 4) : name;
    }

    // ================================================================== main loop

    /**
     * Runs until the user quits, the window closes, a {@code -playdemo}/{@code -timedemo} demo ends or {@code -maxtics}
     * is reached.
     *
     * @return the process exit status (0)
     */
    public int run() {
        startGame();
        startNanos = clock.nanoTime();
        timedemoStartNanos = startNanos;
        fpsWindowStart = startNanos;
        boolean virtualTime = headless || timedemo;

        while (!quit) {
            long now = clock.nanoTime();
            if (now >= quitAtNanos) {
                break;
            }
            if (options.maxTics() >= 0 && ranTics >= options.maxTics()) {
                break;
            }
            if (!headless) {
                if (videoDirty) {
                    reconfigureVideo();
                }
                processInput();
                if (video.closeRequested()) {
                    break;
                }
                if (wiping) {
                    wipeFrame(clock.nanoTime());
                    continue;
                }
            }

            double alpha = 1.0;
            if (virtualTime) {
                runTic();
            } else {
                alpha = runRealtimeTics(now);
            }

            sound.updateSounds();
            if (!headless && !quit) {
                display(alpha);
            }
            frames++;
        }
        return 0;
    }

    /** Runs the tics that are due; returns the interpolation alpha for the frame. */
    private double runRealtimeTics(long now) {
        long ticNanos = NANOS_PER_SECOND / TICRATE;
        long target = (now - startNanos) * TICRATE / NANOS_PER_SECOND;
        if (!interpolation && ranTics >= target) {
            // without interpolation a frame per tic is enough: wait for the next tic like the original does
            clock.waitUntil(startNanos + (ranTics + 1) * NANOS_PER_SECOND / TICRATE);
            now = clock.nanoTime();
            target = ranTics + 1;
        }
        int n = 0;
        while (ranTics < target && n < MAX_TICS_PER_FRAME && !quit) {
            runTic();
            n++;
        }
        if (n == MAX_TICS_PER_FRAME) {
            // far behind: forget the lag instead of running ever more tics
            startNanos = clock.nanoTime() - ranTics * ticNanos;
            return 1.0;
        }
        if (!interpolation) {
            return 1.0;
        }
        double alpha = (double) (clock.nanoTime() - startNanos) / ticNanos - ranTics;
        return Math.max(0.0, Math.min(1.0, alpha));
    }

    /** One game tic: UI ticker, then the simulation with the local command. */
    private void runTic() {
        int console = view.consolePlayer();
        for (TicCmd c : cmds) {
            c.clear();
        }
        localSource.next(cmds[console & 3]);
        if (ui != null) {
            ui.tick();
        }
        session.tick(cmds, ui != null && ui.menuActive());
        titleMusic();
        if (trace != null) {
            trace.record(session);
        }
        ranTics++;
    }

    // ================================================================== music the session does not announce

    private String lastPage = "";

    /**
     * {@code D_DoAdvanceDemo}: each time the title page comes up the title song starts ({@code S_StartMusic}). The
     * session only announces level music, so the engine starts the title, intermission and finale songs itself; asking
     * for a song that already plays does nothing, so a session that announces them too is harmless.
     */
    private void titleMusic() {
        if (view.state() != GameState.DEMOSCREEN) {
            lastPage = "";
            return;
        }
        String page = view.pageName();
        if (page != null && !page.equals(lastPage)) {
            lastPage = page;
            if (page.equalsIgnoreCase("TITLEPIC")) {
                sound.startMusic(iwad.mode() == GameMode.COMMERCIAL ? MusicId.DM2TTL : MusicId.INTRO);
            }
        }
    }

    // ================================================================== input

    private void processInput() {
        queue.clear();
        input.poll(queue);
        for (int i = 0; i < queue.size(); i++) {
            dispatch(queue.get(i));
        }
        queue.clear();
        if (localSource.takePauseRequest()) {
            session.requestPause();
        }
        updateMouseCapture();
    }

    private boolean singleDemo() {
        return view.demoPlayback() && view.demoPlaybackMode() != DemoPlayback.TITLE;
    }

    /** Original dispatch order: the UI (menu, cheats, automap) first, then the game. */
    private void dispatch(InputEvent e) {
        boolean consumed = false;
        switch (e.type) {
            case InputEvent.KEY_DOWN -> {
                if (e.code == KeyCode.PRTSCR) {
                    takeScreenshot();
                    return;
                }
                if (e.code == KeyCode.SCRLCK) {
                    ui.toggleDebugOverlay();
                    return;
                }
                consumed = ui.keyDown(e.code);
                if (!consumed && e.code == KeyCode.F12) {
                    if (view.state() == GameState.LEVEL && (singleDemo() || view.deathmatch() == 0)) {
                        session.cycleDisplayPlayer(); // spy mode, allowed during demos
                        return;
                    }
                    ui.toggleDebugOverlay();
                    return;
                }
            }
            case InputEvent.KEY_UP -> ui.keyUp(e.code); // key releases always reach the game
            case InputEvent.CHAR -> consumed = ui.charTyped(e.code);
            case InputEvent.MOUSE_MOVE -> consumed = ui.mouseMove(e.dx, e.dy);
            case InputEvent.MOUSE_DOWN -> consumed = ui.mouseButton(e.code, true);
            case InputEvent.PAD_DOWN -> consumed = ui.padButton(e.code);
            case InputEvent.FOCUS_GAINED -> focused = true;
            case InputEvent.FOCUS_LOST -> focused = false;
            case InputEvent.RESIZED -> videoDirty = true;
            default -> {
            }
        }
        if (!consumed) {
            localSource.onEvent(e);
        }
    }

    private void updateMouseCapture() {
        boolean want = config.get(Settings.INPUT_MOUSE_CAPTURE) && focused && view.state() == GameState.LEVEL
                && !ui.menuActive() && !view.paused() && !view.demoPlayback() && !wiping;
        if (want != mouseCaptured) {
            mouseCaptured = want;
            input.setMouseCaptured(want);
        }
    }

    // ================================================================== display

    private void reconfigureVideo() {
        videoDirty = false;
        vanilla = options.vanilla() || config.get(Settings.RENDER_VANILLA_MODE);
        video.reconfigure(videoConfig());
        applyFrameSize();
    }

    /** {@code D_Display}: draws the frame; starts a wipe when the game state changed. */
    private void display(double alpha) {
        GameState state = view.state();
        boolean startWipe = wipe != ScreenWipe.NONE && (state != wipeGameState || forceWipe);
        if (startWipe) {
            wipe.captureStart(frame);
        }

        int paletteIndex = ui.paletteIndex();
        if (ui.wantsWorldView()) {
            ViewWindow window = ui.viewWindow();
            int turn = 0;
            if (interpolation && !view.demoPlayback() && !view.paused() && !ui.menuActive()
                    && view.displayPlayer() == view.consolePlayer()) {
                turn = localSource.pendingTurn();
            }
            renderer.render(frame, canvas, view, window, alpha, turn, paletteIndex);
        }
        if (ui.debugOverlay()) {
            updateDebugInfo();
        }
        ui.draw(canvas, (float) alpha);

        wipeGameState = state;
        forceWipe = false;
        if (startWipe) {
            wipe.captureEnd(frame);
            wiping = true;
            wipeBeganNanos = clock.nanoTime();
            wipeLastNanos = wipeBeganNanos - NANOS_PER_SECOND / TICRATE;
        } else {
            present(paletteIndex);
        }
    }

    private void present(int paletteIndex) {
        video.setPalette(palettes.get(paletteIndex));
        video.present(frame.pixels(), frame.width(), frame.height(), frame.columnMajor());
    }

    /** One frame of a running wipe: the simulation does not advance. */
    private void wipeFrame(long now) {
        long ticNanos = NANOS_PER_SECOND / TICRATE;
        long tics = (now - wipeLastNanos) / ticNanos;
        if (tics <= 0) {
            clock.waitUntil(wipeLastNanos + ticNanos);
            now = clock.nanoTime();
            tics = Math.max(1, (now - wipeLastNanos) / ticNanos);
        }
        wipeLastNanos = now;
        boolean done = wipe.step(frame, (int) tics, session.miscRandom());
        present(0);
        frames++;
        if (done) {
            wiping = false;
            // the game did not run during the wipe: do not catch up the tics it took
            startNanos += now - wipeBeganNanos;
        }
    }

    private void updateDebugInfo() {
        long now = clock.nanoTime();
        fpsWindowFrames++;
        if (now - fpsWindowStart >= NANOS_PER_SECOND / 2) {
            ui.debugInfo().fps = fpsWindowFrames * (double) NANOS_PER_SECOND / (now - fpsWindowStart);
            fpsWindowStart = now;
            fpsWindowFrames = 0;
        }
        ui.debugInfo().frameMillis = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1e6;
        lastFrameNanos = now;
        ui.debugInfo().tic = ranTics;
        ui.debugInfo().audioUnderruns = audioEngine == null ? 0 : (int) audioEngine.underruns();
        ui.debugInfo().internalWidth = frame.width();
        ui.debugInfo().internalHeight = frame.height();
    }

    private void takeScreenshot() {
        if (frame == null) {
            return;
        }
        byte[] pixels = java.util.Arrays.copyOf(frame.pixels(), frame.width() * frame.height());
        byte[] palette = palettes.get(ui.paletteIndex()).clone();
        int w = frame.width();
        int h = frame.height();
        boolean columnMajor = frame.columnMajor();
        Path dir = params.dirs.screenshotDir();
        Thread.ofVirtual().name("screenshot-writer").start(() -> {
            try {
                Files.createDirectories(dir);
                Path file = null;
                for (int i = 0; i < 10000; i++) {
                    Path candidate = dir.resolve(String.format("doom-java-%04d.png", i));
                    if (!Files.exists(candidate)) {
                        file = candidate;
                        break;
                    }
                }
                if (file != null) {
                    PngWriter.writeIndexed(file, pixels, w, h, columnMajor, palette);
                    LOG.log(System.Logger.Level.INFO, "screenshot saved to {0}", file);
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "cannot write screenshot", e);
            }
        });
    }

    // ================================================================== shutdown

    /** Prints the {@code -timedemo} result like the original ({@code G_CheckDemoStatus}). */
    public void printTimedemoResult() {
        TimedemoResult r = timedemoResult;
        if (r != null) {
            out.printf(Locale.ROOT, "timed %d gametics in %d realtics (%.1f fps)%n", r.gameTics(), r.realTics(),
                    r.ticsPerSecond());
            if (!headless) {
                out.printf(Locale.ROOT, "%d frames drawn (%.1f fps)%n", r.frames(), r.framesPerSecond());
            }
        }
    }

    @Override
    public void close() {
        try {
            if (trace != null) {
                trace.close();
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "cannot finish the trace", e);
        }
        if (recording) {
            byte[] demo = session.finishDemoRecording();
            if (demo != null) {
                String name = recordName.toLowerCase(Locale.ROOT).endsWith(".lmp") ? recordName : recordName + ".lmp";
                try {
                    Files.write(Path.of(name), demo);
                    LOG.log(System.Logger.Level.INFO, "recorded demo {0}", name);
                } catch (IOException e) {
                    LOG.log(System.Logger.Level.ERROR, "cannot write demo " + name, e);
                }
            }
        }
        saves.await();
        if (params.saveConfigOnExit) {
            try {
                config.save(options.config() != null ? Path.of(options.config()) : params.dirs.configFile());
            } catch (IOException e) {
                LOG.log(System.Logger.Level.ERROR, "cannot write the configuration file", e);
            }
        }
        if (audioEngine != null) {
            audioEngine.stop();
        }
        if (video != null) {
            video.close();
        }
    }
}
