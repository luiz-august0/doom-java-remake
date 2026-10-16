package doom.ui.hud;

import doom.game.view.GameView;
import doom.math.annotation.VanillaRef;
import doom.render.canvas.Canvas2D;
import doom.ui.UiAssets;
import doom.ui.UiSettings;
import doom.ui.menu.MessageSink;
import doom.ui.widget.FontText;
import doom.wad.iwad.GameMission;
import doom.wad.iwad.GameVersion;

/**
 * Heads-up messages and the automap title ({@code hu_stuff.c}). Chat (network games) is not supported.
 */
public final class Hud implements MessageSink {

    /** {@code HU_MSGTIMEOUT}: 4 seconds. */
    public static final int MESSAGE_TIMEOUT = 4 * 35;
    /** {@code HU_TITLEY} = 167 - font height. */
    private static final int TITLE_BOTTOM = 167;

    private final UiAssets assets;
    private final GameView view;
    private final UiSettings settings;

    private String pending;
    private boolean dontFuckWithMe;
    private boolean messageNotToBeFuckedWith;
    private boolean messageOn;
    private int messageCounter;
    private String current = "";
    private String title = "";

    public Hud(UiAssets assets, GameView view, UiSettings settings) {
        this.assets = assets;
        this.view = view;
        this.settings = settings;
    }

    /** {@code HU_Start}: clears the message state and reads the map title. */
    @VanillaRef("hu_stuff.c:HU_Start")
    public void start() {
        messageOn = false;
        dontFuckWithMe = false;
        messageNotToBeFuckedWith = false;
        title = levelTitle();
    }

    /** {@code plr->message = text}. */
    @Override
    public void post(String text, boolean dontFuckWithMe) {
        this.pending = text;
        if (dontFuckWithMe) {
            this.dontFuckWithMe = true;
        }
    }

    /** {@code HU_Ticker}. */
    @VanillaRef("hu_stuff.c:HU_Ticker")
    public void tick() {
        if (messageCounter != 0 && --messageCounter == 0) {
            messageOn = false;
            messageNotToBeFuckedWith = false;
        }
        if (settings.showMessages() || dontFuckWithMe) {
            if ((pending != null && !messageNotToBeFuckedWith) || (pending != null && dontFuckWithMe)) {
                current = pending;
                pending = null;
                messageOn = true;
                messageCounter = MESSAGE_TIMEOUT;
                messageNotToBeFuckedWith = dontFuckWithMe;
                dontFuckWithMe = false;
            }
        }
    }

    /** Whether a message is on screen. */
    public boolean messageOn() {
        return messageOn;
    }

    public String currentMessage() {
        return current;
    }

    public String title() {
        return title;
    }

    private String text(String key) {
        try {
            return view.defs().text(key);
        } catch (RuntimeException e) {
            return "";
        }
    }

    @VanillaRef("hu_stuff.c:HU_Start")
    private String levelTitle() {
        int ep = view.episode();
        int map = view.map();
        GameMission m = view.mission();
        if (m == GameMission.DOOM) {
            int idx = (ep - 1) * 9 + map - 1;
            if (idx < 0) {
                return "";
            }
            if (idx < 33) {
                return text("HUSTR_E" + ep + "M" + map);
            }
            return idx < 36 ? text("PHUSTR_" + (22 + idx - 33)) : "";
        }
        if (map < 1 || map > 35) {
            return "";
        }
        return switch (m) {
            case DOOM2 -> view.version() == GameVersion.V1_9 && map >= 33 ? "" : text("HUSTR_" + map);
            case PACK_PLUTONIA -> map <= 32 ? text("PHUSTR_" + map) : "";
            case PACK_TNT -> map <= 32 ? text("THUSTR_" + map) : "";
            default -> "Unknown level";
        };
    }

    /** {@code HU_Drawer} messages: the top left corner of the 320×200 space. */
    public void drawMessage(Canvas2D canvas) {
        if (messageOn) {
            FontText.draw(canvas, assets, 0, 0, current);
        }
    }

    /** The automap title, just above the status bar. */
    public void drawTitle(Canvas2D canvas, int x) {
        if (!title.isEmpty()) {
            FontText.draw(canvas, assets, x, TITLE_BOTTOM - assets.fontHeight(), title, Integer.MAX_VALUE);
        }
    }
}
