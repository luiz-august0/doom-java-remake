package doom.platform;

import doom.platform.input.InputEventQueue;

/** Delivers keyboard, mouse and gamepad input as {@link doom.platform.input.InputEvent}s. Main thread only. */
public interface InputSource {

    /** Polls the platform and appends the pending events to {@code queue}. */
    void poll(InputEventQueue queue);

    /** Captures (hides and locks) the mouse with raw motion when possible, or releases it. */
    void setMouseCaptured(boolean captured);
}
