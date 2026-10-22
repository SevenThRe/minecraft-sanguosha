package dev.sgs.core.match;

import java.util.Map;

/** Explicit public team assignments, never inferred from secret identities or hero kingdoms. */
public record HandVisibility(boolean teammateSharing, Map<Integer,Integer> publicTeams) {
  public static final HandVisibility PRIVATE = new HandVisibility(false, Map.of());
  public HandVisibility { publicTeams = Map.copyOf(publicTeams); }
  public boolean maySee(int viewer, int owner) {
    if (viewer < 0 || owner < 0) return false;
    if (viewer == owner) return true;
    Integer team = publicTeams.get(viewer);
    return teammateSharing && team != null && team >= 0 && team.equals(publicTeams.get(owner));
  }
}
