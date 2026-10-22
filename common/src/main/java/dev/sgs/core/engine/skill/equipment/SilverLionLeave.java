package dev.sgs.core.engine.skill.equipment;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Reads the committed source event, not the replacement armor currently in the slot. */
public final class SilverLionLeave implements ImmediateMovementEffect {
  private final SkillEngine api;
  public SilverLionLeave(SkillEngine api){this.api=api;}
  public String id(){return "baiyin_leave";}
  public SgsPlayer owner(){return null;}
  public Timing timing(){return Timing.CARD_MOVE_AFTER;}
  public int priority(){return Priority.EQUIPMENT;}
  public boolean applies(SkillContext ctx){
    var move=ctx.movement();return move!=null && move.from().kind()==CardZone.Kind.EQUIPMENT
        && move.cards().stream().anyMatch(c->c.type()==CardType.BAIYIN)
        && ctx.source()!=null && ctx.source().alive();
  }
  public void execute(SkillContext ctx){int healed=api.heal(ctx.source(),1);if(healed>0)ctx.state().log("seat {} recovered {} after losing baiyin",ctx.source().seat(),healed);}
}
