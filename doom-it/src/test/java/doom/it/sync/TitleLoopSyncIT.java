package doom.it.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import doom.game.api.GameFactory;
import doom.game.api.GameSetup;
import doom.game.api.TicCmd;
import doom.game.impl.SessionImpl;
import doom.testkit.Iwads;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.iwad.IwadIdentifier;
import doom.wad.iwad.IwadInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * W3-D: the title loop (pages and demos, {@code D_DoAdvanceDemo} / {@code D_PageTicker}) against oracle traces taken
 * by running the patched Chocolate Doom without a demo argument ({@code testdata/traces/title-<iwad>.trace.gz}, a
 * whole cycle of the loop). Our trace must match every gated key until the first stub of another rule area is hit.
 */
class TitleLoopSyncIT {

    private static Path projectRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("testdata"))) {
            p = p.getParent();
        }
        return p;
    }

    private void check(String iwadName, String ref) throws IOException {
        assumeTrue(Iwads.find(iwadName).isPresent(), iwadName + " not available");
        Path root = projectRoot();
        assumeTrue(root != null, "testdata not found");
        Path refFile = root.resolve("testdata/traces/" + ref);
        assumeTrue(Files.isRegularFile(refFile), ref + " not found");

        List<String> expected = DemoSync.readTrace(refFile);
        Path iwad = Iwads.require(iwadName);
        IwadInfo info = IwadIdentifier.identify(WadFile.open(iwad));
        LumpDirectory lumps = LumpDirectory.open(List.of(iwad));
        SessionImpl session = (SessionImpl) GameFactory.createSession(
                new GameSetup(lumps, info, info.version(), List.of(), null));
        session.startTitle();

        TicCmd[] cmds = {new TicCmd(), new TicCmd(), new TicCmd(), new TicCmd()};
        List<String> ours = new ArrayList<>(expected.size());
        StringBuilder sb = new StringBuilder(4096);
        for (int tic = 0; tic < expected.size(); tic++) {
            session.tick(cmds, false);
            sb.setLength(0);
            session.appendTraceLine(sb);
            ours.add(sb.toString());
        }
        int firstStub = session.unimplemented().firstTic();
        int limit = firstStub < 0 ? expected.size() : firstStub;
        for (int i = 0; i < limit; i++) {
            String[] diff = DemoSync.firstDiff(expected.get(i), ours.get(i));
            assertThat(diff).as("tic %d (first stub hit at %d)", i, firstStub).isNull();
        }
        System.out.println(iwadName + ": " + limit + " of " + expected.size() + " title-loop tics identical; first stub "
                + firstStub + " " + session.unimplemented().firstKey());
    }

    @Test
    void doom1TitleLoop() throws IOException {
        check("doom1.wad", "title-doom1.trace.gz");
    }

    @Test
    void freedoom1TitleLoop() throws IOException {
        check("freedoom1.wad", "title-freedoom1.trace.gz");
    }

    @Test
    void freedoom2TitleLoop() throws IOException {
        check("freedoom2.wad", "title-freedoom2.trace.gz");
    }
}
