package dev.sgs.core.persistence;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import java.util.*;

/** One accepted rescue use, before recovery, shared by attack and trick damage roots. */
final class RescueCostFrames {
  private RescueCostFrames(){}
  static void validate(StateCheckpoint state,List<String> bindings,DamageProgramContinuation.Snapshot program,Set<Long> retained,long promptSequence){
    var saved=program.rescueCost();if(saved==null)return;var cost=saved.program();var leaf=program.damage();var frame=leaf.damageFrames().getFirst();int n=state.players().size();
    if(cost.actor()>=n||frame.targetSeat()<0||frame.targetSeat()>=n||!state.players().get(cost.actor()).alive()||!state.players().get(frame.targetSeat()).alive()
        ||state.players().get(frame.targetSeat()).hp()!=saved.hpBeforeRecovery()||state.turn().shaPlayed()!=saved.shaBefore()||state.turn().jiuBoost()!=saved.wineBefore()
        ||!Collections.disjoint(retained,cost.cards())||cost.cards().contains(leaf.cardId())||cost.pending().id()!=promptSequence)throw new IllegalArgumentException("rescue payment owner, recovery or entities differ");
    var ring=new ArrayList<Integer>();for(int i=0;i<n;i++){int seat=(state.turn().currentSeat()+i)%n;if(state.players().get(seat).alive())ring.add(seat);}
    if(!ring.equals(frame.rescueRing()))throw new IllegalArgumentException("accepted rescue ring differs");
    var paid=state.zones().stream().filter(z->z.location().equals(CardZone.PROCESSING)).flatMap(z->z.cards().stream()).map(StateCheckpoint.Card::id).toList();
    if(!paid.containsAll(cost.cards()))throw new IllegalArgumentException("rescue payment not committed");
    if(!cost.transformSkill().isEmpty()&&bindings.stream().noneMatch(b->b.startsWith("L/CARD_TRANSFORM/"+cost.transformSkill()+"/"+cost.actor()+"/")))throw new IllegalArgumentException("rescue transform receipt missing binding");
    if(cost.movements().size()!=1)throw new IllegalArgumentException("single rescue payment split events");
    for(var m:cost.movements()){var e=m.event();
      if(e.sequence()>state.movementSequence()||e.actor()!=cost.actor()||e.reason()!=CardMove.Reason.USE||!e.to().equals(CardZone.PROCESSING)||e.from().seat()!=cost.actor()
          ||!Set.of(CardZone.Kind.HAND,CardZone.Kind.EQUIPMENT).contains(e.from().kind())||!e.cards().equals(cost.cards())||e.turn()!=state.turn().turnNumber()||e.currentSeat()!=state.turn().currentSeat())throw new IllegalArgumentException("rescue payment event differs");
    }
  }
}
