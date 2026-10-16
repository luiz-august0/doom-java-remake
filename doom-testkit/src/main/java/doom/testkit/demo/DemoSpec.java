package doom.testkit.demo;

import java.util.Locale;

/**
 * Everything that determines a generated demo: the same spec always gives the same bytes.
 *
 * @param skill      0..4 ({@code sk_baby}..{@code sk_nightmare})
 * @param map        map lump name: {@code E<e>M<m>} or {@code MAP<nn>}
 * @param players    1..4; players {@code 0..players-1} are in game
 * @param deathmatch 0 coop/single, 1 deathmatch, 2 altdeath
 * @param respawn    {@code -respawn}
 * @param fast       {@code -fast}
 * @param noMonsters {@code -nomonsters}
 * @param tics       number of tics
 * @param seed       seed of the random walk
 * @param policy     player policy
 */
public record DemoSpec(int skill, String map, int players, int deathmatch, boolean respawn, boolean fast,
                       boolean noMonsters, int tics, long seed, DemoPolicy policy) {

    public DemoSpec {
        map = map.toUpperCase(Locale.ROOT);
        if (skill < 0 || skill > 4) {
            throw new IllegalArgumentException("skill must be 0..4: " + skill);
        }
        if (players < 1 || players > 4) {
            throw new IllegalArgumentException("players must be 1..4: " + players);
        }
        if (deathmatch < 0 || deathmatch > 2) {
            throw new IllegalArgumentException("deathmatch must be 0..2: " + deathmatch);
        }
        if (tics < 1) {
            throw new IllegalArgumentException("tics must be positive: " + tics);
        }
        parseMap(map);
    }

    /** The episode byte for a map name: {@code E<e>M<m>} gives {@code e}, {@code MAP<nn>} gives 1. */
    public static int episodeOf(String map) {
        return parseMap(map)[0];
    }

    /** The map byte for a map name. */
    public static int mapOf(String map) {
        return parseMap(map)[1];
    }

    private static int[] parseMap(String map) {
        String m = map.toUpperCase(Locale.ROOT);
        try {
            if (m.length() == 4 && m.charAt(0) == 'E' && m.charAt(2) == 'M') {
                int e = m.charAt(1) - '0';
                int n = m.charAt(3) - '0';
                if (e >= 1 && e <= 9 && n >= 1 && n <= 9) {
                    return new int[] {e, n};
                }
            } else if (m.length() == 5 && m.startsWith("MAP")) {
                int n = Integer.parseInt(m.substring(3));
                if (n >= 1 && n <= 99) {
                    return new int[] {1, n};
                }
            }
        } catch (NumberFormatException e) {
            // falls through to the error below
        }
        throw new IllegalArgumentException("Not a map name (ExMy or MAPnn): " + map);
    }

    /** The demo header for this spec (console player 0). */
    public Demo.Header header() {
        boolean[] inGame = new boolean[4];
        for (int i = 0; i < players; i++) {
            inGame[i] = true;
        }
        return new Demo.Header(Demo.VERSION_109, skill, episodeOf(map), mapOf(map), deathmatch, respawn, fast,
                noMonsters, 0, inGame);
    }
}
