package doom.game.view;

/** {@code mobjflag_t} bits ({@code p_mobj.h}). */
public final class MobjFlags {
    public static final int SPECIAL = 0x1;
    public static final int SOLID = 0x2;
    public static final int SHOOTABLE = 0x4;
    public static final int NOSECTOR = 0x8;
    public static final int NOBLOCKMAP = 0x10;
    public static final int AMBUSH = 0x20;
    public static final int JUSTHIT = 0x40;
    public static final int JUSTATTACKED = 0x80;
    public static final int SPAWNCEILING = 0x100;
    public static final int NOGRAVITY = 0x200;
    public static final int DROPOFF = 0x400;
    public static final int PICKUP = 0x800;
    public static final int NOCLIP = 0x1000;
    public static final int SLIDE = 0x2000;
    public static final int FLOAT = 0x4000;
    public static final int TELEPORT = 0x8000;
    public static final int MISSILE = 0x10000;
    public static final int DROPPED = 0x20000;
    /** Drawn with the fuzz effect (Spectre, invisibility). */
    public static final int SHADOW = 0x40000;
    public static final int NOBLOOD = 0x80000;
    public static final int CORPSE = 0x100000;
    public static final int INFLOAT = 0x200000;
    public static final int COUNTKILL = 0x400000;
    public static final int COUNTITEM = 0x800000;
    public static final int SKULLFLY = 0x1000000;
    public static final int NOTDMATCH = 0x2000000;
    /** Player colour translation bits ({@code MF_TRANSLATION}). */
    public static final int TRANSLATION = 0xc000000;
    /** {@code MF_TRANSSHIFT}. */
    public static final int TRANSSHIFT = 26;

    private MobjFlags() {
    }
}
