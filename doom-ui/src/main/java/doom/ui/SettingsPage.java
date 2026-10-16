package doom.ui;

import java.util.List;

/**
 * A text submenu of the extended options (spec 07 §2). The pages of {@link UiSettings#extendedPages()} are listed in
 * an "Extended Options" entry of the Options menu, in order.
 *
 * @param title the name shown in the list and as the page title (e.g. "Video")
 * @param items rows, top to bottom
 */
public record SettingsPage(String title, List<SettingsItem> items) {
}
