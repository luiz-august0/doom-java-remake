package doom.ui.menu;

/** Where the menu puts HUD messages ({@code players[consoleplayer].message}). */
@FunctionalInterface
public interface MessageSink {

    /**
     * @param text            the message
     * @param dontFuckWithMe  {@code message_dontfuckwithme}: shown even with messages switched off
     */
    void post(String text, boolean dontFuckWithMe);
}
