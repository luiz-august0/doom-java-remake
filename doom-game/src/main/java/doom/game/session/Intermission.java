package doom.game.session;

import doom.game.player.Player;
import doom.game.view.IntermissionView;
import doom.game.view.MusicId;
import doom.game.view.SoundId;
import doom.game.world.World;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameMode;
import doom.wad.iwad.GameVersion;

/**
 * The intermission screen logic ({@code wi_stuff.c}): the state machine, the counters, the acceleration through
 * ticcmd buttons, the sounds and the animated backgrounds (which draw from the misc RNG in the original order).
 * The UI draws from the {@link IntermissionView} side. Episodes and maps are 0-based, like {@code wbs}.
 */
public final class Intermission implements IntermissionView {

    /** {@code stateenum_t}: NoState, StatCount, ShowNextLoc. */
    private static final int NO_STATE = -1;
    private static final int STAT_COUNT = 0;
    private static final int SHOW_NEXT_LOC = 1;

    private static final int SHOWNEXTLOCDELAY = 4;
    private static final int TICRATE = World.TICRATE;

    private static final int ANIM_ALWAYS = 0;
    private static final int ANIM_RANDOM = 1;
    private static final int ANIM_LEVEL = 2;

    /** {@code epsd0animinfo}: type, period, nanims, data1, data2 for each animation. */
    private static final int[][] EPSD0 = new int[10][];
    private static final int[][][] ANIMS;

    static {
        int period = TICRATE / 3;
        for (int i = 0; i < 10; i++) {
            EPSD0[i] = new int[] {ANIM_ALWAYS, period, 3, 0, 0};
        }
        int[] nanims1 = {1, 1, 1, 1, 1, 1, 1, 3, 1};
        int[] level1 = {1, 2, 3, 4, 5, 6, 7, 8, 8};
        int[][] epsd1 = new int[9][];
        for (int i = 0; i < 9; i++) {
            epsd1[i] = new int[] {ANIM_LEVEL, period, nanims1[i], level1[i], 0};
        }
        int[][] epsd2 = new int[6][];
        for (int i = 0; i < 6; i++) {
            epsd2[i] = new int[] {ANIM_ALWAYS, i == 5 ? TICRATE / 4 : period, 3, 0, 0};
        }
        ANIMS = new int[][][] {EPSD0, epsd1, epsd2};
    }

    private final World w;
    private final Game game;

    private WbStart wbs;
    private int acceleratestage;
    private int me;
    private int state = NO_STATE;
    private int cnt;
    private int bcnt;
    private final int[] cntKills = new int[World.MAXPLAYERS];
    private final int[] cntItems = new int[World.MAXPLAYERS];
    private final int[] cntSecret = new int[World.MAXPLAYERS];
    private final int[] cntFrags = new int[World.MAXPLAYERS];
    private int cntTime;
    private int cntPar;
    private int cntPause;
    private boolean snlPointerOn;

    private int dmState;
    private final int[][] dmFrags = new int[World.MAXPLAYERS][World.MAXPLAYERS];
    private final int[] dmTotals = new int[World.MAXPLAYERS];

    /** {@code dofrags}: a static in the original, so it carries over between intermissions. */
    private int doFrags;
    private int ngState;
    private int spState;

    private final int[] animNextTic = new int[10];
    private final int[] animCtr = new int[10];

    Intermission(World world, Game game) {
        this.w = world;
        this.game = game;
    }

    // ==================================================================================== WI_Start

    /** {@code WI_Start}: starts the intermission with the parameters of {@code G_DoCompleted}. */
    @VanillaRef("wi_stuff.c:WI_Start")
    void start(WbStart wbstartstruct) {
        initVariables(wbstartstruct);
        if (w.deathmatch != 0) {
            initDeathmatchStats();
        } else if (w.netGame) {
            initNetgameStats();
        } else {
            initStats();
        }
    }

    @VanillaRef("wi_stuff.c:WI_initVariables")
    private void initVariables(WbStart wbstartstruct) {
        wbs = wbstartstruct;
        acceleratestage = 0;
        cnt = bcnt = 0;
        me = wbs.pnum;

        if (wbs.maxKills == 0) {
            wbs.maxKills = 1;
        }
        if (wbs.maxItems == 0) {
            wbs.maxItems = 1;
        }
        if (wbs.maxSecret == 0) {
            wbs.maxSecret = 1;
        }

        if (!w.version.isAtLeast(GameVersion.ULTIMATE)) {
            if (wbs.epsd > 2) {
                wbs.epsd -= 3;
            }
        }
    }

    // ==================================================================================== animated background

    @VanillaRef("wi_stuff.c:WI_initAnimatedBack")
    private void initAnimatedBack() {
        if (w.mode == GameMode.COMMERCIAL) {
            return;
        }
        if (wbs.epsd > 2) {
            return;
        }
        int[][] anims = ANIMS[wbs.epsd];
        for (int i = 0; i < anims.length; i++) {
            int[] a = anims[i];
            // init variables
            animCtr[i] = -1;

            // specify the next time to draw it
            if (a[0] == ANIM_ALWAYS) {
                animNextTic[i] = bcnt + 1 + (w.rng.misc() % a[1]);
            } else if (a[0] == ANIM_RANDOM) {
                animNextTic[i] = bcnt + 1 + a[4] + (w.rng.misc() % a[3]);
            } else if (a[0] == ANIM_LEVEL) {
                animNextTic[i] = bcnt + 1;
            }
        }
    }

    @VanillaRef("wi_stuff.c:WI_updateAnimatedBack")
    private void updateAnimatedBack() {
        if (w.mode == GameMode.COMMERCIAL) {
            return;
        }
        if (wbs.epsd > 2) {
            return;
        }
        int[][] anims = ANIMS[wbs.epsd];
        for (int i = 0; i < anims.length; i++) {
            int[] a = anims[i];
            if (bcnt == animNextTic[i]) {
                switch (a[0]) {
                    case ANIM_ALWAYS -> {
                        if (++animCtr[i] >= a[2]) {
                            animCtr[i] = 0;
                        }
                        animNextTic[i] = bcnt + a[1];
                    }
                    case ANIM_RANDOM -> {
                        animCtr[i]++;
                        if (animCtr[i] == a[2]) {
                            animCtr[i] = -1;
                            animNextTic[i] = bcnt + a[4] + (w.rng.misc() % a[3]);
                        } else {
                            animNextTic[i] = bcnt + a[1];
                        }
                    }
                    case ANIM_LEVEL -> {
                        // gawd-awful hack for level anims
                        if (!(state == STAT_COUNT && i == 7) && wbs.next == a[3]) {
                            animCtr[i]++;
                            if (animCtr[i] == a[2]) {
                                animCtr[i]--;
                            }
                            animNextTic[i] = bcnt + a[1];
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    // ==================================================================================== states

    @VanillaRef("wi_stuff.c:WI_initNoState")
    private void initNoState() {
        state = NO_STATE;
        acceleratestage = 0;
        cnt = 10;
        // WI_drawNoState turns the "you are here" pointer on for good (a static in the original).
        snlPointerOn = true;
    }

    @VanillaRef("wi_stuff.c:WI_updateNoState")
    private void updateNoState() {
        updateAnimatedBack();
        if (--cnt == 0) {
            game.worldDone();
        }
    }

    @VanillaRef("wi_stuff.c:WI_initShowNextLoc")
    private void initShowNextLoc() {
        state = SHOW_NEXT_LOC;
        acceleratestage = 0;
        cnt = SHOWNEXTLOCDELAY * TICRATE;
        initAnimatedBack();
    }

    @VanillaRef("wi_stuff.c:WI_updateShowNextLoc")
    private void updateShowNextLoc() {
        updateAnimatedBack();
        if (--cnt == 0 || acceleratestage != 0) {
            initNoState();
        } else {
            snlPointerOn = (cnt & 31) < 20;
        }
    }

    @VanillaRef("wi_stuff.c:WI_fragSum")
    private int fragSum(int playernum) {
        int frags = 0;
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (w.playerInGame[i] && i != playernum) {
                frags += wbs.plyr[playernum].frags[i];
            }
        }
        // JDC hack - negative frags.
        frags -= wbs.plyr[playernum].frags[playernum];
        return frags;
    }

    private void nextAfterStats() {
        if (w.mode == GameMode.COMMERCIAL) {
            initNoState();
        } else {
            initShowNextLoc();
        }
    }

    // ---------------------------------------------------------------- deathmatch

    @VanillaRef("wi_stuff.c:WI_initDeathmatchStats")
    private void initDeathmatchStats() {
        state = STAT_COUNT;
        acceleratestage = 0;
        dmState = 1;
        cntPause = TICRATE;

        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (w.playerInGame[i]) {
                for (int j = 0; j < World.MAXPLAYERS; j++) {
                    if (w.playerInGame[j]) {
                        dmFrags[i][j] = 0;
                    }
                }
                dmTotals[i] = 0;
            }
        }
        initAnimatedBack();
    }

    @VanillaRef("wi_stuff.c:WI_updateDeathmatchStats")
    private void updateDeathmatchStats() {
        updateAnimatedBack();

        if (acceleratestage != 0 && dmState != 4) {
            acceleratestage = 0;
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                if (w.playerInGame[i]) {
                    for (int j = 0; j < World.MAXPLAYERS; j++) {
                        if (w.playerInGame[j]) {
                            dmFrags[i][j] = wbs.plyr[i].frags[j];
                        }
                    }
                    dmTotals[i] = fragSum(i);
                }
            }
            w.events.startSound(-1, SoundId.BAREXP);
            dmState = 4;
        }

        if (dmState == 2) {
            if ((bcnt & 3) == 0) {
                w.events.startSound(-1, SoundId.PISTOL);
            }
            boolean stillTicking = false;
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                if (w.playerInGame[i]) {
                    for (int j = 0; j < World.MAXPLAYERS; j++) {
                        if (w.playerInGame[j] && dmFrags[i][j] != wbs.plyr[i].frags[j]) {
                            if (wbs.plyr[i].frags[j] < 0) {
                                dmFrags[i][j]--;
                            } else {
                                dmFrags[i][j]++;
                            }
                            if (dmFrags[i][j] > 99) {
                                dmFrags[i][j] = 99;
                            }
                            if (dmFrags[i][j] < -99) {
                                dmFrags[i][j] = -99;
                            }
                            stillTicking = true;
                        }
                    }
                    dmTotals[i] = fragSum(i);
                    if (dmTotals[i] > 99) {
                        dmTotals[i] = 99;
                    }
                    if (dmTotals[i] < -99) {
                        dmTotals[i] = -99;
                    }
                }
            }
            if (!stillTicking) {
                w.events.startSound(-1, SoundId.BAREXP);
                dmState++;
            }
        } else if (dmState == 4) {
            if (acceleratestage != 0) {
                w.events.startSound(-1, SoundId.SLOP);
                nextAfterStats();
            }
        } else if ((dmState & 1) != 0) {
            if (--cntPause == 0) {
                dmState++;
                cntPause = TICRATE;
            }
        }
    }

    // ---------------------------------------------------------------- netgame (coop)

    @VanillaRef("wi_stuff.c:WI_initNetgameStats")
    private void initNetgameStats() {
        state = STAT_COUNT;
        acceleratestage = 0;
        ngState = 1;
        cntPause = TICRATE;

        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (!w.playerInGame[i]) {
                continue;
            }
            cntKills[i] = cntItems[i] = cntSecret[i] = cntFrags[i] = 0;
            // VANILLA-BUG: dofrags is not cleared first, so a "1" from an earlier intermission sticks.
            doFrags += fragSum(i);
        }
        doFrags = doFrags != 0 ? 1 : 0;
        initAnimatedBack();
    }

    @VanillaRef("wi_stuff.c:WI_updateNetgameStats")
    private void updateNetgameStats() {
        updateAnimatedBack();

        if (acceleratestage != 0 && ngState != 10) {
            acceleratestage = 0;
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                if (!w.playerInGame[i]) {
                    continue;
                }
                cntKills[i] = (wbs.plyr[i].skills * 100) / wbs.maxKills;
                cntItems[i] = (wbs.plyr[i].sitems * 100) / wbs.maxItems;
                cntSecret[i] = (wbs.plyr[i].ssecret * 100) / wbs.maxSecret;
                if (doFrags != 0) {
                    cntFrags[i] = fragSum(i);
                }
            }
            w.events.startSound(-1, SoundId.BAREXP);
            ngState = 10;
        }

        if (ngState == 2) {
            if ((bcnt & 3) == 0) {
                w.events.startSound(-1, SoundId.PISTOL);
            }
            boolean stillTicking = false;
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                if (!w.playerInGame[i]) {
                    continue;
                }
                cntKills[i] += 2;
                if (cntKills[i] >= (wbs.plyr[i].skills * 100) / wbs.maxKills) {
                    cntKills[i] = (wbs.plyr[i].skills * 100) / wbs.maxKills;
                } else {
                    stillTicking = true;
                }
            }
            if (!stillTicking) {
                w.events.startSound(-1, SoundId.BAREXP);
                ngState++;
            }
        } else if (ngState == 4) {
            if ((bcnt & 3) == 0) {
                w.events.startSound(-1, SoundId.PISTOL);
            }
            boolean stillTicking = false;
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                if (!w.playerInGame[i]) {
                    continue;
                }
                cntItems[i] += 2;
                if (cntItems[i] >= (wbs.plyr[i].sitems * 100) / wbs.maxItems) {
                    cntItems[i] = (wbs.plyr[i].sitems * 100) / wbs.maxItems;
                } else {
                    stillTicking = true;
                }
            }
            if (!stillTicking) {
                w.events.startSound(-1, SoundId.BAREXP);
                ngState++;
            }
        } else if (ngState == 6) {
            if ((bcnt & 3) == 0) {
                w.events.startSound(-1, SoundId.PISTOL);
            }
            boolean stillTicking = false;
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                if (!w.playerInGame[i]) {
                    continue;
                }
                cntSecret[i] += 2;
                if (cntSecret[i] >= (wbs.plyr[i].ssecret * 100) / wbs.maxSecret) {
                    cntSecret[i] = (wbs.plyr[i].ssecret * 100) / wbs.maxSecret;
                } else {
                    stillTicking = true;
                }
            }
            if (!stillTicking) {
                w.events.startSound(-1, SoundId.BAREXP);
                ngState += 1 + 2 * (doFrags == 0 ? 1 : 0);
            }
        } else if (ngState == 8) {
            if ((bcnt & 3) == 0) {
                w.events.startSound(-1, SoundId.PISTOL);
            }
            boolean stillTicking = false;
            for (int i = 0; i < World.MAXPLAYERS; i++) {
                if (!w.playerInGame[i]) {
                    continue;
                }
                cntFrags[i] += 1;
                int fsum = fragSum(i);
                if (cntFrags[i] >= fsum) {
                    cntFrags[i] = fsum;
                } else {
                    stillTicking = true;
                }
            }
            if (!stillTicking) {
                w.events.startSound(-1, SoundId.PLDETH);
                ngState++;
            }
        } else if (ngState == 10) {
            if (acceleratestage != 0) {
                w.events.startSound(-1, SoundId.SGCOCK);
                nextAfterStats();
            }
        } else if ((ngState & 1) != 0) {
            if (--cntPause == 0) {
                ngState++;
                cntPause = TICRATE;
            }
        }
    }

    // ---------------------------------------------------------------- single player

    @VanillaRef("wi_stuff.c:WI_initStats")
    private void initStats() {
        state = STAT_COUNT;
        acceleratestage = 0;
        spState = 1;
        cntKills[0] = cntItems[0] = cntSecret[0] = -1;
        cntTime = cntPar = -1;
        cntPause = TICRATE;
        initAnimatedBack();
    }

    @VanillaRef("wi_stuff.c:WI_updateStats")
    private void updateStats() {
        updateAnimatedBack();
        WbStart.Player pl = wbs.plyr[me];

        if (acceleratestage != 0 && spState != 10) {
            acceleratestage = 0;
            cntKills[0] = (pl.skills * 100) / wbs.maxKills;
            cntItems[0] = (pl.sitems * 100) / wbs.maxItems;
            cntSecret[0] = (pl.ssecret * 100) / wbs.maxSecret;
            cntTime = pl.stime / TICRATE;
            cntPar = wbs.parTime / TICRATE;
            w.events.startSound(-1, SoundId.BAREXP);
            spState = 10;
        }

        if (spState == 2) {
            cntKills[0] += 2;
            if ((bcnt & 3) == 0) {
                w.events.startSound(-1, SoundId.PISTOL);
            }
            if (cntKills[0] >= (pl.skills * 100) / wbs.maxKills) {
                cntKills[0] = (pl.skills * 100) / wbs.maxKills;
                w.events.startSound(-1, SoundId.BAREXP);
                spState++;
            }
        } else if (spState == 4) {
            cntItems[0] += 2;
            if ((bcnt & 3) == 0) {
                w.events.startSound(-1, SoundId.PISTOL);
            }
            if (cntItems[0] >= (pl.sitems * 100) / wbs.maxItems) {
                cntItems[0] = (pl.sitems * 100) / wbs.maxItems;
                w.events.startSound(-1, SoundId.BAREXP);
                spState++;
            }
        } else if (spState == 6) {
            cntSecret[0] += 2;
            if ((bcnt & 3) == 0) {
                w.events.startSound(-1, SoundId.PISTOL);
            }
            if (cntSecret[0] >= (pl.ssecret * 100) / wbs.maxSecret) {
                cntSecret[0] = (pl.ssecret * 100) / wbs.maxSecret;
                w.events.startSound(-1, SoundId.BAREXP);
                spState++;
            }
        } else if (spState == 8) {
            if ((bcnt & 3) == 0) {
                w.events.startSound(-1, SoundId.PISTOL);
            }
            cntTime += 3;
            if (cntTime >= pl.stime / TICRATE) {
                cntTime = pl.stime / TICRATE;
            }
            cntPar += 3;
            if (cntPar >= wbs.parTime / TICRATE) {
                cntPar = wbs.parTime / TICRATE;
                if (cntTime >= pl.stime / TICRATE) {
                    w.events.startSound(-1, SoundId.BAREXP);
                    spState++;
                }
            }
        } else if (spState == 10) {
            if (acceleratestage != 0) {
                w.events.startSound(-1, SoundId.SGCOCK);
                nextAfterStats();
            }
        } else if ((spState & 1) != 0) {
            if (--cntPause == 0) {
                spState++;
                cntPause = TICRATE;
            }
        }
    }

    // ==================================================================================== WI_Ticker

    /** {@code WI_checkForAccelerate}: button presses skip the delays. */
    @VanillaRef("wi_stuff.c:WI_checkForAccelerate")
    private void checkForAccelerate() {
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (w.playerInGame[i]) {
                Player player = w.players[i];
                int buttons = player.cmd.buttons;
                if ((buttons & doom.game.api.TicCmd.BT_ATTACK) != 0) {
                    if (!player.attackDown) {
                        acceleratestage = 1;
                    }
                    player.attackDown = true;
                } else {
                    player.attackDown = false;
                }
                if ((buttons & doom.game.api.TicCmd.BT_USE) != 0) {
                    if (!player.useDown) {
                        acceleratestage = 1;
                    }
                    player.useDown = true;
                } else {
                    player.useDown = false;
                }
            }
        }
    }

    /** {@code WI_Ticker}: updates the intermission once per tic. */
    @VanillaRef("wi_stuff.c:WI_Ticker")
    void ticker() {
        // counter for general background animation
        bcnt++;

        if (bcnt == 1) {
            // intermission music
            w.events.changeMusic(w.mode == GameMode.COMMERCIAL ? MusicId.DM2INT : MusicId.INTER, true);
        }

        checkForAccelerate();

        switch (state) {
            case STAT_COUNT -> {
                if (w.deathmatch != 0) {
                    updateDeathmatchStats();
                } else if (w.netGame) {
                    updateNetgameStats();
                } else {
                    updateStats();
                }
            }
            case SHOW_NEXT_LOC -> updateShowNextLoc();
            case NO_STATE -> updateNoState();
            default -> {
            }
        }
    }

    // ==================================================================================== IntermissionView

    @Override
    public int stage() {
        return state;
    }

    @Override
    public int episode() {
        return wbs == null ? 0 : wbs.epsd;
    }

    @Override
    public int lastMap() {
        return wbs == null ? 0 : wbs.last;
    }

    @Override
    public int nextMap() {
        return wbs == null ? 0 : wbs.next;
    }

    @Override
    public boolean didSecret() {
        return wbs != null && wbs.didSecret;
    }

    @Override
    public int maxKills() {
        return wbs == null ? 0 : wbs.maxKills;
    }

    @Override
    public int maxItems() {
        return wbs == null ? 0 : wbs.maxItems;
    }

    @Override
    public int maxSecret() {
        return wbs == null ? 0 : wbs.maxSecret;
    }

    @Override
    public int parTime() {
        return wbs == null ? 0 : wbs.parTime;
    }

    @Override
    public int me() {
        return me;
    }

    @Override
    public boolean playerPresent(int p) {
        return wbs != null && wbs.plyr[p].in;
    }

    @Override
    public int playerKills(int p) {
        return wbs == null ? 0 : wbs.plyr[p].skills;
    }

    @Override
    public int playerItems(int p) {
        return wbs == null ? 0 : wbs.plyr[p].sitems;
    }

    @Override
    public int playerSecret(int p) {
        return wbs == null ? 0 : wbs.plyr[p].ssecret;
    }

    @Override
    public int playerTime(int p) {
        return wbs == null ? 0 : wbs.plyr[p].stime;
    }

    @Override
    public int playerFrags(int p, int victim) {
        return wbs == null ? 0 : wbs.plyr[p].frags[victim];
    }

    @Override
    public int bcnt() {
        return bcnt;
    }

    @Override
    public int cnt() {
        return cnt;
    }

    @Override
    public int spState() {
        return spState;
    }

    @Override
    public int ngState() {
        return ngState;
    }

    @Override
    public int dmState() {
        return dmState;
    }

    @Override
    public boolean doFrags() {
        return doFrags != 0;
    }

    @Override
    public int countKills(int p) {
        return cntKills[p];
    }

    @Override
    public int countItems(int p) {
        return cntItems[p];
    }

    @Override
    public int countSecret(int p) {
        return cntSecret[p];
    }

    @Override
    public int countFrags(int p) {
        return cntFrags[p];
    }

    @Override
    public int countTime() {
        return cntTime;
    }

    @Override
    public int countPar() {
        return cntPar;
    }

    @Override
    public int dmFrags(int p, int victim) {
        return dmFrags[p][victim];
    }

    @Override
    public int dmTotal(int p) {
        return dmTotals[p];
    }

    @Override
    public boolean pointerOn() {
        return snlPointerOn;
    }

    @Override
    public int animCount() {
        if (wbs == null || w.mode == GameMode.COMMERCIAL || wbs.epsd > 2) {
            return 0;
        }
        return ANIMS[wbs.epsd].length;
    }

    @Override
    public int animFrame(int anim) {
        if (anim < 0 || anim >= animCount()) {
            return -1;
        }
        return animCtr[anim];
    }
}
