package doom.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import org.junit.jupiter.api.Test;

class BamLintProcessorTest {

    /** A processing environment that is not javac's, like the wrapper of IntelliJ IDEA's build. */
    private static ProcessingEnvironment foreignEnvironment(List<String> notes) {
        Messager messager = (Messager) Proxy.newProxyInstance(Messager.class.getClassLoader(),
                new Class<?>[] {Messager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("printMessage") && args[0] == Diagnostic.Kind.NOTE) {
                        notes.add(args[1].toString());
                    }
                    return null;
                });
        return (ProcessingEnvironment) Proxy.newProxyInstance(ProcessingEnvironment.class.getClassLoader(),
                new Class<?>[] {ProcessingEnvironment.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getOptions" -> Map.of();
                    case "getMessager" -> messager;
                    case "getSourceVersion" -> SourceVersion.latestSupported();
                    default -> null;
                });
    }

    @Test
    void aForeignEnvironmentSkipsTheChecksWithANote() {
        List<String> notes = new ArrayList<>();
        new BamLintProcessor().init(foreignEnvironment(notes));
        assertThat(notes).singleElement().asString().startsWith("BamLint: not a javac task").endsWith("checks skipped");
    }

    @Test
    void unwrapKeepsAnEnvironmentThatIsNotWrapped() {
        ProcessingEnvironment env = foreignEnvironment(new ArrayList<>());
        assertThat(BamLintProcessor.unwrap(env)).isSameAs(env);
    }
}
