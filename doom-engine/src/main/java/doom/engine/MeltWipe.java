package doom.engine;

import doom.game.api.MiscRandom;
import doom.render.FrameBuffer;
import doom.render.wipe.Wipe;

/** {@link ScreenWipe} on the renderer module's melt: keeps copies of the two screens and starts it on the first step. */
final class MeltWipe implements ScreenWipe {

    private FrameBuffer from;
    private FrameBuffer to;
    private Wipe wipe;
    private boolean started;

    private static FrameBuffer fit(FrameBuffer buffer, FrameBuffer like) {
        if (buffer == null || buffer.width() != like.width() || buffer.height() != like.height()
                || buffer.layout() != like.layout()) {
            return new FrameBuffer(like.width(), like.height(), like.layout());
        }
        return buffer;
    }

    @Override
    public void captureStart(FrameBuffer frame) {
        from = fit(from, frame);
        from.copyFrom(frame);
        started = false;
    }

    @Override
    public void captureEnd(FrameBuffer frame) {
        to = fit(to, frame);
        to.copyFrom(frame);
    }

    @Override
    public boolean step(FrameBuffer frame, int tics, MiscRandom rng) {
        if (from == null || to == null || from.width() != frame.width() || from.height() != frame.height()) {
            return true; // the frame size changed during the wipe: give up on it
        }
        if (wipe == null) {
            wipe = new Wipe(rng);
        }
        if (!started) {
            wipe.start(from, to);
            started = true;
        }
        boolean done = wipe.tick(tics, frame);
        if (done) {
            started = false;
        }
        return done;
    }
}
