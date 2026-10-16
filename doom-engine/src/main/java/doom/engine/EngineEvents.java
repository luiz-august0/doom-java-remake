package doom.engine;

import doom.game.api.DemoPlayback;
import doom.game.api.SimEvents;
import doom.sound.SoundSystem;
import doom.ui.Ui;

/**
 * Receives the synchronous {@link SimEvents} of the session and forwards them in the original order to the sound
 * system, the UI and the engine itself. The targets are set after the session exists (the session needs the events
 * object at creation).
 */
final class EngineEvents implements SimEvents {

    /** What the engine itself does with the events that are not sound or UI. */
    interface Listener {
        void forceWipe();

        void gameSaved(int slot, String description, byte[] data);

        void demoFinished(DemoPlayback mode, int gametics);

        void levelLoaded();

        void intermissionStarted();

        void finaleStarted();
    }

    private SoundSystem sound;
    private Ui ui;
    private Listener listener;

    void bind(SoundSystem sound, Ui ui, Listener listener) {
        this.sound = sound;
        this.ui = ui;
        this.listener = listener;
    }

    @Override
    public void startSound(int origin, int sfx) {
        if (sound != null) {
            sound.startSound(origin, sfx);
        }
    }

    @Override
    public void stopSound(int origin) {
        if (sound != null) {
            sound.stopSound(origin);
        }
    }

    @Override
    public void changeMusic(int music, boolean looping) {
        if (sound != null) {
            sound.changeMusic(music, looping);
        }
    }

    @Override
    public void pauseSound(boolean paused) {
        if (sound != null) {
            sound.pauseSound(paused);
        }
    }

    @Override
    public void playerMessage(int player, String text) {
        if (ui != null) {
            ui.playerMessage(player, text);
        }
    }

    @Override
    public void levelLoaded() {
        if (sound != null) {
            sound.levelLoaded();
        }
        if (ui != null) {
            ui.levelLoaded();
        }
        if (listener != null) {
            listener.levelLoaded();
        }
    }

    @Override
    public void levelTick() {
        if (ui != null) {
            ui.levelTick();
        }
    }

    @Override
    public void intermissionStarted() {
        if (listener != null) {
            listener.intermissionStarted();
        }
        if (ui != null) {
            ui.intermissionStarted();
        }
    }

    @Override
    public void finaleStarted() {
        if (listener != null) {
            listener.finaleStarted();
        }
        if (ui != null) {
            ui.finaleStarted();
        }
    }

    @Override
    public void forceWipe() {
        if (listener != null) {
            listener.forceWipe();
        }
    }

    @Override
    public void gameSaved(int slot, String description, byte[] data) {
        if (listener != null) {
            listener.gameSaved(slot, description, data);
        }
    }

    @Override
    public void demoFinished(DemoPlayback mode, int gametics) {
        if (listener != null) {
            listener.demoFinished(mode, gametics);
        }
    }
}
