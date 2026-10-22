package dev.sgs.core.addon;
import java.util.List;
/** A proposal only; the normal authoritative active-skill ingress executes and validates it. */
public record BotSkillAction(String skillId,int targetSeat,List<Long> costs){
  public BotSkillAction{costs=List.copyOf(costs);if(skillId==null||costs.size()>160||targetSeat< -1||targetSeat>7)throw new IllegalArgumentException("invalid bot skill proposal");}
}
