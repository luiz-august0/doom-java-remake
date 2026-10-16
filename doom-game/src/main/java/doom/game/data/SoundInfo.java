package doom.game.data;

/**
 * One entry of the sound effect table ({@code sfxinfo_t}). The name is the lump name without the {@code DS}
 * prefix; DEHACKED {@code Text} replacements apply to it through {@link GameStrings#translate(String)}.
 */
public final class SoundInfo {

    private final String name;
    /** Only one instance of this sound plays at a time. */
    public boolean singularity;
    public int priority;
    /** The sound this one is an alias of ({@link doom.game.view.SoundId}), or -1. */
    public int link;
    /** Pitch of the link; -1 when not a link. */
    public int pitch;
    /** Volume of the link; -1 when not a link. */
    public int volume;

    public SoundInfo(String name, boolean singularity, int priority, int link, int pitch, int volume) {
        this.name = name;
        this.singularity = singularity;
        this.priority = priority;
        this.link = link;
        this.pitch = pitch;
        this.volume = volume;
    }

    /** Lump name without the {@code DS} prefix, e.g. {@code "pistol"}. */
    public String name() {
        return name;
    }

    /** A deep copy. */
    public SoundInfo copy() {
        return new SoundInfo(name, singularity, priority, link, pitch, volume);
    }
}
