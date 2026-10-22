package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Standard Guan Yu: one red owned hand/equipment card becomes a normal Sha. */
public final class WuShengSkill implements SkillListener {
  public static final String ID="wusheng";
  private final SgsPlayer owner;
  public WuShengSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return ID;}
  public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.CARD_TRANSFORM;}
  public int priority(){return Priority.PASSIVE_RESPONSE;}
  public boolean applies(SkillContext ctx){
    if(ctx.actor()!=owner || !owner.alive() || ctx.card()==null || ctx.card().isVirtual()
        || ctx.declaredAs()!=CardType.SHA || !ctx.card().isRed())return false;
    if(ctx.promptType()==null)return true;
    return switch(ctx.promptType()){
      case DUEL_SHA,RESPOND_SHA_NANMAN,QINGLONG_SHA,JIEDAO_RESPONSE,JIJIANG_SHA->true;
      default->false;
    };
  }
  public void execute(SkillContext ctx){ctx.confirmTransform();ctx.confirmPublicActivation();ctx.state().log("seat {} used wusheng",owner.seat());}
}
