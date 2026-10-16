package doom.ui.screens;

import doom.game.view.FinaleView;
import doom.game.view.FrameFlags;
import doom.game.view.GameView;
import doom.game.view.SoundId;
import doom.math.annotation.VanillaRef;
import doom.render.canvas.Canvas2D;
import doom.ui.UiAssets;
import doom.ui.UiSounds;
import doom.ui.widget.FontText;
import doom.wad.LumpDirectory;
import doom.wad.gfx.Flat;
import doom.wad.gfx.Picture;
import doom.wad.iwad.GameVersion;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the finale ({@code F_Drawer}): the text crawl over a tiled flat, the art screens, the bunny scroll and the
 * DOOM II cast call. The logic lives in the simulation ({@link FinaleView}); the only thing done here is the
 * pistol shot that the original plays from inside the bunny drawer.
 */
public final class FinaleScreen {

    private static final int TEXTSPEED = 3;

    private final UiAssets assets;
    private final GameView view;
    private final UiSounds sounds;
    private int lastStage;
    private final Map<String, SpriteRef> spriteCache = new HashMap<>();

    /** A sprite lump to draw and whether it is mirrored. */
    private record SpriteRef(Picture picture, boolean flip) {
    }

    public FinaleScreen(UiAssets assets, GameView view, UiSounds sounds) {
        this.assets = assets;
        this.view = view;
        this.sounds = sounds;
    }

    /** Per-tic work: the bunny scroll's "stage" sounds ({@code F_BunnyScroll} plays them while drawing). */
    public void tick() {
        FinaleView f = view.finale();
        if (view.state() != doom.game.api.GameState.FINALE || f.stage() != 1 || view.episode() != 3) {
            return;
        }
        int count = f.count();
        if (count < 1180) {
            lastStage = 0;
            return;
        }
        int stage = Math.min(6, (count - 1180) / 5);
        if (stage > lastStage) {
            sounds.startSound(SoundId.PISTOL);
            lastStage = stage;
        }
    }

    /** {@code F_Drawer}. */
    @VanillaRef("f_finale.c:F_Drawer")
    public void draw(Canvas2D c) {
        FinaleView f = view.finale();
        switch (f.stage()) {
            case 2 -> drawCast(c, f);
            case 0 -> drawText(c, f);
            default -> drawArtScreen(c, f);
        }
    }

    @VanillaRef("f_finale.c:F_TextWrite")
    private void drawText(Canvas2D c, FinaleView f) {
        Flat flat = assets.flat(f.flat());
        if (flat != null) {
            c.tileFlatFull(flat);
        } else {
            c.clear(0);
        }
        String text = f.text();
        int cx = 10;
        int cy = 10;
        int count = (f.count() - 10) / TEXTSPEED;
        if (count < 0) {
            count = 0;
        }
        for (int i = 0; count > 0 && i < text.length(); count--, i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                cx = 10;
                cy += 11;
                continue;
            }
            Picture g = assets.glyph(ch);
            if (g == null) {
                cx += 4;
                continue;
            }
            int w = g.width();
            if (cx + w > 320) {
                break;
            }
            c.drawPatch(g, cx, cy);
            cx += w;
        }
    }

    private void drawArtScreen(Canvas2D c, FinaleView f) {
        int episode = view.episode();
        c.clear(0);
        if (episode == 3) {
            drawBunnyScroll(c, f.count());
            return;
        }
        String lump;
        switch (episode) {
            case 1 -> lump = view.version().isAtLeast(GameVersion.ULTIMATE) ? "CREDIT" : "HELP2";
            case 2 -> lump = "VICTORY2";
            case 4 -> lump = "ENDPIC";
            default -> {
                return;
            }
        }
        Picture p = assets.patch(lump);
        if (p != null) {
            c.drawPatchCentred(p, 0);
        }
    }

    @VanillaRef("f_finale.c:F_BunnyScroll")
    private void drawBunnyScroll(Canvas2D c, int finalecount) {
        Picture p1 = assets.patch("PFUB2");
        Picture p2 = assets.patch("PFUB1");
        int scrolled = 320 - (finalecount - 230) / 2;
        if (scrolled > 320) {
            scrolled = 320;
        }
        if (scrolled < 0) {
            scrolled = 0;
        }
        // columns [scrolled, 320) of PFUB2 on the left, then PFUB1 from its column 0
        if (p1 != null) {
            c.drawPatch(p1, -scrolled, 0);
        }
        if (p2 != null) {
            c.drawPatch(p2, 320 - scrolled, 0);
        }
        c.fillSides(0);
        if (finalecount < 1130) {
            return;
        }
        int stage = finalecount < 1180 ? 0 : Math.min(6, (finalecount - 1180) / 5);
        Picture end = assets.patch("END" + stage);
        if (end != null) {
            c.drawPatch(end, (320 - 13 * 8) / 2, (200 - 8 * 8) / 2);
        }
    }

    @VanillaRef("f_finale.c:F_CastDrawer")
    private void drawCast(Canvas2D c, FinaleView f) {
        c.clear(0);
        Picture back = assets.patch("BOSSBACK");
        if (back != null) {
            c.drawPatchCentred(back, 0);
        }
        // F_CastPrint
        String name = f.castName();
        int width = FontText.width(assets, name);
        int cx = 160 - width / 2;
        for (int i = 0; i < name.length(); i++) {
            Picture g = assets.glyph(name.charAt(i));
            if (g == null) {
                cx += 4;
                continue;
            }
            c.drawPatch(g, cx, 180);
            cx += g.width();
        }
        SpriteRef s = castSprite(f);
        if (s != null) {
            if (s.flip()) {
                c.drawPatchFlipped(s.picture(), 160, 170);
            } else {
                c.drawPatch(s.picture(), 160, 170);
            }
        }
    }

    /** Finds the picture for rotation 1 (index 0) of the cast frame, as {@code R_InitSpriteLumps} would have installed it. */
    private SpriteRef castSprite(FinaleView f) {
        String sprite = view.defs().spriteName(f.castSprite());
        char frame = (char) ('A' + (f.castFrame() & FrameFlags.FRAMEMASK));
        String key = sprite + frame;
        if (spriteCache.containsKey(key)) {
            return spriteCache.get(key);
        }
        SpriteRef found = findSprite(sprite, frame);
        spriteCache.put(key, found);
        return found;
    }

    private SpriteRef findSprite(String sprite, char frame) {
        LumpDirectory lumps = assets.lumps();
        if (lumps == null) {
            return null;
        }
        int start = lumps.find("S_START");
        int end = lumps.find("S_END");
        if (start < 0 || end < 0) {
            return null;
        }
        String prefix = sprite.toUpperCase() + frame;
        SpriteRef all = null;
        SpriteRef rot1 = null;
        for (int i = start + 1; i < end; i++) {
            String n = lumps.name(i);
            if (!n.startsWith(sprite.toUpperCase())) {
                continue;
            }
            if (n.length() >= 6 && n.startsWith(prefix)) {
                char rot = n.charAt(5);
                if (rot == '0') {
                    all = ref(lumps, i, false);
                } else if (rot == '1') {
                    rot1 = ref(lumps, i, false);
                }
            }
            if (n.length() >= 8 && n.charAt(6) == frame && n.charAt(7) == '1') {
                rot1 = ref(lumps, i, true);
            }
        }
        return all != null ? all : rot1;
    }

    private SpriteRef ref(LumpDirectory lumps, int lump, boolean flip) {
        Picture p = assets.patch(lumps.name(lump));
        return p == null ? null : new SpriteRef(p, flip);
    }
}
