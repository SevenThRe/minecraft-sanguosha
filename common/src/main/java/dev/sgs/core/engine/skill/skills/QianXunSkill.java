package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.card.CardType;
import dev.sgs.core.player.SgsPlayer;
/** Prohibition is checked before any card cost, not a later counterspell. */
public final class QianXunSkill implements RuleModifier {
  private final SgsPlayer owner;
  public QianXunSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "qianxun";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.CARD_TARGET_ALLOWED;}public int priority(){return Priority.RULE_MODIFIER;}
  public boolean applies(SkillContext c){return owner.alive() && c.target()==owner && c.card()!=null
      && (c.card().type()==CardType.SHUNSHOU || c.card().type()==CardType.LEBU);}
  public int modifyInt(SkillContext c,int value){return 0;}
}
