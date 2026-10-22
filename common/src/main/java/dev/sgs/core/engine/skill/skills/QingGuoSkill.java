package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Standard Qingguo permits only black HAND cards, including the lord's Hujia response. */
public final class QingGuoSkill implements SkillListener {
  private final SgsPlayer owner;public QingGuoSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "qingguo";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.CARD_TRANSFORM;}public int priority(){return Priority.PASSIVE_RESPONSE;}
  public boolean applies(SkillContext c){return c.actor()==owner && owner.alive() && c.card()!=null && !c.card().isVirtual()
      && c.card().sourceCards().size()==1 && (c.card().suit()==Suit.CLUB || c.card().suit()==Suit.SPADE) && c.declaredAs()==CardType.SHAN
      && CardZone.hand(owner.seat()).equals(c.state().cards().locate(c.card().sourceCards().getFirst().instanceId()))
      && c.promptType()!=null && switch(c.promptType()){case DODGE_SHA,HUJIA_SHAN->true;default->false;};}
  public void execute(SkillContext c){c.confirmTransform();c.confirmPublicActivation();}
}
