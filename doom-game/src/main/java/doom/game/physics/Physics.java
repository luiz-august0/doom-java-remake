package doom.game.physics;

import doom.game.actor.Actor;
import doom.game.data.MobjType;
import doom.game.data.StateId;
import doom.game.player.Player;
import doom.game.view.LineFlags;
import doom.game.view.MobjFlags;
import doom.game.view.SoundId;
import doom.game.world.Blockmap;
import doom.game.world.Level;
import doom.game.world.Line;
import doom.game.world.Node;
import doom.game.world.Sector;
import doom.game.world.SpawnPoint;
import doom.game.world.Subsector;
import doom.game.world.World;
import doom.math.Angle;
import doom.math.BBox;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.Trig;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameVersion;
import java.util.Arrays;

/**
 * Movement, collision and traversal: {@code p_maputl.c}, {@code p_map.c} and {@code R_PointInSubsector}. The
 * original's file-level globals ({@code tmfloorz}, {@code opentop}, {@code linetarget}, {@code intercepts} ...) are
 * public fields here, because the rule areas read them exactly like the C code does. Callbacks are pre-allocated
 * visitor objects (no allocation per call).
 */
public final class Physics {

    public static final int MAPBLOCKUNITS = 128;
    public static final int MAPBLOCKSIZE = MAPBLOCKUNITS * Fixed.FRACUNIT;
    public static final int MAPBLOCKSHIFT = Fixed.FRACBITS + 7;
    public static final int MAPBTOFRAC = MAPBLOCKSHIFT - Fixed.FRACBITS;
    public static final int MAXRADIUS = 32 * Fixed.FRACUNIT;
    public static final int USERANGE = 64 * Fixed.FRACUNIT;
    public static final int MELEERANGE = 64 * Fixed.FRACUNIT;
    public static final int MISSILERANGE = 32 * 64 * Fixed.FRACUNIT;
    public static final int PT_ADDLINES = 1;
    public static final int PT_ADDTHINGS = 2;
    public static final int PT_EARLYOUT = 4;
    public static final int MAXINTERCEPTS_ORIGINAL = 128;
    public static final int MAXSPECIALCROSS_ORIGINAL = 8;
    private static final int DEFAULT_SPECHIT_MAGIC = 0x01C09C98;
    private static final int NF_SUBSECTOR = 0x8000;
    /**
     * {@code intercepts_overrun[]}: the sizes of the variables after {@code intercepts[]} in DOOM.EXE, and which of our
     * fields each one is (0 none, 1 lowfloor, 2 openbottom, 3 opentop, 4 openrange, 5 bulletslope, 6 playerstarts as
     * 16-bit values, 7 bmapwidth, 8 bmaporgx, 9 bmaporgy, 10 bmapheight).
     */
    private static final int[] OVERRUN_LENGTHS = {4, 4, 4, 4, 4, 4, 4, 4, 120, 8, 4, 4, 4, 4, 40, 4, 4, 4, 4, 4, 4, 4};
    private static final int[] OVERRUN_TARGETS = {0, 0, 0, 1, 2, 3, 4, 0, 0, 0, 5, 0, 0, 0, 6, 0, 7, 0, 8, 9, 0, 10};

    /** A {@code PIT_*} line function. */
    public interface LineVisitor {
        boolean visit(Line line);
    }

    /** A {@code PIT_*} thing function. */
    public interface ThingVisitor {
        boolean visit(Actor thing);
    }

    /** A {@code PTR_*} traverser. */
    public interface InterceptVisitor {
        boolean visit(Intercept in);
    }

    /** {@code divline_t}. */
    public static final class Divline {
        public int x;
        public int y;
        public int dx;
        public int dy;
    }

    /** {@code intercept_t}. */
    public static final class Intercept {
        public int frac;
        public boolean isALine;
        public Line line;
        public Actor thing;
    }

    private final World world;

    // ------------------------------------------------------------------ p_maputl.c globals

    public int openTop;
    public int openBottom;
    public int openRange;
    public int lowFloor;
    /** {@code trace}: the line of the current path traversal. */
    public final Divline trace = new Divline();
    public boolean earlyOut;
    private Intercept[] intercepts = new Intercept[256];
    /** {@code intercept_p - intercepts}. */
    private int interceptCount;

    // ------------------------------------------------------------------ p_map.c globals

    public final int[] tmBBox = new int[4];
    public Actor tmThing;
    public int tmFlags;
    public int tmX;
    public int tmY;
    /** If true, the move would be ok if within {@link #tmFloorZ} - {@link #tmCeilingZ}. */
    public boolean floatOk;
    public int tmFloorZ;
    public int tmCeilingZ;
    public int tmDropoffZ;
    /** The line that lowers the ceiling, so missiles don't explode against sky hack walls. */
    public Line ceilingLine;
    /** Special lines touched by the last position check, processed after a successful move. */
    public Line[] specHit = new Line[32];
    public int numSpecHit;

    private int bestSlideFrac;
    private int secondSlideFrac;
    private Line bestSlideLine;
    private Line secondSlideLine;
    private Actor slideMo;
    private int tmXMove;
    private int tmYMove;

    /** Who got hit by the last aim / line attack (or null). */
    public Actor lineTarget;
    public Actor shootThing;
    /** Height if not aiming up or down. */
    public int shootZ;
    public int laDamage;
    public int attackRange;
    public int aimSlope;
    public int topSlope;
    public int bottomSlope;
    /** {@code bulletslope} of {@code p_pspr.c}; here because the intercepts overrun writes it. */
    public int bulletSlope;

    private Actor useThing;
    private Actor bombSource;
    private Actor bombSpot;
    private int bombDamage;
    /** {@code crushchange} (an int: the spechit overrun may store an address in it). */
    public int crushChange;
    /** {@code nofit} (an int: the spechit overrun may store an address in it). */
    public int noFit;

    private final ThingVisitor pitStompThing = this::pitStompThing;
    private final LineVisitor pitCheckLine = this::pitCheckLine;
    private final ThingVisitor pitCheckThing = this::pitCheckThing;
    private final LineVisitor pitAddLineIntercepts = this::pitAddLineIntercepts;
    private final ThingVisitor pitAddThingIntercepts = this::pitAddThingIntercepts;
    private final InterceptVisitor ptrSlideTraverse = this::ptrSlideTraverse;
    private final InterceptVisitor ptrAimTraverse = this::ptrAimTraverse;
    private final InterceptVisitor ptrShootTraverse = this::ptrShootTraverse;
    private final InterceptVisitor ptrUseTraverse = this::ptrUseTraverse;
    private final ThingVisitor pitRadiusAttack = this::pitRadiusAttack;
    private final ThingVisitor pitChangeSector = this::pitChangeSector;
    private final Divline scratchDivline = new Divline();

    public Physics(World world) {
        this.world = world;
        for (int i = 0; i < intercepts.length; i++) {
            intercepts[i] = new Intercept();
        }
    }

    // ==================================================================================== r_main.c

    /** {@code R_PointOnSide}. */
    @VanillaRef("r_main.c:R_PointOnSide")
    public static int pointOnSide(int x, int y, Node node) {
        if (node.dx == 0) {
            if (x <= node.x) {
                return node.dy > 0 ? 1 : 0;
            }
            return node.dy < 0 ? 1 : 0;
        }
        if (node.dy == 0) {
            if (y <= node.y) {
                return node.dx < 0 ? 1 : 0;
            }
            return node.dx > 0 ? 1 : 0;
        }
        int dx = x - node.x;
        int dy = y - node.y;
        // Try to quickly decide by looking at sign bits.
        if (((node.dy ^ node.dx ^ dx ^ dy) & 0x80000000) != 0) {
            if (((node.dy ^ dx) & 0x80000000) != 0) {
                return 1; // (left is negative)
            }
            return 0;
        }
        int left = Fixed.mul(node.dy >> Fixed.FRACBITS, dx);
        int right = Fixed.mul(dy, node.dx >> Fixed.FRACBITS);
        if (right < left) {
            return 0; // front side
        }
        return 1; // back side
    }

    /** {@code R_PointInSubsector}. */
    @VanillaRef("r_main.c:R_PointInSubsector")
    public Subsector pointInSubsector(int x, int y) {
        Level level = world.level;
        // single subsector is a special case
        if (level.nodes.length == 0) {
            return level.subsectors[0];
        }
        int nodenum = level.nodes.length - 1;
        while ((nodenum & NF_SUBSECTOR) == 0) {
            Node node = level.nodes[nodenum];
            int side = pointOnSide(x, y, node);
            nodenum = node.children[side];
        }
        return level.subsectors[nodenum & ~NF_SUBSECTOR];
    }

    // ==================================================================================== p_maputl.c

    /** {@code P_PointOnLineSide}: 0 front, 1 back. */
    @VanillaRef("p_maputl.c:P_PointOnLineSide")
    public static int pointOnLineSide(int x, int y, Line line) {
        if (line.dx == 0) {
            if (x <= line.v1.x) {
                return line.dy > 0 ? 1 : 0;
            }
            return line.dy < 0 ? 1 : 0;
        }
        if (line.dy == 0) {
            if (y <= line.v1.y) {
                return line.dx < 0 ? 1 : 0;
            }
            return line.dx > 0 ? 1 : 0;
        }
        int dx = x - line.v1.x;
        int dy = y - line.v1.y;
        int left = Fixed.mul(line.dy >> Fixed.FRACBITS, dx);
        int right = Fixed.mul(dy, line.dx >> Fixed.FRACBITS);
        if (right < left) {
            return 0; // front side
        }
        return 1; // back side
    }

    /** {@code P_BoxOnLineSide}: 0 or 1, -1 if the box crosses the (infinite) line. */
    @VanillaRef("p_maputl.c:P_BoxOnLineSide")
    public static int boxOnLineSide(int[] tmbox, Line ld) {
        int p1 = 0;
        int p2 = 0;
        switch (ld.slopeType) {
            case Line.ST_HORIZONTAL -> {
                p1 = tmbox[BBox.BOXTOP] > ld.v1.y ? 1 : 0;
                p2 = tmbox[BBox.BOXBOTTOM] > ld.v1.y ? 1 : 0;
                if (ld.dx < 0) {
                    p1 ^= 1;
                    p2 ^= 1;
                }
            }
            case Line.ST_VERTICAL -> {
                p1 = tmbox[BBox.BOXRIGHT] < ld.v1.x ? 1 : 0;
                p2 = tmbox[BBox.BOXLEFT] < ld.v1.x ? 1 : 0;
                if (ld.dy < 0) {
                    p1 ^= 1;
                    p2 ^= 1;
                }
            }
            case Line.ST_POSITIVE -> {
                p1 = pointOnLineSide(tmbox[BBox.BOXLEFT], tmbox[BBox.BOXTOP], ld);
                p2 = pointOnLineSide(tmbox[BBox.BOXRIGHT], tmbox[BBox.BOXBOTTOM], ld);
            }
            case Line.ST_NEGATIVE -> {
                p1 = pointOnLineSide(tmbox[BBox.BOXRIGHT], tmbox[BBox.BOXTOP], ld);
                p2 = pointOnLineSide(tmbox[BBox.BOXLEFT], tmbox[BBox.BOXBOTTOM], ld);
            }
            default -> {
            }
        }
        if (p1 == p2) {
            return p1;
        }
        return -1;
    }

    /** {@code P_PointOnDivlineSide}: 0 or 1. */
    @VanillaRef("p_maputl.c:P_PointOnDivlineSide")
    public static int pointOnDivlineSide(int x, int y, Divline line) {
        if (line.dx == 0) {
            if (x <= line.x) {
                return line.dy > 0 ? 1 : 0;
            }
            return line.dy < 0 ? 1 : 0;
        }
        if (line.dy == 0) {
            if (y <= line.y) {
                return line.dx < 0 ? 1 : 0;
            }
            return line.dx > 0 ? 1 : 0;
        }
        int dx = x - line.x;
        int dy = y - line.y;
        // try to quickly decide by looking at sign bits
        if (((line.dy ^ line.dx ^ dx ^ dy) & 0x80000000) != 0) {
            if (((line.dy ^ dx) & 0x80000000) != 0) {
                return 1; // (left is negative)
            }
            return 0;
        }
        int left = Fixed.mul(line.dy >> 8, dx >> 8);
        int right = Fixed.mul(dy >> 8, line.dx >> 8);
        if (right < left) {
            return 0; // front side
        }
        return 1; // back side
    }

    /** {@code P_MakeDivline}. */
    @VanillaRef("p_maputl.c:P_MakeDivline")
    public static void makeDivline(Line li, Divline dl) {
        dl.x = li.v1.x;
        dl.y = li.v1.y;
        dl.dx = li.dx;
        dl.dy = li.dy;
    }

    /** {@code P_InterceptVector}: the fractional intercept point along the first divline. */
    @VanillaRef("p_maputl.c:P_InterceptVector")
    public static int interceptVector(Divline v2, Divline v1) {
        int den = Fixed.mul(v1.dy >> 8, v2.dx) - Fixed.mul(v1.dx >> 8, v2.dy);
        if (den == 0) {
            return 0;
        }
        int num = Fixed.mul((v1.x - v2.x) >> 8, v1.dy) + Fixed.mul((v2.y - v1.y) >> 8, v1.dx);
        return Fixed.div(num, den);
    }

    /** {@code P_LineOpening}: sets {@link #openTop}, {@link #openBottom}, {@link #openRange}, {@link #lowFloor}. */
    @VanillaRef("p_maputl.c:P_LineOpening")
    public void lineOpening(Line linedef) {
        if (linedef.sideNum[1] == -1) {
            // single sided line
            openRange = 0;
            return;
        }
        Sector front = linedef.frontSector;
        Sector back = linedef.backSector;

        if (front.ceilingHeight < back.ceilingHeight) {
            openTop = front.ceilingHeight;
        } else {
            openTop = back.ceilingHeight;
        }

        if (front.floorHeight > back.floorHeight) {
            openBottom = front.floorHeight;
            lowFloor = back.floorHeight;
        } else {
            openBottom = back.floorHeight;
            lowFloor = front.floorHeight;
        }
        openRange = openTop - openBottom;
    }

    /** {@code P_UnsetThingPosition}: unlinks a thing from the blockmap and its sector. */
    @VanillaRef("p_maputl.c:P_UnsetThingPosition")
    public void unsetThingPosition(Actor thing) {
        if ((thing.flags & MobjFlags.NOSECTOR) == 0) {
            // inert things don't need to be in blockmap? unlink from subsector
            if (thing.snext != null) {
                thing.snext.sprev = thing.sprev;
            }
            if (thing.sprev != null) {
                thing.sprev.snext = thing.snext;
            } else {
                thing.subsector.sector.thingList = thing.snext;
            }
        }

        if ((thing.flags & MobjFlags.NOBLOCKMAP) == 0) {
            // inert things don't need to be in blockmap; unlink from block map
            if (thing.bnext != null) {
                thing.bnext.bprev = thing.bprev;
            }
            if (thing.bprev != null) {
                thing.bprev.bnext = thing.bnext;
            } else {
                Blockmap bm = world.level.blockmap;
                int blockx = (thing.x - bm.originX) >> MAPBLOCKSHIFT;
                int blocky = (thing.y - bm.originY) >> MAPBLOCKSHIFT;
                if (blockx >= 0 && blockx < bm.columns && blocky >= 0 && blocky < bm.rows) {
                    setBlockLink(blocky * bm.columns + blockx, thing.bnext);
                }
            }
        }
    }

    /** {@code P_SetThingPosition}: links a thing into its subsector's sector and its block; sets the subsector. */
    @VanillaRef("p_maputl.c:P_SetThingPosition")
    public void setThingPosition(Actor thing) {
        // link into subsector
        Subsector ss = pointInSubsector(thing.x, thing.y);
        thing.subsector = ss;

        if ((thing.flags & MobjFlags.NOSECTOR) == 0) {
            // invisible things don't go into the sector links
            Sector sec = ss.sector;
            thing.sprev = null;
            thing.snext = sec.thingList;
            if (sec.thingList != null) {
                sec.thingList.sprev = thing;
            }
            sec.thingList = thing;
        }

        // link into blockmap
        if ((thing.flags & MobjFlags.NOBLOCKMAP) == 0) {
            // inert things don't need to be in blockmap
            Blockmap bm = world.level.blockmap;
            int blockx = (thing.x - bm.originX) >> MAPBLOCKSHIFT;
            int blocky = (thing.y - bm.originY) >> MAPBLOCKSHIFT;
            if (blockx >= 0 && blockx < bm.columns && blocky >= 0 && blocky < bm.rows) {
                int link = blocky * bm.columns + blockx;
                Actor first = blockLink(link);
                thing.bprev = null;
                thing.bnext = first;
                if (first != null) {
                    first.bprev = thing;
                }
                setBlockLink(link, thing);
            } else {
                // thing is off the map
                thing.bnext = thing.bprev = null;
            }
        }
    }

    private Actor blockLink(int index) {
        Actor[] links = world.blockLinks;
        // DIVERGE: an index outside blocklinks (only after an intercepts overrun corrupted the blockmap size) reads
        // as an empty cell instead of random memory.
        return index >= 0 && index < links.length ? links[index] : null;
    }

    private void setBlockLink(int index, Actor thing) {
        Actor[] links = world.blockLinks;
        if (index >= 0 && index < links.length) {
            links[index] = thing;
        }
    }

    /**
     * {@code P_BlockLinesIterator}: calls {@code func} for each line of block (x, y) not yet visited in this
     * {@code validcount} pass. Increment {@link World#validCount} before the first call of a pass.
     */
    @VanillaRef("p_maputl.c:P_BlockLinesIterator")
    public boolean blockLinesIterator(int x, int y, LineVisitor func) {
        Blockmap bm = world.level.blockmap;
        if (x < 0 || y < 0 || x >= bm.columns || y >= bm.rows) {
            return true;
        }
        int offset = y * bm.columns + x;
        int[] lump = bm.lump;
        if (Blockmap.TABLE_START + offset >= lump.length) {
            return true; // only possible after an intercepts overrun changed the blockmap size
        }
        offset = lump[Blockmap.TABLE_START + offset];
        Line[] lines = world.level.lines;
        int validCount = world.validCount;
        for (int list = offset; list < lump.length && lump[list] != -1; list++) {
            int n = lump[list];
            if (n < 0 || n >= lines.length) {
                continue;
            }
            Line ld = lines[n];
            if (ld.validCount == validCount) {
                continue; // line has already been checked
            }
            ld.validCount = validCount;
            if (!func.visit(ld)) {
                return false;
            }
        }
        return true; // everything was checked
    }

    /** {@code P_BlockThingsIterator}. */
    @VanillaRef("p_maputl.c:P_BlockThingsIterator")
    public boolean blockThingsIterator(int x, int y, ThingVisitor func) {
        Blockmap bm = world.level.blockmap;
        if (x < 0 || y < 0 || x >= bm.columns || y >= bm.rows) {
            return true;
        }
        for (Actor mobj = blockLink(y * bm.columns + x); mobj != null; mobj = mobj.bnext) {
            if (!func.visit(mobj)) {
                return false;
            }
        }
        return true;
    }

    private Intercept nextIntercept() {
        if (interceptCount == intercepts.length) {
            int n = intercepts.length;
            intercepts = Arrays.copyOf(intercepts, n * 2);
            for (int i = n; i < intercepts.length; i++) {
                intercepts[i] = new Intercept();
            }
        }
        return intercepts[interceptCount];
    }

    /** {@code PIT_AddLineIntercepts}: a line is crossed if its endpoints are on opposite sides of the trace. */
    @VanillaRef("p_maputl.c:PIT_AddLineIntercepts")
    private boolean pitAddLineIntercepts(Line ld) {
        int s1;
        int s2;
        // avoid precision problems with two routines
        if (trace.dx > Fixed.FRACUNIT * 16 || trace.dy > Fixed.FRACUNIT * 16
                || trace.dx < -Fixed.FRACUNIT * 16 || trace.dy < -Fixed.FRACUNIT * 16) {
            s1 = pointOnDivlineSide(ld.v1.x, ld.v1.y, trace);
            s2 = pointOnDivlineSide(ld.v2.x, ld.v2.y, trace);
        } else {
            s1 = pointOnLineSide(trace.x, trace.y, ld);
            s2 = pointOnLineSide(trace.x + trace.dx, trace.y + trace.dy, ld);
        }
        if (s1 == s2) {
            return true; // line isn't crossed
        }

        // hit the line
        Divline dl = scratchDivline;
        makeDivline(ld, dl);
        int frac = interceptVector(trace, dl);
        if (frac < 0) {
            return true; // behind source
        }

        // try to early out the check
        if (earlyOut && frac < Fixed.FRACUNIT && ld.backSector == null) {
            return false; // stop checking
        }

        Intercept in = nextIntercept();
        in.frac = frac;
        in.isALine = true;
        in.line = ld;
        in.thing = null;
        interceptsOverrun(interceptCount, in);
        interceptCount++;
        return true; // continue
    }

    /** {@code PIT_AddThingIntercepts}. */
    @VanillaRef("p_maputl.c:PIT_AddThingIntercepts")
    private boolean pitAddThingIntercepts(Actor thing) {
        boolean tracePositive = (trace.dx ^ trace.dy) > 0;
        int x1;
        int y1;
        int x2;
        int y2;
        // check a corner to corner crossection for hit
        if (tracePositive) {
            x1 = thing.x - thing.radius;
            y1 = thing.y + thing.radius;
            x2 = thing.x + thing.radius;
            y2 = thing.y - thing.radius;
        } else {
            x1 = thing.x - thing.radius;
            y1 = thing.y - thing.radius;
            x2 = thing.x + thing.radius;
            y2 = thing.y + thing.radius;
        }

        int s1 = pointOnDivlineSide(x1, y1, trace);
        int s2 = pointOnDivlineSide(x2, y2, trace);
        if (s1 == s2) {
            return true; // line isn't crossed
        }

        Divline dl = scratchDivline;
        dl.x = x1;
        dl.y = y1;
        dl.dx = x2 - x1;
        dl.dy = y2 - y1;

        int frac = interceptVector(trace, dl);
        if (frac < 0) {
            return true; // behind source
        }

        Intercept in = nextIntercept();
        in.frac = frac;
        in.isALine = false;
        in.line = null;
        in.thing = thing;
        interceptsOverrun(interceptCount, in);
        interceptCount++;
        return true; // keep going
    }

    /** {@code P_TraverseIntercepts}: visits the intercepts in order of distance, up to {@code maxfrac}. */
    @VanillaRef("p_maputl.c:P_TraverseIntercepts")
    private boolean traverseIntercepts(InterceptVisitor func, int maxfrac) {
        int count = interceptCount;
        Intercept in = null;
        while (count-- > 0) {
            int dist = Integer.MAX_VALUE;
            for (int scan = 0; scan < interceptCount; scan++) {
                if (intercepts[scan].frac < dist) {
                    dist = intercepts[scan].frac;
                    in = intercepts[scan];
                }
            }
            if (dist > maxfrac) {
                return true; // checked everything in range
            }
            if (!func.visit(in)) {
                return false; // don't bother going farther
            }
            in.frac = Integer.MAX_VALUE;
        }
        return true; // everything was traversed
    }

    /**
     * {@code InterceptsOverrun} (Chocolate Doom, after PrBoom-plus): in vanilla DOOM the 129th and later intercepts
     * overwrite the globals that follow {@code intercepts[]} in memory.
     */
    @VanillaRef("p_maputl.c:InterceptsOverrun")
    private void interceptsOverrun(int numIntercepts, Intercept intercept) {
        if (numIntercepts <= MAXINTERCEPTS_ORIGINAL) {
            return; // no overrun
        }
        int location = (numIntercepts - MAXINTERCEPTS_ORIGINAL - 1) * 12;
        interceptsMemoryOverrun(location, intercept.frac);
        interceptsMemoryOverrun(location + 4, intercept.isALine ? 1 : 0);
        // DIVERGE: Chocolate writes the host address of the line / thing here (not reproducible); we write 0.
        interceptsMemoryOverrun(location + 8, 0);
    }

    /** {@code InterceptsMemoryOverrun}: the memory layout of DOOM.EXE after {@code intercepts[]}. */
    private void interceptsMemoryOverrun(int location, int value) {
        int[] lens = OVERRUN_LENGTHS;
        int[] targets = OVERRUN_TARGETS;
        int offset = 0;
        for (int i = 0; i < lens.length; i++) {
            if (offset + lens[i] > location) {
                Blockmap bm = world.level.blockmap;
                switch (targets[i]) {
                    case 1 -> lowFloor = value;
                    case 2 -> openBottom = value;
                    case 3 -> openTop = value;
                    case 4 -> openRange = value;
                    case 5 -> bulletSlope = value;
                    case 6 -> {
                        int index = (location - offset) / 2;
                        writePlayerStartShort(index, value & 0xffff);
                        writePlayerStartShort(index + 1, (value >> 16) & 0xffff);
                    }
                    case 7 -> bm.columns = value;
                    case 8 -> bm.originX = value;
                    case 9 -> bm.originY = value;
                    case 10 -> bm.rows = value;
                    default -> {
                    }
                }
                return;
            }
            offset += lens[i];
        }
    }

    private void writePlayerStartShort(int index, int value) {
        if (index >= 0 && index < 20) {
            SpawnPoint p = world.playerStarts[index / 5];
            p.setShortField(index % 5, value);
        }
    }

    /**
     * {@code P_PathTraverse}: traces a line from (x1, y1) to (x2, y2), collecting the lines and/or things it crosses
     * (block by block) and then calling {@code trav} for each in order of distance.
     *
     * @return true if the traverser returned true for everything
     */
    @VanillaRef("p_maputl.c:P_PathTraverse")
    public boolean pathTraverse(int x1, int y1, int x2, int y2, int flags, InterceptVisitor trav) {
        Blockmap bm = world.level.blockmap;
        earlyOut = (flags & PT_EARLYOUT) != 0;

        world.validCount++;
        interceptCount = 0;

        if (((x1 - bm.originX) & (MAPBLOCKSIZE - 1)) == 0) {
            x1 += Fixed.FRACUNIT; // don't side exactly on a line
        }
        if (((y1 - bm.originY) & (MAPBLOCKSIZE - 1)) == 0) {
            y1 += Fixed.FRACUNIT; // don't side exactly on a line
        }

        trace.x = x1;
        trace.y = y1;
        trace.dx = x2 - x1;
        trace.dy = y2 - y1;

        x1 -= bm.originX;
        y1 -= bm.originY;
        int xt1 = x1 >> MAPBLOCKSHIFT;
        int yt1 = y1 >> MAPBLOCKSHIFT;

        x2 -= bm.originX;
        y2 -= bm.originY;
        int xt2 = x2 >> MAPBLOCKSHIFT;
        int yt2 = y2 >> MAPBLOCKSHIFT;

        int mapxstep;
        int mapystep;
        int partial;
        int xstep;
        int ystep;

        if (xt2 > xt1) {
            mapxstep = 1;
            partial = Fixed.FRACUNIT - ((x1 >> MAPBTOFRAC) & (Fixed.FRACUNIT - 1));
            ystep = Fixed.div(y2 - y1, Math.abs(x2 - x1));
        } else if (xt2 < xt1) {
            mapxstep = -1;
            partial = (x1 >> MAPBTOFRAC) & (Fixed.FRACUNIT - 1);
            ystep = Fixed.div(y2 - y1, Math.abs(x2 - x1));
        } else {
            mapxstep = 0;
            partial = Fixed.FRACUNIT;
            ystep = 256 * Fixed.FRACUNIT;
        }
        int yintercept = (y1 >> MAPBTOFRAC) + Fixed.mul(partial, ystep);

        if (yt2 > yt1) {
            mapystep = 1;
            partial = Fixed.FRACUNIT - ((y1 >> MAPBTOFRAC) & (Fixed.FRACUNIT - 1));
            xstep = Fixed.div(x2 - x1, Math.abs(y2 - y1));
        } else if (yt2 < yt1) {
            mapystep = -1;
            partial = (y1 >> MAPBTOFRAC) & (Fixed.FRACUNIT - 1);
            xstep = Fixed.div(x2 - x1, Math.abs(y2 - y1));
        } else {
            mapystep = 0;
            partial = Fixed.FRACUNIT;
            xstep = 256 * Fixed.FRACUNIT;
        }
        int xintercept = (x1 >> MAPBTOFRAC) + Fixed.mul(partial, xstep);

        // Step through map blocks. Count is present to prevent a round off error from skipping the break.
        int mapx = xt1;
        int mapy = yt1;
        for (int count = 0; count < 64; count++) {
            if ((flags & PT_ADDLINES) != 0) {
                if (!blockLinesIterator(mapx, mapy, pitAddLineIntercepts)) {
                    return false; // early out
                }
            }
            if ((flags & PT_ADDTHINGS) != 0) {
                if (!blockThingsIterator(mapx, mapy, pitAddThingIntercepts)) {
                    return false; // early out
                }
            }
            if (mapx == xt2 && mapy == yt2) {
                break;
            }
            if ((yintercept >> Fixed.FRACBITS) == mapy) {
                yintercept += ystep;
                mapx += mapxstep;
            } else if ((xintercept >> Fixed.FRACBITS) == mapx) {
                xintercept += xstep;
                mapy += mapystep;
            }
        }
        // go through the sorted list
        return traverseIntercepts(trav, Fixed.FRACUNIT);
    }

    // ==================================================================================== p_map.c: teleport move

    /** {@code PIT_StompThing}. */
    @VanillaRef("p_map.c:PIT_StompThing")
    private boolean pitStompThing(Actor thing) {
        if ((thing.flags & MobjFlags.SHOOTABLE) == 0) {
            return true;
        }
        int blockdist = thing.radius + tmThing.radius;
        if (Math.abs(thing.x - tmX) >= blockdist || Math.abs(thing.y - tmY) >= blockdist) {
            return true; // didn't hit it
        }
        // don't clip against self
        if (thing == tmThing) {
            return true;
        }
        // monsters don't stomp things except on boss level
        if (tmThing.player == null && world.gameMap != 30) {
            return false;
        }
        world.combat.damageMobj(thing, tmThing, tmThing, 10000);
        return true;
    }

    /** {@code P_TeleportMove}: kills anything occupying the position, then moves. */
    @VanillaRef("p_map.c:P_TeleportMove")
    public boolean teleportMove(Actor thing, int x, int y) {
        // kill anything occupying the position
        tmThing = thing;
        tmFlags = thing.flags;
        tmX = x;
        tmY = y;

        tmBBox[BBox.BOXTOP] = y + tmThing.radius;
        tmBBox[BBox.BOXBOTTOM] = y - tmThing.radius;
        tmBBox[BBox.BOXRIGHT] = x + tmThing.radius;
        tmBBox[BBox.BOXLEFT] = x - tmThing.radius;

        Subsector newsubsec = pointInSubsector(x, y);
        ceilingLine = null;

        // The base floor/ceiling is from the subsector that contains the point.
        // Any contacted lines the step closer together will adjust them.
        tmFloorZ = tmDropoffZ = newsubsec.sector.floorHeight;
        tmCeilingZ = newsubsec.sector.ceilingHeight;

        world.validCount++;
        numSpecHit = 0;

        // stomp on any things contacted
        Blockmap bm = world.level.blockmap;
        int xl = (tmBBox[BBox.BOXLEFT] - bm.originX - MAXRADIUS) >> MAPBLOCKSHIFT;
        int xh = (tmBBox[BBox.BOXRIGHT] - bm.originX + MAXRADIUS) >> MAPBLOCKSHIFT;
        int yl = (tmBBox[BBox.BOXBOTTOM] - bm.originY - MAXRADIUS) >> MAPBLOCKSHIFT;
        int yh = (tmBBox[BBox.BOXTOP] - bm.originY + MAXRADIUS) >> MAPBLOCKSHIFT;

        for (int bx = xl; bx <= xh; bx++) {
            for (int by = yl; by <= yh; by++) {
                if (!blockThingsIterator(bx, by, pitStompThing)) {
                    return false;
                }
            }
        }

        // the move is ok, so link the thing into its new position
        unsetThingPosition(thing);

        thing.floorZ = tmFloorZ;
        thing.ceilingZ = tmCeilingZ;
        thing.x = x;
        thing.y = y;

        setThingPosition(thing);
        return true;
    }

    // ==================================================================================== p_map.c: position checks

    /** {@code PIT_CheckLine}: adjusts {@link #tmFloorZ} and {@link #tmCeilingZ} as lines are contacted. */
    @VanillaRef("p_map.c:PIT_CheckLine")
    private boolean pitCheckLine(Line ld) {
        if (tmBBox[BBox.BOXRIGHT] <= ld.bbox[BBox.BOXLEFT]
                || tmBBox[BBox.BOXLEFT] >= ld.bbox[BBox.BOXRIGHT]
                || tmBBox[BBox.BOXTOP] <= ld.bbox[BBox.BOXBOTTOM]
                || tmBBox[BBox.BOXBOTTOM] >= ld.bbox[BBox.BOXTOP]) {
            return true;
        }
        if (boxOnLineSide(tmBBox, ld) != -1) {
            return true;
        }

        // A line has been hit. The moving thing's destination position will cross the given line.
        if (ld.backSector == null) {
            return false; // one sided line
        }

        if ((tmThing.flags & MobjFlags.MISSILE) == 0) {
            if ((ld.flags & LineFlags.BLOCKING) != 0) {
                return false; // explicitly blocking everything
            }
            if (tmThing.player == null && (ld.flags & LineFlags.BLOCKMONSTERS) != 0) {
                return false; // block monsters only
            }
        }

        // set openrange, opentop, openbottom
        lineOpening(ld);

        // adjust floor / ceiling heights
        if (openTop < tmCeilingZ) {
            tmCeilingZ = openTop;
            ceilingLine = ld;
        }
        if (openBottom > tmFloorZ) {
            tmFloorZ = openBottom;
        }
        if (lowFloor < tmDropoffZ) {
            tmDropoffZ = lowFloor;
        }

        // if contacted a special line, add it to the list
        if (ld.special != 0) {
            if (numSpecHit == specHit.length) {
                specHit = Arrays.copyOf(specHit, specHit.length * 2);
            }
            specHit[numSpecHit] = ld;
            numSpecHit++;

            // spechits overrun emulation code from prboom-plus
            if (numSpecHit > MAXSPECIALCROSS_ORIGINAL) {
                specHitOverrun(ld);
            }
        }
        return true;
    }

    /** {@code PIT_CheckThing}. */
    @VanillaRef("p_map.c:PIT_CheckThing")
    private boolean pitCheckThing(Actor thing) {
        if ((thing.flags & (MobjFlags.SOLID | MobjFlags.SPECIAL | MobjFlags.SHOOTABLE)) == 0) {
            return true;
        }
        int blockdist = thing.radius + tmThing.radius;
        if (Math.abs(thing.x - tmX) >= blockdist || Math.abs(thing.y - tmY) >= blockdist) {
            return true; // didn't hit it
        }
        // don't clip against self
        if (thing == tmThing) {
            return true;
        }

        // check for skulls slamming into things
        if ((tmThing.flags & MobjFlags.SKULLFLY) != 0) {
            int damage = ((world.rng.game() % 8) + 1) * tmThing.info.damage;
            world.combat.damageMobj(thing, tmThing, tmThing, damage);
            tmThing.flags &= ~MobjFlags.SKULLFLY;
            tmThing.momX = tmThing.momY = tmThing.momZ = 0;
            world.mobjs.setMobjState(tmThing, tmThing.info.spawnState);
            return false; // stop moving
        }

        // missiles can hit other things
        if ((tmThing.flags & MobjFlags.MISSILE) != 0) {
            // see if it went over / under
            if (tmThing.z > thing.z + thing.height) {
                return true; // overhead
            }
            if (tmThing.z + tmThing.height < thing.z) {
                return true; // underneath
            }

            if (tmThing.target != null
                    && (tmThing.target.type == thing.type
                    || (tmThing.target.type == MobjType.KNIGHT && thing.type == MobjType.BRUISER)
                    || (tmThing.target.type == MobjType.BRUISER && thing.type == MobjType.KNIGHT))) {
                // Don't hit same species as originator.
                if (thing == tmThing.target) {
                    return true;
                }
                // sdh: deh_species_infighting
                if (thing.type != MobjType.PLAYER && !world.defs.misc().speciesInfighting()) {
                    // Explode, but do no damage. Let players missile other players.
                    return false;
                }
            }

            if ((thing.flags & MobjFlags.SHOOTABLE) == 0) {
                // didn't do any damage
                return (thing.flags & MobjFlags.SOLID) == 0;
            }

            // damage / explode
            int damage = ((world.rng.game() % 8) + 1) * tmThing.info.damage;
            world.combat.damageMobj(thing, tmThing, tmThing.target, damage);

            // don't traverse any more
            return false;
        }

        // check for special pickup
        if ((thing.flags & MobjFlags.SPECIAL) != 0) {
            boolean solid = (thing.flags & MobjFlags.SOLID) != 0;
            if ((tmFlags & MobjFlags.PICKUP) != 0) {
                // can remove thing
                world.pickups.touchSpecialThing(thing, tmThing);
            }
            return !solid;
        }

        return (thing.flags & MobjFlags.SOLID) == 0;
    }

    /**
     * {@code P_CheckPosition}: whether {@code thing} fits at (x, y). Sets {@link #tmFloorZ}, {@link #tmCeilingZ},
     * {@link #tmDropoffZ}, {@link #ceilingLine} and the special lines touched; touches special things if
     * {@code MF_PICKUP}.
     */
    @VanillaRef("p_map.c:P_CheckPosition")
    public boolean checkPosition(Actor thing, int x, int y) {
        tmThing = thing;
        tmFlags = thing.flags;

        tmX = x;
        tmY = y;

        tmBBox[BBox.BOXTOP] = y + tmThing.radius;
        tmBBox[BBox.BOXBOTTOM] = y - tmThing.radius;
        tmBBox[BBox.BOXRIGHT] = x + tmThing.radius;
        tmBBox[BBox.BOXLEFT] = x - tmThing.radius;

        Subsector newsubsec = pointInSubsector(x, y);
        ceilingLine = null;

        // The base floor / ceiling is from the subsector that contains the point.
        tmFloorZ = tmDropoffZ = newsubsec.sector.floorHeight;
        tmCeilingZ = newsubsec.sector.ceilingHeight;

        world.validCount++;
        numSpecHit = 0;

        if ((tmFlags & MobjFlags.NOCLIP) != 0) {
            return true;
        }

        // Check things first, possibly picking things up. The bounding box is extended by MAXRADIUS because things
        // are grouped into mapblocks based on their origin point.
        Blockmap bm = world.level.blockmap;
        int xl = (tmBBox[BBox.BOXLEFT] - bm.originX - MAXRADIUS) >> MAPBLOCKSHIFT;
        int xh = (tmBBox[BBox.BOXRIGHT] - bm.originX + MAXRADIUS) >> MAPBLOCKSHIFT;
        int yl = (tmBBox[BBox.BOXBOTTOM] - bm.originY - MAXRADIUS) >> MAPBLOCKSHIFT;
        int yh = (tmBBox[BBox.BOXTOP] - bm.originY + MAXRADIUS) >> MAPBLOCKSHIFT;

        for (int bx = xl; bx <= xh; bx++) {
            for (int by = yl; by <= yh; by++) {
                if (!blockThingsIterator(bx, by, pitCheckThing)) {
                    return false;
                }
            }
        }

        // check lines
        bm = world.level.blockmap;
        xl = (tmBBox[BBox.BOXLEFT] - bm.originX) >> MAPBLOCKSHIFT;
        xh = (tmBBox[BBox.BOXRIGHT] - bm.originX) >> MAPBLOCKSHIFT;
        yl = (tmBBox[BBox.BOXBOTTOM] - bm.originY) >> MAPBLOCKSHIFT;
        yh = (tmBBox[BBox.BOXTOP] - bm.originY) >> MAPBLOCKSHIFT;

        for (int bx = xl; bx <= xh; bx++) {
            for (int by = yl; by <= yh; by++) {
                if (!blockLinesIterator(bx, by, pitCheckLine)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@code P_TryMove}: attempts to move, crossing special lines unless {@code MF_TELEPORT} is set. */
    @VanillaRef("p_map.c:P_TryMove")
    public boolean tryMove(Actor thing, int x, int y) {
        floatOk = false;
        if (!checkPosition(thing, x, y)) {
            return false; // solid wall or thing
        }

        if ((thing.flags & MobjFlags.NOCLIP) == 0) {
            if (tmCeilingZ - tmFloorZ < thing.height) {
                return false; // doesn't fit
            }
            floatOk = true;
            if ((thing.flags & MobjFlags.TELEPORT) == 0 && tmCeilingZ - thing.z < thing.height) {
                return false; // mobj must lower itself to fit
            }
            if ((thing.flags & MobjFlags.TELEPORT) == 0 && tmFloorZ - thing.z > 24 * Fixed.FRACUNIT) {
                return false; // too big a step up
            }
            if ((thing.flags & (MobjFlags.DROPOFF | MobjFlags.FLOAT)) == 0
                    && tmFloorZ - tmDropoffZ > 24 * Fixed.FRACUNIT) {
                return false; // don't stand over a dropoff
            }
        }

        // the move is ok, so link the thing into its new position
        unsetThingPosition(thing);

        int oldx = thing.x;
        int oldy = thing.y;
        thing.floorZ = tmFloorZ;
        thing.ceilingZ = tmCeilingZ;
        thing.x = x;
        thing.y = y;

        setThingPosition(thing);

        // if any special lines were hit, do the effect
        if ((thing.flags & (MobjFlags.TELEPORT | MobjFlags.NOCLIP)) == 0) {
            while (numSpecHit-- > 0) {
                // see if the line was crossed
                Line ld = specHit[numSpecHit];
                int side = pointOnLineSide(thing.x, thing.y, ld);
                int oldside = pointOnLineSide(oldx, oldy, ld);
                if (side != oldside) {
                    if (ld.special != 0) {
                        world.specials.crossSpecialLine(ld, oldside, thing);
                    }
                }
            }
        }
        return true;
    }

    /**
     * {@code P_ThingHeightClip}: adjusts floorz, ceilingz and possibly z after a sector height change.
     *
     * @return false if the thing doesn't fit
     */
    @VanillaRef("p_map.c:P_ThingHeightClip")
    public boolean thingHeightClip(Actor thing) {
        boolean onfloor = thing.z == thing.floorZ;

        checkPosition(thing, thing.x, thing.y);
        // what about stranding a monster partially off an edge?

        thing.floorZ = tmFloorZ;
        thing.ceilingZ = tmCeilingZ;

        if (onfloor) {
            // walking monsters rise and fall with the floor
            thing.z = thing.floorZ;
        } else {
            // don't adjust a floating monster unless forced to
            if (thing.z + thing.height > thing.ceilingZ) {
                thing.z = thing.ceilingZ - thing.height;
            }
        }
        return thing.ceilingZ - thing.floorZ >= thing.height;
    }

    // ==================================================================================== p_map.c: slide move

    /** {@code P_HitSlideLine}: adjusts the move so that the next move will slide along the wall. */
    @VanillaRef("p_map.c:P_HitSlideLine")
    private void hitSlideLine(Line ld) {
        if (ld.slopeType == Line.ST_HORIZONTAL) {
            tmYMove = 0;
            return;
        }
        if (ld.slopeType == Line.ST_VERTICAL) {
            tmXMove = 0;
            return;
        }

        int side = pointOnLineSide(slideMo.x, slideMo.y, ld);

        int lineangle = Trig.pointToAngle2(0, 0, ld.dx, ld.dy);
        if (side == 1) {
            lineangle += Angle.ANG180;
        }

        int moveangle = Trig.pointToAngle2(0, 0, tmXMove, tmYMove);
        int deltaangle = moveangle - lineangle;

        if (Angle.gt(deltaangle, Angle.ANG180)) {
            deltaangle += Angle.ANG180;
        }

        lineangle >>>= Angle.ANGLETOFINESHIFT;
        deltaangle >>>= Angle.ANGLETOFINESHIFT;

        int movelen = Trig.approxDistance(tmXMove, tmYMove);
        int newlen = Fixed.mul(movelen, Tables.fineCosine(deltaangle));

        tmXMove = Fixed.mul(newlen, Tables.fineCosine(lineangle));
        tmYMove = Fixed.mul(newlen, Tables.fineSine(lineangle));
    }

    /** {@code PTR_SlideTraverse}. */
    @VanillaRef("p_map.c:PTR_SlideTraverse")
    private boolean ptrSlideTraverse(Intercept in) {
        if (!in.isALine) {
            throw new DoomFatalException("PTR_SlideTraverse: not a line?");
        }
        Line li = in.line;
        boolean blocking;
        if ((li.flags & LineFlags.TWOSIDED) == 0) {
            if (pointOnLineSide(slideMo.x, slideMo.y, li) != 0) {
                return true; // don't hit the back side
            }
            blocking = true;
        } else {
            // set openrange, opentop, openbottom
            lineOpening(li);
            if (openRange < slideMo.height) {
                blocking = true; // doesn't fit
            } else if (openTop - slideMo.z < slideMo.height) {
                blocking = true; // mobj is too high
            } else {
                blocking = openBottom - slideMo.z > 24 * Fixed.FRACUNIT; // too big a step up
            }
        }
        if (!blocking) {
            return true; // this line doesn't block movement
        }

        // the line does block movement, see if it is closer than best so far
        if (in.frac < bestSlideFrac) {
            secondSlideFrac = bestSlideFrac;
            secondSlideLine = bestSlideLine;
            bestSlideFrac = in.frac;
            bestSlideLine = li;
        }
        return false; // stop
    }

    /**
     * {@code P_SlideMove}: the momx / momy move is bad, so try to slide along a wall. Finds the first line hit, moves
     * flush to it and slides along it.
     */
    @VanillaRef("p_map.c:P_SlideMove")
    public void slideMove(Actor mo) {
        slideMo = mo;
        int hitcount = 0;

        while (true) { // retry:
            if (++hitcount == 3) {
                stairStep(mo); // don't loop forever
                return;
            }

            int leadx;
            int leady;
            int trailx;
            int traily;
            // trace along the three leading corners
            if (mo.momX > 0) {
                leadx = mo.x + mo.radius;
                trailx = mo.x - mo.radius;
            } else {
                leadx = mo.x - mo.radius;
                trailx = mo.x + mo.radius;
            }
            if (mo.momY > 0) {
                leady = mo.y + mo.radius;
                traily = mo.y - mo.radius;
            } else {
                leady = mo.y - mo.radius;
                traily = mo.y + mo.radius;
            }

            bestSlideFrac = Fixed.FRACUNIT + 1;

            pathTraverse(leadx, leady, leadx + mo.momX, leady + mo.momY, PT_ADDLINES, ptrSlideTraverse);
            pathTraverse(trailx, leady, trailx + mo.momX, leady + mo.momY, PT_ADDLINES, ptrSlideTraverse);
            pathTraverse(leadx, traily, leadx + mo.momX, traily + mo.momY, PT_ADDLINES, ptrSlideTraverse);

            // move up to the wall
            if (bestSlideFrac == Fixed.FRACUNIT + 1) {
                // the move most have hit the middle, so stairstep
                stairStep(mo);
                return;
            }

            // fudge a bit to make sure it doesn't hit
            bestSlideFrac -= 0x800;
            if (bestSlideFrac > 0) {
                int newx = Fixed.mul(mo.momX, bestSlideFrac);
                int newy = Fixed.mul(mo.momY, bestSlideFrac);
                if (!tryMove(mo, mo.x + newx, mo.y + newy)) {
                    stairStep(mo);
                    return;
                }
            }

            // Now continue along the wall. First calculate remainder.
            bestSlideFrac = Fixed.FRACUNIT - (bestSlideFrac + 0x800);
            if (bestSlideFrac > Fixed.FRACUNIT) {
                bestSlideFrac = Fixed.FRACUNIT;
            }
            if (bestSlideFrac <= 0) {
                return;
            }

            tmXMove = Fixed.mul(mo.momX, bestSlideFrac);
            tmYMove = Fixed.mul(mo.momY, bestSlideFrac);

            hitSlideLine(bestSlideLine); // clip the moves

            mo.momX = tmXMove;
            mo.momY = tmYMove;

            if (tryMove(mo, mo.x + tmXMove, mo.y + tmYMove)) {
                return;
            }
            // goto retry
        }
    }

    /** The {@code stairstep:} label of {@code P_SlideMove}. */
    private void stairStep(Actor mo) {
        if (!tryMove(mo, mo.x, mo.y + mo.momY)) {
            tryMove(mo, mo.x + mo.momX, mo.y);
        }
    }

    // ==================================================================================== p_map.c: attacks

    /** {@code PTR_AimTraverse}: sets {@link #lineTarget} and {@link #aimSlope} when a target is aimed at. */
    @VanillaRef("p_map.c:PTR_AimTraverse")
    private boolean ptrAimTraverse(Intercept in) {
        if (in.isALine) {
            Line li = in.line;
            if ((li.flags & LineFlags.TWOSIDED) == 0) {
                return false; // stop
            }

            // Crosses a two sided line. A two sided line will restrict the possible target ranges.
            lineOpening(li);
            if (openBottom >= openTop) {
                return false; // stop
            }

            int dist = Fixed.mul(attackRange, in.frac);

            if (li.backSector == null || li.frontSector.floorHeight != li.backSector.floorHeight) {
                int slope = Fixed.div(openBottom - shootZ, dist);
                if (slope > bottomSlope) {
                    bottomSlope = slope;
                }
            }
            if (li.backSector == null || li.frontSector.ceilingHeight != li.backSector.ceilingHeight) {
                int slope = Fixed.div(openTop - shootZ, dist);
                if (slope < topSlope) {
                    topSlope = slope;
                }
            }
            if (topSlope <= bottomSlope) {
                return false; // stop
            }
            return true; // shot continues
        }

        // shoot a thing
        Actor th = in.thing;
        if (th == shootThing) {
            return true; // can't shoot self
        }
        if ((th.flags & MobjFlags.SHOOTABLE) == 0) {
            return true; // corpse or something
        }

        // check angles to see if the thing can be aimed at
        int dist = Fixed.mul(attackRange, in.frac);
        int thingtopslope = Fixed.div(th.z + th.height - shootZ, dist);
        if (thingtopslope < bottomSlope) {
            return true; // shot over the thing
        }
        int thingbottomslope = Fixed.div(th.z - shootZ, dist);
        if (thingbottomslope > topSlope) {
            return true; // shot under the thing
        }

        // this thing can be hit!
        if (thingtopslope > topSlope) {
            thingtopslope = topSlope;
        }
        if (thingbottomslope < bottomSlope) {
            thingbottomslope = bottomSlope;
        }

        aimSlope = (thingtopslope + thingbottomslope) / 2;
        lineTarget = th;
        return false; // don't go any farther
    }

    /** {@code PTR_ShootTraverse}. */
    @VanillaRef("p_map.c:PTR_ShootTraverse")
    private boolean ptrShootTraverse(Intercept in) {
        if (in.isALine) {
            Line li = in.line;
            if (li.special != 0) {
                world.specials.shootSpecialLine(shootThing, li);
            }

            boolean hit;
            if ((li.flags & LineFlags.TWOSIDED) == 0) {
                hit = true;
            } else {
                // crosses a two sided line
                lineOpening(li);
                int dist = Fixed.mul(attackRange, in.frac);
                hit = false;
                // e6y: emulation of missed back side on two-sided lines.
                if (li.backSector == null) {
                    int slope = Fixed.div(openBottom - shootZ, dist);
                    if (slope > aimSlope) {
                        hit = true;
                    } else {
                        slope = Fixed.div(openTop - shootZ, dist);
                        if (slope < aimSlope) {
                            hit = true;
                        }
                    }
                } else {
                    if (li.frontSector.floorHeight != li.backSector.floorHeight) {
                        int slope = Fixed.div(openBottom - shootZ, dist);
                        if (slope > aimSlope) {
                            hit = true;
                        }
                    }
                    if (!hit && li.frontSector.ceilingHeight != li.backSector.ceilingHeight) {
                        int slope = Fixed.div(openTop - shootZ, dist);
                        if (slope < aimSlope) {
                            hit = true;
                        }
                    }
                }
                if (!hit) {
                    return true; // shot continues
                }
            }

            // hit line: position a bit closer
            int frac = in.frac - Fixed.div(4 * Fixed.FRACUNIT, attackRange);
            int x = trace.x + Fixed.mul(trace.dx, frac);
            int y = trace.y + Fixed.mul(trace.dy, frac);
            int z = shootZ + Fixed.mul(aimSlope, Fixed.mul(frac, attackRange));

            if (li.frontSector.ceilingPic == world.skyFlatNum) {
                // don't shoot the sky!
                if (z > li.frontSector.ceilingHeight) {
                    return false;
                }
                // it's a sky hack wall
                if (li.backSector != null && li.backSector.ceilingPic == world.skyFlatNum) {
                    return false;
                }
            }

            // Spawn bullet puffs.
            world.combat.spawnPuff(x, y, z);

            // don't go any farther
            return false;
        }

        // shoot a thing
        Actor th = in.thing;
        if (th == shootThing) {
            return true; // can't shoot self
        }
        if ((th.flags & MobjFlags.SHOOTABLE) == 0) {
            return true; // corpse or something
        }

        // check angles to see if the thing can be aimed at
        int dist = Fixed.mul(attackRange, in.frac);
        int thingtopslope = Fixed.div(th.z + th.height - shootZ, dist);
        if (thingtopslope < aimSlope) {
            return true; // shot over the thing
        }
        int thingbottomslope = Fixed.div(th.z - shootZ, dist);
        if (thingbottomslope > aimSlope) {
            return true; // shot under the thing
        }

        // hit thing: position a bit closer
        int frac = in.frac - Fixed.div(10 * Fixed.FRACUNIT, attackRange);
        int x = trace.x + Fixed.mul(trace.dx, frac);
        int y = trace.y + Fixed.mul(trace.dy, frac);
        int z = shootZ + Fixed.mul(aimSlope, Fixed.mul(frac, attackRange));

        // Spawn bullet puffs or blood spots, depending on target type.
        if ((in.thing.flags & MobjFlags.NOBLOOD) != 0) {
            world.combat.spawnPuff(x, y, z);
        } else {
            world.combat.spawnBlood(x, y, z, laDamage);
        }

        if (laDamage != 0) {
            world.combat.damageMobj(th, shootThing, shootThing, laDamage);
        }

        // don't go any farther
        return false;
    }

    /** {@code P_AimLineAttack}: returns the slope to the target, sets {@link #lineTarget}. */
    @VanillaRef("p_map.c:P_AimLineAttack")
    public int aimLineAttack(Actor t1, int angle, int distance) {
        t1 = world.mobjs.substNullMobj(t1);

        angle >>>= Angle.ANGLETOFINESHIFT;
        shootThing = t1;

        int x2 = t1.x + (distance >> Fixed.FRACBITS) * Tables.fineCosine(angle);
        int y2 = t1.y + (distance >> Fixed.FRACBITS) * Tables.fineSine(angle);
        shootZ = t1.z + (t1.height >> 1) + 8 * Fixed.FRACUNIT;

        // can't shoot outside view angles
        topSlope = (200 / 2) * Fixed.FRACUNIT / (320 / 2);
        bottomSlope = -(200 / 2) * Fixed.FRACUNIT / (320 / 2);

        attackRange = distance;
        lineTarget = null;

        pathTraverse(t1.x, t1.y, x2, y2, PT_ADDLINES | PT_ADDTHINGS, ptrAimTraverse);

        if (lineTarget != null) {
            return aimSlope;
        }
        return 0;
    }

    /** {@code P_LineAttack}: if damage is 0, it is just a test trace that leaves {@link #lineTarget} set. */
    @VanillaRef("p_map.c:P_LineAttack")
    public void lineAttack(Actor t1, int angle, int distance, int slope, int damage) {
        angle >>>= Angle.ANGLETOFINESHIFT;
        shootThing = t1;
        laDamage = damage;
        int x2 = t1.x + (distance >> Fixed.FRACBITS) * Tables.fineCosine(angle);
        int y2 = t1.y + (distance >> Fixed.FRACBITS) * Tables.fineSine(angle);
        shootZ = t1.z + (t1.height >> 1) + 8 * Fixed.FRACUNIT;
        attackRange = distance;
        aimSlope = slope;

        pathTraverse(t1.x, t1.y, x2, y2, PT_ADDLINES | PT_ADDTHINGS, ptrShootTraverse);
    }

    // ==================================================================================== p_map.c: use lines

    /** {@code PTR_UseTraverse}. */
    @VanillaRef("p_map.c:PTR_UseTraverse")
    private boolean ptrUseTraverse(Intercept in) {
        if (in.line.special == 0) {
            lineOpening(in.line);
            if (openRange <= 0) {
                world.startSound(useThing, SoundId.NOWAY);
                // can't use through a wall
                return false;
            }
            // not a special line, but keep checking
            return true;
        }

        int side = 0;
        if (pointOnLineSide(useThing.x, useThing.y, in.line) == 1) {
            side = 1;
        }
        world.specials.useSpecialLine(useThing, in.line, side);

        // can't use for than one special line in a row
        return false;
    }

    /** {@code P_UseLines}: looks for special lines in front of the player to activate. */
    @VanillaRef("p_map.c:P_UseLines")
    public void useLines(Player player) {
        useThing = player.mo;

        int angle = player.mo.angle >>> Angle.ANGLETOFINESHIFT;

        int x1 = player.mo.x;
        int y1 = player.mo.y;
        int x2 = x1 + (USERANGE >> Fixed.FRACBITS) * Tables.fineCosine(angle);
        int y2 = y1 + (USERANGE >> Fixed.FRACBITS) * Tables.fineSine(angle);

        pathTraverse(x1, y1, x2, y2, PT_ADDLINES, ptrUseTraverse);
    }

    // ==================================================================================== p_map.c: radius attack

    /** {@code PIT_RadiusAttack}: "bombsource" is the creature that caused the explosion at "bombspot". */
    @VanillaRef("p_map.c:PIT_RadiusAttack")
    private boolean pitRadiusAttack(Actor thing) {
        if ((thing.flags & MobjFlags.SHOOTABLE) == 0) {
            return true;
        }
        // Boss spider and cyborg take no damage from concussion.
        if (thing.type == MobjType.CYBORG || thing.type == MobjType.SPIDER) {
            return true;
        }

        int dx = Math.abs(thing.x - bombSpot.x);
        int dy = Math.abs(thing.y - bombSpot.y);

        int dist = dx > dy ? dx : dy;
        dist = (dist - thing.radius) >> Fixed.FRACBITS;

        if (dist < 0) {
            dist = 0;
        }
        if (dist >= bombDamage) {
            return true; // out of range
        }

        if (world.sight.checkSight(thing, bombSpot)) {
            // must be in direct path
            world.combat.damageMobj(thing, bombSpot, bombSource, bombDamage - dist);
        }
        return true;
    }

    /** {@code P_RadiusAttack}: source is the creature that caused the explosion at spot. */
    @VanillaRef("p_map.c:P_RadiusAttack")
    public void radiusAttack(Actor spot, Actor source, int damage) {
        Blockmap bm = world.level.blockmap;
        // VANILLA-BUG: MAXRADIUS is an unparenthesised macro; (damage + MAXRADIUS) << FRACBITS overflows.
        int dist = (damage + MAXRADIUS) << Fixed.FRACBITS;
        int yh = (spot.y + dist - bm.originY) >> MAPBLOCKSHIFT;
        int yl = (spot.y - dist - bm.originY) >> MAPBLOCKSHIFT;
        int xh = (spot.x + dist - bm.originX) >> MAPBLOCKSHIFT;
        int xl = (spot.x - dist - bm.originX) >> MAPBLOCKSHIFT;
        bombSpot = spot;
        bombSource = source;
        bombDamage = damage;

        for (int y = yl; y <= yh; y++) {
            for (int x = xl; x <= xh; x++) {
                blockThingsIterator(x, y, pitRadiusAttack);
            }
        }
    }

    // ==================================================================================== p_map.c: sector change

    /** {@code PIT_ChangeSector}. */
    @VanillaRef("p_map.c:PIT_ChangeSector")
    private boolean pitChangeSector(Actor thing) {
        if (thingHeightClip(thing)) {
            return true; // keep checking
        }

        // crunch bodies to giblets
        if (thing.health <= 0) {
            world.mobjs.setMobjState(thing, StateId.GIBS);
            if (world.version.isAtLeast(GameVersion.V1_9)) { // gameversion > exe_doom_1_2
                thing.flags &= ~MobjFlags.SOLID;
            }
            thing.height = 0;
            thing.radius = 0;
            return true; // keep checking
        }

        // crunch dropped items
        if ((thing.flags & MobjFlags.DROPPED) != 0) {
            world.mobjs.removeMobj(thing);
            return true; // keep checking
        }

        if ((thing.flags & MobjFlags.SHOOTABLE) == 0) {
            return true; // assume it is bloody gibs or something
        }

        noFit = 1;

        if (crushChange != 0 && (world.levelTime & 3) == 0) {
            world.combat.damageMobj(thing, null, null, 10);

            // spray blood in a random direction
            Actor mo = world.mobjs.spawnMobj(thing.x, thing.y, thing.z + thing.height / 2, MobjType.BLOOD);
            mo.momX = world.rng.subRandom() << 12;
            mo.momY = world.rng.subRandom() << 12;
        }

        // keep checking (crush other things)
        return true;
    }

    /**
     * {@code P_ChangeSector}: after a sector's floor or ceiling height changed, adjusts every thing that touches it.
     *
     * @return true if anything doesn't fit any more
     */
    @VanillaRef("p_map.c:P_ChangeSector")
    public boolean changeSector(Sector sector, boolean crunch) {
        noFit = 0;
        crushChange = crunch ? 1 : 0;

        // re-check heights for all things near the moving sector
        for (int x = sector.blockBox[BBox.BOXLEFT]; x <= sector.blockBox[BBox.BOXRIGHT]; x++) {
            for (int y = sector.blockBox[BBox.BOXBOTTOM]; y <= sector.blockBox[BBox.BOXTOP]; y++) {
                blockThingsIterator(x, y, pitChangeSector);
            }
        }
        return noFit != 0;
    }

    /**
     * {@code SpechitOverrun} (Chocolate Doom, after PrBoom-plus): vanilla DOOM's {@code spechit[8]} overflows into
     * {@code tmbbox}, {@code crushchange} and {@code nofit}; the value written is the DOS address of the line.
     */
    @VanillaRef("p_map.c:SpechitOverrun")
    private void specHitOverrun(Line ld) {
        int addr = DEFAULT_SPECHIT_MAGIC + ld.index * 0x3E;
        switch (numSpecHit) {
            case 9, 10, 11, 12 -> tmBBox[numSpecHit - 9] = addr;
            case 13 -> crushChange = addr;
            case 14 -> noFit = addr;
            default -> {
                // Chocolate Doom prints a warning: unable to emulate an overrun where numspechit > 14.
            }
        }
    }
}
