package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.CardZone;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Obtain the FINAL judgment physical card; the resolved result remains immutable. */
public final class TianDuSkill implements SkillListener {
  private final SgsPlayer owner;private final SkillEngine api;
  public TianDuSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "tiandu";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.JUDGE_CARD_EFFECT;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.target()==owner && owner.alive() && owner.hp()>0 && c.judgeCard()!=null
      && CardZone.PROCESSING.equals(c.state().cards().locate(c.judgeCard().instanceId()));}
  public void execute(SkillContext c){
    if(api.requestSkillInvoke(owner,id(),owner) && api.obtainProcessingCards(owner,c.judgeCard())==1)c.confirmPublicActivation();
  }
}
