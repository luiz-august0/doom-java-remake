package doom.testkit.demo;

import doom.testkit.Iwads;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The synthetic demo corpus, computed deterministically (nothing is random between runs): every map of
 * {@code doom1.wad}, {@code freedoom1.wad} and {@code freedoom2.wad} and a few of {@code freedm.wad}, each with at
 * least three demos of 1,000..3,000 tics plus a movement-only, no-monsters subset (single player on every map,
 * multiplayer on every second map).
 *
 * <p>Per map, the demo names are {@code <iwad><map><variant>}:
 * <ul>
 *   <li>{@code a} single player, random walk;</li>
 *   <li>{@code b} cooperative with 2..4 players, sometimes {@code -fast} or {@code -respawn};</li>
 *   <li>{@code c} single player with {@code -fast}/{@code -respawn} variants, or deathmatch with 2..4 players on
 *       every third map;</li>
 *   <li>{@code m} single player, {@code -nomonsters}, movement only;</li>
 *   <li>{@code p} 2..4 players (coop or deathmatch), {@code -nomonsters}, movement only.</li>
 * </ul>
 * FreeDM is deathmatch-only, so its {@code a/b/c/p} demos are deathmatch.
 */
public final class CorpusPlan {

    /** Maps of {@code freedm.wad} included in the corpus. */
    public static final List<String> FREEDM_MAPS =
            List.of("MAP01", "MAP03", "MAP07", "MAP12", "MAP18", "MAP24", "MAP29", "MAP32");

    /**
     * Freedoom's E4M2 and E4M3 have a player start with angle 360. When a second player respawns on it in a
     * cooperative game, Chocolate Doom calls {@code I_Error("G_CheckSpot: unexpected angle 8192")} (a vanilla bug in
     * the deathmatch-silent-spawn emulation), which ends the demo early. The seed of E4M2 is shifted so its walk
     * avoids the death, and the cooperative demo of E4M3 ({@code b}) is a deathmatch instead.
     */
    private static final java.util.Map<String, Long> RESEED = java.util.Map.of("f1e4m2", 1L);
    /** Corpus names whose cooperative demo is played as a deathmatch (see {@link #RESEED}). */
    private static final java.util.Set<String> NO_COOP = java.util.Set.of("f1e4m3");

    private CorpusPlan() {
    }

    /** The maps of an IWAD of the corpus. */
    public static List<String> maps(String iwad) {
        List<String> out = new ArrayList<>();
        switch (iwad) {
            case Iwads.DOOM1 -> addEpisode(out, 1);
            case Iwads.FREEDOOM1 -> {
                for (int e = 1; e <= 4; e++) {
                    addEpisode(out, e);
                }
            }
            case Iwads.FREEDOOM2 -> {
                for (int m = 1; m <= 32; m++) {
                    out.add(String.format("MAP%02d", m));
                }
            }
            case Iwads.FREEDM -> out.addAll(FREEDM_MAPS);
            default -> throw new IllegalArgumentException("Not an IWAD of the corpus: " + iwad);
        }
        return out;
    }

    private static void addEpisode(List<String> out, int episode) {
        for (int m = 1; m <= 9; m++) {
            out.add("E" + episode + "M" + m);
        }
    }

    /** The whole corpus, in manifest order. */
    public static List<ManifestEntry> build() {
        List<ManifestEntry> out = new ArrayList<>();
        for (String iwad : Iwads.ALL) {
            List<String> maps = maps(iwad);
            for (int i = 0; i < maps.size(); i++) {
                addMap(out, iwad, maps.get(i), i);
            }
        }
        return out;
    }

    private static void addMap(List<ManifestEntry> out, String iwad, String map, int index) {
        boolean dmOnly = iwad.equals(Iwads.FREEDM);
        String base = prefix(iwad) + shortMap(map);
        SplittableRandom rnd = new SplittableRandom(hash(base) + RESEED.getOrDefault(base, 0L));
        int[] skills = {2, 3, 1, 4, 0, 3, 2};
        int skill = skills[(int) Long.remainderUnsigned(rnd.nextLong(), skills.length)];

        // a: single player (a two-player deathmatch on FreeDM).
        out.add(entry(base + "a", iwad, new DemoSpec(skill, map, dmOnly ? 2 : 1, dmOnly ? 1 : 0, false, false, false,
                tics(rnd, 1500, 3000), rnd.nextLong(), DemoPolicy.RANDOM_WALK)));

        // b: cooperative with 2..4 players (three-player deathmatch on FreeDM).
        int players = dmOnly ? 3 : pickPlayers(rnd);
        boolean fast = rnd.nextInt(4) == 0;
        boolean respawn = !fast && rnd.nextInt(4) == 0;
        out.add(entry(base + "b", iwad, new DemoSpec(skills[(index + 1) % skills.length], map, players,
                dmOnly || NO_COOP.contains(base) ? 1 : 0, respawn, fast, false, tics(rnd, 1000, 2500), rnd.nextLong(), DemoPolicy.RANDOM_WALK)));

        // c: single player with option variants, or a deathmatch on every third map.
        int variant = index % 4;
        boolean cFast = variant == 1 || variant == 3;
        boolean cRespawn = variant == 2 || variant == 3;
        DemoSpec c;
        if (dmOnly) {
            c = new DemoSpec(skills[(index + 2) % skills.length], map, 4, 2, false, cFast, false,
                    tics(rnd, 1000, 3000), rnd.nextLong(), DemoPolicy.RANDOM_WALK);
        } else if (index % 3 == 0) {
            c = new DemoSpec(skills[(index + 2) % skills.length], map, 2 + rnd.nextInt(3), 1 + rnd.nextInt(2),
                    false, cFast, false, tics(rnd, 1000, 3000), rnd.nextLong(), DemoPolicy.RANDOM_WALK);
        } else {
            c = new DemoSpec(skills[(index + 2) % skills.length], map, 1, 0, cRespawn, cFast, false,
                    tics(rnd, 1000, 3000), rnd.nextLong(), DemoPolicy.RANDOM_WALK);
        }
        out.add(entry(base + "c", iwad, c));

        // m: movement-only, no monsters, single player.
        out.add(entry(base + "m", iwad, new DemoSpec(skill, map, 1, 0, false, false, true, tics(rnd, 1000, 2000),
                rnd.nextLong(), DemoPolicy.MOVEMENT_ONLY)));

        // p: movement-only, no monsters, multiplayer (every second map, every map of FreeDM).
        if (dmOnly || index % 2 == 0) {
            int deathmatch = dmOnly || index % 4 == 0 ? 1 : 0;
            out.add(entry(base + "p", iwad, new DemoSpec(skill, map, 2 + rnd.nextInt(3), deathmatch, false, false,
                    true, tics(rnd, 1000, 2000), rnd.nextLong(), DemoPolicy.MOVEMENT_ONLY)));
        }
    }

    private static ManifestEntry entry(String name, String iwad, DemoSpec spec) {
        return new ManifestEntry(name, iwad, spec);
    }

    private static int pickPlayers(SplittableRandom rnd) {
        int r = rnd.nextInt(10);
        return r < 6 ? 2 : r < 9 ? 3 : 4;
    }

    private static int tics(SplittableRandom rnd, int min, int max) {
        return min + rnd.nextInt(max - min + 1);
    }

    private static String prefix(String iwad) {
        return switch (iwad) {
            case Iwads.DOOM1 -> "d1";
            case Iwads.FREEDOOM1 -> "f1";
            case Iwads.FREEDOOM2 -> "f2";
            default -> "fm";
        };
    }

    /** {@code E1M5} gives {@code e1m5}; {@code MAP07} gives {@code m07}. */
    private static String shortMap(String map) {
        return map.startsWith("MAP") ? "m" + map.substring(3) : map.toLowerCase(java.util.Locale.ROOT);
    }

    private static long hash(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
