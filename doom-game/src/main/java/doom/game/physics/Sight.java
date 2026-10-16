package doom.game.physics;

import doom.game.actor.Actor;
import doom.game.view.LineFlags;
import doom.game.world.Level;
import doom.game.world.Line;
import doom.game.world.Node;
import doom.game.world.Sector;
import doom.game.world.Seg;
import doom.game.world.Subsector;
import doom.game.world.Vertex;
import doom.game.world.World;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;

/** Line of sight ({@code p_sight.c}): REJECT first, then a BSP traversal (DOOM 1.9 algorithm). */
public final class Sight {

    private static final int NF_SUBSECTOR = 0x8000;

    private final World world;

    /** Eye z of the looker. */
    private int sightZStart;
    /** Slopes to the top and bottom of the target. */
    private int topSlope;
    private int bottomSlope;
    /** From t1 to t2. */
    private final Physics.Divline strace = new Physics.Divline();
    private int t2x;
    private int t2y;
    private final Physics.Divline divl = new Physics.Divline();
    /** {@code sightcounts[2]} (statistics only). */
    public final int[] sightCounts = new int[2];

    public Sight(World world) {
        this.world = world;
    }

    /** {@code P_DivlineSide}: 0 front, 1 back, 2 on the line. */
    @VanillaRef("p_sight.c:P_DivlineSide")
    static int divlineSide(int x, int y, int nx, int ny, int ndx, int ndy) {
        if (ndx == 0) {
            if (x == nx) {
                return 2;
            }
            if (x <= nx) {
                return ndy > 0 ? 1 : 0;
            }
            return ndy < 0 ? 1 : 0;
        }
        if (ndy == 0) {
            // VANILLA-BUG: compares x with node->y.
            if (x == ny) {
                return 2;
            }
            if (y <= ny) {
                return ndx < 0 ? 1 : 0;
            }
            return ndx > 0 ? 1 : 0;
        }
        int dx = x - nx;
        int dy = y - ny;
        int left = (ndy >> Fixed.FRACBITS) * (dx >> Fixed.FRACBITS);
        int right = (dy >> Fixed.FRACBITS) * (ndx >> Fixed.FRACBITS);
        if (right < left) {
            return 0; // front side
        }
        if (left == right) {
            return 2;
        }
        return 1; // back side
    }

    /** {@code P_InterceptVector2}. */
    @VanillaRef("p_sight.c:P_InterceptVector2")
    static int interceptVector2(Physics.Divline v2, Physics.Divline v1) {
        int den = Fixed.mul(v1.dy >> 8, v2.dx) - Fixed.mul(v1.dx >> 8, v2.dy);
        if (den == 0) {
            return 0;
        }
        int num = Fixed.mul((v1.x - v2.x) >> 8, v1.dy) + Fixed.mul((v2.y - v1.y) >> 8, v1.dx);
        return Fixed.div(num, den);
    }

    /** {@code P_CrossSubsector}: returns true if the strace crosses the given subsector successfully. */
    @VanillaRef("p_sight.c:P_CrossSubsector")
    private boolean crossSubsector(int num) {
        Level level = world.level;
        Subsector sub = level.subsectors[num];
        int validCount = world.validCount;
        Seg[] segs = level.segs;
        for (int s = sub.firstLine, count = sub.numLines; count > 0; s++, count--) {
            Seg seg = segs[s];
            Line line = seg.lineDef;

            // allready checked other side?
            if (line.validCount == validCount) {
                continue;
            }
            line.validCount = validCount;

            Vertex v1 = line.v1;
            Vertex v2 = line.v2;
            int s1 = divlineSide(v1.x, v1.y, strace.x, strace.y, strace.dx, strace.dy);
            int s2 = divlineSide(v2.x, v2.y, strace.x, strace.y, strace.dx, strace.dy);

            // line isn't crossed?
            if (s1 == s2) {
                continue;
            }

            divl.x = v1.x;
            divl.y = v1.y;
            divl.dx = v2.x - v1.x;
            divl.dy = v2.y - v1.y;
            s1 = divlineSide(strace.x, strace.y, divl.x, divl.y, divl.dx, divl.dy);
            s2 = divlineSide(t2x, t2y, divl.x, divl.y, divl.dx, divl.dy);

            // line isn't crossed?
            if (s1 == s2) {
                continue;
            }

            // Backsector may be NULL if this is an "impassible glass" hack line.
            if (line.backSector == null) {
                return false;
            }

            // stop because it is not two sided anyway
            if ((line.flags & LineFlags.TWOSIDED) == 0) {
                return false;
            }

            // crosses a two sided line
            Sector front = seg.frontSector;
            Sector back = seg.backSector;

            // no wall to block sight with?
            if (front.floorHeight == back.floorHeight && front.ceilingHeight == back.ceilingHeight) {
                continue;
            }

            // possible occluder because of ceiling height differences
            int opentop = front.ceilingHeight < back.ceilingHeight ? front.ceilingHeight : back.ceilingHeight;
            // because of ceiling height differences
            int openbottom = front.floorHeight > back.floorHeight ? front.floorHeight : back.floorHeight;

            // quick test for totally closed doors
            if (openbottom >= opentop) {
                return false; // stop
            }

            int frac = interceptVector2(strace, divl);

            if (front.floorHeight != back.floorHeight) {
                int slope = Fixed.div(openbottom - sightZStart, frac);
                if (slope > bottomSlope) {
                    bottomSlope = slope;
                }
            }
            if (front.ceilingHeight != back.ceilingHeight) {
                int slope = Fixed.div(opentop - sightZStart, frac);
                if (slope < topSlope) {
                    topSlope = slope;
                }
            }
            if (topSlope <= bottomSlope) {
                return false; // stop
            }
        }
        // passed the subsector ok
        return true;
    }

    /** {@code P_CrossBSPNode}: returns true if strace crosses the given node successfully. */
    @VanillaRef("p_sight.c:P_CrossBSPNode")
    private boolean crossBspNode(int bspnum) {
        if ((bspnum & NF_SUBSECTOR) != 0) {
            if (bspnum == -1) {
                return crossSubsector(0);
            }
            return crossSubsector(bspnum & ~NF_SUBSECTOR);
        }
        Node bsp = world.level.nodes[bspnum];

        // decide which side the start point is on
        int side = divlineSide(strace.x, strace.y, bsp.x, bsp.y, bsp.dx, bsp.dy);
        if (side == 2) {
            side = 0; // an "on" should cross both sides
        }

        // cross the starting side
        if (!crossBspNode(bsp.children[side])) {
            return false;
        }

        // the partition plane is crossed here
        if (side == divlineSide(t2x, t2y, bsp.x, bsp.y, bsp.dx, bsp.dy)) {
            // the line doesn't touch the other side
            return true;
        }

        // cross the ending side
        return crossBspNode(bsp.children[side ^ 1]);
    }

    /** {@code P_CheckSight}: whether a straight line between t1 and t2 is unobstructed. Uses REJECT. */
    @VanillaRef("p_sight.c:P_CheckSight")
    public boolean checkSight(Actor t1, Actor t2) {
        Level level = world.level;
        // First check for trivial rejection. Determine subsector entries in REJECT table.
        int s1 = t1.subsector.sector.index;
        int s2 = t2.subsector.sector.index;
        int pnum = s1 * level.sectors.length + s2;
        int bytenum = pnum >> 3;
        int bitnum = 1 << (pnum & 7);

        // Check in REJECT table.
        byte[] reject = level.reject.matrix;
        if (bytenum >= 0 && bytenum < reject.length && (reject[bytenum] & bitnum) != 0) {
            sightCounts[0]++;
            // can't possibly be connected
            return false;
        }

        // An unobstructed LOS is possible. Now look from eyes of t1 to any part of t2.
        sightCounts[1]++;

        world.validCount++;

        sightZStart = t1.z + t1.height - (t1.height >> 2);
        topSlope = (t2.z + t2.height) - sightZStart;
        bottomSlope = t2.z - sightZStart;

        strace.x = t1.x;
        strace.y = t1.y;
        t2x = t2.x;
        t2y = t2.y;
        strace.dx = t2.x - t1.x;
        strace.dy = t2.y - t1.y;

        // the head node is the last node output
        return crossBspNode(level.nodes.length - 1);
    }
}
