package doom.tools.bench;

import doom.tools.Tool;

/**
 * Run -timedemo over a fixed demo list at several resolutions.
 *
 * <p><b>Stub (Wave 0):</b> implemented by W5.
 */
public final class BenchSuiteTool implements Tool {

    @Override
    public String name() {
        return "bench-suite";
    }

    @Override
    public String summary() {
        return "Run -timedemo over a fixed demo list at several resolutions";
    }

    @Override
    public int run(String[] args) {
        System.err.println(name() + ": not implemented yet (W5)");
        return 2;
    }
}
