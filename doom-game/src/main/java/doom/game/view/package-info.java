/**
 * Read-only views of the simulation state for the presentation layer (ADR-004): renderer, UI, sound and engine read
 * the game exclusively through these interfaces. The API is index-based (sector, line, actor id ...) so that reading
 * never allocates; implementations are monomorphic and inlined by the JIT.
 *
 * <p>Fixed-point values are 16.16 {@code int}s, angles are BAM {@code int}s (unsigned), texture and flat numbers use
 * the vanilla numbering (TEXTURE1 then TEXTURE2; flats in F_START..F_END order).
 *
 * <p>Frozen in Wave 0 by the orchestrator; implemented by the simulation (W1-A geometry, W2-A the rest).
 */
package doom.game.view;
