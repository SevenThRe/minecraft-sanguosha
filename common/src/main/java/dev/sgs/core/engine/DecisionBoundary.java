package dev.sgs.core.engine;

import java.util.Objects;
import java.util.UUID;

/** Engine decision identity and budget. Transport nonce/epoch/control gates stay in MatchSession.
 * Matching a decision never validates a caller's card or pays a cost. Parents still own expiry effects.
 */
public record DecisionBoundary(UUID actor,long token,long revision,long deadlineMillis) {
  public DecisionBoundary {Objects.requireNonNull(actor);}
  public static DecisionBoundary of(PromptRequest request,long revision){
    return new DecisionBoundary(request.asker().id(),request.id(),revision,request.deadlineMillis());
  }
  public boolean matches(UUID actor,long token,long revision){
    return this.actor.equals(actor) && this.token==token && this.revision==revision;
  }
  /** Existing inclusive-deadline rule: an answer exactly at the deadline remains valid. */
  public boolean expired(long now){return now>deadlineMillis;}
  public static long restoredDeadline(long deadline,long capturedAt,long now){
    return Math.addExact(now,Math.max(0,Math.subtractExact(deadline,capturedAt)));
  }
}
