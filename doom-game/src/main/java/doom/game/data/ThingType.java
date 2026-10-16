package doom.game.data;

/**
 * One entry of the actor table ({@code mobjinfo_t}). Mutable for DEHACKED; field names follow the original with
 * the camel-case spelling of the builder DSL. States are {@link StateId}s, sounds are {@link doom.game.view.SoundId}s.
 */
public final class ThingType {

    public int doomedNum;
    public int spawnState;
    public int spawnHealth;
    public int seeState;
    public int seeSound;
    public int reactionTime;
    public int attackSound;
    public int painState;
    public int painChance;
    public int painSound;
    public int meleeState;
    public int missileState;
    public int deathState;
    public int xDeathState;
    public int deathSound;
    /** Plain units per tic for monsters; 16.16 fixed point for projectiles. */
    public int speed;
    /** 16.16 fixed point. */
    public int radius;
    /** 16.16 fixed point. */
    public int height;
    public int mass;
    public int damage;
    public int activeSound;
    /** {@link doom.game.view.MobjFlags}. */
    public int flags;
    public int raiseState;

    /** A deep copy. */
    public ThingType copy() {
        ThingType t = new ThingType();
        t.doomedNum = doomedNum;
        t.spawnState = spawnState;
        t.spawnHealth = spawnHealth;
        t.seeState = seeState;
        t.seeSound = seeSound;
        t.reactionTime = reactionTime;
        t.attackSound = attackSound;
        t.painState = painState;
        t.painChance = painChance;
        t.painSound = painSound;
        t.meleeState = meleeState;
        t.missileState = missileState;
        t.deathState = deathState;
        t.xDeathState = xDeathState;
        t.deathSound = deathSound;
        t.speed = speed;
        t.radius = radius;
        t.height = height;
        t.mass = mass;
        t.damage = damage;
        t.activeSound = activeSound;
        t.flags = flags;
        t.raiseState = raiseState;
        return t;
    }
}
