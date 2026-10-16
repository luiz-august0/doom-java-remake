package doom.game.view;

/**
 * Intermission state ({@code wbstartstruct_t} plus the {@code wi_stuff.c} state machine). The logic lives in the
 * simulation; the UI draws from these values exactly as {@code WI_Drawer} does. Episodes and maps are 0-based here,
 * like {@code wbs->epsd}, {@code wbs->last}, {@code wbs->next}.
 */
public interface IntermissionView {

    /** -1 NoState, 0 StatCount, 1 ShowNextLoc. */
    int stage();

    int episode();

    int lastMap();

    int nextMap();

    boolean didSecret();

    int maxKills();

    int maxItems();

    int maxSecret();

    /** Par time in tics. */
    int parTime();

    /** The local player ({@code wbs->pnum}). */
    int me();

    boolean playerPresent(int p);

    int playerKills(int p);

    int playerItems(int p);

    int playerSecret(int p);

    /** Level time in tics. */
    int playerTime(int p);

    int playerFrags(int p, int victim);

    int bcnt();

    int cnt();

    int spState();

    int ngState();

    int dmState();

    boolean doFrags();

    /** Displayed (counting-up) values. */
    int countKills(int p);

    int countItems(int p);

    int countSecret(int p);

    int countFrags(int p);

    /** Seconds. */
    int countTime();

    /** Seconds. */
    int countPar();

    int dmFrags(int p, int victim);

    int dmTotal(int p);

    boolean pointerOn();

    /** Number of animations of the current episode's background (DOOM 1 only). */
    int animCount();

    /** Current frame of animation {@code anim} ({@code a->ctr}), or -1 when nothing is drawn yet. */
    int animFrame(int anim);
}
