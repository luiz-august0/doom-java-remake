package doom.it.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.game.api.DemoPlayback;
import doom.game.api.GameFactory;
import doom.game.api.GameState;
import doom.game.api.Skill;
import doom.game.view.ActorView;
import doom.game.view.DefsView;
import doom.game.view.FinaleView;
import doom.game.view.GameView;
import doom.game.view.IntermissionView;
import doom.game.view.PlayerView;
import doom.game.view.WorldView;
import doom.render.FrameBuffer;
import doom.render.canvas.Canvas2D;
import doom.render.image.PngWriter;
import doom.ui.MemoryUiSettings;
import doom.ui.SaveSlots;
import doom.ui.SettingsItem;
import doom.ui.SettingsPage;
import doom.ui.Ui;
import doom.ui.UiActions;
import doom.ui.UiAssets;
import doom.ui.UiKeys;
import doom.ui.ViewWindow;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.gfx.Palettes;
import doom.wad.iwad.IwadIdentifier;
import doom.wad.iwad.IwadInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Renders every UI screen with fake views over a real IWAD into {@code target/ui-it/*.png} (320×200 and 2560×900) and
 * checks that the pictures contain what they should.
 */
class UiRenderIT {

    private static final int[] SIZES_W = {320, 2560};
    private static final int[] SIZES_H = {200, 900};

    private LumpDirectory lumps;
    private IwadInfo info;
    private byte[] palette;
    private DefsView defs;
    private Path outDir;

    /** A fake game over the loaded IWAD. */
    private final class Scene {
        final Fake<GameView> game = Fake.of(GameView.class);
        final Fake<PlayerView> player = Fake.of(PlayerView.class);
        final Fake<ActorView> actors = Fake.of(ActorView.class);
        final Fake<IntermissionView> inter = Fake.of(IntermissionView.class);
        final Fake<FinaleView> finale = Fake.of(FinaleView.class);
        WorldView world = Fake.of(WorldView.class).get();
        final MemoryUiSettings settings = new MemoryUiSettings();
        final List<String> events = new ArrayList<>();
        final String[] saves = new String[SaveSlots.COUNT];

        Scene() {
            game.set("mode", info.mode()).set("version", info.version()).set("mission", info.mission())
                .set("variant", info.variant()).set("state", GameState.LEVEL).set("userGame", true)
                .set("skill", Skill.MEDIUM).set("episode", 1).set("map", 1).set("defs", defs)
                .set("actors", actors.get()).set("intermission", inter.get()).set("finale", finale.get())
                .set("pageName", "TITLEPIC").set("demoPlaybackMode", (DemoPlayback) null)
                .set("player", (Function<Object[], Object>) a -> player.get())
                .set("playerInGame", (Function<Object[], Object>) a -> (Integer) a[0] == 0);
            game.set("world", (Function<Object[], Object>) a -> world);
        }

        Ui ui() {
            return Ui.builder(new UiAssets(lumps), game.get()).settings(settings).saves(slot -> saves[slot])
                .miscRandom(new java.util.concurrent.atomic.AtomicInteger()::incrementAndGet)
                .actions(new UiActions() {
                    @Override
                    public void newGame(Skill skill, int episode, int map) {
                        events.add("new");
                    }

                    @Override
                    public void loadGame(int slot) {
                        events.add("load");
                    }

                    @Override
                    public void saveGame(int slot, String description) {
                        events.add("save " + description);
                    }

                    @Override
                    public void endGame() {
                        events.add("end");
                    }

                    @Override
                    public void quit() {
                        events.add("quit");
                    }
                }).build();
        }
    }

    private void open(String wad) throws IOException {
        String dir = System.getenv("DOOM_IWAD_DIR");
        assumeTrue(dir != null && !dir.isEmpty(), "DOOM_IWAD_DIR not set");
        Path p = Path.of(dir, wad);
        assumeTrue(Files.isRegularFile(p), wad + " not found");
        lumps = LumpDirectory.open(List.of(p));
        info = IwadIdentifier.identify(WadFile.open(p));
        palette = Palettes.read(lumps).toArray(0);
        defs = GameFactory.loadDefs(lumps, info, List.of());
        outDir = Path.of("target", "ui-it");
        Files.createDirectories(outDir);
    }

    private void write(FrameBuffer fb, String name) throws IOException {
        PngWriter.writeIndexed(outDir.resolve(name + "-" + fb.width() + "x" + fb.height() + ".png"), fb, palette);
    }

    private static int count(FrameBuffer fb, int colour) {
        int n = 0;
        for (int y = 0; y < fb.height(); y++) {
            for (int x = 0; x < fb.width(); x++) {
                if (fb.get(x, y) == colour) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int distinct(FrameBuffer fb) {
        boolean[] seen = new boolean[256];
        int n = 0;
        for (int y = 0; y < fb.height(); y += 1) {
            for (int x = 0; x < fb.width(); x += 1) {
                int c = fb.get(x, y);
                if (!seen[c]) {
                    seen[c] = true;
                    n++;
                }
            }
        }
        return n;
    }

    /** Renders a scene on both sizes; the prepared view is filled with a placeholder 3D view first. */
    private void render(String name, Ui ui, Scene sc, boolean fakeView) throws IOException {
        for (int i = 0; i < SIZES_W.length; i++) {
            FrameBuffer fb = new FrameBuffer(SIZES_W[i], SIZES_H[i]);
            Canvas2D canvas = new Canvas2D(fb);
            if (fakeView) {
                canvas.clear(0);
                ViewWindow vw = ui.viewWindow();
                canvas.fillRect(vw.x(), vw.y(), vw.width(), vw.height(), 100);
            }
            ui.draw(canvas, 1f);
            write(fb, name);
            assertThat(distinct(fb)).as(name + " " + fb).isGreaterThan(1);
        }
    }

    @Test
    void menusOfDoomOne() throws Exception {
        open("doom1.wad");
        Scene sc = new Scene();
        Ui ui = sc.ui();
        // the game behind the menu: a flat grey view
        ui.keyDown(UiKeys.ESCAPE);
        for (int i = 0; i < 12; i++) {
            ui.tick();
        }
        render("menu-main", ui, sc, true);

        ui.keyDown(UiKeys.ENTER); // episodes
        render("menu-episode", ui, sc, true);
        ui.keyDown(UiKeys.ENTER); // skill
        render("menu-skill", ui, sc, true);
        ui.keyDown('n');
        ui.keyDown(UiKeys.ENTER); // nightmare confirmation
        render("menu-nightmare", ui, sc, true);
        ui.keyDown('n');

        ui.keyDown(UiKeys.ESCAPE);
        ui.keyDown('o');
        ui.keyDown(UiKeys.ENTER);
        sc.settings.setScreenBlocks(8);
        sc.settings.setMouseSensitivity(3);
        render("menu-options", ui, sc, true);

        ui.keyDown(UiKeys.BACKSPACE);
        ui.keyDown(UiKeys.ESCAPE);
        sc.saves[0] = "E1M1 AT THE START";
        sc.saves[2] = "BEFORE THE BARONS";
        ui.keyDown(UiKeys.F2);
        ui.keyDown(UiKeys.DOWNARROW);
        ui.keyDown(UiKeys.DOWNARROW);
        ui.keyDown(UiKeys.DOWNARROW);
        ui.keyDown(UiKeys.ENTER);
        for (char c : "my new save".toCharArray()) {
            ui.charTyped(c);
        }
        render("menu-save", ui, sc, true);
        ui.keyDown(UiKeys.ESCAPE);
        ui.keyDown(UiKeys.ESCAPE);

        ui.keyDown(UiKeys.F3);
        render("menu-load", ui, sc, true);
        ui.keyDown(UiKeys.ESCAPE);

        ui.keyDown(UiKeys.F4);
        render("menu-sound", ui, sc, true);
        ui.keyDown(UiKeys.ESCAPE);

        ui.keyDown(UiKeys.F1);
        render("menu-readthis", ui, sc, true);
        ui.keyDown(UiKeys.ESCAPE);

        sc.game.set("gametic", 12);
        ui.keyDown(UiKeys.F10);
        FrameBuffer fb = new FrameBuffer(320, 200);
        Canvas2D canvas = new Canvas2D(fb);
        ui.draw(canvas, 1f);
        assertThat(distinct(fb)).isGreaterThan(2);
        render("menu-quit", ui, sc, true);
        ui.keyDown('n');
        assertThat(sc.events).isEmpty();
    }

    @Test
    void extendedMenuPages() throws Exception {
        open("doom1.wad");
        Scene sc = new Scene();
        boolean[] on = {true, false};
        int[] v = {2, 144};
        sc.settings.setExtendedPages(List.of(
            new SettingsPage("Video", List.of(
                new SettingsItem.Choice("Window mode", List.of("Windowed", "Borderless", "Fullscreen"), () -> v[0],
                    x -> v[0] = x),
                new SettingsItem.Toggle("Aspect correction", () -> on[0], x -> on[0] = x),
                new SettingsItem.Toggle("VSync", () -> on[1], x -> on[1] = x),
                new SettingsItem.Slider("Max FPS", 0, 360, 24, () -> v[1], x -> v[1] = x, null),
                new SettingsItem.Info("Resolution", () -> "2560X900"),
                new SettingsItem.Action("Apply", () -> "", () -> {
                }))),
            new SettingsPage("Controls", List.of(
                new SettingsItem.Action("Forward", () -> "W", () -> {
                }),
                new SettingsItem.Action("Back", () -> "S", () -> {
                })))));
        Ui ui = sc.ui();
        ui.keyDown(UiKeys.ESCAPE);
        ui.keyDown('o');
        ui.keyDown(UiKeys.ENTER);
        ui.keyDown('x');
        render("menu-options-ext", ui, sc, true);
        ui.keyDown(UiKeys.ENTER);
        render("menu-ext-list", ui, sc, true);
        ui.keyDown(UiKeys.ENTER);
        ui.keyDown(UiKeys.DOWNARROW);
        render("menu-ext-video", ui, sc, true);
    }

    @Test
    void statusBarAndHud() throws Exception {
        open("doom1.wad");
        Scene sc = new Scene();
        int[] ammo = {50, 12, 100, 20};
        int[] max = {400, 100, 600, 100};
        sc.player.set("health", 75).set("armorPoints", 50).set("armorType", 1).set("readyWeapon", 2)
            .setInt("weaponOwned", w -> w == 1 || w == 2 || w == 3 || w == 5)
            .setInt("ammo", a -> ammo[a]).setInt("maxAmmo", a -> max[a])
            .setInt("card", c -> c == 0 || c == 5).set("backpack", true).set("health", 75);
        sc.game.set("consolePlayer", 0);
        Ui ui = sc.ui();
        ui.levelLoaded();
        for (int i = 0; i < 40; i++) {
            ui.levelTick();
        }
        ui.playerMessage(0, "Picked up a shotgun.");
        ui.levelTick();
        render("statusbar-view10", ui, sc, true);

        sc.settings.setScreenBlocks(8); // border around a smaller view
        render("statusbar-view8", ui, sc, true);
        sc.settings.setScreenBlocks(10);

        sc.game.set("paused", true);
        render("statusbar-paused", ui, sc, true);
        sc.game.set("paused", false);

        sc.settings.setScreenBlocks(11);
        render("fullscreen-vanilla", ui, sc, true);
        sc.settings.setFullscreenHud(doom.ui.UiSettings.FullscreenHud.MINIMAL);
        render("fullscreen-minimal-hud", ui, sc, true);
        sc.settings.setHudAlign(doom.ui.UiSettings.HudAlign.CENTRE);
        render("fullscreen-minimal-hud-centre", ui, sc, true);
        sc.settings.setScreenBlocks(10);

        // death
        sc.player.set("health", 0);
        ui.levelTick();
        render("statusbar-dead", ui, sc, true);

        // pixel checks at 320x200: the bar shows STBAR and numbers
        FrameBuffer fb = new FrameBuffer(320, 200);
        Canvas2D canvas = new Canvas2D(fb);
        sc.player.set("health", 75);
        ui.draw(canvas, 1f);
        assertThat(distinct(fb)).isGreaterThan(12);
    }

    @Test
    void automapOfE1M1() throws Exception {
        open("doom1.wad");
        Scene sc = new Scene();
        sc.world = GameFactory.loadLevelGeometry(lumps, info, "E1M1");
        sc.game.set("mission", info.mission());
        sc.player.set("x", 1056 << 16).set("y", -3616 << 16).set("angle", 0x40000000).set("health", 100)
            .set("actor", -1);
        Ui ui = Ui.builder(new UiAssets(lumps), sc.game.get()).settings(sc.settings).mappedLines(l -> l % 3 != 0)
            .build();
        ui.levelLoaded();
        ui.keyDown(UiKeys.TAB);
        ui.levelTick();
        ui.keyDown('g');
        ui.keyDown('m');
        ui.levelTick();
        render("automap-e1m1", ui, sc, false);
        ui.keyDown('f');
        ui.keyDown(UiKeys.EQUALS);
        for (int i = 0; i < 20; i++) {
            ui.levelTick();
        }
        ui.keyUp(UiKeys.EQUALS);
        for (char c : "iddt".toCharArray()) {
            ui.keyDown(c);
        }
        render("automap-e1m1-zoom-iddt", ui, sc, false);
        FrameBuffer fb = new FrameBuffer(1280, 450);
        ui.draw(new Canvas2D(fb), 1f);
        assertThat(count(fb, 176) + count(fb, 64) + count(fb, 231)).isGreaterThan(1000);
    }

    @Test
    void intermissionOfEpisodeOne() throws Exception {
        open("doom1.wad");
        Scene sc = new Scene();
        sc.game.set("state", GameState.INTERMISSION);
        sc.inter.set("stage", 0).set("episode", 0).set("lastMap", 0).set("nextMap", 1).set("me", 0)
            .set("countKills", 75).set("countItems", 100).set("countSecret", 50).set("countTime", 125)
            .set("countPar", 30).set("animCount", 10).set("pointerOn", true)
            .setInt("animFrame", i -> i % 3).setInt("playerPresent", p -> p == 0);
        Ui ui = sc.ui();
        render("intermission-stats", ui, sc, false);
        sc.inter.set("stage", 1);
        render("intermission-nextloc", ui, sc, false);
        sc.inter.set("lastMap", 8).set("nextMap", 3).set("didSecret", true);
        render("intermission-nextloc-secret", ui, sc, false);
    }

    @Test
    void finaleScreensAndTitlePages() throws Exception {
        open("doom1.wad");
        Scene sc = new Scene();
        sc.game.set("state", GameState.FINALE).set("episode", 1);
        sc.finale.set("stage", 0).set("count", 700).set("text", defs.text("E1TEXT")).set("flat", "FLOOR4_8");
        Ui ui = sc.ui();
        render("finale-text", ui, sc, false);
        sc.game.set("state", GameState.DEMOSCREEN);
        for (String page : new String[] {"TITLEPIC", "CREDIT", "HELP2"}) {
            sc.game.set("pageName", page);
            render("page-" + page.toLowerCase(), ui, sc, false);
        }
    }

    @Test
    void bunnyScrollAndArtScreens() throws Exception {
        open("freedoom1.wad");
        Scene sc = new Scene();
        sc.game.set("state", GameState.FINALE).set("episode", 1);
        sc.finale.set("stage", 0).set("count", 700).set("text", defs.text("E1TEXT")).set("flat", "FLOOR4_8");
        Ui ui = sc.ui();
        sc.finale.set("stage", 1);
        sc.game.set("episode", 3);
        sc.finale.set("count", 600);
        render("finale-bunny-scroll", ui, sc, false);
        sc.finale.set("count", 1190);
        render("finale-bunny-end", ui, sc, false);
        sc.game.set("episode", 2);
        render("finale-victory2", ui, sc, false);

    }

    @Test
    void castCallOfDoomTwo() throws Exception {
        open("freedoom2.wad");
        Scene sc = new Scene();
        int troo = -1;
        for (int i = 0; i < defs.spriteCount(); i++) {
            if (defs.spriteName(i).equals("TROO")) {
                troo = i;
            }
        }
        sc.game.set("state", GameState.FINALE).set("episode", 1).set("map", 30);
        sc.finale.set("stage", 2).set("count", 100).set("castName", defs.text("CC_IMP")).set("castSprite", troo)
            .set("castFrame", 1);
        Ui ui = sc.ui();
        render("finale-cast", ui, sc, false);
        sc.finale.set("stage", 0).set("count", 900).set("text", defs.text("C1TEXT")).set("flat", "SLIME16");
        sc.game.set("map", 6);
        render("finale-text-doom2", ui, sc, false);
    }

    @Test
    void doomTwoStatusBarAndMenu() throws Exception {
        open("freedoom2.wad");
        Scene sc = new Scene();
        sc.player.set("health", 40).set("armorPoints", 120).set("readyWeapon", 3)
            .setInt("ammo", a -> 33).setInt("maxAmmo", a -> 200).setInt("weaponOwned", w -> w < 4);
        Ui ui = sc.ui();
        ui.levelLoaded();
        ui.levelTick();
        render("d2-statusbar", ui, sc, true);
        ui.keyDown(UiKeys.ESCAPE);
        render("d2-menu-main", ui, sc, true);
    }
}
