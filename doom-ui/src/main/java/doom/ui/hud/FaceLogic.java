package doom.ui.hud;

import doom.game.api.MiscRandom;
import doom.game.view.ActorView;
import doom.game.view.CheatFlags;
import doom.game.view.GameView;
import doom.game.view.PlayerView;
import doom.game.view.Power;
import doom.game.view.Weapon;
import doom.math.Angle;
import doom.math.Trig;
import doom.math.annotation.VanillaRef;
import java.util.function.IntPredicate;

/**
 * The status bar face and the per-tic state that goes with it ({@code ST_Ticker}, {@code ST_updateWidgets},
 * {@code ST_updateFaceWidget}). It calls the misc RNG exactly once per {@link #tick()}, like {@code ST_Ticker}.
 */
public final class FaceLogic {

    static final int NUM_PAIN_FACES = 5;
    static final int NUM_STRAIGHT_FACES = 3;
    static final int NUM_TURN_FACES = 2;
    static final int NUM_SPECIAL_FACES = 3;
    static final int FACE_STRIDE = NUM_STRAIGHT_FACES + NUM_TURN_FACES + NUM_SPECIAL_FACES;
    static final int NUM_EXTRA_FACES = 2;
    static final int NUM_FACES = FACE_STRIDE * NUM_PAIN_FACES + NUM_EXTRA_FACES;
    static final int TURN_OFFSET = NUM_STRAIGHT_FACES;
    static final int OUCH_OFFSET = TURN_OFFSET + NUM_TURN_FACES;
    static final int EVIL_GRIN_OFFSET = OUCH_OFFSET + 1;
    static final int RAMPAGE_OFFSET = EVIL_GRIN_OFFSET + 1;
    static final int GOD_FACE = NUM_PAIN_FACES * FACE_STRIDE;
    static final int DEAD_FACE = GOD_FACE + 1;

    static final int TICRATE = 35;
    static final int EVIL_GRIN_COUNT = 2 * TICRATE;
    static final int STRAIGHT_FACE_COUNT = TICRATE / 2;
    static final int TURN_COUNT = 1 * TICRATE;
    static final int RAMPAGE_DELAY = 2 * TICRATE;
    static final int MUCH_PAIN = 20;

    private final GameView view;
    private final MiscRandom rnd;
    private IntPredicate attackDown = p -> false;

    private int faceCount;
    private int faceIndex;
    private int oldHealth = -1;
    private final boolean[] oldWeaponsOwned = new boolean[Weapon.COUNT];
    private int randomNumber;

    // "static" locals of ST_updateFaceWidget: they survive ST_Start
    private int lastAttackDown = -1;
    private int priority;

    public FaceLogic(GameView view, MiscRandom rnd) {
        this.view = view;
        this.rnd = rnd;
    }

    /** Supplies {@code player->attackdown}, which the views do not expose yet. */
    public void setAttackDown(IntPredicate attackDown) {
        this.attackDown = attackDown;
    }

    /** {@code ST_initData} (the parts that concern the face). */
    @VanillaRef("st_stuff.c:ST_initData")
    public void start() {
        PlayerView plyr = view.player(view.consolePlayer());
        faceIndex = 0;
        oldHealth = -1;
        for (int i = 0; i < Weapon.COUNT; i++) {
            oldWeaponsOwned[i] = plyr.weaponOwned(i);
        }
    }

    /** Index of the face patch to draw ({@code st_faceindex}). */
    public int faceIndex() {
        return faceIndex;
    }

    /** {@code st_randomnumber} of the last tic. */
    public int randomNumber() {
        return randomNumber;
    }

    /** {@code ST_Ticker}. */
    @VanillaRef("st_stuff.c:ST_Ticker")
    public void tick() {
        randomNumber = rnd.next();
        updateFace();
        oldHealth = view.player(view.consolePlayer()).health();
    }

    @VanillaRef("st_stuff.c:ST_calcPainOffset")
    int calcPainOffset() {
        int health = view.player(view.consolePlayer()).health();
        if (health > 100) {
            health = 100;
        }
        return FACE_STRIDE * (((100 - health) * NUM_PAIN_FACES) / 101);
    }

    @VanillaRef("st_stuff.c:ST_updateFaceWidget")
    void updateFace() {
        int me = view.consolePlayer();
        PlayerView plyr = view.player(me);
        ActorView actors = view.actors();

        if (priority < 10) {
            // dead
            if (plyr.health() == 0) {
                priority = 9;
                faceIndex = DEAD_FACE;
                faceCount = 1;
            }
        }

        if (priority < 9) {
            if (plyr.bonusCount() != 0) {
                // picking up bonus
                boolean doEvilGrin = false;
                for (int i = 0; i < Weapon.COUNT; i++) {
                    if (oldWeaponsOwned[i] != plyr.weaponOwned(i)) {
                        doEvilGrin = true;
                        oldWeaponsOwned[i] = plyr.weaponOwned(i);
                    }
                }
                if (doEvilGrin) {
                    // evil grin if just picked up weapon
                    priority = 8;
                    faceCount = EVIL_GRIN_COUNT;
                    faceIndex = calcPainOffset() + EVIL_GRIN_OFFSET;
                }
            }
        }

        if (priority < 8) {
            if (plyr.damageCount() != 0 && plyr.attacker() >= 0 && plyr.attacker() != plyr.actor()
                && actors.exists(plyr.attacker())) {
                // being attacked
                priority = 7;
                if (plyr.health() - oldHealth > MUCH_PAIN) {
                    faceCount = TURN_COUNT;
                    faceIndex = calcPainOffset() + OUCH_OFFSET;
                } else {
                    int attacker = plyr.attacker();
                    int badGuyAngle = Trig.pointToAngle2(plyr.x(), plyr.y(), actors.x(attacker), actors.y(attacker));
                    int diffAng;
                    boolean right;
                    if (Integer.compareUnsigned(badGuyAngle, plyr.angle()) > 0) {
                        // whether right or left
                        diffAng = badGuyAngle - plyr.angle();
                        right = Integer.compareUnsigned(diffAng, Angle.ANG180) > 0;
                    } else {
                        // whether left or right
                        diffAng = plyr.angle() - badGuyAngle;
                        right = Integer.compareUnsigned(diffAng, Angle.ANG180) <= 0;
                    }
                    faceCount = TURN_COUNT;
                    faceIndex = calcPainOffset();
                    if (Integer.compareUnsigned(diffAng, Angle.ANG45) < 0) {
                        // head-on
                        faceIndex += RAMPAGE_OFFSET;
                    } else if (right) {
                        // turn face right
                        faceIndex += TURN_OFFSET;
                    } else {
                        // turn face left
                        faceIndex += TURN_OFFSET + 1;
                    }
                }
            }
        }

        if (priority < 7) {
            // getting hurt because of your own damn stupidity
            if (plyr.damageCount() != 0) {
                if (plyr.health() - oldHealth > MUCH_PAIN) {
                    priority = 7;
                    faceCount = TURN_COUNT;
                    faceIndex = calcPainOffset() + OUCH_OFFSET;
                } else {
                    priority = 6;
                    faceCount = TURN_COUNT;
                    faceIndex = calcPainOffset() + RAMPAGE_OFFSET;
                }
            }
        }

        if (priority < 6) {
            // rapid firing
            if (attackDown.test(me)) {
                if (lastAttackDown == -1) {
                    lastAttackDown = RAMPAGE_DELAY;
                } else if (--lastAttackDown == 0) {
                    priority = 5;
                    faceIndex = calcPainOffset() + RAMPAGE_OFFSET;
                    faceCount = 1;
                    lastAttackDown = 1;
                }
            } else {
                lastAttackDown = -1;
            }
        }

        if (priority < 5) {
            // invulnerability
            if ((plyr.cheats() & CheatFlags.GODMODE) != 0 || plyr.power(Power.INVULNERABILITY) != 0) {
                priority = 4;
                faceIndex = GOD_FACE;
                faceCount = 1;
            }
        }

        // look left or look right if the facecount has timed out
        if (faceCount == 0) {
            faceIndex = calcPainOffset() + (randomNumber % 3);
            faceCount = STRAIGHT_FACE_COUNT;
            priority = 0;
        }

        faceCount--;
    }
}
