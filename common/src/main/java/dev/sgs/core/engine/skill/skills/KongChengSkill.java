package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Target prohibition is evaluated when selected, not retroactively after losing the last card. */
public final class KongChengSkill implements RuleModifier {
  private final SgsPlayer owner;public KongChengSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "kongcheng";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.CARD_TARGET_ALLOWED;}public int priority(){return Priority.RULE_MODIFIER;}
  public boolean applies(SkillContext c){return c.target()==owner && owner.alive() && owner.handCount()==0 && c.card()!=null
      && switch(c.card().type()){case SHA,HUOSHA,LEISHA,JUEDOU->true;default->false;};}
  public int modifyInt(SkillContext c,int value){return 0;}
}
