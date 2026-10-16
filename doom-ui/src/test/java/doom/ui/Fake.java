package doom.ui;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A scriptable stand-in for the frozen view interfaces: every method returns the value set under its name (or the
 * result of a {@link Function} over the arguments), and 0/false/""/null otherwise.
 */
public final class Fake<T> {

    private final Map<String, Object> values = new HashMap<>();
    private final T proxy;

    @SuppressWarnings("unchecked")
    private Fake(Class<T> type) {
        InvocationHandler h = (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "Fake" + type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    default -> null;
                };
            }
            Object v = values.get(method.getName());
            if (v instanceof Function<?, ?> f) {
                v = ((Function<Object[], Object>) f).apply(args == null ? new Object[0] : args);
            }
            if (v != null) {
                return v;
            }
            Class<?> r = method.getReturnType();
            if (r == int.class) {
                return 0;
            }
            if (r == boolean.class) {
                return false;
            }
            if (r == long.class) {
                return 0L;
            }
            if (r == String.class) {
                return "";
            }
            return null;
        };
        this.proxy = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, h);
    }

    public static <T> Fake<T> of(Class<T> type) {
        return new Fake<>(type);
    }

    /** Sets a constant return value, or a {@code Function<Object[], Object>} over the arguments. */
    public Fake<T> set(String method, Object value) {
        values.put(method, value);
        return this;
    }

    /** A function of the single int argument. */
    public Fake<T> setInt(String method, java.util.function.IntFunction<Object> f) {
        values.put(method, (Function<Object[], Object>) a -> f.apply((Integer) a[0]));
        return this;
    }

    public T get() {
        return proxy;
    }
}
