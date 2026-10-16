package doom.testkit.demo;

/** How the generator drives a player. */
public enum DemoPolicy {
    /** Random walk with intent held for a while, plus fire bursts, use pulses and weapon changes. */
    RANDOM_WALK("random-walk"),
    /** The same walk without any button: no fire, no use, no weapon change. Isolates movement and physics. */
    MOVEMENT_ONLY("movement-only");

    private final String id;

    DemoPolicy(String id) {
        this.id = id;
    }

    /** The name used in manifests. */
    public String id() {
        return id;
    }

    /** Parses a manifest name. */
    public static DemoPolicy parse(String id) {
        for (DemoPolicy p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown demo policy: " + id);
    }
}
