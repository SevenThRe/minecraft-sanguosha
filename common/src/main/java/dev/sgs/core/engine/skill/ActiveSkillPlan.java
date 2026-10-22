package dev.sgs.core.engine.skill;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.SkillChoice;
import java.util.*;

/** Immutable preparation, before any question, payment, use limit or activation is committed. */
public record ActiveSkillPlan(int actor, int turn, String skillId, int primaryTarget,
    PromptType selection, List<Integer> candidates, List<Long> costs, Effect effect,int hpLoss,int drawCount) {
  public enum Effect { UNCOUNTERABLE_DUEL, LOSE_HP_DRAW, PROVIDED_ATTACK, REVEALED_GIFT }
  public ActiveSkillPlan(int actor,int turn,String skillId,int primaryTarget,PromptType selection,
      List<Integer> candidates,List<Long> costs,Effect effect){this(actor,turn,skillId,primaryTarget,selection,candidates,costs,effect,0,0);}
  public static ActiveSkillPlan hpLossDraw(int actor,int turn,String skillId,int hpLoss,int drawCount){
    return new ActiveSkillPlan(actor,turn,skillId,actor,null,List.of(),List.of(),Effect.LOSE_HP_DRAW,hpLoss,drawCount);
  }

  public ActiveSkillPlan {
    Objects.requireNonNull(skillId);Objects.requireNonNull(effect);
    candidates=List.copyOf(candidates);costs=List.copyOf(costs);
    if(actor<0||actor>7||primaryTarget<0||primaryTarget>7||turn<0
        ||!skillId.matches("(?:[a-z][a-z0-9_]{1,31}:)?[a-z][a-z0-9_]{0,63}")
        ||candidates.size()>6||new HashSet<>(candidates).size()!=candidates.size()
        ||candidates.stream().anyMatch(s->s<0||s>7||s==actor||s==primaryTarget)
        ||costs.size()>160||new HashSet<>(costs).size()!=costs.size()
        ||costs.stream().anyMatch(id->id<1))throw new IllegalArgumentException("invalid active skill plan");
    if(effect==Effect.UNCOUNTERABLE_DUEL&&(actor==primaryTarget||selection!=PromptType.LIJIAN_TARGET||candidates.isEmpty()
        ||costs.isEmpty()||hpLoss!=0||drawCount!=0)
        ||effect==Effect.LOSE_HP_DRAW&&(actor!=primaryTarget||selection!=null||!candidates.isEmpty()||!costs.isEmpty()
        ||hpLoss<1||hpLoss>1_000_000||drawCount<0||drawCount>160)
        ||effect==Effect.PROVIDED_ATTACK&&(actor==primaryTarget||selection!=null||!candidates.isEmpty()||!costs.isEmpty()
        ||hpLoss!=0||drawCount!=0)
        ||effect==Effect.REVEALED_GIFT&&(actor==primaryTarget||selection!=PromptType.FANJIAN_SUIT||!candidates.isEmpty()
        ||!costs.isEmpty()||hpLoss!=0||drawCount!=0))throw new IllegalArgumentException("invalid active effect shape");
  }

  /** The zero card sentinel selects a role, never an opaque card position or a physical fee. */
  public boolean allows(SkillChoice choice) {
    return selection!=null&&choice!=null&&choice.cardId()==0&&candidates.contains(choice.targetSeat());
  }

  /** Resolve actual current entities; a prepared ID is not permission to spend a moved card. */
  public Optional<List<CardInstance>> ownedCosts(SkillEngine api) {
    var state=api.gameState();
    if(actor>=state.players().size())return Optional.empty();
    var cards=new ArrayList<CardInstance>();
    for(long id:costs) {
      var zone=state.cards().locate(id);
      if(!CardZone.hand(actor).equals(zone)&&!CardZone.equipment(actor).equals(zone))return Optional.empty();
      var card=state.cards().cards(zone).stream().filter(c->c.instanceId()==id).findFirst();
      if(card.isEmpty())return Optional.empty();
      cards.add(card.get());
    }
    return Optional.of(List.copyOf(cards));
  }
}
