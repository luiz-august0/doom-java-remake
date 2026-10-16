/**
 * Platform SPI (ADR-004): the interfaces a backend implements to give the game a window, input, audio output
 * and a clock. Backends are discovered with {@link java.util.ServiceLoader} through
 * {@link doom.platform.PlatformProvider} and {@link doom.platform.AudioProvider}.
 */
module doom.platform {
    exports doom.platform;
    exports doom.platform.input;
}
