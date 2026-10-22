package dev.sgs.core.engine.skill.skills;

import java.util.List;
import dev.sgs.core.card.CardInstance;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Standard Huang Gai: HP loss resolves dying before the two-card draw. No usage limit. */
public final class KuRouSkill implements PlannedActiveSkill {
  public static final String ID="kurou";
  private final SgsPlayer owner;
  public KuRouSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return ID;}
  public SgsPlayer owner(){return owner;}
  public Preparation prepare(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){
    if(actor!=owner || !owner.alive() || owner.hp()<1)return Preparation.reject("not_skill_owner");
    if(target!=null || !cards.isEmpty())return Preparation.reject("unexpected_target_or_cost");
    return Preparation.ready(ActiveSkillPlan.hpLossDraw(owner.seat(),api.gameState().turnNumber(),id(),1,2));
  }
  public void commitUse(SkillEngine api,ActiveSkillPlan plan){
    if(!stillPrepared(api,plan))throw new IllegalArgumentException("stale HP-loss preparation");
  }
  public boolean committedPlan(SkillEngine api,ActiveSkillPlan plan,int selectedSource){
    return selectedSource==-1&&plan.actor()==owner.seat()&&plan.primaryTarget()==owner.seat()&&plan.skillId().equals(id())
        &&plan.turn()==api.gameState().turnNumber()&&plan.effect()==ActiveSkillPlan.Effect.LOSE_HP_DRAW&&plan.hpLoss()==1&&plan.drawCount()==2;
  }
  public void effectCompleted(SkillEngine api,ActiveSkillPlan plan){
    api.gameState().log("seat {} used kurou (alive {})",owner.seat(),owner.alive());
  }
}
