package doom.game.persist;

import doom.game.data.StateId;

/** The decoded SESS section: game parameters and the counters that outlive a level. */
final class SessionData {

    static final int SARG_STATES = StateId.SARG_PAIN2 - StateId.SARG_RUN1 + 1;

    int skill;
    int episode;
    int map;
    int gametic;
    int levelTime;
    int levelStartTic;
    int totalKills;
    int totalItems;
    int totalSecret;
    int rngGame;
    int rngMisc;
    boolean netGame;
    int deathmatch;
    boolean respawnParm;
    boolean fastParm;
    boolean noMonsters;
    boolean respawnMonsters;
    int consolePlayer;
    int displayPlayer;
    final boolean[] playerInGame = new boolean[4];
    int skyTexture;
    int skyFlatNum;
    int validCount;
    boolean onGround;
    final int[] sargTics = new int[SARG_STATES];
    int bruiserShotSpeed;
    int headShotSpeed;
    int troopShotSpeed;

    static SessionData read(ByteSource s) {
        SessionData d = new SessionData();
        d.skill = s.getRange(0, 4, "skill");
        d.episode = s.getRange(1, 9, "episode");
        d.map = s.getRange(1, 99, "map");
        d.gametic = s.getInt();
        d.levelTime = s.getInt();
        d.levelStartTic = s.getInt();
        d.totalKills = s.getInt();
        d.totalItems = s.getInt();
        d.totalSecret = s.getInt();
        d.rngGame = s.getRange(0, 255, "game random index");
        d.rngMisc = s.getRange(0, 255, "misc random index");
        d.netGame = s.getBool();
        d.deathmatch = s.getRange(0, 2, "deathmatch");
        d.respawnParm = s.getBool();
        d.fastParm = s.getBool();
        d.noMonsters = s.getBool();
        d.respawnMonsters = s.getBool();
        d.consolePlayer = s.getRange(0, 3, "console player");
        d.displayPlayer = s.getRange(0, 3, "display player");
        for (int i = 0; i < 4; i++) {
            d.playerInGame[i] = s.getBool();
        }
        d.skyTexture = s.getInt();
        d.skyFlatNum = s.getInt();
        d.validCount = s.getInt();
        d.onGround = s.getBool();
        int n = s.getInt();
        if (n != SARG_STATES) {
            throw new SaveGameException("Save game is corrupt (definition table size " + n + ")");
        }
        for (int i = 0; i < n; i++) {
            d.sargTics[i] = s.getInt();
        }
        d.bruiserShotSpeed = s.getInt();
        d.headShotSpeed = s.getInt();
        d.troopShotSpeed = s.getInt();
        s.expectEnd();
        if (!d.playerInGame[d.consolePlayer]) {
            throw new SaveGameException("Save game is corrupt (console player is not in the game)");
        }
        return d;
    }
}
