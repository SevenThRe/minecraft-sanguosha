package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.CardCategory;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Standard identity version: transformed tricks count, while recasting is not use. */
public final class JiZhiSkill implements SkillListener {
  public static final String ID="jizhi";
  private final SgsPlayer owner;private final SkillEngine api;
  public JiZhiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return ID;}
  public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.CARD_USE_COMMITTED;}
  public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext ctx){return owner.alive() && ctx.actor()==owner && ctx.card()!=null && ctx.card().type().category()==CardCategory.TRICK;}
  public void execute(SkillContext ctx){if(api.requestSkillInvoke(owner,ID,owner)){api.drawCards(owner,1);ctx.confirmPublicActivation();ctx.state().log("seat {} used jizhi",owner.seat());}}
}
