/**
 * Application entry point: logging setup, command line, engine start-up. Backends are found through ServiceLoader.
 */
module doom.app {
    requires doom.engine;

    uses doom.platform.PlatformProvider;
    uses doom.platform.AudioProvider;

    provides java.lang.System.LoggerFinder with doom.app.ConsoleLoggerFinder;
}
