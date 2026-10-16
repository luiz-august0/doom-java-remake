package doom.game.mapspecial;

import doom.game.actor.Actor;
import doom.game.player.Player;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.World;

/**
 * Placeholder until W3-C implements the map specials. Calls record a stub hit ({@link doom.game.world.Unimplemented})
 * only when the original would have changed simulation state: {@link #spawnSpecials} when the level has a special
 * sector or a scrolling wall, {@link #updateSpecials} when it has a scrolling wall (animations only change the
 * presentation-side texture translation), every trigger always. Texture and flat translations are the identity.
 */
public final class MapSpecialsStub implements MapSpecials {

    /** Line special 48: scrolling wall, the only {@code P_UpdateSpecials} effect that the trace sees. */
    private static final int SCROLL_LINE = 48;

    private final World world;
    private boolean hasScrollers;

    public MapSpecialsStub(World world) {
        this.world = world;
    }

    @Override
    public void init() {
    }

    @Override
    public void spawnSpecials() {
        boolean special = false;
        for (Sector s : world.level.sectors) {
            if (s.special != 0) {
                special = true;
                break;
            }
        }
        hasScrollers = false;
        for (Line l : world.level.lines) {
            if (l.special == SCROLL_LINE) {
                hasScrollers = true;
                break;
            }
        }
        if (special || hasScrollers) {
            world.unimplemented.hit("mapspecial.spawnSpecials");
        }
    }

    @Override
    public void updateSpecials() {
        if (hasScrollers) {
            world.unimplemented.hit("mapspecial.updateSpecials");
        }
    }

    @Override
    public void crossSpecialLine(Line line, int side, Actor thing) {
        world.unimplemented.hit("mapspecial.crossSpecialLine");
    }

    @Override
    public boolean useSpecialLine(Actor thing, Line line, int side) {
        world.unimplemented.hit("mapspecial.useSpecialLine");
        return false;
    }

    @Override
    public void shootSpecialLine(Actor thing, Line line) {
        world.unimplemented.hit("mapspecial.shootSpecialLine");
    }

    @Override
    public void playerInSpecialSector(Player player) {
        world.unimplemented.hit("mapspecial.playerInSpecialSector");
    }

    @Override
    public boolean doFloor(Line line, int floorType) {
        world.unimplemented.hit("mapspecial.doFloor");
        return false;
    }

    @Override
    public boolean doDoor(Line line, int doorType) {
        world.unimplemented.hit("mapspecial.doDoor");
        return false;
    }

    @Override
    public int textureTranslation(int texture) {
        return texture;
    }

    @Override
    public int flatTranslation(int flat) {
        return flat;
    }
}
