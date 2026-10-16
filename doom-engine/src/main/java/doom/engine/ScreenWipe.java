package doom.engine;

import doom.game.api.MiscRandom;
import doom.render.FrameBuffer;

/**
 * The melt between two screens ({@code f_wipe.c}). The simulation does not advance while a wipe runs. The renderer
 * module's {@code Wipe} is adapted to this interface; {@link #NONE} skips wipes.
 */
public interface ScreenWipe {

    /** {@code wipe_StartScreen}: remembers the screen that is about to disappear. */
    void captureStart(FrameBuffer frame);

    /** {@code wipe_EndScreen}: remembers the screen that replaces it; {@code frame} holds it on return. */
    void captureEnd(FrameBuffer frame);

    /**
     * {@code wipe_ScreenWipe}: advances the melt and draws it into {@code frame}. The first call of a wipe draws the
     * random start offsets from {@code rng} (exactly 160 numbers).
     *
     * @param tics elapsed game tics since the previous call
     * @return {@code true} when the wipe is finished
     */
    boolean step(FrameBuffer frame, int tics, MiscRandom rng);

    /** No wipes. */
    ScreenWipe NONE = new ScreenWipe() {
        @Override
        public void captureStart(FrameBuffer frame) {
        }

        @Override
        public void captureEnd(FrameBuffer frame) {
        }

        @Override
        public boolean step(FrameBuffer frame, int tics, MiscRandom rng) {
            return true;
        }
    };
}
