package doom.tools;

/** One sub-command of {@link Main}. */
public interface Tool {

    /** Command name, e.g. {@code "wad-dump"}. */
    String name();

    /** One-line description for the usage text. */
    String summary();

    /**
     * Runs the tool.
     *
     * @return process exit status (0 = success)
     */
    int run(String[] args) throws Exception;
}
