package doom.render;

import doom.game.view.ActorView;
import doom.game.view.DefsView;
import doom.game.view.PlayerView;
import doom.game.view.WorldView;
import doom.render.scene.Renderer;
import doom.wad.LumpDirectory;

/**
 * The 3D view renderer (spec 04 §1–§5): draws the player's view of a level into the 3D window of a {@link FrameBuffer}
 * of any size. At 320x200 with {@link RenderOptions#vanillaMode()} the view window is pixel-identical to Chocolate Doom's.
 *
 * <p>Usage: create once per IWAD/PWAD set ({@link #SceneRenderer(LumpDirectory, DefsView, RenderOptions)}), then every
 * frame fill a {@link Camera} and call {@link #render}. The frame must have the size of the {@link RenderOptions}.
 * The 2D layer (status bar, border, HUD, menu) is drawn by the caller; this class only touches the view window
 * ({@link #viewWindowX()} …), whose position follows the original {@code R_ExecuteSetViewSize} rules.
 *
 * <p>Interpolation: the caller passes already interpolated camera and views (spec 04 §7).
 * Not thread-safe; allocation free per frame after warm-up ({@link #precache()}).
 */
public final class SceneRenderer {

    private final Renderer impl;

    public SceneRenderer(LumpDirectory lumps, DefsView defs, RenderOptions options) {
        this.impl = new Renderer(lumps, defs, options);
    }

    /** Draws the view. Sector, wall and actor state come from the views; psprites and powers from {@code player}. */
    public void render(FrameBuffer frame, WorldView world, ActorView actors, PlayerView player, Camera camera) {
        impl.render(frame, world, actors, player, camera);
    }

    /** Changes size, detail level, view size or mode. */
    public void setOptions(RenderOptions options) {
        impl.setOptions(options);
    }

    public RenderOptions options() {
        return impl.options();
    }

    /** Composes all textures and loads all flats and sprites now (virtual threads), so frames never pay for it. */
    public void precache() {
        impl.precache();
    }

    /** Left edge of the 3D view window in the framebuffer ({@code viewwindowx}). */
    public int viewWindowX() {
        return impl.viewWindowX();
    }

    /** Top edge of the 3D view window ({@code viewwindowy}). */
    public int viewWindowY() {
        return impl.viewWindowY();
    }

    /** Width of the 3D view window in pixels ({@code scaledviewwidth}). */
    public int viewWindowWidth() {
        return impl.viewWindowWidth();
    }

    /** Height of the 3D view window in pixels ({@code viewheight}). */
    public int viewWindowHeight() {
        return impl.viewWindowHeight();
    }

    /** Position in the spectre fuzz table; persists from frame to frame. */
    public int fuzzPos() {
        return impl.fuzzPos();
    }

    public void setFuzzPos(int pos) {
        impl.setFuzzPos(pos);
    }

    /** Whether line {@code line} has been seen so far (the automap's {@code ML_MAPPED} state, kept by the renderer). */
    public boolean lineMapped(int line) {
        return impl.lineMapped(line);
    }

    /** Forgets the lines seen so far; call when a level starts. */
    public void resetMapped(int lineCount) {
        impl.resetMapped(lineCount);
    }
}
