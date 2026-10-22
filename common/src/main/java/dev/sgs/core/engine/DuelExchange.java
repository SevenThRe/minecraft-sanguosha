package dev.sgs.core.engine;

/**
 * Shared duel turn state. The parent owns prompts, payments, damage and cleanup; only a committed
 * response advances this state. In particular, a two-Sha requirement belongs to the current other
 * participant and is held stable until that responder finishes the whole exchange.
 *
 * The four values deliberately match the existing trick checkpoint projection.
 */
public record DuelExchange(int responder, int other, int required, int paid) {
  public DuelExchange {
    ResponseSequence.restorePaid(required, paid);
    if (responder == -1 && other == -1) {
      if (required != 0 || paid != 0) throw new IllegalArgumentException("inactive duel has responses");
    } else if (responder < 0 || other < 0 || responder == other) {
      throw new IllegalArgumentException("invalid duel participants");
    }
  }

  public static DuelExchange inactive() { return new DuelExchange(-1, -1, 0, 0); }

  /** The selected target answers first, including when the source is another skill's target. */
  public static DuelExchange start(int source, int target) {
    return new DuelExchange(target, source, 0, 0);
  }

  public boolean started() { return responder >= 0; }
  public boolean needsRequirement() { return started() && required == 0; }

  public DuelExchange requiring(int count) {
    if (!needsRequirement()) throw new IllegalStateException("duel requirement already fixed or inactive");
    if (count < 1) throw new IllegalArgumentException("duel requires at least one response");
    return new DuelExchange(responder, other, count, 0);
  }

  public DuelExchange committed() {
    if (!started() || required == 0) throw new IllegalStateException("duel response has no requirement");
    var remaining = ResponseSequence.restorePaid(required, paid).committed();
    return remaining.complete()
        ? new DuelExchange(other, responder, 0, 0)
        : new DuelExchange(responder, other, required, remaining.paidOutOf(required));
  }
}
