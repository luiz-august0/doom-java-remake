package doom.game.api;

/** How a demo is played back; decides what happens when it ends ({@code G_CheckDemoStatus}). */
public enum DemoPlayback {
    /** Part of the title loop: the loop advances when the demo ends. */
    TITLE,
    /** {@code -playdemo}: the program quits when the demo ends. */
    SINGLE,
    /** {@code -timedemo}: the engine reports timing and quits when the demo ends. */
    TIMEDEMO
}
