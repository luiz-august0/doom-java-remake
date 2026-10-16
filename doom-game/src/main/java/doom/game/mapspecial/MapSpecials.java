package doom.game.mapspecial;

import doom.game.actor.Actor;
import doom.game.actor.SpecialThinker;
import doom.game.player.Player;
import doom.game.world.Line;

/**
 * Line triggers, special sectors and sector effects ({@code p_spec.c}, {@code p_doors.c}, {@code p_floor.c},
 * {@code p_ceilng.c}, {@code p_plats.c}, {@code p_lights.c}, {@code p_switch.c}, {@code p_telept.c}), implemented by
 * W3-C. Sector-effect thinkers extend {@link doom.game.actor.SpecialThinker}; moving planes call
 * {@link doom.game.physics.Physics#changeSector}.
 */
public interface MapSpecials {

    /** {@code P_InitPicAnims} + {@code P_InitSwitchList}: once per session, after the definitions are final. */
    void init();

    /** {@code P_SpawnSpecials}, at the end of level setup (after things were spawned). */
    void spawnSpecials();

    /** {@code P_UpdateSpecials}, every tic after the thinkers ran. */
    void updateSpecials();

    /** {@code P_CrossSpecialLine(linenum, side, thing)}, from {@code P_TryMove}; only for lines with a special. */
    void crossSpecialLine(Line line, int side, Actor thing);

    /** {@code P_UseSpecialLine(thing, line, side)}, from {@code PTR_UseTraverse} and monster movement. */
    boolean useSpecialLine(Actor thing, Line line, int side);

    /** {@code P_ShootSpecialLine(thing, line)}, from {@code PTR_ShootTraverse}. */
    void shootSpecialLine(Actor thing, Line line);

    /** {@code P_PlayerInSpecialSector}, from {@code P_PlayerThink} when the sector special is not 0. */
    void playerInSpecialSector(Player player);

    /** {@code EV_DoFloor(line, type)} for the boss deaths ({@code A_BossDeath}). */
    boolean doFloor(Line line, int floorType);

    /** {@code EV_DoDoor(line, type)} for the boss deaths ({@code A_BossDeath}, {@code A_KeenDie}). */
    boolean doDoor(Line line, int doorType);

    /** {@code texturetranslation[texture]} (animated walls). */
    int textureTranslation(int texture);

    /** {@code flattranslation[flat]} (animated flats). */
    int flatTranslation(int flat);

    // ------------------------------------------------------------------ save games (W4-P)

    /**
     * Describes a sector-effect thinker of this area for a save game: the kind followed by every field, with the
     * sector as its index. The inverse of {@link #restoreThinker}.
     *
     * @throws IllegalArgumentException if the thinker does not belong to this area
     */
    default int[] saveThinker(SpecialThinker thinker) {
        throw new UnsupportedOperationException("save games");
    }

    /**
     * Re-creates a thinker from {@link #saveThinker} output. The thinker is neither added to the thinker list nor
     * attached to its sector ({@code sector.specialdata}); the caller does both.
     *
     * @throws IllegalArgumentException if the data does not describe a thinker of this level
     */
    default SpecialThinker restoreThinker(int[] state) {
        throw new UnsupportedOperationException("save games");
    }

    /** {@code activeceilings[]}: the slots in order, null where free. */
    default SpecialThinker[] activeCeilings() {
        return new SpecialThinker[0];
    }

    /** {@code activeplats[]}: the slots in order, null where free. */
    default SpecialThinker[] activePlats() {
        return new SpecialThinker[0];
    }

    /** Replaces the slots of {@code activeceilings[]} and {@code activeplats[]} (same lengths as the getters). */
    default void restoreActiveSpecials(SpecialThinker[] ceilings, SpecialThinker[] plats) {
    }

    /** {@code buttonlist[]}: per slot {@code line index (-1 none), where, texture, timer}, flattened. */
    default int[] buttons() {
        return new int[0];
    }

    /** Replaces {@code buttonlist[]} with the {@link #buttons()} layout. */
    default void restoreButtons(int[] buttons) {
    }
}
