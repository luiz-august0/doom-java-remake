package doom.game.player;

import doom.game.actor.Actor;
import doom.game.data.MiscSettings;
import doom.game.data.SpriteId;
import doom.game.view.AmmoType;
import doom.game.view.Card;
import doom.game.view.MobjFlags;
import doom.game.view.Power;
import doom.game.view.SoundId;
import doom.game.view.Weapon;
import doom.game.world.World;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameMode;

/** Items and inventory: {@code P_Give*} and {@code P_TouchSpecialThing} ({@code p_inter.c}). */
public final class Pickups {

    public static final int BONUSADD = 6;
    /** {@code MAXHEALTH}. */
    public static final int MAXHEALTH = 100;
    public static final int INVULNTICS = 30 * World.TICRATE;
    public static final int INVISTICS = 60 * World.TICRATE;
    public static final int INFRATICS = 120 * World.TICRATE;
    public static final int IRONTICS = 60 * World.TICRATE;

    private final World world;

    public Pickups(World world) {
        this.world = world;
    }

    /**
     * {@code P_GiveAmmo}: {@code num} is the number of clip loads, not the individual count (0 = 1/2 clip).
     *
     * @return false if the ammo can't be picked up at all
     */
    @VanillaRef("p_inter.c:P_GiveAmmo")
    public boolean giveAmmo(Player player, int ammo, int num) {
        if (ammo == AmmoType.NO_AMMO) {
            return false;
        }
        if (ammo < 0 || ammo >= AmmoType.COUNT) {
            throw new DoomFatalException("P_GiveAmmo: bad type " + ammo);
        }
        if (player.ammo[ammo] == player.maxAmmo[ammo]) {
            return false;
        }

        int[] clipAmmo = world.defs.clipAmmo();
        if (num != 0) {
            num *= clipAmmo[ammo];
        } else {
            num = clipAmmo[ammo] / 2;
        }

        if (world.gameSkill == 0 || world.gameSkill == 4) {
            // give double ammo in trainer mode, you'll need in nightmare
            num <<= 1;
        }

        int oldammo = player.ammo[ammo];
        player.ammo[ammo] += num;

        if (player.ammo[ammo] > player.maxAmmo[ammo]) {
            player.ammo[ammo] = player.maxAmmo[ammo];
        }

        // If non zero ammo, don't change up weapons, player was lower on purpose.
        if (oldammo != 0) {
            return true;
        }

        // We were down to zero, so select a new weapon. Preferences are not user selectable.
        switch (ammo) {
            case AmmoType.CLIP -> {
                if (player.readyWeapon == Weapon.FIST) {
                    if (player.weaponOwned[Weapon.CHAINGUN]) {
                        player.pendingWeapon = Weapon.CHAINGUN;
                    } else {
                        player.pendingWeapon = Weapon.PISTOL;
                    }
                }
            }
            case AmmoType.SHELL -> {
                if (player.readyWeapon == Weapon.FIST || player.readyWeapon == Weapon.PISTOL) {
                    if (player.weaponOwned[Weapon.SHOTGUN]) {
                        player.pendingWeapon = Weapon.SHOTGUN;
                    }
                }
            }
            case AmmoType.CELL -> {
                if (player.readyWeapon == Weapon.FIST || player.readyWeapon == Weapon.PISTOL) {
                    if (player.weaponOwned[Weapon.PLASMA]) {
                        player.pendingWeapon = Weapon.PLASMA;
                    }
                }
            }
            case AmmoType.MISSILE -> {
                if (player.readyWeapon == Weapon.FIST) {
                    if (player.weaponOwned[Weapon.MISSILE]) {
                        player.pendingWeapon = Weapon.MISSILE;
                    }
                }
            }
            default -> {
            }
        }
        return true;
    }

    /** {@code P_GiveWeapon}: the weapon name may have a MF_DROPPED flag ored in. */
    @VanillaRef("p_inter.c:P_GiveWeapon")
    public boolean giveWeapon(Player player, int weapon, boolean dropped) {
        int weaponAmmo = world.defs.weapons()[weapon].ammo;
        if (world.netGame && world.deathmatch != 2 && !dropped) {
            // leave placed weapons forever on net games
            if (player.weaponOwned[weapon]) {
                return false;
            }
            player.bonusCount += BONUSADD;
            player.weaponOwned[weapon] = true;

            if (world.deathmatch != 0) {
                giveAmmo(player, weaponAmmo, 5);
            } else {
                giveAmmo(player, weaponAmmo, 2);
            }
            player.pendingWeapon = weapon;

            if (player == world.players[world.consolePlayer]) {
                world.startSound(null, SoundId.WPNUP);
            }
            return false;
        }

        boolean gaveammo;
        if (weaponAmmo != AmmoType.NO_AMMO) {
            // give one clip with a dropped weapon, two clips with a found weapon
            if (dropped) {
                gaveammo = giveAmmo(player, weaponAmmo, 1);
            } else {
                gaveammo = giveAmmo(player, weaponAmmo, 2);
            }
        } else {
            gaveammo = false;
        }

        boolean gaveweapon;
        if (player.weaponOwned[weapon]) {
            gaveweapon = false;
        } else {
            gaveweapon = true;
            player.weaponOwned[weapon] = true;
            player.pendingWeapon = weapon;
        }
        return gaveweapon || gaveammo;
    }

    /** {@code P_GiveBody}: returns false if the body isn't needed at all. */
    @VanillaRef("p_inter.c:P_GiveBody")
    public boolean giveBody(Player player, int num) {
        if (player.health >= MAXHEALTH) {
            return false;
        }
        player.health += num;
        if (player.health > MAXHEALTH) {
            player.health = MAXHEALTH;
        }
        player.mo.health = player.health;
        return true;
    }

    /** {@code P_GiveArmor}: returns false if the armor is worse than the current armor. */
    @VanillaRef("p_inter.c:P_GiveArmor")
    public boolean giveArmor(Player player, int armortype) {
        int hits = armortype * 100;
        if (player.armorPoints >= hits) {
            return false; // don't pick up
        }
        player.armorType = armortype;
        player.armorPoints = hits;
        return true;
    }

    /** {@code P_GiveCard}. */
    @VanillaRef("p_inter.c:P_GiveCard")
    public void giveCard(Player player, int card) {
        if (player.cards[card]) {
            return;
        }
        player.bonusCount = BONUSADD;
        player.cards[card] = true;
    }

    /** {@code P_GivePower}. */
    @VanillaRef("p_inter.c:P_GivePower")
    public boolean givePower(Player player, int power) {
        if (power == Power.INVULNERABILITY) {
            player.powers[power] = INVULNTICS;
            return true;
        }
        if (power == Power.INVISIBILITY) {
            player.powers[power] = INVISTICS;
            player.mo.flags |= MobjFlags.SHADOW;
            return true;
        }
        if (power == Power.INFRARED) {
            player.powers[power] = INFRATICS;
            return true;
        }
        if (power == Power.IRONFEET) {
            player.powers[power] = IRONTICS;
            return true;
        }
        if (power == Power.STRENGTH) {
            giveBody(player, 100);
            player.powers[power] = 1;
            return true;
        }
        if (player.powers[power] != 0) {
            return false; // already got it
        }
        player.powers[power] = 1;
        return true;
    }

    private void message(Player player, String key) {
        String text = world.strings.get(key);
        player.message = text;
        world.events.playerMessage(player.index, text);
    }

    /** {@code P_TouchSpecialThing}. */
    @VanillaRef("p_inter.c:P_TouchSpecialThing")
    public void touchSpecialThing(Actor special, Actor toucher) {
        int delta = special.z - toucher.z;

        if (delta > toucher.height || delta < -8 * Fixed.FRACUNIT) {
            // out of reach
            return;
        }

        int sound = SoundId.ITEMUP;
        Player player = toucher.player;

        // Dead thing touching. Can happen with a sliding player corpse.
        if (toucher.health <= 0) {
            return;
        }

        MiscSettings misc = world.defs.misc();
        // Identify by sprite.
        switch (special.sprite) {
            // armor
            case SpriteId.ARM1 -> {
                if (!giveArmor(player, misc.greenArmorClass())) {
                    return;
                }
                message(player, "GOTARMOR");
            }
            case SpriteId.ARM2 -> {
                if (!giveArmor(player, misc.blueArmorClass())) {
                    return;
                }
                message(player, "GOTMEGA");
            }

            // bonus items
            case SpriteId.BON1 -> {
                player.health++; // can go over 100%
                if (player.health > misc.maxHealth()) {
                    player.health = misc.maxHealth();
                }
                player.mo.health = player.health;
                message(player, "GOTHTHBONUS");
            }
            case SpriteId.BON2 -> {
                player.armorPoints++; // can go over 100%
                if (player.armorPoints > misc.maxArmor()) { // gameversion > exe_doom_1_2
                    player.armorPoints = misc.maxArmor();
                }
                // deh_green_armor_class only applies to the green armor shirt; for the armor helmets,
                // armortype 1 is always used.
                if (player.armorType == 0) {
                    player.armorType = 1;
                }
                message(player, "GOTARMBONUS");
            }
            case SpriteId.SOUL -> {
                player.health += misc.soulsphereHealth();
                if (player.health > misc.maxSoulsphere()) {
                    player.health = misc.maxSoulsphere();
                }
                player.mo.health = player.health;
                message(player, "GOTSUPER");
                sound = SoundId.GETPOW;
            }
            case SpriteId.MEGA -> {
                if (world.mode != GameMode.COMMERCIAL) {
                    return;
                }
                player.health = misc.megasphereHealth();
                player.mo.health = player.health;
                // We always give armor type 2 for the megasphere; dehacked only affects the MegaArmor.
                giveArmor(player, 2);
                message(player, "GOTMSPHERE");
                sound = SoundId.GETPOW;
            }

            // cards: leave cards for everyone
            case SpriteId.BKEY -> {
                if (!player.cards[Card.BLUE_CARD]) {
                    message(player, "GOTBLUECARD");
                }
                giveCard(player, Card.BLUE_CARD);
                if (world.netGame) {
                    return;
                }
            }
            case SpriteId.YKEY -> {
                if (!player.cards[Card.YELLOW_CARD]) {
                    message(player, "GOTYELWCARD");
                }
                giveCard(player, Card.YELLOW_CARD);
                if (world.netGame) {
                    return;
                }
            }
            case SpriteId.RKEY -> {
                if (!player.cards[Card.RED_CARD]) {
                    message(player, "GOTREDCARD");
                }
                giveCard(player, Card.RED_CARD);
                if (world.netGame) {
                    return;
                }
            }
            case SpriteId.BSKU -> {
                if (!player.cards[Card.BLUE_SKULL]) {
                    message(player, "GOTBLUESKUL");
                }
                giveCard(player, Card.BLUE_SKULL);
                if (world.netGame) {
                    return;
                }
            }
            case SpriteId.YSKU -> {
                if (!player.cards[Card.YELLOW_SKULL]) {
                    message(player, "GOTYELWSKUL");
                }
                giveCard(player, Card.YELLOW_SKULL);
                if (world.netGame) {
                    return;
                }
            }
            case SpriteId.RSKU -> {
                if (!player.cards[Card.RED_SKULL]) {
                    message(player, "GOTREDSKULL");
                }
                giveCard(player, Card.RED_SKULL);
                if (world.netGame) {
                    return;
                }
            }

            // medikits, heals
            case SpriteId.STIM -> {
                if (!giveBody(player, 10)) {
                    return;
                }
                message(player, "GOTSTIM");
            }
            case SpriteId.MEDI -> {
                if (!giveBody(player, 25)) {
                    return;
                }
                // VANILLA-BUG: health was already raised, so "needed" is shown only below 25 after healing.
                if (player.health < 25) {
                    message(player, "GOTMEDINEED");
                } else {
                    message(player, "GOTMEDIKIT");
                }
            }

            // power ups
            case SpriteId.PINV -> {
                if (!givePower(player, Power.INVULNERABILITY)) {
                    return;
                }
                message(player, "GOTINVUL");
                sound = SoundId.GETPOW;
            }
            case SpriteId.PSTR -> {
                if (!givePower(player, Power.STRENGTH)) {
                    return;
                }
                message(player, "GOTBERSERK");
                if (player.readyWeapon != Weapon.FIST) {
                    player.pendingWeapon = Weapon.FIST;
                }
                sound = SoundId.GETPOW;
            }
            case SpriteId.PINS -> {
                if (!givePower(player, Power.INVISIBILITY)) {
                    return;
                }
                message(player, "GOTINVIS");
                sound = SoundId.GETPOW;
            }
            case SpriteId.SUIT -> {
                if (!givePower(player, Power.IRONFEET)) {
                    return;
                }
                message(player, "GOTSUIT");
                sound = SoundId.GETPOW;
            }
            case SpriteId.PMAP -> {
                if (!givePower(player, Power.ALLMAP)) {
                    return;
                }
                message(player, "GOTMAP");
                sound = SoundId.GETPOW;
            }
            case SpriteId.PVIS -> {
                if (!givePower(player, Power.INFRARED)) {
                    return;
                }
                message(player, "GOTVISOR");
                sound = SoundId.GETPOW;
            }

            // ammo
            case SpriteId.CLIP -> {
                if ((special.flags & MobjFlags.DROPPED) != 0) {
                    if (!giveAmmo(player, AmmoType.CLIP, 0)) {
                        return;
                    }
                } else {
                    if (!giveAmmo(player, AmmoType.CLIP, 1)) {
                        return;
                    }
                }
                message(player, "GOTCLIP");
            }
            case SpriteId.AMMO -> {
                if (!giveAmmo(player, AmmoType.CLIP, 5)) {
                    return;
                }
                message(player, "GOTCLIPBOX");
            }
            case SpriteId.ROCK -> {
                if (!giveAmmo(player, AmmoType.MISSILE, 1)) {
                    return;
                }
                message(player, "GOTROCKET");
            }
            case SpriteId.BROK -> {
                if (!giveAmmo(player, AmmoType.MISSILE, 5)) {
                    return;
                }
                message(player, "GOTROCKBOX");
            }
            case SpriteId.CELL -> {
                if (!giveAmmo(player, AmmoType.CELL, 1)) {
                    return;
                }
                message(player, "GOTCELL");
            }
            case SpriteId.CELP -> {
                if (!giveAmmo(player, AmmoType.CELL, 5)) {
                    return;
                }
                message(player, "GOTCELLBOX");
            }
            case SpriteId.SHEL -> {
                if (!giveAmmo(player, AmmoType.SHELL, 1)) {
                    return;
                }
                message(player, "GOTSHELLS");
            }
            case SpriteId.SBOX -> {
                if (!giveAmmo(player, AmmoType.SHELL, 5)) {
                    return;
                }
                message(player, "GOTSHELLBOX");
            }
            case SpriteId.BPAK -> {
                if (!player.backpack) {
                    for (int i = 0; i < AmmoType.COUNT; i++) {
                        player.maxAmmo[i] *= 2;
                    }
                    player.backpack = true;
                }
                for (int i = 0; i < AmmoType.COUNT; i++) {
                    giveAmmo(player, i, 1);
                }
                message(player, "GOTBACKPACK");
            }

            // weapons
            case SpriteId.BFUG -> {
                if (!giveWeapon(player, Weapon.BFG, false)) {
                    return;
                }
                message(player, "GOTBFG9000");
                sound = SoundId.WPNUP;
            }
            case SpriteId.MGUN -> {
                if (!giveWeapon(player, Weapon.CHAINGUN, (special.flags & MobjFlags.DROPPED) != 0)) {
                    return;
                }
                message(player, "GOTCHAINGUN");
                sound = SoundId.WPNUP;
            }
            case SpriteId.CSAW -> {
                if (!giveWeapon(player, Weapon.CHAINSAW, false)) {
                    return;
                }
                message(player, "GOTCHAINSAW");
                sound = SoundId.WPNUP;
            }
            case SpriteId.LAUN -> {
                if (!giveWeapon(player, Weapon.MISSILE, false)) {
                    return;
                }
                message(player, "GOTLAUNCHER");
                sound = SoundId.WPNUP;
            }
            case SpriteId.PLAS -> {
                if (!giveWeapon(player, Weapon.PLASMA, false)) {
                    return;
                }
                message(player, "GOTPLASMA");
                sound = SoundId.WPNUP;
            }
            case SpriteId.SHOT -> {
                if (!giveWeapon(player, Weapon.SHOTGUN, (special.flags & MobjFlags.DROPPED) != 0)) {
                    return;
                }
                message(player, "GOTSHOTGUN");
                sound = SoundId.WPNUP;
            }
            case SpriteId.SGN2 -> {
                if (!giveWeapon(player, Weapon.SUPERSHOTGUN, (special.flags & MobjFlags.DROPPED) != 0)) {
                    return;
                }
                message(player, "GOTSHOTGUN2");
                sound = SoundId.WPNUP;
            }
            default -> throw new DoomFatalException("P_SpecialThing: Unknown gettable thing");
        }

        if ((special.flags & MobjFlags.COUNTITEM) != 0) {
            player.itemCount++;
        }
        world.mobjs.removeMobj(special);
        player.bonusCount += BONUSADD;
        if (player == world.players[world.consolePlayer]) {
            world.startSound(null, sound);
        }
    }
}
