package doom.game.impl;

import doom.game.api.DemoPlayback;
import doom.game.api.GameParams;
import doom.game.api.GameSession;
import doom.game.api.MiscRandom;
import doom.game.api.TicCmd;
import doom.game.session.Cheats;
import doom.game.session.Game;
import doom.game.trace.TraceWriter;
import doom.game.view.ActorView;
import doom.game.view.GameView;
import doom.game.view.WorldView;
import doom.game.world.GameFlow;
import doom.game.world.Unimplemented;
import doom.game.world.World;

/** The {@link GameSession} implementation: commands go to {@link Game}, state is read through the views. */
public final class SessionImpl implements GameSession {

    private final World world;
    private final Game game;
    private final Cheats cheats;
    private final SimGameView view;
    private final TraceWriter trace;
    private final MiscRandom miscRandom;

    SessionImpl(World world, Game game) {
        this.world = world;
        this.game = game;
        this.cheats = new Cheats(world, game);
        this.view = new SimGameView(world, game);
        this.trace = new TraceWriter(world);
        this.miscRandom = world.rng::misc;
    }

    @Override
    public void startTitle() {
        game.startTitle();
    }

    @Override
    public void newGame(GameParams params) {
        game.deferredNewGame(params);
    }

    @Override
    public void playDemo(String name, byte[] demo, DemoPlayback mode) {
        game.deferredPlayDemo(name, demo, mode);
    }

    @Override
    public void recordDemo(String name, GameParams params) {
        game.recordDemo(name, params);
    }

    @Override
    public byte[] finishDemoRecording() {
        return game.finishDemoRecording();
    }

    @Override
    public void loadGame(byte[] saveData) {
        game.loadGame(saveData);
    }

    @Override
    public void requestSave(int slot, String description) {
        game.requestSave(slot, description);
    }

    @Override
    public void requestPause() {
        game.requestPause();
    }

    @Override
    public void endGame() {
        game.startTitle();
    }

    @Override
    public void cheatKey(int ch) {
        cheats.key(ch);
    }

    @Override
    public void finaleKey() {
        game.finaleKey();
    }

    @Override
    public void cycleDisplayPlayer() {
        game.cycleDisplayPlayer();
    }

    @Override
    public void tick(TicCmd[] cmds, boolean menuActive) {
        game.tick(cmds, menuActive);
    }

    @Override
    public GameView view() {
        return view;
    }

    @Override
    public WorldView world() {
        return view.world();
    }

    @Override
    public ActorView actors() {
        return view.actors();
    }

    @Override
    public MiscRandom miscRandom() {
        return miscRandom;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Appends nothing after a tick that ended the program (the end marker of a {@code SINGLE} or {@code TIMEDEMO}
     * demo: the original leaves through {@code I_Quit} / {@code I_Error} inside {@code G_Ticker}).
     */
    @Override
    public void appendTraceLine(StringBuilder out) {
        if (game.quit) {
            return;
        }
        trace.appendLine(out, world.gametic - 1, game.gameState.ordinal());
    }

    @Override
    public void appendTraceVerbose(StringBuilder out) {
        if (game.quit) {
            return;
        }
        trace.appendVerbose(out);
    }

    // ------------------------------------------------------------------ test and tool hooks (not part of the API)

    /** Stub hits of the rule areas not implemented yet. */
    public Unimplemented unimplemented() {
        return world.unimplemented;
    }

    /** The game-flow requests of the rule areas ({@code G_ExitLevel} ...), so tests can trigger an exit directly. */
    public GameFlow flow() {
        return world.flow;
    }

    /** The session's game flow object, for tools that install a {@link doom.game.session.SaveGameHandler}. */
    public Game game() {
        return game;
    }

    /**
     * The save game codec of this session, for tests and tools: {@code save} takes a snapshot between tics and
     * {@code load} applies one immediately (the game API goes through {@link #requestSave} / {@link #loadGame}).
     */
    public doom.game.session.SaveGameHandler saveHandler() {
        return game.saveGameHandler();
    }

    /** True once the program would have ended (a SINGLE / TIMEDEMO demo reached its end marker). */
    public boolean quit() {
        return game.quit;
    }
}
