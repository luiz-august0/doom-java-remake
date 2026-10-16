package doom.ui.menu;

import doom.game.api.GameState;
import doom.game.api.Skill;
import doom.game.view.GameView;
import doom.game.view.SoundId;
import doom.math.annotation.VanillaRef;
import doom.render.canvas.Canvas2D;
import doom.ui.SaveSlots;
import doom.ui.SettingsItem;
import doom.ui.SettingsPage;
import doom.ui.UiActions;
import doom.ui.UiAssets;
import doom.ui.UiConfig;
import doom.ui.UiKeys;
import doom.ui.UiSettings;
import doom.ui.UiSounds;
import doom.ui.widget.FontText;
import doom.ui.widget.MenuItem;
import doom.ui.widget.MenuList;
import doom.ui.widget.PatchWidget;
import doom.ui.widget.TextEntry;
import doom.wad.gfx.Picture;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * The menu system of {@code m_menu.c}: main, episode, skill, options, sound, load/save, read this, quit, messages and
 * the extended text submenus. Input arrives through {@link #respond}; time through {@link #tick()}; {@link #draw}
 * only draws.
 */
public final class MenuSystem {

    private static final int SKULL_X_OFFSET = -32;
    private static final int SAVE_SLOTS = SaveSlots.COUNT;

    private static final String[] DETAIL_PATCHES = {"M_GDHIGH", "M_GDLOW"};
    private static final String[] MESSAGE_PATCHES = {"M_MSGOFF", "M_MSGON"};
    private static final String[] SKULLS = {"M_SKULL1", "M_SKULL2"};

    private static final int[] QUIT_SOUNDS = {SoundId.PLDETH, SoundId.DMPAIN, SoundId.POPAIN, SoundId.SLOP,
        SoundId.TELEPT, SoundId.POSIT1, SoundId.POSIT3, SoundId.SGTATK};
    private static final int[] QUIT_SOUNDS_2 = {SoundId.VILACT, SoundId.GETPOW, SoundId.BOSCUB, SoundId.SLOP,
        SoundId.SKESWG, SoundId.KNTDTH, SoundId.BSPACT, SoundId.SGTATK};

    private static final String[] DOOM1_END = {
        null,
        "please don't leave, there's more\ndemons to toast!",
        "let's beat it -- this is turning\ninto a bloodbath!",
        "i wouldn't leave if i were you.\ndos is much worse.",
        "you're trying to say you like dos\nbetter than me, right?",
        "don't leave yet -- there's a\ndemon around that corner!",
        "ya know, next time you come in here\ni'm gonna toast ya.",
        "go ahead and leave. see if i care."};
    private static final String[] DOOM2_END = {
        null,
        "you want to quit?\nthen, thou hast lost an eighth!",
        "don't go now, there's a \ndimensional shambler waiting\nat the dos prompt!",
        "get outta here and go back\nto your boring programs.",
        "if i were your boss, i'd \n deathmatch ya in a minute!",
        "look, bud. you leave now\nand you forfeit your body count!",
        "just leave. when you come\nback, i'll be waiting with a bat.",
        "you're lucky i don't smack\nyou for thinking about leaving."};

    private final UiAssets assets;
    private final GameView view;
    private final UiSounds sounds;
    private final UiActions actions;
    private final UiSettings settings;
    private final SaveSlots saves;
    private final UiConfig config;
    private final MessageSink messages;
    private final BooleanSupplier automapActive;
    private final TextEntry entry;

    private final Menu mainDef;
    private final Menu epiDef;
    private final Menu newDef;
    private final Menu optionsDef;
    private final Menu readDef1;
    private final Menu readDef2;
    private final Menu soundDef;
    private final Menu loadDef;
    private final Menu saveDef;
    private Menu extendedDef;

    private final boolean commercial;
    private final GameVersion version;

    private Menu currentMenu;
    private boolean menuActive;
    private int whichSkull;
    private int skullAnimCounter = 10;
    private int epi;

    private boolean messageToPrint;
    private String messageString = "";
    private IntConsumer messageRoutine;
    private boolean messageNeedsInput;
    private boolean messageLastMenuActive;

    private int saveSlot;
    private final String[] saveStrings = new String[SAVE_SLOTS];
    private int quickSaveSlot = -1;

    private long ticks = 1;
    private long mouseWait;
    private int mouseX;
    private int mouseY;
    private int lastX;
    private int lastY;
    private boolean charEvents = true;

    public MenuSystem(UiAssets assets, GameView view, UiSounds sounds, UiActions actions, UiSettings settings,
                      SaveSlots saves, UiConfig config, MessageSink messages, BooleanSupplier automapActive) {
        this.assets = assets;
        this.view = view;
        this.sounds = sounds;
        this.actions = actions;
        this.settings = settings;
        this.saves = saves;
        this.config = config;
        this.messages = messages;
        this.automapActive = automapActive;
        this.entry = new TextEntry(assets);
        this.commercial = view.mode() == GameMode.COMMERCIAL;
        this.version = view.version();

        // ---- main
        List<MenuItem> main = new ArrayList<>();
        main.add(MenuItem.action("M_NGAME", 'n', c -> newGame()));
        main.add(MenuItem.action("M_OPTION", 'o', c -> setup(optionsDef())));
        main.add(MenuItem.action("M_LOADG", 'l', c -> loadGame()));
        main.add(MenuItem.action("M_SAVEG", 's', c -> saveGame()));
        main.add(MenuItem.action("M_RDTHIS", 'r', c -> setup(version.isAtLeast(GameVersion.ULTIMATE) ? readDef2() : readDef1())));
        main.add(MenuItem.action("M_QUITG", 'q', c -> quitDoom()));
        mainDef = new Menu("main", new MenuList(main, 0), null, 97, 64, c -> PatchWidget.draw(c, assets, "M_DOOM", 94, 2));

        // ---- episode
        List<MenuItem> epis = new ArrayList<>();
        epis.add(MenuItem.action("M_EPI1", 'k', this::episode));
        epis.add(MenuItem.action("M_EPI2", 't', this::episode));
        epis.add(MenuItem.action("M_EPI3", 'i', this::episode));
        epis.add(MenuItem.action("M_EPI4", 't', this::episode));
        epiDef = new Menu("episode", new MenuList(epis, 0), mainDef, 48, 63,
            c -> PatchWidget.draw(c, assets, "M_EPISOD", 54, 38));

        // ---- skill
        List<MenuItem> skills = new ArrayList<>();
        skills.add(MenuItem.action("M_JKILL", 'i', this::chooseSkill));
        skills.add(MenuItem.action("M_ROUGH", 'h', this::chooseSkill));
        skills.add(MenuItem.action("M_HURT", 'h', this::chooseSkill));
        skills.add(MenuItem.action("M_ULTRA", 'u', this::chooseSkill));
        skills.add(MenuItem.action("M_NMARE", 'n', this::chooseSkill));
        newDef = new Menu("skill", new MenuList(skills, 2), epiDef, 48, 63, c -> {
            PatchWidget.draw(c, assets, "M_NEWG", 96, 14);
            PatchWidget.draw(c, assets, "M_SKILL", 54, 38);
        });

        // ---- options
        List<MenuItem> opts = new ArrayList<>();
        opts.add(MenuItem.action("M_ENDGAM", 'e', c -> endGame()));
        opts.add(MenuItem.action("M_MESSG", 'm', c -> changeMessages()));
        opts.add(MenuItem.action("M_DETAIL", 'g', c -> changeDetail()));
        opts.add(MenuItem.slider("M_SCRNSZ", "", 's', this::sizeDisplay));
        opts.add(MenuItem.spacer());
        opts.add(MenuItem.slider("M_MSENS", "", 'm', this::changeSensitivity));
        opts.add(MenuItem.spacer());
        opts.add(MenuItem.action("M_SVOL", 's', c -> setup(soundDef())));
        optionsDef = new Menu("options", new MenuList(opts, 0), mainDef, 60, 37, this::drawOptions);

        // ---- read this
        readDef1 = new Menu("read1", new MenuList(List.of(MenuItem.action("", 0, c -> setup(readDef2()))), 0), mainDef,
            280, 185, c -> drawFullPatch(c, "HELP2"));
        readDef2 = new Menu("read2", new MenuList(List.of(MenuItem.action("", 0, c -> setup(mainDef))), 0), readDef1,
            330, 175, c -> drawFullPatch(c, "HELP1"));
        readDef1.fullScreen = true;
        readDef2.fullScreen = true;

        // ---- sound
        List<MenuItem> snd = new ArrayList<>();
        snd.add(MenuItem.slider("M_SFXVOL", "", 's', this::sfxVol));
        snd.add(MenuItem.spacer());
        snd.add(MenuItem.slider("M_MUSVOL", "", 'm', this::musicVol));
        snd.add(MenuItem.spacer());
        soundDef = new Menu("sound", new MenuList(snd, 0), optionsDef, 80, 64, c -> {
            PatchWidget.draw(c, assets, "M_SVOL", 60, 38);
            drawThermo(c, 80, 64 + Menu.LINE_HEIGHT, 16, settings.sfxVolume());
            drawThermo(c, 80, 64 + Menu.LINE_HEIGHT * 3, 16, settings.musicVolume());
        });

        // ---- load / save
        List<MenuItem> load = new ArrayList<>();
        List<MenuItem> save = new ArrayList<>();
        for (int i = 0; i < SAVE_SLOTS; i++) {
            load.add(MenuItem.textEntry('1' + i, this::loadSelect));
            save.add(MenuItem.textEntry('1' + i, this::saveSelect));
        }
        loadDef = new Menu("load", new MenuList(load, 0), mainDef, 80, 54, c -> drawLoadSave(c, "M_LOADG", false));
        saveDef = new Menu("save", new MenuList(save, 0), mainDef, 80, 54, c -> drawLoadSave(c, "M_SAVEG", true));
        loadDef.customBody = false;

        for (int i = 0; i < SAVE_SLOTS; i++) {
            saveStrings[i] = "";
        }

        // ---- version dependent edits of M_Init
        if (version.isAtLeast(GameVersion.ULTIMATE)) {
            readDef2.prev = null;
        }
        if (version.isAtLeast(GameVersion.FINAL)) {
            readDef2.drawer = c -> drawFullPatch(c, "HELP");
        }
        if (commercial) {
            mainDef.list.replace(4, mainDef.list.item(5));
            mainDef.list.truncate(5);
            mainDef.y += 8;
            newDef.prev = mainDef;
            readDef1.drawer = c -> drawFullPatch(c, "HELP");
            readDef1.x = 330;
            readDef1.y = 165;
            readDef1.list.replace(0, MenuItem.action("", 0, c -> setup(mainDef)));
        }
        if (!version.isAtLeast(GameVersion.ULTIMATE)) {
            epiDef.list.truncate(3);
        }

        buildExtended();
        currentMenu = mainDef;
    }

    // ------------------------------------------------------------------ small accessors

    private Menu optionsDef() {
        return optionsDef;
    }

    private Menu soundDef() {
        return soundDef;
    }

    private Menu readDef1() {
        return readDef1;
    }

    private Menu readDef2() {
        return readDef2;
    }

    public boolean active() {
        return menuActive;
    }

    /** Whether a modal message box is up. */
    public boolean messageUp() {
        return messageToPrint;
    }

    /** Whether the message box up needs a y/n answer ({@code messageNeedsInput}). */
    public boolean messageNeedsInput() {
        return messageToPrint && messageNeedsInput;
    }

    /** Whether the menu currently shows a full-screen help page, which replaces the game view. */
    public boolean fullScreenPage() {
        return menuActive && !messageToPrint && currentMenu.fullScreen;
    }

    /** True while a save description is being typed. */
    public boolean textEntryActive() {
        return entry.active();
    }

    /** When true (default) printable characters of a save name come from {@link #typed}; otherwise from key codes. */
    public void setCharEvents(boolean charEvents) {
        this.charEvents = charEvents;
    }

    /** Name of the current menu (for tests and tools). */
    public String currentName() {
        return currentMenu.name;
    }

    public int itemOn() {
        return currentMenu.list.itemOn();
    }

    public int quickSaveSlot() {
        return quickSaveSlot;
    }

    public String saveString(int slot) {
        return saveStrings[slot];
    }

    private String text(String key) {
        try {
            return view.defs().text(key);
        } catch (RuntimeException e) {
            return key;
        }
    }

    private void play(int sfx) {
        sounds.startSound(sfx);
    }

    // ------------------------------------------------------------------ control panel

    /** {@code M_StartControlPanel}. */
    @VanillaRef("m_menu.c:M_StartControlPanel")
    public void startControlPanel() {
        if (menuActive) {
            return;
        }
        menuActive = true;
        currentMenu = mainDef;
        currentMenu.list.setItemOn(currentMenu.list.lastOn());
    }

    /** {@code M_ClearMenus}. */
    @VanillaRef("m_menu.c:M_ClearMenus")
    public void clearMenus() {
        menuActive = false;
    }

    private void setup(Menu m) {
        currentMenu = m;
        m.list.setItemOn(m.list.lastOn());
    }

    // ------------------------------------------------------------------ ticker

    /** {@code M_Ticker}. */
    @VanillaRef("m_menu.c:M_Ticker")
    public void tick() {
        ticks++;
        if (--skullAnimCounter <= 0) {
            whichSkull ^= 1;
            skullAnimCounter = 8;
        }
    }

    // ------------------------------------------------------------------ message boxes

    /** {@code M_StartMessage}. */
    @VanillaRef("m_menu.c:M_StartMessage")
    public void startMessage(String string, IntConsumer routine, boolean input) {
        messageLastMenuActive = menuActive;
        messageToPrint = true;
        messageString = string;
        messageRoutine = routine;
        messageNeedsInput = input;
        menuActive = true;
    }

    // ------------------------------------------------------------------ actions of the items

    private void newGame() {
        if (view.netGame() && !view.demoPlayback()) {
            startMessage(text("NEWGAME"), null, false);
            return;
        }
        setup(commercial ? newDef : epiDef);
    }

    private void episode(int choice) {
        if (view.mode() == GameMode.SHAREWARE && choice != 0) {
            startMessage(text("SWSTRING"), null, false);
            setup(readDef1);
            return;
        }
        epi = choice;
        setup(newDef);
    }

    private void chooseSkill(int choice) {
        if (choice == Skill.NIGHTMARE.ordinal()) {
            startMessage(text("NIGHTMARE"), this::verifyNightmare, true);
            return;
        }
        actions.newGame(Skill.of(choice), epi + 1, 1);
        clearMenus();
    }

    private void verifyNightmare(int key) {
        if (key != 'y') {
            return;
        }
        actions.newGame(Skill.NIGHTMARE, epi + 1, 1);
        clearMenus();
    }

    private void loadGame() {
        if (view.netGame()) {
            startMessage(text("LOADNET"), null, false);
            return;
        }
        setup(loadDef);
        readSaveStrings();
    }

    private void saveGame() {
        if (!view.userGame()) {
            startMessage(text("SAVEDEAD"), null, false);
            return;
        }
        if (view.state() != GameState.LEVEL) {
            return;
        }
        setup(saveDef);
        readSaveStrings();
    }

    /** {@code M_ReadSaveStrings}. */
    private void readSaveStrings() {
        for (int i = 0; i < SAVE_SLOTS; i++) {
            String d = saves.description(i);
            if (d == null) {
                saveStrings[i] = text("EMPTYSTRING");
                loadDef.list.item(i).setEnabled(false);
            } else {
                saveStrings[i] = d.length() > TextEntry.SIZE - 1 ? d.substring(0, TextEntry.SIZE - 1) : d;
                loadDef.list.item(i).setEnabled(true);
            }
        }
    }

    private void loadSelect(int slot) {
        actions.loadGame(slot);
        clearMenus();
    }

    private void saveSelect(int slot) {
        saveSlot = slot;
        String old = saveStrings[slot];
        entry.begin(old.equals(text("EMPTYSTRING")) ? "" : old, old);
        saveStrings[slot] = entry.text();
    }

    private void doSave(int slot) {
        actions.saveGame(slot, saveStrings[slot]);
        clearMenus();
        if (quickSaveSlot == -2) {
            quickSaveSlot = slot;
        }
    }

    private void quickSave() {
        if (!view.userGame()) {
            play(SoundId.OOF);
            return;
        }
        if (view.state() != GameState.LEVEL) {
            return;
        }
        if (quickSaveSlot < 0) {
            startControlPanel();
            readSaveStrings();
            setup(saveDef);
            quickSaveSlot = -2;
            return;
        }
        String s = text("QSPROMPT").replace("%s", saveStrings[quickSaveSlot]);
        startMessage(s, key -> {
            if (key == 'y') {
                doSave(quickSaveSlot);
                play(SoundId.SWTCHX);
            }
        }, true);
    }

    private void quickLoad() {
        if (view.netGame()) {
            startMessage(text("QLOADNET"), null, false);
            return;
        }
        if (quickSaveSlot < 0) {
            startMessage(text("QSAVESPOT"), null, false);
            return;
        }
        String s = text("QLPROMPT").replace("%s", saveStrings[quickSaveSlot]);
        startMessage(s, key -> {
            if (key == 'y') {
                loadSelect(quickSaveSlot);
                play(SoundId.SWTCHX);
            }
        }, true);
    }

    private void endGame() {
        if (!view.userGame()) {
            play(SoundId.OOF);
            return;
        }
        if (view.netGame()) {
            startMessage(text("NETEND"), null, false);
            return;
        }
        startMessage(text("ENDGAME"), key -> {
            if (key != 'y') {
                return;
            }
            currentMenu.list.setLastOn(currentMenu.list.itemOn());
            clearMenus();
            actions.endGame();
        }, true);
    }

    private void changeMessages() {
        settings.setShowMessages(!settings.showMessages());
        messages.post(text(settings.showMessages() ? "MSGON" : "MSGOFF"), true);
    }

    private void changeDetail() {
        settings.setDetailLevel(1 - settings.detailLevel());
        messages.post(text(settings.detailLevel() == 0 ? "DETAILHI" : "DETAILLO"), false);
    }

    private void sizeDisplay(int choice) {
        int blocks = settings.screenBlocks();
        int screenSize = Math.max(0, Math.min(8, blocks - 3));
        if (choice == 0) {
            if (screenSize > 0) {
                settings.setScreenBlocks(blocks - 1);
            }
        } else if (screenSize < 8) {
            settings.setScreenBlocks(blocks + 1);
        }
    }

    private void changeSensitivity(int choice) {
        int s = settings.mouseSensitivity();
        if (choice == 0) {
            if (s > 0) {
                settings.setMouseSensitivity(s - 1);
            }
        } else if (s < 9) {
            settings.setMouseSensitivity(s + 1);
        }
    }

    private void sfxVol(int choice) {
        int v = settings.sfxVolume();
        if (choice == 0) {
            if (v > 0) {
                settings.setSfxVolume(v - 1);
            }
        } else if (v < 15) {
            settings.setSfxVolume(v + 1);
        }
    }

    private void musicVol(int choice) {
        int v = settings.musicVolume();
        if (choice == 0) {
            if (v > 0) {
                settings.setMusicVolume(v - 1);
            }
        } else if (v < 15) {
            settings.setMusicVolume(v + 1);
        }
    }

    private String endMessage() {
        int idx = Math.floorMod(view.gametic(), 8);
        boolean doom1 = view.mission() == GameMission.DOOM;
        String m = (doom1 ? DOOM1_END : DOOM2_END)[idx];
        return m != null ? m : text("QUITMSG");
    }

    /** {@code M_QuitDOOM}. */
    @VanillaRef("m_menu.c:M_QuitDOOM")
    public void quitDoom() {
        startMessage(endMessage() + "\n\n" + text("DOSY"), this::quitResponse, true);
    }

    private void quitResponse(int key) {
        if (key != 'y') {
            return;
        }
        if (!view.netGame()) {
            int i = (view.gametic() >> 2) & 7;
            play(commercial ? QUIT_SOUNDS_2[i] : QUIT_SOUNDS[i]);
        }
        actions.quit();
    }

    /** Window close button: first request asks, a second one while the question is up confirms. */
    public void requestQuit() {
        if (menuActive && messageToPrint && messageRoutine != null && messageString.contains(text("DOSY"))) {
            quitResponse('y');
        } else {
            play(SoundId.SWTCHN);
            quitDoom();
        }
    }

    // ------------------------------------------------------------------ extended pages

    private void buildExtended() {
        List<SettingsPage> pages = settings.extendedPages();
        if (config.vanillaMode() || pages.isEmpty()) {
            return;
        }
        List<MenuItem> titles = new ArrayList<>();
        List<Menu> pageMenus = new ArrayList<>();
        for (SettingsPage page : pages) {
            Menu pm = pageMenu(page);
            pageMenus.add(pm);
            titles.add(MenuItem.textAction(page.title(), Character.toLowerCase(page.title().charAt(0)),
                c -> setup(pm)));
        }
        MenuList titleList = new MenuList(titles, 0);
        Menu ext = new Menu("extended", titleList, optionsDef, 90, 40, c -> drawTextList(c, "EXTENDED OPTIONS", titles));
        ext.lineHeight = 14;
        ext.customBody = true;
        extendedDef = ext;
        for (Menu pm : pageMenus) {
            pm.prev = ext;
        }
        optionsDef.list.add(MenuItem.textAction("Extended Options", 'x', c -> setup(extendedDef)));
    }

    private Menu pageMenu(SettingsPage page) {
        List<MenuItem> rows = new ArrayList<>();
        for (SettingsItem si : page.items()) {
            rows.add(rowFor(si));
        }
        MenuList list = new MenuList(rows, 0);
        if (list.size() > 0 && list.item(0).skipped()) {
            list.down();
        }
        Menu m = new Menu("page:" + page.title(), list, null, 42, 38, null);
        m.lineHeight = 12;
        m.customBody = true;
        m.drawer = c -> drawPage(c, page, list);
        return m;
    }

    private static MenuItem rowFor(SettingsItem si) {
        return switch (si) {
            case SettingsItem.Toggle t -> MenuItem.toggle(t.label(), d -> t.set().accept(!t.get().getAsBoolean()));
            case SettingsItem.Choice ch -> MenuItem.slider("", ch.label(), 0, d -> {
                int n = ch.options().size();
                if (n > 0) {
                    ch.set().accept(Math.floorMod(ch.get().getAsInt() + (d == 0 ? -1 : 1), n));
                }
            });
            case SettingsItem.Slider s -> MenuItem.slider("", s.label(), 0, d -> {
                int v = s.get().getAsInt() + (d == 0 ? -s.step() : s.step());
                s.set().accept(Math.max(s.min(), Math.min(s.max(), v)));
            });
            case SettingsItem.Action a -> MenuItem.textAction(a.label(), 0, d -> a.run().run());
            case SettingsItem.Info i -> MenuItem.spacer();
        };
    }

    private static String valueOf(SettingsItem si) {
        return switch (si) {
            case SettingsItem.Toggle t -> t.get().getAsBoolean() ? "ON" : "OFF";
            case SettingsItem.Choice ch -> {
                int i = ch.get().getAsInt();
                yield i >= 0 && i < ch.options().size() ? ch.options().get(i) : "?";
            }
            case SettingsItem.Slider s -> s.format() != null ? s.format().apply(s.get().getAsInt())
                : Integer.toString(s.get().getAsInt());
            case SettingsItem.Action a -> a.value() != null ? a.value().get() : "";
            case SettingsItem.Info i -> i.value() != null ? i.value().get() : "";
        };
    }

    // ------------------------------------------------------------------ drawing

    private void drawFullPatch(Canvas2D c, String name) {
        Picture p = assets.patch(name);
        if (p != null) {
            c.drawPatchCentred(p, 0);
        }
    }

    private void drawThermo(Canvas2D c, int x, int y, int width, int dot) {
        int xx = x;
        PatchWidget.draw(c, assets, "M_THERML", xx, y);
        xx += 8;
        for (int i = 0; i < width; i++) {
            PatchWidget.draw(c, assets, "M_THERMM", xx, y);
            xx += 8;
        }
        PatchWidget.draw(c, assets, "M_THERMR", xx, y);
        PatchWidget.draw(c, assets, "M_THERMO", x + 8 + dot * 8, y);
    }

    private void drawOptions(Canvas2D c) {
        PatchWidget.draw(c, assets, "M_OPTTTL", 108, 15);
        PatchWidget.draw(c, assets, DETAIL_PATCHES[settings.detailLevel() & 1], optionsDef.x + 175,
            optionsDef.y + Menu.LINE_HEIGHT * 2);
        PatchWidget.draw(c, assets, MESSAGE_PATCHES[settings.showMessages() ? 1 : 0], optionsDef.x + 120,
            optionsDef.y + Menu.LINE_HEIGHT);
        drawThermo(c, optionsDef.x, optionsDef.y + Menu.LINE_HEIGHT * 6, 10, settings.mouseSensitivity());
        drawThermo(c, optionsDef.x, optionsDef.y + Menu.LINE_HEIGHT * 4, 9,
            Math.max(0, Math.min(8, settings.screenBlocks() - 3)));
    }

    private void drawSaveLoadBorder(Canvas2D c, int x, int y) {
        PatchWidget.draw(c, assets, "M_LSLEFT", x - 8, y + 7);
        for (int i = 0; i < 24; i++) {
            PatchWidget.draw(c, assets, "M_LSCNTR", x, y + 7);
            x += 8;
        }
        PatchWidget.draw(c, assets, "M_LSRGHT", x, y + 7);
    }

    private void drawLoadSave(Canvas2D c, String title, boolean saving) {
        PatchWidget.draw(c, assets, title, 72, 28);
        for (int i = 0; i < SAVE_SLOTS; i++) {
            drawSaveLoadBorder(c, loadDef.x, loadDef.y + Menu.LINE_HEIGHT * i);
            String s = entry.active() && i == saveSlot ? entry.text() : saveStrings[i];
            FontText.draw(c, assets, loadDef.x, loadDef.y + Menu.LINE_HEIGHT * i, s);
        }
        if (saving && entry.active()) {
            int w = FontText.width(assets, entry.text());
            FontText.draw(c, assets, loadDef.x + w, loadDef.y + Menu.LINE_HEIGHT * saveSlot, "_");
        }
    }

    private void drawTextList(Canvas2D c, String title, List<MenuItem> items) {
        FontText.draw(c, assets, 160 - FontText.width(assets, title) / 2, 18, title);
        for (int i = 0; i < items.size(); i++) {
            FontText.draw(c, assets, extendedDef.x, extendedDef.y + i * extendedDef.lineHeight, items.get(i).label());
        }
        drawCursor(c, extendedDef.x, extendedDef.y, extendedDef.lineHeight, extendedDef.list.itemOn(), 0);
    }

    private void drawPage(Canvas2D c, SettingsPage page, MenuList list) {
        Menu m = currentMenu;
        FontText.draw(c, assets, 160 - FontText.width(assets, page.title()) / 2, 18, page.title().toUpperCase());
        int visible = (190 - m.y) / m.lineHeight;
        int on = list.itemOn();
        int top = Math.max(0, Math.min(on - visible / 2, list.size() - visible));
        for (int row = top; row < list.size() && row < top + visible; row++) {
            SettingsItem si = page.items().get(row);
            int y = m.y + (row - top) * m.lineHeight;
            FontText.draw(c, assets, m.x, y, si.label());
            String v = valueOf(si);
            if (!v.isEmpty()) {
                FontText.drawRight(c, assets, 290, y, v);
            }
        }
        drawCursor(c, m.x, m.y, m.lineHeight, on, top);
    }

    private void drawCursor(Canvas2D c, int x, int y, int lineHeight, int itemOn, int top) {
        PatchWidget.draw(c, assets, SKULLS[whichSkull], x + SKULL_X_OFFSET, y - 2 + (itemOn - top) * lineHeight);
    }

    /** {@code M_Drawer}. */
    @VanillaRef("m_menu.c:M_Drawer")
    public void draw(Canvas2D canvas) {
        if (messageToPrint) {
            int y = 100 - FontText.height(assets, messageString) / 2;
            FontText.drawCentredLines(canvas, assets, y, messageString);
            return;
        }
        if (!menuActive) {
            return;
        }
        if (currentMenu.drawer != null) {
            currentMenu.drawer.accept(canvas);
        }
        if (currentMenu.customBody) {
            return;
        }
        int x = currentMenu.x;
        int y = currentMenu.y;
        MenuList l = currentMenu.list;
        for (int i = 0; i < l.size(); i++) {
            MenuItem it = l.item(i);
            if (!it.patch().isEmpty()) {
                PatchWidget.draw(canvas, assets, it.patch(), x, y);
            } else if (!it.label().isEmpty()) {
                FontText.draw(canvas, assets, x, y + 4, it.label());
            }
            y += Menu.LINE_HEIGHT;
        }
        PatchWidget.draw(canvas, assets, SKULLS[whichSkull], x + SKULL_X_OFFSET,
            currentMenu.y - 5 + l.itemOn() * Menu.LINE_HEIGHT);
    }

    // ------------------------------------------------------------------ input

    /** A typed character (Unicode code point) for the save-name editor. Returns true when it was used. */
    public boolean typed(int ch) {
        if (!entry.active() || !charEvents) {
            return false;
        }
        if (entry.type(ch)) {
            saveStrings[saveSlot] = entry.text();
        }
        return true;
    }

    /** Mouse movement in raw counts (positive y = down). The menu turns it into cursor keys like the original. */
    public boolean mouseMove(int dx, int dy) {
        if (!menuActive || mouseWait >= ticks) {
            return false;
        }
        int key = -1;
        mouseY += dy;
        if (mouseY > lastY + 30) {
            key = UiKeys.DOWNARROW;
            mouseWait = ticks + 5;
            mouseY = lastY += 30;
        } else if (mouseY < lastY - 30) {
            key = UiKeys.UPARROW;
            mouseWait = ticks + 5;
            mouseY = lastY -= 30;
        }
        mouseX += dx;
        if (mouseX < lastX - 30) {
            key = UiKeys.LEFTARROW;
            mouseWait = ticks + 5;
            mouseX = lastX -= 30;
        } else if (mouseX > lastX + 30) {
            key = UiKeys.RIGHTARROW;
            mouseWait = ticks + 5;
            mouseX = lastX += 30;
        }
        return key != -1 && respond(key);
    }

    /** Mouse button: 0 = Enter, 1 = Backspace (menu back), as the original. */
    public boolean mouseButton(int button) {
        if (!menuActive || mouseWait >= ticks) {
            return false;
        }
        if (button == 0) {
            mouseWait = ticks + 15;
            return respond(UiKeys.ENTER);
        }
        if (button == 1) {
            mouseWait = ticks + 15;
            return respond(UiKeys.BACKSPACE);
        }
        return false;
    }

    private static boolean isNullKey(int key) {
        return key == UiKeys.PAUSE || key == UiKeys.CAPSLOCK || key == UiKeys.SCRLCK || key == UiKeys.NUMLOCK;
    }

    /**
     * {@code M_Responder} for a key press. Printable keys use their lower-case ASCII code. Returns whether the
     * event was consumed.
     */
    @VanillaRef("m_menu.c:M_Responder")
    public boolean respond(int key) {
        int ch = key >= 32 && key < 127 ? key : 0;

        // ---- save game string input
        if (entry.active()) {
            switch (key) {
                case UiKeys.BACKSPACE -> {
                    entry.backspace();
                    saveStrings[saveSlot] = entry.text();
                }
                case UiKeys.ESCAPE -> saveStrings[saveSlot] = entry.cancel();
                case UiKeys.ENTER -> {
                    String s = entry.accept();
                    saveStrings[saveSlot] = s;
                    if (!s.isEmpty()) {
                        doSave(saveSlot);
                    }
                }
                default -> {
                    if (!charEvents && ch != 0 && entry.type(ch)) {
                        saveStrings[saveSlot] = entry.text();
                    }
                }
            }
            return true;
        }

        // ---- messages that need input
        if (messageToPrint) {
            if (messageNeedsInput && key != ' ' && key != UiKeys.ESCAPE && key != 'y' && key != 'n') {
                return false;
            }
            menuActive = messageLastMenuActive;
            messageToPrint = false;
            if (messageRoutine != null) {
                messageRoutine.accept(key);
            }
            menuActive = false;
            play(SoundId.SWTCHX);
            return true;
        }

        if (key == UiKeys.PRTSCR) {
            actions.screenshot();
            return true;
        }

        // ---- function keys
        if (!menuActive) {
            switch (key) {
                case UiKeys.MINUS -> {
                    if (automapActive.getAsBoolean()) {
                        return false;
                    }
                    sizeDisplay(0);
                    play(SoundId.STNMOV);
                    return true;
                }
                case UiKeys.EQUALS -> {
                    if (automapActive.getAsBoolean()) {
                        return false;
                    }
                    sizeDisplay(1);
                    play(SoundId.STNMOV);
                    return true;
                }
                case UiKeys.F1 -> {
                    startControlPanel();
                    currentMenu = version.isAtLeast(GameVersion.ULTIMATE) ? readDef2 : readDef1;
                    currentMenu.list.setItemOn(0);
                    play(SoundId.SWTCHN);
                    return true;
                }
                case UiKeys.F2 -> {
                    startControlPanel();
                    play(SoundId.SWTCHN);
                    saveGame();
                    return true;
                }
                case UiKeys.F3 -> {
                    startControlPanel();
                    play(SoundId.SWTCHN);
                    loadGame();
                    return true;
                }
                case UiKeys.F4 -> {
                    startControlPanel();
                    currentMenu = soundDef;
                    currentMenu.list.setItemOn(0);
                    play(SoundId.SWTCHN);
                    return true;
                }
                case UiKeys.F5 -> {
                    changeDetail();
                    play(SoundId.SWTCHN);
                    return true;
                }
                case UiKeys.F6 -> {
                    play(SoundId.SWTCHN);
                    quickSave();
                    return true;
                }
                case UiKeys.F7 -> {
                    play(SoundId.SWTCHN);
                    endGame();
                    return true;
                }
                case UiKeys.F8 -> {
                    changeMessages();
                    play(SoundId.SWTCHN);
                    return true;
                }
                case UiKeys.F9 -> {
                    play(SoundId.SWTCHN);
                    quickLoad();
                    return true;
                }
                case UiKeys.F10 -> {
                    play(SoundId.SWTCHN);
                    quitDoom();
                    return true;
                }
                case UiKeys.F11 -> {
                    int g = settings.gamma() + 1;
                    if (g > 4) {
                        g = 0;
                    }
                    settings.setGamma(g);
                    messages.post(text("GAMMALVL" + g), false);
                    return true;
                }
                default -> {
                }
            }
        }

        // ---- pop-up menu
        if (!menuActive) {
            if (key == UiKeys.ESCAPE) {
                startControlPanel();
                play(SoundId.SWTCHN);
                return true;
            }
            return false;
        }

        // ---- keys usable within the menu
        MenuList list = currentMenu.list;
        switch (key) {
            case UiKeys.DOWNARROW -> {
                for (int s = list.down(); s > 0; s--) {
                    play(SoundId.PSTOP);
                }
                return true;
            }
            case UiKeys.UPARROW -> {
                for (int s = list.up(); s > 0; s--) {
                    play(SoundId.PSTOP);
                }
                return true;
            }
            case UiKeys.LEFTARROW -> {
                if (list.item(list.itemOn()).hasArrows()) {
                    play(SoundId.STNMOV);
                    list.slide(0);
                }
                return true;
            }
            case UiKeys.RIGHTARROW -> {
                if (list.item(list.itemOn()).hasArrows()) {
                    play(SoundId.STNMOV);
                    list.slide(1);
                }
                return true;
            }
            case UiKeys.ENTER -> {
                int r = list.activate();
                if (r == 1) {
                    play(SoundId.STNMOV);
                } else if (r == 2) {
                    play(SoundId.PISTOL);
                }
                return true;
            }
            case UiKeys.ESCAPE -> {
                list.setLastOn(list.itemOn());
                clearMenus();
                play(SoundId.SWTCHX);
                return true;
            }
            case UiKeys.BACKSPACE -> {
                list.setLastOn(list.itemOn());
                if (currentMenu.prev != null) {
                    currentMenu = currentMenu.prev;
                    currentMenu.list.setItemOn(currentMenu.list.lastOn());
                    play(SoundId.SWTCHN);
                }
                return true;
            }
            default -> {
            }
        }

        if (ch != 0 || isNullKey(key)) {
            if (list.shortcut(ch)) {
                play(SoundId.PSTOP);
                return true;
            }
        }
        return false;
    }
}
