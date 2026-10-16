package doom.game.trace;

import doom.game.actor.Actor;
import doom.game.actor.SpecialThinker;
import doom.game.actor.Thinker;
import doom.game.actor.TraceSink;
import doom.game.player.PSprite;
import doom.game.player.Player;
import doom.game.view.AmmoType;
import doom.game.view.Card;
import doom.game.view.Power;
import doom.game.view.Weapon;
import doom.game.world.Level;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.Side;
import doom.game.world.World;
import java.util.Arrays;

/**
 * Per-tic trace v1 (contracts §6): the line written after each {@code G_Ticker} call and the verbose object lines.
 * Must produce byte-identical output to the oracle patch ({@code oracle.c WriteTraceLine}).
 */
public final class TraceWriter {

    private static final long FNV_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int MOBJ_FIELDS = 26;
    private static final int PLAYER_FIELDS = 61;
    private static final int SPECIAL_MAX = 64;

    private final World world;
    private int stamp;
    private final int[] fields = new int[SPECIAL_MAX];
    private final Collector collector = new Collector();

    /** Collects the fields of a special thinker. */
    private final class Collector implements TraceSink {
        int n;

        @Override
        public void put(int value) {
            if (n < fields.length) {
                fields[n] = value;
            }
            n++;
        }

        @Override
        public int thinkerIndex(Thinker t) {
            return TraceWriter.this.thinkerIndex(t);
        }
    }

    public TraceWriter(World world) {
        this.world = world;
    }

    /** Header line (without the line feed): {@code #doom-trace v1 iwad=... demo=... version=...}. */
    public static String header(String iwadFileName, String demoName, String version) {
        return "#doom-trace v1 iwad=" + iwadFileName + " demo=" + demoName + " version=" + version;
    }

    // ------------------------------------------------------------------ thinker index (§6.3)

    private void buildThinkerIndex() {
        stamp++;
        int n = 0;
        for (Thinker t = world.thinkers.first(); t != null; t = world.thinkers.next(t)) {
            if (!t.isRemoved()) {
                t.traceIndex = n++;
                t.traceStamp = stamp;
            }
        }
    }

    /** -1 for null, -2 for a thinker that is not live, else the position among the live thinkers. */
    int thinkerIndex(Thinker t) {
        if (t == null) {
            return -1;
        }
        if (t.isRemoved() || t.traceStamp != stamp) {
            return -2;
        }
        return t.traceIndex;
    }

    // ------------------------------------------------------------------ field extraction

    private void mobjFields(Actor mo) {
        int[] f = fields;
        int n = 0;
        f[n++] = mo.type;
        f[n++] = mo.x;
        f[n++] = mo.y;
        f[n++] = mo.z;
        f[n++] = mo.angle;
        f[n++] = mo.momX;
        f[n++] = mo.momY;
        f[n++] = mo.momZ;
        f[n++] = mo.floorZ;
        f[n++] = mo.ceilingZ;
        f[n++] = mo.radius;
        f[n++] = mo.height;
        f[n++] = mo.state == null ? -1 : mo.stateNum;
        f[n++] = mo.tics;
        f[n++] = mo.sprite;
        f[n++] = mo.frame;
        f[n++] = mo.flags;
        f[n++] = mo.health;
        f[n++] = mo.moveDir;
        f[n++] = mo.moveCount;
        f[n++] = mo.reactionTime;
        f[n++] = mo.threshold;
        f[n++] = mo.lastLook;
        f[n++] = thinkerIndex(mo.target);
        f[n++] = thinkerIndex(mo.tracer);
        f[n] = mo.player == null ? -1 : mo.player.index;
    }

    /** Fills {@link #fields} with the code and fields of a special thinker; returns the count. */
    private int specialFields(Thinker th) {
        collector.n = 0;
        if (th instanceof SpecialThinker s) {
            collector.put(s.traceCode());
            s.traceFields(collector);
        } else {
            collector.put(0);
        }
        return Math.min(collector.n, fields.length);
    }

    private void playerFields(Player p) {
        int[] v = fields;
        Arrays.fill(v, 0, PLAYER_FIELDS, 0);
        Actor mo = p.mo;
        if (mo != null) {
            v[0] = mo.x;
            v[1] = mo.y;
            v[2] = mo.z;
            v[3] = mo.angle;
            v[4] = mo.momX;
            v[5] = mo.momY;
            v[6] = mo.momZ;
            v[7] = mo.health;
        }
        int n = 8;
        v[n++] = p.viewZ;
        v[n++] = p.viewHeight;
        v[n++] = p.deltaViewHeight;
        v[n++] = p.bob;
        v[n++] = p.playerState;
        v[n++] = p.health;
        v[n++] = p.armorPoints;
        v[n++] = p.armorType;
        v[n++] = p.readyWeapon;
        v[n++] = p.pendingWeapon;
        int mask = 0;
        for (int i = 0; i < Weapon.COUNT; i++) {
            if (p.weaponOwned[i]) {
                mask |= 1 << i;
            }
        }
        v[n++] = mask;
        for (int i = 0; i < AmmoType.COUNT; i++) {
            v[n++] = p.ammo[i];
        }
        for (int i = 0; i < AmmoType.COUNT; i++) {
            v[n++] = p.maxAmmo[i];
        }
        mask = 0;
        for (int i = 0; i < Card.COUNT; i++) {
            if (p.cards[i]) {
                mask |= 1 << i;
            }
        }
        v[n++] = mask;
        v[n++] = p.backpack ? 1 : 0;
        for (int i = 0; i < Power.COUNT; i++) {
            v[n++] = p.powers[i];
        }
        v[n++] = p.cheats;
        v[n++] = p.refire;
        v[n++] = p.killCount;
        v[n++] = p.itemCount;
        v[n++] = p.secretCount;
        v[n++] = p.damageCount;
        v[n++] = p.bonusCount;
        v[n++] = p.extraLight;
        v[n++] = p.fixedColormap;
        v[n++] = p.colormap;
        v[n++] = thinkerIndex(p.attacker);
        for (int i = 0; i < 2; i++) {
            PSprite ps = p.psprites[i];
            v[n++] = ps.state == null ? -1 : ps.stateNum;
            v[n++] = ps.tics;
            v[n++] = ps.sx;
            v[n++] = ps.sy;
        }
        v[n++] = p.attackDown ? 1 : 0;
        v[n++] = p.useDown ? 1 : 0;
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            v[n++] = p.frags[i];
        }
        v[n] = p.didSecret ? 1 : 0;
    }

    // ------------------------------------------------------------------ hashing

    private static long fnv(long h, int value) {
        for (int i = 0; i < 4; i++) {
            h ^= value & 0xff;
            h *= FNV_PRIME;
            value >>>= 8;
        }
        return h;
    }

    private static void hex16(StringBuilder out, long v) {
        for (int shift = 60; shift >= 0; shift -= 4) {
            int d = (int) (v >>> shift) & 0xf;
            out.append((char) (d < 10 ? '0' + d : 'a' + d - 10));
        }
    }

    /**
     * Appends the trace line of the tic just run (without the line feed).
     *
     * @param tic       the 0-based number of the {@code G_Ticker} call
     * @param gameState {@code gamestate} after the call
     */
    public void appendLine(StringBuilder out, int tic, int gameState) {
        buildThinkerIndex();
        long ha = FNV_BASIS;
        long hs = FNV_BASIS;
        long hl = FNV_BASIS;
        long hd = FNV_BASIS;
        long hx = FNV_BASIS;
        int acount = 0;
        int xcount = 0;

        for (Thinker t = world.thinkers.first(); t != null; t = world.thinkers.next(t)) {
            if (t.isRemoved()) {
                continue;
            }
            if (t instanceof Actor mo) {
                mobjFields(mo);
                for (int k = 0; k < MOBJ_FIELDS; k++) {
                    ha = fnv(ha, fields[k]);
                }
                acount++;
            } else {
                int n = specialFields(t);
                for (int k = 0; k < n; k++) {
                    hx = fnv(hx, fields[k]);
                }
                xcount++;
            }
        }

        Level level = world.level;
        if (level != null) {
            for (Sector s : level.sectors) {
                hs = fnv(hs, s.floorHeight);
                hs = fnv(hs, s.ceilingHeight);
                hs = fnv(hs, s.floorPic);
                hs = fnv(hs, s.ceilingPic);
                hs = fnv(hs, s.lightLevel);
                hs = fnv(hs, s.special);
                hs = fnv(hs, s.tag);
                hs = fnv(hs, s.soundTraversed);
                hs = fnv(hs, thinkerIndex(s.soundTarget));
                hs = fnv(hs, thinkerIndex(s.specialData));
            }
            for (Line l : level.lines) {
                hl = fnv(hl, l.flags & ~0x100);
                hl = fnv(hl, l.special);
                hl = fnv(hl, l.tag);
            }
            for (Side sd : level.sides) {
                hd = fnv(hd, sd.textureOffset);
                hd = fnv(hd, sd.rowOffset);
                hd = fnv(hd, sd.topTexture);
                hd = fnv(hd, sd.bottomTexture);
                hd = fnv(hd, sd.midTexture);
            }
        }

        out.append("t=").append(tic)
                .append(";gs=").append(gameState)
                .append(";r=").append(world.rng.gameIndex())
                .append(";lt=").append(world.levelTime)
                .append(";map=").append(world.gameEpisode).append(',').append(world.gameMap).append(',')
                .append(world.gameSkill)
                .append(";tot=").append(world.totalKills).append(',').append(world.totalItems).append(',')
                .append(world.totalSecret);
        for (int i = 0; i < World.MAXPLAYERS; i++) {
            if (world.playerInGame[i]) {
                playerFields(world.players[i]);
                out.append(";p").append(i).append('=');
                for (int k = 0; k < PLAYER_FIELDS; k++) {
                    if (k > 0) {
                        out.append(',');
                    }
                    out.append(fields[k]);
                }
            }
        }
        out.append(";a=").append(acount).append(',');
        hex16(out, ha);
        out.append(";s=");
        hex16(out, hs);
        out.append(";l=");
        hex16(out, hl);
        out.append(";d=");
        hex16(out, hd);
        out.append(";x=").append(xcount).append(',');
        hex16(out, hx);
        out.append(";m=").append(world.rng.miscIndex());
    }

    /**
     * Appends the verbose object lines of the tic just run (§6.6), each ending with a line feed. Call right after
     * {@link #appendLine} (it reuses the thinker index built there).
     */
    public void appendVerbose(StringBuilder out) {
        for (Thinker t = world.thinkers.first(); t != null; t = world.thinkers.next(t)) {
            if (t.isRemoved()) {
                continue;
            }
            int index = thinkerIndex(t);
            int n;
            if (t instanceof Actor mo) {
                mobjFields(mo);
                n = MOBJ_FIELDS;
                out.append("  a").append(index).append('=');
            } else {
                n = specialFields(t);
                out.append("  x").append(index).append('=');
            }
            for (int k = 0; k < n; k++) {
                if (k > 0) {
                    out.append(',');
                }
                out.append(fields[k]);
            }
            out.append('\n');
        }
    }
}
