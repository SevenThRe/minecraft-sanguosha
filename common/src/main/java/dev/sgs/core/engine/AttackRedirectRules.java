package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.SkillEngine;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;

/** Shared real-entity and post-payment range checks for a discard-to-redirect effect. */
public final class AttackRedirectRules {
  private AttackRedirectRules(){}
  public static List<CardInstance> costs(SgsPlayer owner){
    var result=new ArrayList<>(owner.handView());result.addAll(owner.equipment().all());return List.copyOf(result);
  }
  public static List<Integer> targets(SkillEngine api,SgsPlayer source,SgsPlayer owner,GameCard attack,List<CardInstance> costs,List<Integer> originalTargets){
    return api.gameState().players().stream().filter(target->!originalTargets.contains(target.seat())&&costs.stream()
        .anyMatch(cost->api.canRedirectAttack(source,owner,target,attack,cost))).map(SgsPlayer::seat).toList();
  }
  public static Optional<CardInstance> payment(SkillEngine api,SgsPlayer source,SgsPlayer owner,GameCard attack,
      SkillChoice choice,List<Long> offeredCosts,List<Integer> offeredTargets,List<Integer> originalTargets){
    if(choice==null||!offeredCosts.contains(choice.cardId())||!offeredTargets.contains(choice.targetSeat())
        ||originalTargets.contains(choice.targetSeat())||choice.targetSeat()<0||choice.targetSeat()>=api.gameState().players().size())return Optional.empty();
    return costs(owner).stream().filter(c->c.instanceId()==choice.cardId())
        .filter(c->api.canRedirectAttack(source,owner,api.gameState().players().get(choice.targetSeat()),attack,c)).findFirst();
  }
}
