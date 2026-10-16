package doom.sound;

import doom.sound.mixer.SfxSample;
import doom.wad.audio.MusicScore;

/** Source of sound effects and songs, by lump name. Implementations may cache. */
public interface SoundData {

    /**
     * The decoded effect for lump {@code DS<name>}.
     *
     * @param name sound name without the {@code DS} prefix, e.g. {@code "pistol"}
     * @return the sample, or {@code null} when the lump is missing or not a valid DMX sound
     */
    SfxSample sfx(String name);

    /** Whether a song lump named {@code D_<name>} exists. */
    boolean hasMusic(String name);

    /**
     * The song for lump {@code D_<name>}.
     *
     * @param name music name without the {@code D_} prefix, e.g. {@code "e1m1"}
     * @return the score, or {@code null} when the lump is missing or cannot be decoded
     */
    MusicScore music(String name);
}
