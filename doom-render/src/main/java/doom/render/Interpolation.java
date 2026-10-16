package doom.render;

import doom.game.view.ActorView;
import doom.game.view.PlayerState;
import doom.game.view.PlayerView;
import doom.game.view.WorldView;

/**
 * Presentation-only interpolation between the previous tic and the current one (spec 04 section 7, ADR-011).
 *
 * <p>Three decorators over the simulation views return interpolated positions and heights and delegate everything
 * else: actors ({@code x, y, z, angle}), sectors (floor and ceiling heights), sides (horizontal texture offset of
 * scrolling walls) and the player sprites ({@code sx, sy}). They read the {@code prev*} values the simulation records
 * at the start of every tic and never write anything, so the simulation is untouched.
 *
 * <p>Angles interpolate through the signed {@code int} difference, which always takes the shortest path. Values that
 * moved further in one tic than any real motion can (a teleport, a level start or a loaded game, where the previous
 * values are not meaningful) are not interpolated: the current value is returned.
 *
 * <p>Allocation free: build the decorators once, then call {@code set} every frame. With {@code alpha} 1 they return the
 * current values.
 */
public final class Interpolation {

    /** One in 16.16: the current state. */
    public static final int ONE = 65536;

    /** Sector heights move at most 8 units per tic (fast doors and platforms); anything beyond this is a jump. */
    public static final int MAX_SECTOR_STEP = 32 << 16;
    /** Scrolling walls move one unit per tic. */
    static final int MAX_SIDE_STEP = 16 << 16;
    /** Weapon sprites move a few units per tic. */
    static final int MAX_PSPRITE_STEP = 48 << 16;

    private Interpolation() {
    }

    /** {@code alpha} in [0, 1] as a 16.16 fixed-point fraction. */
    public static int fixedAlpha(double alpha) {
        if (!(alpha > 0.0)) {
            return 0;
        }
        return alpha >= 1.0 ? ONE : (int) (alpha * ONE);
    }

    /** {@code prev + (cur - prev) * alpha}, in 64-bit so that large differences cannot overflow. */
    public static int lerp(int prev, int cur, int alpha) {
        return prev + (int) (((long) (cur - prev) * alpha) >> 16);
    }

    /** Like {@link #lerp} but returns {@code cur} when the step is larger than {@code maxStep} (a jump). */
    public static int lerpLimited(int prev, int cur, int alpha, int maxStep) {
        long d = (long) cur - prev;
        if (d > maxStep || d < -maxStep) {
            return cur;
        }
        return prev + (int) ((d * alpha) >> 16);
    }

    /** Shortest-path interpolation of BAM angles. */
    public static int lerpAngle(int prev, int cur, int alpha) {
        return prev + (int) (((long) (cur - prev) * alpha) >> 16);
    }

    /** Interpolated {@link WorldView}: sector floor and ceiling heights and scrolling wall offsets. */
    public static final class World implements WorldView {
        private WorldView d;
        private int alpha = ONE;

        public World set(WorldView delegate, int fixedAlpha) {
            this.d = delegate;
            this.alpha = fixedAlpha;
            return this;
        }

        @Override
        public int sectorFloorHeight(int s) {
            int cur = d.sectorFloorHeight(s);
            return alpha >= ONE ? cur : lerpLimited(d.sectorPrevFloorHeight(s), cur, alpha, MAX_SECTOR_STEP);
        }

        @Override
        public int sectorCeilingHeight(int s) {
            int cur = d.sectorCeilingHeight(s);
            return alpha >= ONE ? cur : lerpLimited(d.sectorPrevCeilingHeight(s), cur, alpha, MAX_SECTOR_STEP);
        }

        @Override
        public int sideTextureOffset(int sd) {
            int cur = d.sideTextureOffset(sd);
            return alpha >= ONE ? cur : lerpLimited(d.sidePrevTextureOffset(sd), cur, alpha, MAX_SIDE_STEP);
        }

        @Override
        public String mapName() {
            return d.mapName();
        }

        @Override
        public int vertexCount() {
            return d.vertexCount();
        }

        @Override
        public int vertexX(int v) {
            return d.vertexX(v);
        }

        @Override
        public int vertexY(int v) {
            return d.vertexY(v);
        }

        @Override
        public int sectorCount() {
            return d.sectorCount();
        }

        @Override
        public int sectorPrevFloorHeight(int s) {
            return d.sectorPrevFloorHeight(s);
        }

        @Override
        public int sectorPrevCeilingHeight(int s) {
            return d.sectorPrevCeilingHeight(s);
        }

        @Override
        public int sectorFloorPic(int s) {
            return d.sectorFloorPic(s);
        }

        @Override
        public int sectorCeilingPic(int s) {
            return d.sectorCeilingPic(s);
        }

        @Override
        public int sectorLightLevel(int s) {
            return d.sectorLightLevel(s);
        }

        @Override
        public int sectorSpecial(int s) {
            return d.sectorSpecial(s);
        }

        @Override
        public int sectorTag(int s) {
            return d.sectorTag(s);
        }

        @Override
        public int sectorSoundOriginX(int s) {
            return d.sectorSoundOriginX(s);
        }

        @Override
        public int sectorSoundOriginY(int s) {
            return d.sectorSoundOriginY(s);
        }

        @Override
        public int sectorFirstActor(int s) {
            return d.sectorFirstActor(s);
        }

        @Override
        public int sectorLineCount(int s) {
            return d.sectorLineCount(s);
        }

        @Override
        public int sectorLine(int s, int i) {
            return d.sectorLine(s, i);
        }

        @Override
        public int sideCount() {
            return d.sideCount();
        }

        @Override
        public int sidePrevTextureOffset(int sd) {
            return d.sidePrevTextureOffset(sd);
        }

        @Override
        public int sideRowOffset(int sd) {
            return d.sideRowOffset(sd);
        }

        @Override
        public int sideTopTexture(int sd) {
            return d.sideTopTexture(sd);
        }

        @Override
        public int sideBottomTexture(int sd) {
            return d.sideBottomTexture(sd);
        }

        @Override
        public int sideMidTexture(int sd) {
            return d.sideMidTexture(sd);
        }

        @Override
        public int sideSector(int sd) {
            return d.sideSector(sd);
        }

        @Override
        public int lineCount() {
            return d.lineCount();
        }

        @Override
        public int lineV1(int l) {
            return d.lineV1(l);
        }

        @Override
        public int lineV2(int l) {
            return d.lineV2(l);
        }

        @Override
        public int lineFlags(int l) {
            return d.lineFlags(l);
        }

        @Override
        public int lineSpecial(int l) {
            return d.lineSpecial(l);
        }

        @Override
        public int lineTag(int l) {
            return d.lineTag(l);
        }

        @Override
        public int lineFrontSide(int l) {
            return d.lineFrontSide(l);
        }

        @Override
        public int lineBackSide(int l) {
            return d.lineBackSide(l);
        }

        @Override
        public int lineFrontSector(int l) {
            return d.lineFrontSector(l);
        }

        @Override
        public int lineBackSector(int l) {
            return d.lineBackSector(l);
        }

        @Override
        public int segCount() {
            return d.segCount();
        }

        @Override
        public int segV1(int g) {
            return d.segV1(g);
        }

        @Override
        public int segV2(int g) {
            return d.segV2(g);
        }

        @Override
        public int segAngle(int g) {
            return d.segAngle(g);
        }

        @Override
        public int segOffset(int g) {
            return d.segOffset(g);
        }

        @Override
        public int segLine(int g) {
            return d.segLine(g);
        }

        @Override
        public int segSide(int g) {
            return d.segSide(g);
        }

        @Override
        public int segFrontSector(int g) {
            return d.segFrontSector(g);
        }

        @Override
        public int segBackSector(int g) {
            return d.segBackSector(g);
        }

        @Override
        public int subsectorCount() {
            return d.subsectorCount();
        }

        @Override
        public int subsectorFirstSeg(int ss) {
            return d.subsectorFirstSeg(ss);
        }

        @Override
        public int subsectorSegCount(int ss) {
            return d.subsectorSegCount(ss);
        }

        @Override
        public int subsectorSector(int ss) {
            return d.subsectorSector(ss);
        }

        @Override
        public int nodeCount() {
            return d.nodeCount();
        }

        @Override
        public int nodeX(int n) {
            return d.nodeX(n);
        }

        @Override
        public int nodeY(int n) {
            return d.nodeY(n);
        }

        @Override
        public int nodeDx(int n) {
            return d.nodeDx(n);
        }

        @Override
        public int nodeDy(int n) {
            return d.nodeDy(n);
        }

        @Override
        public int nodeBBox(int n, int child, int coord) {
            return d.nodeBBox(n, child, coord);
        }

        @Override
        public int nodeChild(int n, int child) {
            return d.nodeChild(n, child);
        }

        @Override
        public int blockmapOriginX() {
            return d.blockmapOriginX();
        }

        @Override
        public int blockmapOriginY() {
            return d.blockmapOriginY();
        }

        @Override
        public int blockmapColumns() {
            return d.blockmapColumns();
        }

        @Override
        public int blockmapRows() {
            return d.blockmapRows();
        }

        @Override
        public int textureTranslation(int texture) {
            return d.textureTranslation(texture);
        }

        @Override
        public int flatTranslation(int flat) {
            return d.flatTranslation(flat);
        }

        @Override
        public int skyTexture() {
            return d.skyTexture();
        }

        @Override
        public int skyFlat() {
            return d.skyFlat();
        }
    }

    /** Interpolated {@link ActorView}: position and angle of map objects. */
    public static final class Actors implements ActorView {
        private ActorView d;
        private int alpha = ONE;

        public Actors set(ActorView delegate, int fixedAlpha) {
            this.d = delegate;
            this.alpha = fixedAlpha;
            return this;
        }

        @Override
        public int x(int id) {
            int cur = d.x(id);
            return alpha >= ONE ? cur : lerp(d.prevX(id), cur, alpha);
        }

        @Override
        public int y(int id) {
            int cur = d.y(id);
            return alpha >= ONE ? cur : lerp(d.prevY(id), cur, alpha);
        }

        @Override
        public int z(int id) {
            int cur = d.z(id);
            return alpha >= ONE ? cur : lerp(d.prevZ(id), cur, alpha);
        }

        @Override
        public int angle(int id) {
            int cur = d.angle(id);
            return alpha >= ONE ? cur : lerpAngle(d.prevAngle(id), cur, alpha);
        }

        @Override
        public int capacity() {
            return d.capacity();
        }

        @Override
        public boolean exists(int id) {
            return d.exists(id);
        }

        @Override
        public int type(int id) {
            return d.type(id);
        }

        @Override
        public int prevX(int id) {
            return d.prevX(id);
        }

        @Override
        public int prevY(int id) {
            return d.prevY(id);
        }

        @Override
        public int prevZ(int id) {
            return d.prevZ(id);
        }

        @Override
        public int prevAngle(int id) {
            return d.prevAngle(id);
        }

        @Override
        public int sprite(int id) {
            return d.sprite(id);
        }

        @Override
        public int frame(int id) {
            return d.frame(id);
        }

        @Override
        public int flags(int id) {
            return d.flags(id);
        }

        @Override
        public int radius(int id) {
            return d.radius(id);
        }

        @Override
        public int height(int id) {
            return d.height(id);
        }

        @Override
        public int health(int id) {
            return d.health(id);
        }

        @Override
        public int floorZ(int id) {
            return d.floorZ(id);
        }

        @Override
        public int ceilingZ(int id) {
            return d.ceilingZ(id);
        }

        @Override
        public int player(int id) {
            return d.player(id);
        }

        @Override
        public int sector(int id) {
            return d.sector(id);
        }

        @Override
        public int nextInSector(int id) {
            return d.nextInSector(id);
        }

        @Override
        public int first() {
            return d.first();
        }

        @Override
        public int next(int id) {
            return d.next(id);
        }
    }

    /** Interpolated {@link PlayerView}: the weapon and flash sprite offsets. */
    public static final class Player implements PlayerView {
        private PlayerView d;
        private int alpha = ONE;

        public Player set(PlayerView delegate, int fixedAlpha) {
            this.d = delegate;
            this.alpha = fixedAlpha;
            return this;
        }

        @Override
        public int pspriteSx(int which) {
            int cur = d.pspriteSx(which);
            return alpha >= ONE ? cur : lerpLimited(d.pspritePrevSx(which), cur, alpha, MAX_PSPRITE_STEP);
        }

        @Override
        public int pspriteSy(int which) {
            int cur = d.pspriteSy(which);
            return alpha >= ONE ? cur : lerpLimited(d.pspritePrevSy(which), cur, alpha, MAX_PSPRITE_STEP);
        }

        @Override
        public boolean inGame() {
            return d.inGame();
        }

        @Override
        public PlayerState state() {
            return d.state();
        }

        @Override
        public int actor() {
            return d.actor();
        }

        @Override
        public int x() {
            return d.x();
        }

        @Override
        public int y() {
            return d.y();
        }

        @Override
        public int z() {
            return d.z();
        }

        @Override
        public int angle() {
            return d.angle();
        }

        @Override
        public int viewZ() {
            return d.viewZ();
        }

        @Override
        public int prevViewZ() {
            return d.prevViewZ();
        }

        @Override
        public int viewHeight() {
            return d.viewHeight();
        }

        @Override
        public int deltaViewHeight() {
            return d.deltaViewHeight();
        }

        @Override
        public int bob() {
            return d.bob();
        }

        @Override
        public int health() {
            return d.health();
        }

        @Override
        public int armorPoints() {
            return d.armorPoints();
        }

        @Override
        public int armorType() {
            return d.armorType();
        }

        @Override
        public int readyWeapon() {
            return d.readyWeapon();
        }

        @Override
        public int pendingWeapon() {
            return d.pendingWeapon();
        }

        @Override
        public boolean weaponOwned(int weapon) {
            return d.weaponOwned(weapon);
        }

        @Override
        public int ammo(int ammoType) {
            return d.ammo(ammoType);
        }

        @Override
        public int maxAmmo(int ammoType) {
            return d.maxAmmo(ammoType);
        }

        @Override
        public boolean card(int card) {
            return d.card(card);
        }

        @Override
        public boolean backpack() {
            return d.backpack();
        }

        @Override
        public int power(int power) {
            return d.power(power);
        }

        @Override
        public int cheats() {
            return d.cheats();
        }

        @Override
        public int damageCount() {
            return d.damageCount();
        }

        @Override
        public int bonusCount() {
            return d.bonusCount();
        }

        @Override
        public int extraLight() {
            return d.extraLight();
        }

        @Override
        public int fixedColormap() {
            return d.fixedColormap();
        }

        @Override
        public int colormap() {
            return d.colormap();
        }

        @Override
        public int attacker() {
            return d.attacker();
        }

        @Override
        public int killCount() {
            return d.killCount();
        }

        @Override
        public int itemCount() {
            return d.itemCount();
        }

        @Override
        public int secretCount() {
            return d.secretCount();
        }

        @Override
        public int frags(int otherPlayer) {
            return d.frags(otherPlayer);
        }

        @Override
        public boolean didSecret() {
            return d.didSecret();
        }

        @Override
        public boolean attackDown() {
            return d.attackDown();
        }

        @Override
        public boolean pspriteActive(int which) {
            return d.pspriteActive(which);
        }

        @Override
        public int pspriteSprite(int which) {
            return d.pspriteSprite(which);
        }

        @Override
        public int pspriteFrame(int which) {
            return d.pspriteFrame(which);
        }

        @Override
        public int pspritePrevSx(int which) {
            return d.pspritePrevSx(which);
        }

        @Override
        public int pspritePrevSy(int which) {
            return d.pspritePrevSy(which);
        }
    }
}
