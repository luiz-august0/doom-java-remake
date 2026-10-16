package doom.engine;

import doom.game.view.ActorView;
import doom.game.view.DefsView;
import doom.game.view.GameView;
import doom.game.view.PlayerView;
import doom.render.Camera;
import doom.render.FrameBuffer;
import doom.render.Interpolation;
import doom.render.RenderOptions;
import doom.render.SceneRenderer;
import doom.render.canvas.Canvas2D;
import doom.ui.MappedLines;
import doom.ui.ViewWindow;
import doom.wad.LumpDirectory;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Connects the software 3D renderer to the engine: keeps its options in step with the frame size and the settings,
 * builds the (interpolated) camera of the display player every frame, and keeps the automap's "seen lines".
 *
 * <p>Interpolation (spec 04 section 7): position and eye height of the displayed player are interpolated between the
 * previous and the current tic with the shortest-path rule for angles. The console player's own view adds the
 * mouse turn not yet seen by a tic, so turning never lags by more than a tic.
 */
final class RenderAdapter implements WorldRenderer {

    private final SceneRenderer renderer;
    private final IntSupplier screenBlocks;
    private final IntSupplier detail;
    private final BooleanSupplier vanilla;
    private final BooleanSupplier precisionFixes;
    private final BooleanSupplier squarePixels;
    private final Interpolation.World iWorld = new Interpolation.World();
    private final Interpolation.Actors iActors = new Interpolation.Actors();
    private final Interpolation.Player iPlayer = new Interpolation.Player();
    private final Camera camera = new Camera();
    private RenderOptions options;
    private int width;
    private int height;

    RenderAdapter(LumpDirectory lumps, DefsView defs, int width, int height, BooleanSupplier vanilla,
                  IntSupplier screenBlocks, IntSupplier detail, BooleanSupplier precisionFixes,
                  BooleanSupplier squarePixels) {
        this.vanilla = vanilla;
        this.precisionFixes = precisionFixes;
        this.squarePixels = squarePixels;
        this.screenBlocks = screenBlocks;
        this.detail = detail;
        this.width = width;
        this.height = height;
        this.options = options();
        this.renderer = new SceneRenderer(lumps, defs, options);
    }

    private RenderOptions options() {
        int blocks = screenBlocks.getAsInt();
        int det = detail.getAsInt();
        if (vanilla.getAsBoolean() && width == 320 && height == 200) {
            return new RenderOptions(320, 200, true, false, det, blocks, RenderOptions.FuzzStyle.PIXEL);
        }
        return RenderOptions.of(width, height).withScreenBlocks(blocks).withDetail(det)
                .withPrecisionFixes(precisionFixes.getAsBoolean()).withSquarePixels(squarePixels.getAsBoolean());
    }

    @Override
    public void resized(int w, int h) {
        this.width = w;
        this.height = h;
    }

    @Override
    public void precache() {
        try {
            renderer.precache();
        } catch (RuntimeException e) {
            // frames then load what they need on demand
            System.getLogger(RenderAdapter.class.getName()).log(System.Logger.Level.WARNING,
                    "texture precache failed: " + e.getMessage());
        }
    }

    @Override
    public void levelLoaded(int lineCount) {
        renderer.resetMapped(lineCount);
    }

    @Override
    public MappedLines mappedLines() {
        return renderer::lineMapped;
    }

    @Override
    public void render(FrameBuffer frame, Canvas2D canvas, GameView view, ViewWindow window, double alpha,
                       int cameraTurn, int paletteIndex) {
        RenderOptions wanted = options();
        if (frame.width() != wanted.width() || frame.height() != wanted.height()) {
            wanted = wanted.vanilla() ? wanted : wanted.withSize(frame.width(), frame.height());
        }
        if (!wanted.equals(options)) {
            options = wanted;
            renderer.setOptions(options);
        }

        int dp = view.displayPlayer();
        PlayerView player = view.player(dp);
        int fa = Interpolation.fixedAlpha(alpha);
        iWorld.set(view.world(), fa);
        iActors.set(view.actors(), fa);
        iPlayer.set(player, fa);
        int actor = player.actor();
        int x;
        int y;
        int angle;
        if (actor >= 0) {
            x = iActors.x(actor);
            y = iActors.y(actor);
            angle = iActors.angle(actor) + cameraTurn;
        } else {
            x = player.x();
            y = player.y();
            angle = player.angle();
        }
        // the eye height is not snapped by the simulation when the body teleports: a big step is a jump, not motion
        int z = Interpolation.lerpLimited(player.prevViewZ(), player.viewZ(), fa, Interpolation.MAX_SECTOR_STEP);
        camera.set(x, y, z, angle, player.extraLight(), player.fixedColormap(), actor);
        renderer.render(frame, iWorld, iActors, iPlayer, camera);
    }
}
