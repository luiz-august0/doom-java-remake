package doom.game.data;

/** One entry of the weapon table ({@code weaponinfo_t}). Mutable for DEHACKED. */
public final class WeaponInfo {

    /** {@link doom.game.view.AmmoType}, {@code NO_AMMO} for none. */
    public int ammo;
    /** Raising state ({@code upstate}). */
    public int upState;
    /** Lowering state ({@code downstate}). */
    public int downState;
    /** Ready state ({@code readystate}). */
    public int readyState;
    /** Attack state ({@code atkstate}). */
    public int atkState;
    /** Muzzle flash state ({@code flashstate}). */
    public int flashState;

    public WeaponInfo(int ammo, int upState, int downState, int readyState, int atkState, int flashState) {
        this.ammo = ammo;
        this.upState = upState;
        this.downState = downState;
        this.readyState = readyState;
        this.atkState = atkState;
        this.flashState = flashState;
    }

    /** A deep copy. */
    public WeaponInfo copy() {
        return new WeaponInfo(ammo, upState, downState, readyState, atkState, flashState);
    }
}
