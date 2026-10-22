package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.CardZone;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Project rule: one committed equipment-loss event asks once and draws exactly two. */
public final class XiaoJiSkill implements AwaitableMovementEffect {
  private final SgsPlayer owner;private final SkillEngine api;
  public XiaoJiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "xiaoji";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.CARD_MOVE_AFTER;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return owner.alive() && c.movement()!=null
      && c.movement().from().equals(CardZone.equipment(owner.seat()))
      && !c.movement().to().equals(c.movement().from()) && !c.movement().cards().isEmpty();}
  public void execute(SkillContext c){
    if(api.requestSkillInvoke(owner,id(),owner)){api.drawCards(owner,2);c.state().publishSkillActivation(owner.seat(),id());}
  }
}
