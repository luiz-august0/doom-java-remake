package doom.game.session;

import doom.game.world.World;

/**
 * {@code wbstartstruct_t}: the parameters {@code G_DoCompleted} hands to the intermission ({@code wminfo}). One
 * instance lives for the whole session, like the original global: {@code next} keeps its previous value on the
 * paths where {@code G_DoCompleted} does not assign it (VANILLA-BUG compatible).
 */
final class WbStart {

    /** {@code wbplayerstruct_t}. */
    static final class Player {
        /** {@code in}: the player is in the game. */
        boolean in;
        int skills;
        int sitems;
        int ssecret;
        int stime;
        final int[] frags = new int[World.MAXPLAYERS];
    }

    boolean didSecret;
    /** 0-based episode ({@code gameepisode - 1}); {@code WI_Start} subtracts 3 before Ultimate. */
    int epsd;
    /** 0-based map just completed. */
    int last;
    /** 0-based map to go to next. */
    int next;
    int maxKills;
    int maxItems;
    int maxSecret;
    int maxFrags;
    /** Par time in tics. */
    int parTime;
    /** The console player. */
    int pnum;
    final Player[] plyr = new Player[World.MAXPLAYERS];

    WbStart() {
        for (int i = 0; i < plyr.length; i++) {
            plyr[i] = new Player();
        }
    }
}
