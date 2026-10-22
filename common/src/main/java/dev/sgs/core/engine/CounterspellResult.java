package dev.sgs.core.engine;
import java.util.*;
/** Worker-only result; private scope preferences are never a wire DTO. */
public record CounterspellResult(Optional<Answer> winner,Set<UUID> scopePasses){
  public record Answer(UUID actor,PlayDecision decision){public Answer{Objects.requireNonNull(actor);Objects.requireNonNull(decision);}}
  public CounterspellResult{Objects.requireNonNull(winner);scopePasses=Set.copyOf(scopePasses);}
}
