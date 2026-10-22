package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.card.CardZone;
import dev.sgs.core.player.SgsPlayer;
/** Triggered by actual hand departures, including use before its damage resolves. */
public final class LianYingSkill implements AwaitableMovementEffect {
  private final SgsPlayer owner;private final SkillEngine api;
  public LianYingSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "lianying";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.CARD_MOVE_AFTER;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return owner.alive() && owner.handCount()==0 && c.movement()!=null
      && c.movement().from().equals(CardZone.hand(owner.seat())) && !c.movement().cards().isEmpty();}
  public void execute(SkillContext c){if(api.requestSkillInvoke(owner,id(),owner)){api.drawCards(owner,1);c.confirmPublicActivation();}}
}
