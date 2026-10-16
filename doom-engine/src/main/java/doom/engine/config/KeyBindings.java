package doom.engine.config;

import java.util.ArrayList;
import java.util.List;

/** The inputs bound to each {@link Action}. Immutable after construction. */
public final class KeyBindings {

    private static final Action[] NO_ACTIONS = new Action[0];

    private final int[][] inputs;
    private final Action[][] byInput;

    private KeyBindings(int[][] inputs) {
        this.inputs = inputs;
        this.byInput = new Action[InputNames.COUNT][];
        for (int id = 0; id < InputNames.COUNT; id++) {
            List<Action> list = new ArrayList<>();
            for (Action a : Action.values()) {
                for (int i : inputs[a.ordinal()]) {
                    if (i == id) {
                        list.add(a);
                        break;
                    }
                }
            }
            byInput[id] = list.isEmpty() ? NO_ACTIONS : list.toArray(new Action[0]);
        }
    }

    /** The default bindings. */
    public static KeyBindings defaults() {
        return fromConfig(Config.empty());
    }

    /** Reads the {@code key.*} settings; unknown names are reported as warnings of {@code config}. */
    public static KeyBindings fromConfig(Config config) {
        int[][] ids = new int[Action.values().length][];
        for (Action a : Action.values()) {
            String text = config.get(Settings.binding(a));
            List<Integer> list = new ArrayList<>();
            for (String part : text.split(",")) {
                if (part.isBlank()) {
                    continue;
                }
                int id = InputNames.parse(part);
                if (id < 0) {
                    config.warnings().add("unknown input '" + part.strip() + "' in " + a.configKey());
                } else if (!list.contains(id)) {
                    list.add(id);
                }
            }
            ids[a.ordinal()] = list.stream().mapToInt(Integer::intValue).toArray();
        }
        return new KeyBindings(ids);
    }

    /** Input ids bound to an action (do not modify). */
    public int[] inputs(Action action) {
        return inputs[action.ordinal()];
    }

    /** Actions bound to an input id (do not modify); empty for unbound or out of range ids. */
    public Action[] actionsFor(int inputId) {
        return inputId >= 0 && inputId < InputNames.COUNT ? byInput[inputId] : NO_ACTIONS;
    }

    /** Stores these bindings in {@code config}. */
    public void writeTo(Config config) {
        for (Action a : Action.values()) {
            StringBuilder sb = new StringBuilder();
            for (int id : inputs[a.ordinal()]) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(InputNames.name(id));
            }
            config.setRaw(a.configKey(), sb.toString());
        }
    }

    /** A copy with {@code action} bound to exactly {@code ids}. */
    public KeyBindings with(Action action, int... ids) {
        int[][] copy = inputs.clone();
        copy[action.ordinal()] = ids.clone();
        return new KeyBindings(copy);
    }
}
