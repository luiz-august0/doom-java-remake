package doom.ui;

import doom.game.api.DemoPlayback;
import doom.game.api.GameState;
import doom.game.api.MiscRandom;
import doom.game.view.GameView;
import doom.game.view.SoundId;
import doom.render.canvas.Canvas2D;
import doom.ui.automap.Automap;
import doom.ui.hud.Hud;
import doom.ui.hud.MinimalHud;
import doom.ui.hud.StatusBar;
import doom.ui.menu.MenuSystem;
import doom.ui.screens.DebugOverlay;
import doom.ui.screens.FinaleScreen;
import doom.ui.screens.IntermissionScreen;
import doom.ui.widget.PatchWidget;
import doom.wad.gfx.Flat;
import doom.wad.gfx.Picture;
import java.util.function.IntPredicate;

/**
 * The UI facade the engine drives. It draws every non-3D layer of the game from the frozen views and handles the
 * menu, automap and cheat input, in the original dispatch order. The 3D view itself is drawn by the engine
 * (renderer) <em>before</em> {@link #draw}, and only when {@link #wantsWorldView()} is true.
 *
 * <h2>Engine integration</h2>
 * <ul>
 *   <li>Build once with {@link #builder}; pass the {@code GameView} of the session and the engine-side interfaces
 *       {@link UiSounds}, {@link UiActions}, {@link UiSettings}, {@link SaveSlots}, {@link MappedLines}.</li>
 *   <li>Forward input events: {@link #keyDown}, {@link #keyUp}, {@link #charTyped}, {@link #mouseMove},
 *       {@link #mouseButton}, {@link #padButton}. A {@code true} result means the UI consumed the event; otherwise
 *       the engine processes it as a game input.</li>
 *   <li>Once per tic, before {@code GameSession.tick}: {@link #tick()}. Pass {@link #menuActive()} as the
 *       {@code menuActive} argument of {@code GameSession.tick}.</li>
 *   <li>Forward the {@code SimEvents} callbacks: {@link #levelTick()} (exactly once per {@code levelTick}: it consumes
 *       the misc RNG), {@link #levelLoaded()}, {@link #playerMessage}, {@link #intermissionStarted()},
 *       {@link #finaleStarted()}.</li>
 *   <li>Every frame: if {@link #wantsWorldView()} render the 3D view in the window of {@link #viewWindow()}
 *       (scaled by the canvas), set the palette {@link #paletteIndex()}, then call {@link #draw}.</li>
 * </ul>
 * Not thread-safe: main thread only.
 */
public final class Ui {

    private final UiAssets assets;
    private final GameView view;
    private final UiSounds sounds;
    private final UiActions actions;
    private final UiSettings settings;
    private final UiConfig config;

    private final Hud hud;
    private final StatusBar statusBar;
    private final MinimalHud minimalHud;
    private final Automap automap;
    private final MenuSystem menu;
    private final IntermissionScreen intermission;
    private final FinaleScreen finale;
    private final DebugOverlay debugOverlay;
    private final DebugInfo debugInfo = new DebugInfo();
    private boolean debugOverlayOn;
    private boolean borderChecked;
    private Picture[] bezel;

    private Ui(Builder b) {
        this.assets = b.assets;
        this.view = b.view;
        this.sounds = b.sounds;
        this.actions = b.actions;
        this.settings = b.settings;
        this.config = b.config;
        this.hud = new Hud(assets, view, settings);
        this.statusBar = new StatusBar(assets, view, b.rnd, config);
        this.statusBar.setAttackDown(b.attackDown);
        this.minimalHud = new MinimalHud(assets, view);
        this.automap = new Automap(assets, view, hud);
        this.automap.setMappedLines(b.mapped);
        this.automap.setLineScale(config.automapLineScale());
        this.menu = new MenuSystem(assets, view, sounds, actions, settings, b.saves, config, hud, automap::active);
        this.intermission = new IntermissionScreen(assets, view);
        this.finale = new FinaleScreen(assets, view, sounds);
        this.debugOverlay = new DebugOverlay(assets);
    }

    /** Starts building a UI for {@code view}. */
    public static Builder builder(UiAssets assets, GameView view) {
        return new Builder(assets, view);
    }

    /** Builder with the engine-side collaborators; every one has a harmless default. */
    public static final class Builder {
        private final UiAssets assets;
        private final GameView view;
        private UiSounds sounds = UiSounds.SILENT;
        private UiActions actions = UiActions.NONE;
        private UiSettings settings = new MemoryUiSettings();
        private SaveSlots saves = SaveSlots.EMPTY;
        private MappedLines mapped = MappedLines.NONE;
        private UiConfig config = UiConfig.defaults();
        private MiscRandom rnd = () -> 0;
        private IntPredicate attackDown = p -> false;

        private Builder(UiAssets assets, GameView view) {
            this.assets = assets;
            this.view = view;
        }

        public Builder sounds(UiSounds sounds) {
            this.sounds = sounds;
            return this;
        }

        public Builder actions(UiActions actions) {
            this.actions = actions;
            return this;
        }

        public Builder settings(UiSettings settings) {
            this.settings = settings;
            return this;
        }

        public Builder saves(SaveSlots saves) {
            this.saves = saves;
            return this;
        }

        public Builder mappedLines(MappedLines mapped) {
            this.mapped = mapped;
            return this;
        }

        public Builder config(UiConfig config) {
            this.config = config;
            return this;
        }

        /** The session's misc RNG ({@code GameSession.miscRandom()}); the status bar face draws from it once per level tic. */
        public Builder miscRandom(MiscRandom rnd) {
            this.rnd = rnd;
            return this;
        }

        /** {@code player->attackdown} per player number, until {@code PlayerView} exposes it (face "rampage" look). */
        public Builder attackDown(IntPredicate attackDown) {
            this.attackDown = attackDown;
            return this;
        }

        public Ui build() {
            return new Ui(this);
        }
    }

    // ------------------------------------------------------------------ state queries

    /** Whether the menu (or a message box) is open: pass it to {@code GameSession.tick}. */
    public boolean menuActive() {
        return menu.active();
    }

    public boolean automapActive() {
        return automap.active();
    }

    /**
     * Whether the engine must render the 3D view this frame: in a level, with the automap closed and no full-screen
     * help page open.
     */
    public boolean wantsWorldView() {
        return view.state() == GameState.LEVEL && !automap.active() && !menu.fullScreenPage();
    }

    /** The PLAYPAL palette to use this frame ({@code ST_doPaletteStuff}); 0 outside levels. */
    public int paletteIndex() {
        return view.state() == GameState.LEVEL ? statusBar.paletteIndex() : 0;
    }

    /** The 3D view window in virtual 320×200 coordinates for the current view size. */
    public ViewWindow viewWindow() {
        return ViewWindow.of(settings.screenBlocks());
    }

    /** Internal component (tests and tools of this module). */
    MenuSystem menu() {
        return menu;
    }

    /** Internal component (tests and tools of this module). */
    Automap automap() {
        return automap;
    }

    /** Internal component (tests and tools of this module). */
    Hud hud() {
        return hud;
    }

    /** Internal component (tests and tools of this module). */
    StatusBar statusBar() {
        return statusBar;
    }

    public void startControlPanel() {
        menu.startControlPanel();
    }

    /** Debug overlay switch (F12, configurable by the engine). */
    public void setDebugOverlay(boolean on) {
        debugOverlayOn = on && !config.vanillaMode();
    }

    public void toggleDebugOverlay() {
        setDebugOverlay(!debugOverlayOn);
    }

    public boolean debugOverlay() {
        return debugOverlayOn;
    }

    /** The values the overlay shows; the engine updates them every frame. */
    public DebugInfo debugInfo() {
        return debugInfo;
    }

    // ------------------------------------------------------------------ SimEvents callbacks

    /** {@code SimEvents.levelTick}: {@code ST_Ticker} (one misc RNG call), {@code AM_Ticker}, {@code HU_Ticker}, in that order. */
    public void levelTick() {
        statusBar.tick();
        automap.tick();
        hud.tick();
    }

    /** {@code SimEvents.levelLoaded}: {@code ST_Start}, {@code HU_Start}; the automap is switched off as {@code G_InitNew} does. */
    public void levelLoaded() {
        statusBar.start();
        hud.start();
        automap.deactivate();
    }

    /** {@code SimEvents.playerMessage}: only the console player's HUD shows it. */
    public void playerMessage(int player, String text) {
        if (player == view.consolePlayer()) {
            hud.post(text, false);
        }
    }

    /** {@code SimEvents.intermissionStarted}: {@code G_DoCompleted} stops the automap. */
    public void intermissionStarted() {
        if (automap.active()) {
            automap.stop();
        }
    }

    /** {@code SimEvents.finaleStarted}: {@code automapactive = false}. */
    public void finaleStarted() {
        automap.deactivate();
    }

    // ------------------------------------------------------------------ time

    /** One tic of UI time ({@code M_Ticker} and the bunny scroll shots); call before {@code GameSession.tick}. */
    public void tick() {
        menu.tick();
        finale.tick();
    }

    // ------------------------------------------------------------------ input

    private boolean singleDemo() {
        DemoPlayback m = view.demoPlaybackMode();
        return view.demoPlayback() && m != DemoPlayback.TITLE;
    }

    /**
     * A key was pressed (auto-repeat repeats it). Printable keys use their lower-case ASCII code, the rest the
     * numbering of {@link UiKeys}. Dispatch order of the original: menu, then (in levels) cheats and the automap;
     * while a title demo or page runs, any key opens the menu.
     *
     * @return whether the UI consumed the event
     */
    public boolean keyDown(int code) {
        if (menu.respond(code)) {
            return true;
        }
        GameState st = view.state();
        if (!singleDemo() && (view.demoPlayback() || st == GameState.DEMOSCREEN)) {
            menu.startControlPanel();
            return true;
        }
        if (st == GameState.LEVEL) {
            if (code >= 32 && code < 127) {
                actions.cheatKey(code);
            }
            if (automap.keyDown(code)) {
                return true;
            }
        }
        if (st == GameState.FINALE && view.finale().stage() == 2) {
            actions.finaleKey();
            return true;
        }
        return false;
    }

    /** A key was released. */
    public boolean keyUp(int code) {
        if (view.state() == GameState.LEVEL) {
            return automap.keyUp(code);
        }
        return false;
    }

    /** Typed text (a Unicode code point), used by the save-name editor. */
    public boolean charTyped(int codePoint) {
        return menu.typed(codePoint);
    }

    /** Relative mouse motion in raw counts (positive y = down). Only the open menu uses it. */
    public boolean mouseMove(double dx, double dy) {
        return menu.mouseMove((int) Math.round(dx), (int) Math.round(dy));
    }

    /** Mouse button (0 left, 1 right, 2 middle). Left and right drive the menu; any button opens it from a title demo. */
    public boolean mouseButton(int button, boolean down) {
        if (!down) {
            return false;
        }
        if (menu.active()) {
            return menu.mouseButton(button);
        }
        if (!singleDemo() && (view.demoPlayback() || view.state() == GameState.DEMOSCREEN)) {
            menu.startControlPanel();
            return true;
        }
        return false;
    }

    /**
     * Gamepad button press using the SDL/GLFW gamepad numbering (A 0, B 1, START 7, D-pad up/right/down/left 11..14).
     * Behaves like the original joystick emulation of the menu keys.
     */
    public boolean padButton(int button) {
        if (!menu.active()) {
            if (button == 7) {
                return keyDown(UiKeys.ESCAPE);
            }
            return !singleDemo() && (view.demoPlayback() || view.state() == GameState.DEMOSCREEN)
                && keyDown(UiKeys.ENTER);
        }
        int key;
        switch (button) {
            case 0 -> key = menu.messageNeedsInput() ? 'y' : UiKeys.ENTER;
            case 1 -> key = menu.messageNeedsInput() ? 'n' : menu.textEntryActive() ? UiKeys.ESCAPE : UiKeys.BACKSPACE;
            case 7 -> key = UiKeys.ESCAPE;
            case 11 -> key = UiKeys.UPARROW;
            case 12 -> key = UiKeys.RIGHTARROW;
            case 13 -> key = UiKeys.DOWNARROW;
            case 14 -> key = UiKeys.LEFTARROW;
            default -> {
                return false;
            }
        }
        return menu.respond(key);
    }

    /** The window's close button: asks "quit?", and confirms on a second request. */
    public void requestQuit() {
        menu.requestQuit();
    }

    // ------------------------------------------------------------------ drawing

    /** Clears the frame for a full-screen page, including the pillarbox bars. */
    private void clearPage(Canvas2D canvas) {
        if (config.sideFill() == UiConfig.SideFill.FLAT) {
            Flat flat = statusBar.borderFlat();
            if (flat != null) {
                canvas.clear(0);
                canvas.tileSides(flat);
                return;
            }
        }
        canvas.clear(0);
    }

    private void drawPage(Canvas2D canvas, String name) {
        clearPage(canvas);
        Picture p = assets.patch(name);
        if (p != null) {
            canvas.drawPatchCentred(p, 0);
        }
    }

    /**
     * Draws all UI layers for the current game state on top of the 3D view (already rendered by the engine in a level).
     *
     * @param alpha fraction between the previous tic (0) and the current one (1), for the automap
     */
    public void draw(Canvas2D canvas, float alpha) {
        if (menu.fullScreenPage()) {
            // Read This! pages replace everything, bars included
            clearPage(canvas);
            menu.draw(canvas);
            if (debugOverlayOn) {
                debugOverlay.draw(canvas, debugInfo);
            }
            return;
        }
        switch (view.state()) {
            case LEVEL -> drawLevel(canvas, alpha);
            case INTERMISSION -> {
                clearPage(canvas);
                intermission.draw(canvas);
            }
            case FINALE -> {
                clearPage(canvas);
                finale.draw(canvas);
            }
            case DEMOSCREEN -> drawPage(canvas, view.pageName());
            default -> {
            }
        }
        menu.draw(canvas);
        if (debugOverlayOn) {
            debugOverlay.draw(canvas, debugInfo);
        }
    }

    private void drawLevel(Canvas2D canvas, float alpha) {
        ViewWindow vw = viewWindow();
        boolean automapOn = automap.active();
        if (automapOn) {
            automap.draw(canvas, alpha);
            hud.drawTitle(canvas, canvas.virtualX(0) + 2);
        } else if (!vw.fullScreen()) {
            drawViewBorder(canvas, vw);
        }
        if (!vw.fullScreen() || automapOn) {
            statusBar.draw(canvas);
        } else if (!config.vanillaMode() && settings.fullscreenHud() == UiSettings.FullscreenHud.MINIMAL) {
            minimalHud.draw(canvas, settings.hudAlign());
        }
        hud.drawMessage(canvas);
        if (view.paused()) {
            int y = automapOn ? 4 : vw.y() + 4;
            PatchWidget.draw(canvas, assets, "M_PAUSE", vw.x() + (vw.width() - 68) / 2, y);
        }
    }

    /**
     * {@code R_DrawViewBorder}: the border flat around the view window (and in the pillarbox bars above the status
     * bar) plus the bezel graphics. Drawn after the 3D view; the area of the view window is left untouched.
     */
    public void drawViewBorder(Canvas2D canvas, ViewWindow vw) {
        Flat flat = statusBar.borderFlat();
        if (flat == null || vw.width() >= 320) {
            // R_DrawViewBorder: no border when the view spans the width (screenblocks 10); on a wide frame the 3D
            // view then fills the whole width above the status bar (Hor+)
            return;
        }
        int w = canvas.frame().width();
        int barTop = canvas.physicalY(StatusBar.ST_Y);
        int vx0 = canvas.physicalX(vw.x());
        int vx1 = canvas.physicalX(vw.x() + vw.width());
        int vy0 = canvas.physicalY(vw.y());
        int vy1 = canvas.physicalY(vw.y() + vw.height());
        canvas.tileFlatPixels(flat, 0, 0, w, vy0);
        canvas.tileFlatPixels(flat, 0, vy1, w, barTop);
        canvas.tileFlatPixels(flat, 0, vy0, vx0, vy1);
        canvas.tileFlatPixels(flat, vx1, vy0, w, vy1);
        if (!borderChecked) {
            borderChecked = true;
            bezel = new Picture[] {assets.patch("BRDR_T"), assets.patch("BRDR_B"), assets.patch("BRDR_L"),
                assets.patch("BRDR_R"), assets.patch("BRDR_TL"), assets.patch("BRDR_TR"), assets.patch("BRDR_BL"),
                assets.patch("BRDR_BR")};
        }
        for (int x = 0; x < vw.width(); x += 8) {
            drawBezel(canvas, bezel[0], vw.x() + x, vw.y() - 8);
            drawBezel(canvas, bezel[1], vw.x() + x, vw.y() + vw.height());
        }
        for (int y = 0; y < vw.height(); y += 8) {
            drawBezel(canvas, bezel[2], vw.x() - 8, vw.y() + y);
            drawBezel(canvas, bezel[3], vw.x() + vw.width(), vw.y() + y);
        }
        drawBezel(canvas, bezel[4], vw.x() - 8, vw.y() - 8);
        drawBezel(canvas, bezel[5], vw.x() + vw.width(), vw.y() - 8);
        drawBezel(canvas, bezel[6], vw.x() - 8, vw.y() + vw.height());
        drawBezel(canvas, bezel[7], vw.x() + vw.width(), vw.y() + vw.height());
    }

    private static void drawBezel(Canvas2D canvas, Picture p, int x, int y) {
        if (p != null) {
            canvas.drawPatch(p, x, y);
        }
    }
}
