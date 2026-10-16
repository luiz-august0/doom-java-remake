package doom.bench;

import doom.math.Fixed;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/** Placeholder benchmark; renderer and mixer benchmarks arrive in Wave 5. */
@State(Scope.Thread)
public class FixedMulBench {

    private int a = 3 << 16;
    private int b = 5 << 15;

    @Benchmark
    public int mul() {
        return Fixed.mul(a, b);
    }
}
