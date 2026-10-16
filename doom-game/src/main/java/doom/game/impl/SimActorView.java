package doom.game.impl;

import doom.game.actor.Actor;
import doom.game.actor.Thinker;
import doom.game.view.ActorView;
import doom.game.world.World;

/** The session's {@link ActorView}: map objects by actor id. */
final class SimActorView implements ActorView {

    private final World w;

    SimActorView(World world) {
        this.w = world;
    }

    private Actor a(int id) {
        Actor a = w.actorIds.get(id);
        if (a == null) {
            throw new IllegalArgumentException("no actor with id " + id);
        }
        return a;
    }

    @Override
    public int capacity() {
        return w.actorIds.capacity();
    }

    @Override
    public boolean exists(int id) {
        return w.actorIds.get(id) != null;
    }

    @Override
    public int type(int id) {
        return a(id).type;
    }

    @Override
    public int x(int id) {
        return a(id).x;
    }

    @Override
    public int y(int id) {
        return a(id).y;
    }

    @Override
    public int z(int id) {
        return a(id).z;
    }

    @Override
    public int angle(int id) {
        return a(id).angle;
    }

    @Override
    public int prevX(int id) {
        return a(id).prevX;
    }

    @Override
    public int prevY(int id) {
        return a(id).prevY;
    }

    @Override
    public int prevZ(int id) {
        return a(id).prevZ;
    }

    @Override
    public int prevAngle(int id) {
        return a(id).prevAngle;
    }

    @Override
    public int sprite(int id) {
        return a(id).sprite;
    }

    @Override
    public int frame(int id) {
        return a(id).frame;
    }

    @Override
    public int flags(int id) {
        return a(id).flags;
    }

    @Override
    public int radius(int id) {
        return a(id).radius;
    }

    @Override
    public int height(int id) {
        return a(id).height;
    }

    @Override
    public int health(int id) {
        return a(id).health;
    }

    @Override
    public int floorZ(int id) {
        return a(id).floorZ;
    }

    @Override
    public int ceilingZ(int id) {
        return a(id).ceilingZ;
    }

    @Override
    public int player(int id) {
        Actor a = a(id);
        return a.player == null ? -1 : a.player.index;
    }

    @Override
    public int sector(int id) {
        return a(id).subsector.sector.index;
    }

    @Override
    public int nextInSector(int id) {
        Actor n = a(id).snext;
        return n == null ? -1 : n.id;
    }

    @Override
    public int first() {
        return nextActor(w.thinkers.first());
    }

    @Override
    public int next(int id) {
        Actor a = a(id);
        if (a.isRemoved()) {
            return -1;
        }
        return nextActor(w.thinkers.next(a));
    }

    private int nextActor(Thinker t) {
        for (; t != null; t = w.thinkers.next(t)) {
            if (t instanceof Actor a && !a.isRemoved() && a.id >= 0) {
                return a.id;
            }
        }
        return -1;
    }
}
