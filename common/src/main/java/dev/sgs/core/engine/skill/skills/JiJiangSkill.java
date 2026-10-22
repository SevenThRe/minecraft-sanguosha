package dev.sgs.core.engine.skill.skills;

import java.util.*;
import dev.sgs.core.Identity;
import dev.sgs.core.Kingdom;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Providers supply physical costs; the lord remains the user/damage source. */
public final class JiJiangSkill implements PlannedActiveSkill {
  public static final String ID="jijiang";
  private final SgsPlayer owner;
  private final SkillEngine api;
  public JiJiangSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return ID;}
  public SgsPlayer owner(){return owner;}
  public boolean requiresAttackContinuation(){return true;}
  public Optional<GameCard> provide(boolean offer){
    if(!owner.alive() || owner.identity()!=Identity.LORD)return Optional.empty();
    var helpers=api.gameState().alivePlayersFromCurrent().stream()
        .filter(p->p!=owner && p.hero()!=null && api.gameState().kingdom(p)==Kingdom.SHU).toList();
    if(helpers.isEmpty() || (offer && !api.requestSkillInvoke(owner,ID,owner)))return Optional.empty();
    for(var helper:helpers){
      if(!owner.alive())break;
      var card=api.requestProvidedAttack(helper,owner);
      if(card.isPresent()){
        api.gameState().publishSkillActivation(owner.seat(),ID);
        api.gameState().log("seat {} used jijiang: sha supplied by seat {}",owner.seat(),helper.seat());
        return card;
      }
    }
    return Optional.empty();
  }
  public Preparation prepare(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){
    if(actor!=owner || actor.identity()!=Identity.LORD||!actor.alive())return Preparation.reject("not_lord");
    if(!cards.isEmpty())return Preparation.reject("unexpected_cost");
    if(!api.canUseActiveAttack(actor,target))return Preparation.reject("illegal_attack_target");
    return Preparation.ready(new ActiveSkillPlan(actor.seat(),api.gameState().turnNumber(),id(),target.seat(),null,List.of(),List.of(),ActiveSkillPlan.Effect.PROVIDED_ATTACK));
  }
  public void commitUse(SkillEngine api,ActiveSkillPlan plan){
    if(!stillPrepared(api,plan))throw new IllegalArgumentException("stale provided attack preparation");
  }
  public boolean committedPlan(SkillEngine api,ActiveSkillPlan plan,int selectedSource){
    return selectedSource==-1&&plan.actor()==owner.seat()&&plan.skillId().equals(id())&&owner.identity()==Identity.LORD
        &&plan.turn()==api.gameState().turnNumber()&&plan.effect()==ActiveSkillPlan.Effect.PROVIDED_ATTACK;
  }
}
