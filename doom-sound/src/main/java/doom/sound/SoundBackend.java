package doom.sound;

import doom.sound.mixer.SfxSample;
import doom.wad.audio.MusicScore;

/**
 * What {@link SoundSystem} needs from the audio side, called from the main thread only. The real implementation
 * ({@link AudioEngineBackend}) posts commands to the audio thread; {@link NullSoundBackend} keeps the same channel
 * bookkeeping without producing sound, so that headless runs follow the same logic.
 */
public interface SoundBackend {

    /** {@code I_StartSound}: starts {@code sample} on {@code channel} ({@code sample} may be null: nothing audible). */
    void startSfx(int channel, SfxSample sample, int vol, int sep, int pitch);

    /** {@code I_UpdateSoundParams}. */
    void updateChannel(int channel, int vol, int sep);

    /** {@code I_StopSound}. */
    void stopChannel(int channel);

    /** {@code I_SoundIsPlaying}. */
    boolean isPlaying(int channel);

    /** Stops every effect. */
    void stopAllSfx();

    /** {@code I_PlaySong}; clears a pause. */
    void playMusic(MusicScore score, boolean looping);

    /** {@code I_StopSong}. */
    void stopMusic();

    /** {@code I_PauseSong}. */
    void pauseMusic();

    /** {@code I_ResumeSong}. */
    void resumeMusic();

    /** Music volume on the menu scale 0..15 ({@code S_SetMusicVolume(level * 8)}). */
    void setMusicVolume(int level);

    /** Called once per {@link SoundSystem#updateSounds} with the current game tic (headless time base). */
    default void update(int gameTic) {
    }
}
