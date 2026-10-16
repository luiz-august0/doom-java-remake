package doom.engine;

import doom.game.view.GameView;
import doom.render.FrameBuffer;
import doom.render.canvas.Canvas2D;
import doom.ui.MappedLines;
import doom.ui.ViewWindow;

/**
 * The 3D view as the engine sees it. The software renderer ({@code doom.render.SceneRenderer}) is wired in through
 * {@link RenderAdapter}; {@link #NONE} leaves the view window black (tests).
 */
public interface WorldRenderer {

    /**
     * Renders the player's view into the view window of {@code frame}.
     *
     * @param canvas      the 2D canvas over {@code frame}, to convert the virtual {@code window} to pixels
     * @param window      view window in virtual 320x200 coordinates
     * @param alpha       fraction between the previous tic (0) and the current one (1)
     * @param cameraTurn  BAM angle to add to the display player's angle for the per-frame mouse turn (0 when not
     *                    applicable: demos, other players' views)
     * @param paletteIndex the PLAYPAL palette in use this frame (the renderer may need it for fuzz and fixed colormaps)
     */
    void render(FrameBuffer frame, Canvas2D canvas, GameView view, ViewWindow window, double alpha, int cameraTurn,
                int paletteIndex);

    /** Which lines the renderer has drawn ({@code ML_MAPPED}), for the automap. */
    default MappedLines mappedLines() {
        return MappedLines.NONE;
    }

    /** Called when the frame size changed. */
    default void resized(int width, int height) {
    }

    /** A level was loaded: forget what the player has seen of the previous one. */
    default void levelLoaded(int lineCount) {
    }

    /** Composes textures and loads sprites ahead of the first frame. */
    default void precache() {
    }

    /** Renders nothing: the view window is filled with colour 0. */
    WorldRenderer NONE = (frame, canvas, view, window, alpha, cameraTurn, paletteIndex) ->
            canvas.fillRect(window.x(), window.y(), window.width(), window.height(), 0);
}
