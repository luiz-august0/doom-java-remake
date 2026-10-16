package doom.engine;

import doom.engine.cli.LaunchOptions;
import doom.engine.config.Config;
import doom.engine.config.DataDirs;
import doom.game.api.GameSession;
import doom.game.api.GameSetup;
import doom.platform.AudioSink;
import doom.platform.Clock;
import doom.platform.InputSource;
import doom.platform.VideoSink;
import java.io.PrintStream;
import java.util.function.Function;

/**
 * Everything {@link DoomEngine} is built from. Only the options are required; every other part has a default (the
 * discovered platform backends, the system clock, the real session). Tests replace the parts they need.
 */
public final class EngineParams {

    /** The window, input and audio output backends of a run. */
    public record Platform(VideoSink video, InputSource input, AudioSink audio) {
    }

    final LaunchOptions options;
    Config config = Config.defaults();
    DataDirs dirs = DataDirs.system();
    GameSources sources;
    Clock clock = Clock.system();
    Platform platform;
    WorldRenderer renderer;
    ScreenWipe wipe;
    Function<GameSetup, GameSession> sessionFactory;
    PrintStream out = System.out;
    boolean saveConfigOnExit;

    public EngineParams(LaunchOptions options) {
        this.options = options;
    }

    /** The configuration in effect (defaults when not set). */
    public EngineParams config(Config config) {
        this.config = config;
        return this;
    }

    public EngineParams dirs(DataDirs dirs) {
        this.dirs = dirs;
        return this;
    }

    /** Already loaded game data; otherwise the WADs named by the options are found and opened. */
    public EngineParams sources(GameSources sources) {
        this.sources = sources;
        return this;
    }

    public EngineParams clock(Clock clock) {
        this.clock = clock;
        return this;
    }

    /** Backends to use; otherwise they are discovered with {@link java.util.ServiceLoader} (not when headless). */
    public EngineParams platform(Platform platform) {
        this.platform = platform;
        return this;
    }

    public EngineParams renderer(WorldRenderer renderer) {
        this.renderer = renderer;
        return this;
    }

    public EngineParams wipe(ScreenWipe wipe) {
        this.wipe = wipe;
        return this;
    }

    /** Replaces {@code GameFactory.createSession} (tests with a fake session). */
    public EngineParams sessionFactory(Function<GameSetup, GameSession> factory) {
        this.sessionFactory = factory;
        return this;
    }

    /** Where {@code -timedemo} results and other console messages go. */
    public EngineParams out(PrintStream out) {
        this.out = out;
        return this;
    }

    /** Write the configuration file when the engine closes (the application does; tests do not). */
    public EngineParams saveConfigOnExit(boolean save) {
        this.saveConfigOnExit = save;
        return this;
    }
}
