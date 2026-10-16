package doom.platform;

/**
 * Window and presentation settings.
 *
 * @param title       window title
 * @param mode        windowed, borderless or full screen
 * @param width       requested window width in screen coordinates (full-screen modes may ignore it)
 * @param height      requested window height in screen coordinates
 * @param vsync       wait for vertical sync when presenting
 * @param maxFps      frame limiter when vsync is off; 0 = unlimited
 * @param scaleMode   how the frame is fitted to the output
 * @param filter      scaling filter
 * @param pixelAspect height/width ratio of one internal pixel when displayed: 1.2 for the original 5:6 pixels
 *                    (aspect correction on), 1.0 for square pixels (ADR-010)
 */
public record VideoConfig(String title, WindowMode mode, int width, int height, boolean vsync, int maxFps,
                          ScaleMode scaleMode, ScaleFilter filter, double pixelAspect) {
}
