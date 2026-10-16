package doom.game.view;

/** Game definitions in effect for this session (after DEHACKED): names and properties the presentation needs. */
public interface DefsView {

    int spriteCount();

    /** Four-letter sprite name, e.g. {@code "TROO"}. */
    String spriteName(int sprite);

    /** Sound name without the {@code DS} prefix, e.g. {@code "pistol"} (lump {@code DSPISTOL}). */
    String sfxName(int sfx);

    int sfxPriority(int sfx);

    /** Linked sound ({@code sfx->link}), or -1. */
    int sfxLink(int sfx);

    int sfxLinkPitch(int sfx);

    int sfxLinkVolume(int sfx);

    boolean sfxSingularity(int sfx);

    /** Music name without the {@code D_} prefix, e.g. {@code "e1m1"}. */
    String musicName(int music);

    /** Current text for a string identified by its BEX mnemonic (e.g. {@code "HUSTR_E1M1"}, {@code "QUITMSG"}). */
    String text(String key);

    /** Ammo type used by a weapon ({@code weaponinfo[w].ammo}); {@link AmmoType#NO_AMMO} for none. */
    int weaponAmmo(int weapon);
}
