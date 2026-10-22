package dev.sgs.core.persistence;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import java.util.*;

/** Committed atomic discard and the exact weapon parent to resume after its listeners. */
final class DiscardCostFrames {
  private DiscardCostFrames(){}
  static void validate(ContinuationCheckpoint cp){
    var root=cp.continuation();var c=root.discardCost();
    CardType weapon=switch(root.step()){
      case GUANSHI_PAYMENT->CardType.GUANSHI;case CIXIONG_DISCARD->CardType.CIXIONG;
      case HANBING_FIRST_DISCARD,HANBING_SECOND_DISCARD->CardType.HANBING;case QILIN_DISCARD->CardType.QILIN_GONG;default->null;
    };
    boolean own=root.step()==AttackContinuation.AttackStep.GUANSHI_PAYMENT;
    if((weapon!=null)!=(c!=null)||c!=null&&(cp.version()<17||!own&&cp.version()<18))throw new IllegalArgumentException("discard version or return phase differs");
    if(c==null)return;var p=c.queue().pending();var r=root.pending();
    int actor=root.step()==AttackContinuation.AttackStep.CIXIONG_DISCARD?root.targetSeat():root.sourceSeat();int owner=own?root.sourceSeat():root.targetSeat();
    if(c.step()!=DiscardCostContinuation.Step.MOVEMENTS||p==null||r==null||c.actor()!=actor||c.owner()!=owner||c.cards().size()!=(own?2:1)
        ||root.useCost()!=null||root.ruleQuery()!=null||root.responseProgram()!=null||root.damageProgram()!=null||!root.damageFrames().isEmpty()
        ||own&&root.dodgesRemaining()!=0||root.weaponCost()!=0||r.type()!=PromptType.SKILL_INVOKE||r.id()!=p.id()||r.askerSeat()!=p.asker()||r.subjectSeat()!=p.subject()
        ||r.deadlineMillis()!=p.deadline()||!r.skillId().equals(p.skill())||!r.allowedTypes().isEmpty())throw new IllegalArgumentException("discard parent or question differs");
    var reserved=new HashSet<Long>(root.parents());reserved.add(root.cardId());reserved.addAll(root.extraCosts());if(!Collections.disjoint(reserved,c.cards()))throw new IllegalArgumentException("discard aliases attack");
    var discards=cp.state().zones().stream().filter(z->z.location().equals(CardZone.DISCARD)).flatMap(z->z.cards().stream()).toList();
    if(!discards.stream().map(StateCheckpoint.Card::id).toList().containsAll(c.cards()))throw new IllegalArgumentException("discard entities missing");
    if(cp.state().zones().stream().filter(z->z.location().equals(CardZone.equipment(root.sourceSeat()))).flatMap(z->z.cards().stream()).noneMatch(card->card.type()==weapon&&!c.cards().contains(card.id())))throw new IllegalArgumentException("original weapon missing from discard parent");
    if(!own&&c.queue().movements().size()!=1)throw new IllegalArgumentException("single weapon discard split into multiple events");
    for(var program:c.queue().movements()){var e=program.event();
      if(e.actor()!=actor||e.reason()!=CardMove.Reason.DISCARD||!e.to().equals(CardZone.DISCARD)||e.from().seat()!=owner
          ||!Set.of(CardZone.Kind.HAND,CardZone.Kind.EQUIPMENT).contains(e.from().kind())||!c.cards().containsAll(e.cards())
          ||e.sequence()>cp.state().movementSequence()||e.turn()!=cp.state().turn().turnNumber()||e.currentSeat()!=cp.state().turn().currentSeat())throw new IllegalArgumentException("discard event differs");
      if(!own&&!e.cards().equals(c.cards())||weapon==CardType.CIXIONG&&e.from().kind()!=CardZone.Kind.HAND
          ||weapon==CardType.QILIN_GONG&&(e.from().kind()!=CardZone.Kind.EQUIPMENT||discards.stream().filter(card->c.cards().contains(card.id())).anyMatch(card->!Set.of(CardCategory.HORSE_PLUS,CardCategory.HORSE_MINUS).contains(card.type().category()))))throw new IllegalArgumentException("weapon discarded from wrong source or category");
    }
  }
}
