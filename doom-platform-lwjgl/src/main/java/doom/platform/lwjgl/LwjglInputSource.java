package doom.platform.lwjgl;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RAW_MOUSE_MOTION;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.glfwGetGamepadState;
import static org.lwjgl.glfw.GLFW.glfwJoystickIsGamepad;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwRawMouseMotionSupported;
import static org.lwjgl.glfw.GLFW.glfwSetCharCallback;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetScrollCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowFocusCallback;

import doom.platform.InputSource;
import doom.platform.input.InputEvent;
import doom.platform.input.InputEventQueue;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * GLFW keyboard, mouse and gamepad input as {@link InputEvent}s (spec 07 §3).
 *
 * <p>GLFW delivers events through callbacks during {@code glfwPollEvents}; they are appended to the queue passed to
 * {@link #poll}. Events that GLFW fires outside {@code poll} (for example a resize caused by a reconfigure) wait in a
 * private queue and are delivered by the next {@code poll}. Callbacks are created once; steady state is allocation free.
 */
public final class LwjglInputSource implements InputSource {

    private static final int MAX_PADS = 4;
    private static final int PAD_BUTTONS = 15;
    private static final int PAD_AXES = 6;
    private static final float AXIS_EPSILON = 0.005f;

    private final LwjglVideoSink video;
    private final long window;
    private final InputEventQueue pending = new InputEventQueue(64);
    private InputEventQueue target = pending;

    private boolean captured;
    private boolean rawMotion;
    private boolean haveCursor;
    private double lastX;
    private double lastY;
    private int modifiers;

    private final GLFWGamepadState padState = GLFWGamepadState.malloc();
    private final boolean[][] padButtons = new boolean[MAX_PADS][PAD_BUTTONS];
    private final float[][] padAxes = new float[MAX_PADS][PAD_AXES];
    private final boolean[] padPresent = new boolean[MAX_PADS];

    LwjglInputSource(LwjglVideoSink video) {
        this.video = video;
        this.window = video.windowHandle();
        if (window == 0) {
            throw new IllegalStateException("the video sink must be open before creating the input source");
        }
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            modifiers = mods & 0xF;
            int code = GlfwKeys.translate(key);
            if (code == 0) {
                return;
            }
            InputEvent e = target.add(action == GLFW_RELEASE ? InputEvent.KEY_UP : InputEvent.KEY_DOWN, now());
            e.code = code;
            e.modifiers = mods & 0xF;
        });
        glfwSetCharCallback(window, (w, codepoint) -> {
            InputEvent e = target.add(InputEvent.CHAR, now());
            e.code = codepoint;
            e.modifiers = modifiers;
        });
        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (!captured) {
                haveCursor = false;
                return;
            }
            if (haveCursor) {
                double dx = x - lastX;
                double dy = y - lastY;
                if (dx != 0 || dy != 0) {
                    InputEvent e = target.add(InputEvent.MOUSE_MOVE, now());
                    e.dx = dx;
                    e.dy = dy;
                    e.modifiers = modifiers;
                }
            }
            lastX = x;
            lastY = y;
            haveCursor = true;
        });
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            modifiers = mods & 0xF;
            InputEvent e = target.add(action == GLFW_PRESS ? InputEvent.MOUSE_DOWN : InputEvent.MOUSE_UP, now());
            e.code = button;
            e.modifiers = modifiers;
        });
        glfwSetScrollCallback(window, (w, xoff, yoff) -> {
            InputEvent e = target.add(InputEvent.WHEEL, now());
            e.dx = xoff;
            e.dy = yoff;
            e.modifiers = modifiers;
        });
        glfwSetWindowFocusCallback(window, (w, focused) ->
                target.add(focused ? InputEvent.FOCUS_GAINED : InputEvent.FOCUS_LOST, now()));
        video.closeHook = this::release;
        video.framebufferListener = (fw, fh) -> {
            InputEvent e = target.add(InputEvent.RESIZED, now());
            e.code = fw;
            e.device = fh;
        };
    }

    private static long now() {
        return System.nanoTime();
    }

    @Override
    public void poll(InputEventQueue queue) {
        for (int i = 0; i < pending.size(); i++) {
            InputEvent p = pending.get(i);
            InputEvent e = queue.add(p.type, p.timeNanos);
            e.code = p.code;
            e.device = p.device;
            e.dx = p.dx;
            e.dy = p.dy;
            e.value = p.value;
            e.modifiers = p.modifiers;
        }
        pending.clear();
        target = queue;
        try {
            glfwPollEvents();
        } finally {
            target = pending;
        }
        pollGamepads(queue);
    }

    private void pollGamepads(InputEventQueue queue) {
        for (int jid = 0; jid < MAX_PADS; jid++) {
            boolean present = glfwJoystickIsGamepad(jid);
            if (!present) {
                if (padPresent[jid]) {
                    // pad disconnected: release everything it held
                    for (int b = 0; b < PAD_BUTTONS; b++) {
                        if (padButtons[jid][b]) {
                            padButtons[jid][b] = false;
                            InputEvent e = queue.add(InputEvent.PAD_UP, now());
                            e.code = b;
                            e.device = jid;
                        }
                    }
                    for (int a = 0; a < PAD_AXES; a++) {
                        if (padAxes[jid][a] != 0f) {
                            padAxes[jid][a] = 0f;
                            InputEvent e = queue.add(InputEvent.PAD_AXIS, now());
                            e.code = a;
                            e.device = jid;
                            e.value = 0;
                        }
                    }
                    padPresent[jid] = false;
                }
                continue;
            }
            padPresent[jid] = true;
            if (!glfwGetGamepadState(jid, padState)) {
                continue;
            }
            for (int b = 0; b < PAD_BUTTONS; b++) {
                boolean down = padState.buttons(b) == GLFW_PRESS;
                if (down != padButtons[jid][b]) {
                    padButtons[jid][b] = down;
                    InputEvent e = queue.add(down ? InputEvent.PAD_DOWN : InputEvent.PAD_UP, now());
                    e.code = b;
                    e.device = jid;
                }
            }
            for (int a = 0; a < PAD_AXES; a++) {
                float v = padState.axes(a);
                if (Math.abs(v - padAxes[jid][a]) > AXIS_EPSILON) {
                    padAxes[jid][a] = v;
                    InputEvent e = queue.add(InputEvent.PAD_AXIS, now());
                    e.code = a;
                    e.device = jid;
                    e.value = v;
                }
            }
        }
    }

    @Override
    public void setMouseCaptured(boolean capture) {
        if (capture == captured) {
            return;
        }
        captured = capture;
        haveCursor = false;
        if (capture) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            if (glfwRawMouseMotionSupported()) {
                glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, org.lwjgl.glfw.GLFW.GLFW_TRUE);
                rawMotion = true;
            }
        } else {
            if (rawMotion) {
                glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, org.lwjgl.glfw.GLFW.GLFW_FALSE);
                rawMotion = false;
            }
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }
    }

    /** Whether raw (unaccelerated) mouse motion is active. */
    public boolean rawMotionActive() {
        return rawMotion;
    }

    /** Frees the native gamepad buffer. The window's callbacks are freed by the video sink. */
    void release() {
        padState.free();
    }
}
