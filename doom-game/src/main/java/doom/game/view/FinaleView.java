package doom.game.view;

/** Finale state ({@code f_finale.c}): text crawl, art screens and the DOOM II cast call. Logic in the simulation. */
public interface FinaleView {

    /** 0 = text, 1 = art screen (end picture / bunny scroll), 2 = cast call. */
    int stage();

    /** {@code finalecount}. */
    int count();

    /** Finale text (after DEHACKED). */
    String text();

    /** Lump name of the flat tiled behind the text. */
    String flat();

    /** Index in the cast order. */
    int castIndex();

    /** Name of the current cast member (after DEHACKED). */
    String castName();

    /** Sprite of the current cast state. */
    int castSprite();

    /** Frame of the current cast state (with {@link FrameFlags}). */
    int castFrame();
}
