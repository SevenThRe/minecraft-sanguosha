package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Mandatory classic Hua Xiong drawback; the damage source chooses healing or drawing. */
public final class YaoWuSkill implements SkillListener {
  private final SgsPlayer owner;private final SkillEngine api;
  public YaoWuSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "yaowu";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.DAMAGE_TAKE_BEFORE;}public int priority(){return Priority.EQUIPMENT+100;}
  public boolean applies(SkillContext c){return c.target()==owner && owner.alive() && c.damage()>0 && c.source()!=null && c.source().alive()
      && c.card()!=null && c.card().isRed() && (c.card().type()==CardType.SHA || c.card().type()==CardType.HUOSHA || c.card().type()==CardType.LEISHA);}
  public void execute(SkillContext c){var source=c.source();
    if(source.hp()<source.maxHp() && api.requestSkillInvoke(source,"yaowu_recover",owner))api.heal(source,1);
    else api.drawCards(source,1);
    c.confirmPublicActivation();
  }
}
