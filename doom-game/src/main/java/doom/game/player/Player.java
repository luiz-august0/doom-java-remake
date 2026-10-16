package doom.game.player;

import doom.game.actor.Actor;
import doom.game.api.TicCmd;
import doom.game.view.AmmoType;
import doom.game.view.Card;
import doom.game.view.Power;
import doom.game.view.Weapon;
import java.util.Arrays;

/** {@code player_t}. Field names follow the original (camel case). */
public final class Player {

    public static final int PST_LIVE = 0;
    public static final int PST_DEAD = 1;
    public static final int PST_REBORN = 2;

    public static final int PS_WEAPON = 0;
    public static final int PS_FLASH = 1;
    public static final int NUMPSPRITES = 2;

    /** Player number 0..3 ({@code player - players}). */
    public final int index;

    public Actor mo;
    public int playerState;
    /** The command of this tic (a copy, changed by {@code P_PlayerThink}). */
    public final TicCmd cmd = new TicCmd();
    /** Eye height in world coordinates. */
    public int viewZ;
    /** Base height above the floor for {@link #viewZ}. */
    public int viewHeight;
    /** Bob / squat speed. */
    public int deltaViewHeight;
    /** Bounded / scaled total momentum. */
    public int bob;
    public int health;
    public int armorPoints;
    /** 0 none, 1 green, 2 blue. */
    public int armorType;
    /** Remaining tics of each power (or its flag value). */
    public final int[] powers = new int[Power.COUNT];
    public final boolean[] cards = new boolean[Card.COUNT];
    public boolean backpack;
    public final int[] frags = new int[4];
    public int readyWeapon;
    public int pendingWeapon;
    public final boolean[] weaponOwned = new boolean[Weapon.COUNT];
    public final int[] ammo = new int[AmmoType.COUNT];
    public final int[] maxAmmo = new int[AmmoType.COUNT];
    /** True if the attack button was down last tic. */
    public boolean attackDown;
    public boolean useDown;
    /** {@link doom.game.view.CheatFlags} bits. */
    public int cheats;
    /** Refired shots are less accurate. */
    public int refire;
    public int killCount;
    public int itemCount;
    public int secretCount;
    /** Last HUD message, or null. */
    public String message;
    /** For screen flashing (red or bright). */
    public int damageCount;
    public int bonusCount;
    /** Who did damage (null for floors / ceilings). */
    public Actor attacker;
    /** So gun flashes light up areas. */
    public int extraLight;
    /** Current PLAYPAL colormap override (0 = none). */
    public int fixedColormap;
    /** Player colour translation (0..3). */
    public int colormap;
    public final PSprite[] psprites = {new PSprite(), new PSprite()};
    /** True if the secret level has been done. */
    public boolean didSecret;

    /** {@code viewz} at the start of the last tic (presentation only). */
    public int prevViewZ;

    public Player(int index) {
        this.index = index;
    }

    /**
     * {@code memset(p, 0, sizeof(*p))} of {@code G_PlayerReborn}: clears every field (the caller restores the ones the
     * original keeps).
     */
    public void clearAll() {
        mo = null;
        playerState = PST_LIVE;
        cmd.clear();
        viewZ = 0;
        viewHeight = 0;
        deltaViewHeight = 0;
        bob = 0;
        health = 0;
        armorPoints = 0;
        armorType = 0;
        Arrays.fill(powers, 0);
        Arrays.fill(cards, false);
        backpack = false;
        Arrays.fill(frags, 0);
        readyWeapon = 0;
        pendingWeapon = 0;
        Arrays.fill(weaponOwned, false);
        Arrays.fill(ammo, 0);
        Arrays.fill(maxAmmo, 0);
        attackDown = false;
        useDown = false;
        cheats = 0;
        refire = 0;
        killCount = 0;
        itemCount = 0;
        secretCount = 0;
        message = null;
        damageCount = 0;
        bonusCount = 0;
        attacker = null;
        extraLight = 0;
        fixedColormap = 0;
        colormap = 0;
        psprites[0].clear();
        psprites[1].clear();
        didSecret = false;
        prevViewZ = 0;
    }

    @Override
    public String toString() {
        return "Player[" + index + ", state=" + playerState + ", health=" + health + "]";
    }
}
