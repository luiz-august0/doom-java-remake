package doom.game.mapspecial;

/**
 * The type enumerations of {@code p_spec.h}, as plain ints because the trace prints their numeric value and the
 * boss-death code of the AI area passes them to {@link MapSpecials#doFloor} / {@link MapSpecials#doDoor}.
 */
public final class SpecialTypes {

    private SpecialTypes() {
    }

    /** {@code vldoor_e}. */
    public static final class Door {
        public static final int NORMAL = 0;
        public static final int CLOSE_30_THEN_OPEN = 1;
        public static final int CLOSE = 2;
        public static final int OPEN = 3;
        public static final int RAISE_IN_5_MINS = 4;
        public static final int BLAZE_RAISE = 5;
        public static final int BLAZE_OPEN = 6;
        public static final int BLAZE_CLOSE = 7;

        private Door() {
        }
    }

    /** {@code floor_e}. */
    public static final class Floor {
        public static final int LOWER_FLOOR = 0;
        public static final int LOWER_FLOOR_TO_LOWEST = 1;
        public static final int TURBO_LOWER = 2;
        public static final int RAISE_FLOOR = 3;
        public static final int RAISE_FLOOR_TO_NEAREST = 4;
        public static final int RAISE_TO_TEXTURE = 5;
        public static final int LOWER_AND_CHANGE = 6;
        public static final int RAISE_FLOOR_24 = 7;
        public static final int RAISE_FLOOR_24_AND_CHANGE = 8;
        public static final int RAISE_FLOOR_CRUSH = 9;
        public static final int RAISE_FLOOR_TURBO = 10;
        public static final int DONUT_RAISE = 11;
        public static final int RAISE_FLOOR_512 = 12;

        private Floor() {
        }
    }

    /** {@code stair_e}. */
    public static final class Stair {
        public static final int BUILD_8 = 0;
        public static final int TURBO_16 = 1;

        private Stair() {
        }
    }

    /** {@code ceiling_e}. */
    public static final class Ceiling {
        public static final int LOWER_TO_FLOOR = 0;
        public static final int RAISE_TO_HIGHEST = 1;
        public static final int LOWER_AND_CRUSH = 2;
        public static final int CRUSH_AND_RAISE = 3;
        public static final int FAST_CRUSH_AND_RAISE = 4;
        public static final int SILENT_CRUSH_AND_RAISE = 5;

        private Ceiling() {
        }
    }

    /** {@code plattype_e}. */
    public static final class Plat {
        public static final int PERPETUAL_RAISE = 0;
        public static final int DOWN_WAIT_UP_STAY = 1;
        public static final int RAISE_AND_CHANGE = 2;
        public static final int RAISE_TO_NEAREST_AND_CHANGE = 3;
        public static final int BLAZE_DWUS = 4;

        private Plat() {
        }
    }

    /** {@code plat_e}. */
    static final class PlatStatus {
        static final int UP = 0;
        static final int DOWN = 1;
        static final int WAITING = 2;
        static final int IN_STASIS = 3;

        private PlatStatus() {
        }
    }

    /** {@code result_e}. */
    static final class Result {
        static final int OK = 0;
        static final int CRUSHED = 1;
        static final int PASTDEST = 2;

        private Result() {
        }
    }
}
