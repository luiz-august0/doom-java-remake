/**
 * The engine (spec 02 §5–§7): owns the session, renderer, UI and sound system, runs the main loop, builds ticcmds from
 * input, and handles configuration and the command line. Platform backends are discovered with ServiceLoader.
 */
module doom.engine {
    requires transitive doom.math;
    requires transitive doom.wad;
    requires transitive doom.game;
    requires transitive doom.platform;
    requires transitive doom.render;
    requires transitive doom.sound;
    requires transitive doom.ui;

    exports doom.engine;
    exports doom.engine.config;
    exports doom.engine.cli;

    uses doom.platform.PlatformProvider;
    uses doom.platform.AudioProvider;
}
