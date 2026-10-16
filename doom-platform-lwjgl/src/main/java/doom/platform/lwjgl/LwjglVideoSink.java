package doom.platform.lwjgl;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_DECORATED;
import static org.lwjgl.glfw.GLFW.GLFW_DONT_CARE;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_API;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetMonitorPos;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowAttrib;
import static org.lwjgl.glfw.GLFW.glfwSetWindowMonitor;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glClear;
import static org.lwjgl.opengl.GL11C.glClearColor;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL11C.glPixelStorei;
import static org.lwjgl.opengl.GL11C.glReadPixels;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glTexSubImage2D;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUniform2i;
import static org.lwjgl.opengl.GL20C.glUniform4f;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_R8;
import static org.lwjgl.opengl.GL30C.GL_RED;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;

import doom.platform.Clock;
import doom.platform.ScaleFilter;
import doom.platform.VideoConfig;
import doom.platform.VideoSink;
import doom.platform.WindowMode;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/**
 * GLFW window + OpenGL 3.3 core presentation of an indexed frame (ADR-003, ADR-009, spec 04 §8).
 *
 * <p>Each frame the {@code byte[]} of palette indices is copied into a pre-allocated direct buffer and uploaded to a
 * {@code GL_R8} texture with {@code glTexSubImage2D}; the fragment shader resolves colours through a 256×1 RGBA palette
 * texture (re-uploaded only when it changes). Column-major frames are uploaded as a transposed texture and sampled
 * transposed. The frame is drawn on a quad whose rectangle comes from {@link PresentGeometry}.
 *
 * <p>All methods must be called from the main thread (on macOS the JVM must run with {@code -XstartOnFirstThread}).
 */
public final class LwjglVideoSink implements VideoSink {

    private static final System.Logger LOG = System.getLogger("doom.platform.lwjgl");

    private static final String VERTEX_SHADER = """
            #version 330 core
            uniform vec4 uRect;
            out vec2 vUv;
            void main() {
                vec2 c = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1));
                vUv = vec2(c.x, 1.0 - c.y);
                gl_Position = vec4(mix(uRect.xy, uRect.zw, c), 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            uniform sampler2D uIndex;
            uniform sampler2D uPalette;
            uniform ivec2 uFrameSize;
            uniform int uColumnMajor;
            uniform int uSharp;
            uniform vec2 uScale;
            in vec2 vUv;
            out vec4 fragColor;

            vec4 lookup(ivec2 p) {
                p = clamp(p, ivec2(0), uFrameSize - 1);
                ivec2 tp = (uColumnMajor != 0) ? ivec2(p.y, p.x) : p;
                int idx = int(texelFetch(uIndex, tp, 0).r * 255.0 + 0.5);
                return texelFetch(uPalette, ivec2(idx, 0), 0);
            }

            void main() {
                vec2 pos = vUv * vec2(uFrameSize);
                if (uSharp == 0) {
                    fragColor = lookup(ivec2(floor(pos)));
                    return;
                }
                vec2 scale = max(uScale, vec2(1.0));
                vec2 range = 0.5 - 0.5 / scale;
                vec2 d = fract(pos) - 0.5;
                vec2 q = floor(pos) + 0.5 + (d - clamp(d, -range, range)) * scale;
                vec2 b = floor(q - 0.5);
                vec2 w = (q - 0.5) - b;
                ivec2 bi = ivec2(b);
                vec4 c00 = lookup(bi);
                vec4 c10 = lookup(bi + ivec2(1, 0));
                vec4 c01 = lookup(bi + ivec2(0, 1));
                vec4 c11 = lookup(bi + ivec2(1, 1));
                fragColor = mix(mix(c00, c10, w.x), mix(c01, c11, w.x), w.y);
            }
            """;

    private final Clock clock;

    private VideoConfig config;
    long window;
    private boolean glfwInitialised;

    private int program;
    private int vao;
    private int indexTexture;
    private int paletteTexture;
    private int uRect;
    private int uFrameSize;
    private int uColumnMajor;
    private int uSharp;
    private int uScale;

    private int texWidth;
    private int texHeight;
    private ByteBuffer staging;
    private ByteBuffer paletteStaging = MemoryUtil.memCalloc(1024);
    private final byte[] paletteRgb = new byte[768];
    private boolean paletteDirty = true;

    private int frameWidth;
    private int frameHeight;
    private boolean frameColumnMajor;
    private boolean hasFrame;

    private int outputWidth;
    private int outputHeight;
    private int windowedX = 100;
    private int windowedY = 100;
    private int windowedWidth;
    private int windowedHeight;
    private boolean appliedVsync;

    private long nextFrameNanos;
    private final int[] tmpA = new int[1];
    private final int[] tmpB = new int[1];

    /** Creates a sink using the system clock for the frame limiter. */
    public LwjglVideoSink() {
        this(Clock.system());
    }

    /** Creates a sink with an explicit clock (tests). */
    public LwjglVideoSink(Clock clock) {
        this.clock = clock;
        Arrays.fill(paletteRgb, (byte) 0);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------------------

    @Override
    public void open(VideoConfig cfg) {
        if (window != 0) {
            throw new IllegalStateException("already open");
        }
        LwjglPlatformProvider.requireMainThreadOnMac();
        this.config = cfg;
        glfwSetErrorCallback((error, description) -> LOG.log(System.Logger.Level.ERROR, "GLFW error 0x"
                + Integer.toHexString(error) + ": " + org.lwjgl.glfw.GLFWErrorCallback.getDescription(description)));
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW could not be initialised (no display?)");
        }
        glfwInitialised = true;
        try {
            createWindow(cfg);
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glfwSwapInterval(cfg.vsync() ? 1 : 0);
            appliedVsync = cfg.vsync();
            createResources();
            glfwSetFramebufferSizeCallback(window, (w, fw, fh) -> onFramebufferSize(fw, fh));
            updateOutputSize();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
        LOG.log(System.Logger.Level.INFO, "window opened: output " + outputWidth + "x" + outputHeight + ", vsync "
                + cfg.vsync());
    }

    private void createWindow(VideoConfig cfg) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        windowedWidth = Math.max(1, cfg.width());
        windowedHeight = Math.max(1, cfg.height());
        long monitor = 0;
        int w = windowedWidth;
        int h = windowedHeight;
        int x = 0;
        int y = 0;
        if (cfg.mode() != WindowMode.WINDOWED) {
            long primary = glfwGetPrimaryMonitor();
            GLFWVidMode mode = primary != 0 ? glfwGetVideoMode(primary) : null;
            if (mode != null) {
                w = mode.width();
                h = mode.height();
                glfwGetMonitorPos(primary, tmpA, tmpB);
                x = tmpA[0];
                y = tmpB[0];
            }
            if (cfg.mode() == WindowMode.FULLSCREEN) {
                monitor = primary;
            } else {
                glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
            }
        }
        window = glfwCreateWindow(w, h, cfg.title(), monitor, 0);
        if (window == 0) {
            throw new IllegalStateException("could not create an OpenGL 3.3 core window (see GLFW error above)");
        }
        if (cfg.mode() == WindowMode.BORDERLESS) {
            org.lwjgl.glfw.GLFW.glfwSetWindowPos(window, x, y);
        }
    }

    private void createResources() {
        program = glCreateProgram();
        int vs = compile(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("shader link failed: " + glGetProgramInfoLog(program));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "uIndex"), 0);
        glUniform1i(glGetUniformLocation(program, "uPalette"), 1);
        uRect = glGetUniformLocation(program, "uRect");
        uFrameSize = glGetUniformLocation(program, "uFrameSize");
        uColumnMajor = glGetUniformLocation(program, "uColumnMajor");
        uSharp = glGetUniformLocation(program, "uSharp");
        uScale = glGetUniformLocation(program, "uScale");

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        indexTexture = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, indexTexture);
        setNearest();
        paletteTexture = glGenTextures();
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, paletteTexture);
        setNearest();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 256, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glActiveTexture(GL_TEXTURE0);
        glClearColor(0f, 0f, 0f, 1f);
    }

    private static void setNearest() {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S,
                org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T,
                org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("shader compile failed: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    @Override
    public void close() {
        if (closeHook != null) {
            closeHook.run();
            closeHook = null;
        }
        framebufferListener = null;
        if (window != 0) {
            try {
                glfwMakeContextCurrent(window);
                if (program != 0) {
                    glDeleteProgram(program);
                    glDeleteVertexArrays(vao);
                    glDeleteTextures(indexTexture);
                    glDeleteTextures(paletteTexture);
                }
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "GL cleanup failed", e);
            }
            program = 0;
            org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
            window = 0;
        }
        if (staging != null) {
            MemoryUtil.memFree(staging);
            staging = null;
        }
        if (paletteStaging != null) {
            MemoryUtil.memFree(paletteStaging);
            paletteStaging = null;
        }
        if (glfwInitialised) {
            glfwTerminate();
            org.lwjgl.glfw.GLFWErrorCallback cb = glfwSetErrorCallback(null);
            if (cb != null) {
                cb.free();
            }
            glfwInitialised = false;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // VideoSink
    // ---------------------------------------------------------------------------------------------------------

    @Override
    public void reconfigure(VideoConfig cfg) {
        checkOpen();
        VideoConfig old = config;
        config = cfg;
        if (!cfg.title().equals(old.title())) {
            glfwSetWindowTitle(window, cfg.title());
        }
        if (cfg.mode() != old.mode()) {
            applyMode(old.mode(), cfg);
        } else if (cfg.mode() == WindowMode.WINDOWED
                && (cfg.width() != old.width() || cfg.height() != old.height())) {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(window, Math.max(1, cfg.width()), Math.max(1, cfg.height()));
            windowedWidth = Math.max(1, cfg.width());
            windowedHeight = Math.max(1, cfg.height());
        }
        if (cfg.vsync() != appliedVsync) {
            glfwSwapInterval(cfg.vsync() ? 1 : 0);
            appliedVsync = cfg.vsync();
        }
        updateOutputSize();
    }

    private void applyMode(WindowMode from, VideoConfig cfg) {
        if (from == WindowMode.WINDOWED) {
            glfwGetWindowPos(window, tmpA, tmpB);
            windowedX = tmpA[0];
            windowedY = tmpB[0];
            glfwGetWindowSize(window, tmpA, tmpB);
            windowedWidth = tmpA[0];
            windowedHeight = tmpB[0];
        }
        long primary = glfwGetPrimaryMonitor();
        GLFWVidMode vm = primary != 0 ? glfwGetVideoMode(primary) : null;
        switch (cfg.mode()) {
            case WINDOWED -> {
                glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_TRUE);
                int w = cfg.width() > 0 ? cfg.width() : windowedWidth;
                int h = cfg.height() > 0 ? cfg.height() : windowedHeight;
                glfwSetWindowMonitor(window, 0, windowedX, windowedY, w, h, GLFW_DONT_CARE);
            }
            case BORDERLESS -> {
                if (vm != null) {
                    glfwGetMonitorPos(primary, tmpA, tmpB);
                    glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_FALSE);
                    glfwSetWindowMonitor(window, 0, tmpA[0], tmpB[0], vm.width(), vm.height(), GLFW_DONT_CARE);
                }
            }
            case FULLSCREEN -> {
                if (vm != null) {
                    glfwSetWindowMonitor(window, primary, 0, 0, vm.width(), vm.height(), vm.refreshRate());
                }
            }
            default -> throw new IllegalStateException();
        }
        glfwSwapInterval(cfg.vsync() ? 1 : 0);
        appliedVsync = cfg.vsync();
    }

    /** Receives output size changes (physical pixels); set by the input source. */
    interface FramebufferListener {
        void resized(int width, int height);
    }

    FramebufferListener framebufferListener;
    /** Run once when the sink closes (the input source frees its native buffers here). */
    Runnable closeHook;

    void onFramebufferSize(int fw, int fh) {
        outputWidth = fw;
        outputHeight = fh;
        FramebufferListener l = framebufferListener;
        if (l != null) {
            l.resized(fw, fh);
        }
    }

    private void updateOutputSize() {
        glfwGetFramebufferSize(window, tmpA, tmpB);
        outputWidth = tmpA[0];
        outputHeight = tmpB[0];
    }

    private void checkOpen() {
        if (window == 0) {
            throw new IllegalStateException("video sink is not open");
        }
    }

    @Override
    public int outputWidth() {
        return outputWidth;
    }

    @Override
    public int outputHeight() {
        return outputHeight;
    }

    /** The native GLFW window handle, for the input source. */
    long windowHandle() {
        return window;
    }

    @Override
    public void setPalette(byte[] rgb768) {
        if (rgb768.length < 768) {
            throw new IllegalArgumentException("palette needs 768 bytes, got " + rgb768.length);
        }
        if (!paletteDirty && Arrays.equals(paletteRgb, 0, 768, rgb768, 0, 768)) {
            return;
        }
        System.arraycopy(rgb768, 0, paletteRgb, 0, 768);
        for (int i = 0; i < 256; i++) {
            paletteStaging.put(i * 4, paletteRgb[i * 3]);
            paletteStaging.put(i * 4 + 1, paletteRgb[i * 3 + 1]);
            paletteStaging.put(i * 4 + 2, paletteRgb[i * 3 + 2]);
            paletteStaging.put(i * 4 + 3, (byte) 0xFF);
        }
        paletteDirty = true;
    }

    @Override
    public void present(byte[] indexed, int width, int height, boolean columnMajor) {
        checkOpen();
        if (width <= 0 || height <= 0 || indexed.length < width * height) {
            throw new IllegalArgumentException("bad frame " + width + "x" + height + " in " + indexed.length + " bytes");
        }
        glfwMakeContextCurrent(window);
        updateOutputSize();
        uploadPalette();
        uploadFrame(indexed, width, height, columnMajor);
        draw();
        glfwSwapBuffers(window);
        limit();
    }

    private void uploadPalette() {
        if (!paletteDirty) {
            return;
        }
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, paletteTexture);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 256, 1, GL_RGBA, GL_UNSIGNED_BYTE, paletteStaging);
        glActiveTexture(GL_TEXTURE0);
        paletteDirty = false;
    }

    private void uploadFrame(byte[] indexed, int width, int height, boolean columnMajor) {
        int n = width * height;
        if (staging == null || staging.capacity() < n) {
            if (staging != null) {
                MemoryUtil.memFree(staging);
            }
            staging = MemoryUtil.memAlloc(n);
        }
        staging.put(0, indexed, 0, n);
        int tw = columnMajor ? height : width;
        int th = columnMajor ? width : height;
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, indexTexture);
        if (tw != texWidth || th != texHeight) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, tw, th, 0, GL_RED, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            texWidth = tw;
            texHeight = th;
        }
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, tw, th, GL_RED, GL_UNSIGNED_BYTE, staging);
        frameWidth = width;
        frameHeight = height;
        frameColumnMajor = columnMajor;
        hasFrame = true;
    }

    /** Draws the last uploaded frame into the back buffer. */
    private void draw() {
        glViewport(0, 0, outputWidth, outputHeight);
        glClear(GL_COLOR_BUFFER_BIT);
        if (!hasFrame || outputWidth <= 0 || outputHeight <= 0) {
            return;
        }
        PresentGeometry.Rect r = PresentGeometry.compute(config.scaleMode(), outputWidth, outputHeight, frameWidth,
                frameHeight, config.pixelAspect());
        float x0 = 2f * r.x() / outputWidth - 1f;
        float x1 = 2f * (r.x() + r.width()) / outputWidth - 1f;
        float yTop = 1f - 2f * r.y() / outputHeight;
        float yBottom = 1f - 2f * (r.y() + r.height()) / outputHeight;
        glUseProgram(program);
        glBindVertexArray(vao);
        glUniform4f(uRect, x0, yBottom, x1, yTop);
        glUniform2i(uFrameSize, frameWidth, frameHeight);
        glUniform1i(uColumnMajor, frameColumnMajor ? 1 : 0);
        glUniform1i(uSharp, config.filter() == ScaleFilter.SHARP_BILINEAR ? 1 : 0);
        glUniform2f(uScale, r.width() / (float) frameWidth, r.height() / (float) frameHeight);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, indexTexture);
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, paletteTexture);
        glActiveTexture(GL_TEXTURE0);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }

    /** Hybrid frame limiter (spec 04 §7) when vsync is off. */
    private void limit() {
        if (config.vsync() || config.maxFps() <= 0) {
            nextFrameNanos = 0;
            return;
        }
        long frame = 1_000_000_000L / config.maxFps();
        long now = clock.nanoTime();
        if (nextFrameNanos == 0 || now - nextFrameNanos > frame) {
            nextFrameNanos = now;
        }
        nextFrameNanos += frame;
        clock.waitUntil(nextFrameNanos);
    }

    @Override
    public boolean closeRequested() {
        return window != 0 && glfwWindowShouldClose(window);
    }

    @Override
    public byte[] readBackOutput() {
        checkOpen();
        glfwMakeContextCurrent(window);
        updateOutputSize();
        int w = outputWidth;
        int h = outputHeight;
        // Re-render the last frame into the back buffer (its content is undefined after a swap) and read it.
        draw();
        ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
        try {
            org.lwjgl.opengl.GL11C.glReadBuffer(org.lwjgl.opengl.GL11C.GL_BACK);
            glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            int err = glGetError();
            if (err != 0) {
                throw new IllegalStateException("glReadPixels failed: 0x" + Integer.toHexString(err));
            }
            byte[] out = new byte[w * h * 4];
            int stride = w * 4;
            for (int y = 0; y < h; y++) {
                buf.get((h - 1 - y) * stride, out, y * stride, stride);
            }
            return out;
        } finally {
            MemoryUtil.memFree(buf);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        }
    }
}
