package doom.game.world;

/** {@code rejectmatrix}: one bit per (sector, sector) pair, padded like Chocolate Doom when the lump is short. */
public final class Reject {
    public byte[] matrix;

    public Reject(byte[] matrix) {
        this.matrix = matrix;
    }
}
