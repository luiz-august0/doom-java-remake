package doom.ui.screens;

import doom.render.canvas.Canvas2D;
import doom.ui.DebugInfo;
import doom.ui.UiAssets;
import doom.ui.widget.FontText;
import java.util.Locale;

/** The F12 debug overlay (spec 07 §2): FPS, frame time, tic, audio underruns and the internal resolution. */
public final class DebugOverlay {

    private final UiAssets assets;
    private final StringBuilder line = new StringBuilder(96);

    public DebugOverlay(UiAssets assets) {
        this.assets = assets;
    }

    public void draw(Canvas2D canvas, DebugInfo info) {
        int x = canvas.virtualX(0) + 2;
        int y = 12;
        line.setLength(0);
        line.append(String.format(Locale.ROOT, "FPS %.1f  FRAME %.2f MS", info.fps, info.frameMillis));
        FontText.draw(canvas, assets, x, y, line, Integer.MAX_VALUE);
        y += 9;
        line.setLength(0);
        line.append("TIC ").append(info.tic).append("  UNDERRUNS ").append(info.audioUnderruns);
        FontText.draw(canvas, assets, x, y, line, Integer.MAX_VALUE);
        y += 9;
        line.setLength(0);
        line.append("RES ").append(info.internalWidth).append('X').append(info.internalHeight);
        FontText.draw(canvas, assets, x, y, line, Integer.MAX_VALUE);
        if (info.extra != null) {
            y += 9;
            FontText.draw(canvas, assets, x, y, info.extra, Integer.MAX_VALUE);
        }
    }
}
