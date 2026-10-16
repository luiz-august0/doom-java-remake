package doom.game.data;

import doom.game.view.DefsView;

/**
 * The presentation's read-only window on a session's definitions. Names go through
 * {@link GameStrings#translate(String)}, like Chocolate Doom's {@code DEH_String} calls at the point of use
 * (sprite, sound and music lump names), so a DEHACKED {@code Text} replacement is visible here.
 */
public final class SessionDefsView implements DefsView {

    private final GameDefs defs;
    private final GameStrings strings;

    public SessionDefsView(GameDefs defs, GameStrings strings) {
        this.defs = defs;
        this.strings = strings;
    }

    @Override
    public int spriteCount() {
        return defs.spriteNames().length;
    }

    @Override
    public String spriteName(int sprite) {
        return strings.translate(defs.spriteNames()[sprite]);
    }

    @Override
    public String sfxName(int sfx) {
        return strings.translate(defs.sounds()[sfx].name());
    }

    @Override
    public int sfxPriority(int sfx) {
        return defs.sounds()[sfx].priority;
    }

    @Override
    public int sfxLink(int sfx) {
        return defs.sounds()[sfx].link;
    }

    @Override
    public int sfxLinkPitch(int sfx) {
        return defs.sounds()[sfx].pitch;
    }

    @Override
    public int sfxLinkVolume(int sfx) {
        return defs.sounds()[sfx].volume;
    }

    @Override
    public boolean sfxSingularity(int sfx) {
        return defs.sounds()[sfx].singularity;
    }

    @Override
    public String musicName(int music) {
        String name = defs.musicNames()[music];
        return name == null ? null : strings.translate(name);
    }

    @Override
    public String text(String key) {
        return strings.get(key);
    }

    @Override
    public int weaponAmmo(int weapon) {
        return defs.weapons()[weapon].ammo;
    }
}
