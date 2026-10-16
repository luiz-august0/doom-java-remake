package doom.render;

/**
 * The viewpoint of one 3D render ({@code R_SetupFrame}): filled by the caller every frame (already interpolated when
 * the engine interpolates). Mutable, so the steady state allocates nothing.
 */
public final class Camera {

    /** {@code viewx}, fixed point. */
    public int x;
    /** {@code viewy}, fixed point. */
    public int y;
    /** {@code viewz}, fixed point. */
    public int z;
    /** {@code viewangle}, BAM. */
    public int angle;
    /** {@code extralight} (weapon flash), 0..2 normally. */
    public int extraLight;
    /** {@code fixedcolormap}: 0 = none, otherwise the colormap number (1 = light amplification, 32 = invulnerability). */
    public int fixedColormap;
    /** Actor id whose sprite is not drawn (the viewer's own body), or -1. */
    public int viewActor = -1;

    public Camera() {
    }

    public Camera set(int x, int y, int z, int angle, int extraLight, int fixedColormap, int viewActor) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.angle = angle;
        this.extraLight = extraLight;
        this.fixedColormap = fixedColormap;
        this.viewActor = viewActor;
        return this;
    }
}
