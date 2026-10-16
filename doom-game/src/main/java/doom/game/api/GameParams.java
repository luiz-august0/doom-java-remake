package doom.game.api;

/**
 * Parameters that start a game ({@code G_InitNew}) — from the menu, the command line or a demo header.
 *
 * @param skill         difficulty
 * @param episode       1-based episode (1 for DOOM II)
 * @param map           1-based map
 * @param deathmatch    0 = cooperative/single player, 1 = deathmatch, 2 = altdeath
 * @param respawn       {@code -respawn}
 * @param fast          {@code -fast}
 * @param noMonsters    {@code -nomonsters}
 * @param playersInGame bit mask of players present (bit {@code i} = player {@code i}); single player = 1
 * @param consolePlayer the local player
 */
public record GameParams(Skill skill, int episode, int map, int deathmatch, boolean respawn, boolean fast,
                         boolean noMonsters, int playersInGame, int consolePlayer) {

    /** A single-player game with no special flags. */
    public static GameParams singlePlayer(Skill skill, int episode, int map) {
        return new GameParams(skill, episode, map, 0, false, false, false, 1, 0);
    }
}
