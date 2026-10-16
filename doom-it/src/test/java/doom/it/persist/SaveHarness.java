package doom.it.persist;

import doom.game.api.GameFactory;
import doom.game.api.GameParams;
import doom.game.api.GameSetup;
import doom.game.api.SimEvents;
import doom.game.api.Skill;
import doom.game.api.TicCmd;
import doom.game.impl.SessionImpl;
import doom.game.persist.SaveGameCodec;
import doom.game.persist.SaveInfo;
import doom.testkit.demo.Demo;
import doom.wad.LumpDirectory;
import doom.wad.WadFile;
import doom.wad.iwad.IwadIdentifier;
import doom.wad.iwad.IwadInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A session driven tic by tic with the commands of a synthetic demo, recording snapshots and trace lines. */
final class SaveHarness {

    final SessionImpl session;
    final List<byte[]> saved = new ArrayList<>();
    /** Tic during which each snapshot of {@link #saved} was taken (the value of {@code gametic} at the time). */
    final List<Integer> savedAt = new ArrayList<>();
    final List<String> messages = new ArrayList<>();
    final TicCmd[] cmds = {new TicCmd(), new TicCmd(), new TicCmd(), new TicCmd()};
    final int[] tic = {0};
    private final StringBuilder sb = new StringBuilder(4096);

    SaveHarness(LumpDirectory lumps, IwadInfo info) {
        SimEvents events = new SimEvents() {
            @Override
            public void gameSaved(int slot, String description, byte[] data) {
                saved.add(data);
                savedAt.add(tic[0]);
            }

            @Override
            public void playerMessage(int player, String text) {
                messages.add(text);
            }
        };
        session = (SessionImpl) GameFactory.createSession(
                new GameSetup(lumps, info, info.version(), List.of(), events));
    }

    static LumpDirectory open(Path iwad) throws IOException {
        return LumpDirectory.open(List.of(iwad));
    }

    static IwadInfo identify(Path iwad) throws IOException {
        return IwadIdentifier.identify(WadFile.open(iwad));
    }

    /** Starts the game of the demo's header (as the menu / {@code -warp} would). */
    void newGame(Demo demo) {
        Demo.Header h = demo.header();
        int mask = 0;
        boolean[] inGame = h.inGame();
        for (int i = 0; i < 4; i++) {
            if (inGame[i]) {
                mask |= 1 << i;
            }
        }
        session.newGame(new GameParams(Skill.of(h.skill()), h.episode(), h.map(), h.deathmatch(), h.respawn(),
                h.fast(), h.noMonsters(), mask, h.consolePlayer()));
    }

    /** Runs tic {@code t} with the commands of the demo and returns its trace line. */
    String tick(Demo demo, int t) {
        tic[0] = t;
        boolean[] inGame = demo.header().inGame();
        int k = 0;
        for (int p = 0; p < 4; p++) {
            if (inGame[p]) {
                Demo.Cmd c = demo.cmds()[t][k++];
                TicCmd out = cmds[p];
                out.forwardMove = (byte) c.forward();
                out.sideMove = (byte) c.side();
                out.angleTurn = (short) c.angleTurn();
                out.buttons = (byte) c.buttons();
            }
        }
        session.tick(cmds, false);
        sb.setLength(0);
        session.appendTraceLine(sb);
        return sb.toString();
    }

    /** The trace line without the informational {@code m} key. */
    static String gated(String line) {
        int m = line.lastIndexOf(";m=");
        return m < 0 ? line : line.substring(0, m);
    }

    /** A snapshot taken between tics. */
    byte[] snapshot() {
        return session.saveHandler().save(0, "snapshot");
    }

    /** The snapshot without the header (which holds the timestamp) and without the checksum that covers it. */
    static byte[] payload(byte[] snapshot) {
        SaveInfo info = SaveGameCodec.inspect(snapshot);
        return Arrays.copyOfRange(snapshot, info.payloadOffset(), snapshot.length - 12);
    }
}
