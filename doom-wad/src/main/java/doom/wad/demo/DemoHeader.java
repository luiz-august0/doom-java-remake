package doom.wad.demo;

/**
 * The 13-byte header of a demo ({@code G_DoPlayDemo} / {@code G_BeginRecording}). Values are the raw bytes, so a
 * demo can be written back unchanged.
 *
 * @param version       version byte as read (109 or 110); a demo written by this remake always has 109
 * @param skill         0-based skill ({@code sk_baby} = 0)
 * @param episode       episode (1 in Doom II demos)
 * @param map           map number
 * @param deathmatch    0 = cooperative/single player, 1 = deathmatch, 2 = altdeath
 * @param respawn       {@code -respawn}
 * @param fast          {@code -fast}
 * @param noMonsters    {@code -nomonsters}
 * @param consolePlayer camera player
 * @param playerInGame  which of the 4 players take part (length 4)
 */
public record DemoHeader(int version, int skill, int episode, int map, int deathmatch, int respawn, int fast,
                         int noMonsters, int consolePlayer, boolean[] playerInGame) {

    /** Number of players present. */
    public int playerCount() {
        int n = 0;
        for (boolean b : playerInGame) {
            if (b) {
                n++;
            }
        }
        return n;
    }
}
