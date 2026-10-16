package doom.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One row of an extended options page (Video, Controls, Audio, Compatibility). The engine builds these on top of its
 * configuration; the UI only renders them with the WAD font and edits them with the menu keys.
 */
public sealed interface SettingsItem {

    /** Text shown on the left. */
    String label();

    /** On/off switch: Enter, left and right flip it. */
    record Toggle(String label, BooleanSupplier get, java.util.function.Consumer<Boolean> set) implements SettingsItem {
    }

    /** One of several named values: left/right step through {@code options} (wrapping), Enter steps forward. */
    record Choice(String label, List<String> options, IntSupplier get, IntConsumer set) implements SettingsItem {
    }

    /** Integer in {@code [min, max]}: left/right change by {@code step}. {@code format} may be null (plain number). */
    record Slider(String label, int min, int max, int step, IntSupplier get, IntConsumer set,
                  IntFunction<String> format) implements SettingsItem {
    }

    /** Runs {@code run} on Enter; {@code value} (may be null) is shown on the right, e.g. a key binding. */
    record Action(String label, Supplier<String> value, Runnable run) implements SettingsItem {
    }

    /** Read-only information row. */
    record Info(String label, Supplier<String> value) implements SettingsItem {
    }
}
