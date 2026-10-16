/**
 * LWJGL backend (ADR-003): GLFW window and input, OpenGL 3.3 core presentation of the indexed frame.
 */
module doom.platform.lwjgl {
    requires doom.platform;
    requires org.lwjgl;
    requires org.lwjgl.glfw;
    requires org.lwjgl.opengl;
    requires org.lwjgl.natives;
    requires org.lwjgl.glfw.natives;
    requires org.lwjgl.opengl.natives;

    provides doom.platform.PlatformProvider with doom.platform.lwjgl.LwjglPlatformProvider;
}
