package dev.sgs.core.match.dto;

import dev.sgs.core.match.dto.MatchViews.SeatView;
import java.util.List;
import java.util.Objects;

/** Presentation-only comparison of confirmed views; never predicts damage from an action. */
public final class HealthChanges {
  private HealthChanges() {}

  public static boolean lostHealth(List<SeatView> before, List<SeatView> after) {
    return after.stream().anyMatch(now -> before.stream().anyMatch(old ->
        old.seat() == now.seat() && old.alive() && old.heroId() != null
        && Objects.equals(old.heroId(), now.heroId()) && old.hp() > now.hp()
        // Losing maximum HP (awakening, etc.) is not a hit.
        && old.maxHp() == now.maxHp()));
  }
}
