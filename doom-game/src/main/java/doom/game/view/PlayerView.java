package doom.game.view;

/** One player ({@code player_t}). Weapon, ammo, card and power indices use {@link Weapon}, {@link AmmoType}, {@link Card}, {@link Power}. */
public interface PlayerView {

    boolean inGame();

    PlayerState state();

    /** Actor id of the player's body, or -1. */
    int actor();

    /** Body position and angle (0 when there is no body). */
    int x();

    int y();

    int z();

    int angle();

    /** Eye height in world coordinates ({@code viewz}). */
    int viewZ();

    /** {@code viewz} at the start of the last tic (interpolation). */
    int prevViewZ();

    int viewHeight();

    int deltaViewHeight();

    int bob();

    int health();

    int armorPoints();

    /** 0 none, 1 green, 2 blue. */
    int armorType();

    int readyWeapon();

    /** {@link Weapon#NO_CHANGE} when no change is pending. */
    int pendingWeapon();

    boolean weaponOwned(int weapon);

    int ammo(int ammoType);

    int maxAmmo(int ammoType);

    boolean card(int card);

    boolean backpack();

    /** Remaining tics of a power, or its flag value (see the original). */
    int power(int power);

    /** {@link CheatFlags} bits. */
    int cheats();

    int damageCount();

    int bonusCount();

    int extraLight();

    /** Fixed colormap (0 = none, 1 = light amplification, 32 = invulnerability) as in the original. */
    int fixedColormap();

    /** Player colour translation (0–3). */
    int colormap();

    /** Actor id of the last attacker, or -1. */
    int attacker();

    int killCount();

    int itemCount();

    int secretCount();

    int frags(int otherPlayer);

    boolean didSecret();

    /** {@code player->attackdown}: the attack button was held last tic (status bar rampage face). */
    default boolean attackDown() {
        return false;
    }

    /** Whether psprite {@code which} (0 = weapon, 1 = flash) has a state. */
    boolean pspriteActive(int which);

    int pspriteSprite(int which);

    /** Frame including {@link FrameFlags#FULLBRIGHT}. */
    int pspriteFrame(int which);

    /** Fixed point, 320×200 screen space. */
    int pspriteSx(int which);

    int pspriteSy(int which);

    /** Values at the start of the last tic (interpolation). */
    int pspritePrevSx(int which);

    int pspritePrevSy(int which);
}
