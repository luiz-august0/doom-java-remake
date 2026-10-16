package doom.game.impl;

import doom.game.data.State;
import doom.game.player.PSprite;
import doom.game.player.Player;
import doom.game.view.PlayerState;
import doom.game.view.PlayerView;
import doom.game.world.World;

/** The session's {@link PlayerView} of one player slot. */
final class SimPlayerView implements PlayerView {

    private static final PlayerState[] STATES = PlayerState.values();

    private final World w;
    private final Player p;

    SimPlayerView(World world, int index) {
        this.w = world;
        this.p = world.players[index];
    }

    @Override
    public boolean inGame() {
        return w.playerInGame[p.index];
    }

    @Override
    public PlayerState state() {
        return STATES[p.playerState];
    }

    @Override
    public int actor() {
        return p.mo == null ? -1 : p.mo.id;
    }

    @Override
    public int x() {
        return p.mo == null ? 0 : p.mo.x;
    }

    @Override
    public int y() {
        return p.mo == null ? 0 : p.mo.y;
    }

    @Override
    public int z() {
        return p.mo == null ? 0 : p.mo.z;
    }

    @Override
    public int angle() {
        return p.mo == null ? 0 : p.mo.angle;
    }

    @Override
    public int viewZ() {
        return p.viewZ;
    }

    @Override
    public int prevViewZ() {
        return p.prevViewZ;
    }

    @Override
    public int viewHeight() {
        return p.viewHeight;
    }

    @Override
    public int deltaViewHeight() {
        return p.deltaViewHeight;
    }

    @Override
    public int bob() {
        return p.bob;
    }

    @Override
    public int health() {
        return p.health;
    }

    @Override
    public int armorPoints() {
        return p.armorPoints;
    }

    @Override
    public int armorType() {
        return p.armorType;
    }

    @Override
    public int readyWeapon() {
        return p.readyWeapon;
    }

    @Override
    public int pendingWeapon() {
        return p.pendingWeapon;
    }

    @Override
    public boolean weaponOwned(int weapon) {
        return p.weaponOwned[weapon];
    }

    @Override
    public int ammo(int ammoType) {
        return p.ammo[ammoType];
    }

    @Override
    public int maxAmmo(int ammoType) {
        return p.maxAmmo[ammoType];
    }

    @Override
    public boolean card(int card) {
        return p.cards[card];
    }

    @Override
    public boolean backpack() {
        return p.backpack;
    }

    @Override
    public int power(int power) {
        return p.powers[power];
    }

    @Override
    public int cheats() {
        return p.cheats;
    }

    @Override
    public int damageCount() {
        return p.damageCount;
    }

    @Override
    public int bonusCount() {
        return p.bonusCount;
    }

    @Override
    public int extraLight() {
        return p.extraLight;
    }

    @Override
    public int fixedColormap() {
        return p.fixedColormap;
    }

    @Override
    public int colormap() {
        return p.colormap;
    }

    @Override
    public int attacker() {
        return p.attacker == null || p.attacker.isRemoved() ? -1 : p.attacker.id;
    }

    @Override
    public int killCount() {
        return p.killCount;
    }

    @Override
    public int itemCount() {
        return p.itemCount;
    }

    @Override
    public int secretCount() {
        return p.secretCount;
    }

    @Override
    public int frags(int otherPlayer) {
        return p.frags[otherPlayer];
    }

    @Override
    public boolean didSecret() {
        return p.didSecret;
    }

    @Override
    public boolean attackDown() {
        return p.attackDown;
    }

    @Override
    public boolean pspriteActive(int which) {
        return p.psprites[which].state != null;
    }

    @Override
    public int pspriteSprite(int which) {
        State s = p.psprites[which].state;
        return s == null ? 0 : s.sprite;
    }

    @Override
    public int pspriteFrame(int which) {
        State s = p.psprites[which].state;
        return s == null ? 0 : s.frame;
    }

    @Override
    public int pspriteSx(int which) {
        return p.psprites[which].sx;
    }

    @Override
    public int pspriteSy(int which) {
        return p.psprites[which].sy;
    }

    @Override
    public int pspritePrevSx(int which) {
        PSprite ps = p.psprites[which];
        return ps.prevSx;
    }

    @Override
    public int pspritePrevSy(int which) {
        return p.psprites[which].prevSy;
    }
}
