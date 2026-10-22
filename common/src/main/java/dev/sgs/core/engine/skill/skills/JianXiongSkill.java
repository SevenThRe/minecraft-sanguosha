package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.CardZone;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Standard Cao Cao: optional acquisition of the damage card, with no extra draw. */
public final class JianXiongSkill implements SkillListener {
  public static final String ID="jianxiong";
  private final SgsPlayer owner;
  private final SkillEngine engine;
  public JianXiongSkill(SgsPlayer owner,SkillEngine engine){this.owner=owner;this.engine=engine;}
  public String id(){return ID;}
  public Timing timing(){return Timing.DAMAGE_TAKE_AFTER;}
  public int priority(){return Priority.ACTIVE_CHOICE;}
  public SgsPlayer owner(){return owner;}
  public boolean applies(SkillContext ctx){
    return ctx.target()==owner && owner.alive() && owner.hp()>0 && ctx.card()!=null
        && !ctx.card().sourceCards().isEmpty()
        && ctx.card().sourceCards().stream().allMatch(c->CardZone.PROCESSING.equals(ctx.state().cards().locate(c.instanceId())));
  }
  public void execute(SkillContext ctx){
    if(!engine.requestSkillInvoke(owner,ID,ctx.source()))return;
    int gained=engine.obtainProcessingCards(owner,ctx.card());
    if(gained>0){ctx.confirmPublicActivation();ctx.state().log("seat {} used jianxiong: obtained {} physical cards",owner.seat(),gained);}
  }
}
