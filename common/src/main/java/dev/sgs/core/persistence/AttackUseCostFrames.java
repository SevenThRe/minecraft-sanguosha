package dev.sgs.core.persistence;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import java.util.*;

/** Shared use-cost linkage for an initial use or a pending Qinglong follow-up. */
final class AttackUseCostFrames {
  private AttackUseCostFrames(){}
  static void validate(ContinuationCheckpoint cp){
    var f=cp.continuation();var use=f.useCost();boolean follow=f.step()==AttackContinuation.AttackStep.QINGLONG_COST;
    if((f.step()==AttackContinuation.AttackStep.USE_COST||follow)!=(use!=null)||use!=null&&(cp.version()<15||follow&&cp.version()<19))throw new IllegalArgumentException("invalid attack use-cost phase");
    if(use==null)return;var c=use.program();var p=c.pending();var root=f.pending();var state=cp.state();
    if(f.sourceSeat()<0||f.sourceSeat()>=state.players().size()||f.targetSeat()<0||f.targetSeat()>=state.players().size())throw new IllegalArgumentException("use-cost parent seat outside match");
    if(c.step()!=CardUseCostContinuation.Step.MOVEMENTS||p==null||root==null||state.turn().shaPlayed()!=use.shaBefore()||state.turn().jiuBoost()!=use.wineBefore()
        ||root.id()!=p.id()||root.type()!=p.type()||root.askerSeat()!=p.asker()||root.subjectSeat()!=p.subject()||root.deadlineMillis()!=p.deadline()||!root.skillId().equals(p.skill())||!root.allowedTypes().isEmpty()
        ||f.ruleQuery()!=null||f.responseProgram()!=null||f.damageProgram()!=null||f.discardCost()!=null||!f.damageFrames().isEmpty())throw new IllegalArgumentException("attack cost context or request differs");
    var expected=new HashSet<>(c.cards());
    if(!follow){
      if(c.actor()!=f.sourceSeat()||c.type()!=f.effectiveType()||c.reason()!=CardMove.Reason.USE||c.cards().getFirst()!=f.cardId()||!c.cards().subList(1,c.cards().size()).equals(f.extraCosts())||f.baseDamage()!=1||!f.parents().isEmpty())throw new IllegalArgumentException("ordinary attack cost differs");
    }else{
      if(!Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA).contains(c.type())||f.dodgesRemaining()!=0||f.dodgeProhibited()||use.shaBefore()<1)throw new IllegalArgumentException("Qinglong cost before completed dodge");
      var reserved=new HashSet<>(f.parents());reserved.add(f.cardId());reserved.addAll(f.extraCosts());if(!Collections.disjoint(reserved,c.cards()))throw new IllegalArgumentException("Qinglong cost aliases parent");expected.addAll(reserved);
      if(state.zones().stream().filter(z->z.location().equals(CardZone.equipment(f.sourceSeat()))).flatMap(z->z.cards().stream()).noneMatch(card->card.type()==CardType.QINGLONG&&!c.cards().contains(card.id())))throw new IllegalArgumentException("Qinglong weapon missing");
      if(c.reason()==CardMove.Reason.USE){
        if(c.actor()!=f.sourceSeat()||c.cards().size()!=1||!f.providers().isEmpty()||f.providerCursor()!=0)throw new IllegalArgumentException("own Qinglong cost differs");
      }else{
        int cursor=f.providerCursor();if(cursor<0||cursor>=f.providers().size()||f.providers().get(cursor)!=c.actor()||c.actor()==f.sourceSeat()||c.cards().size()>2)throw new IllegalArgumentException("provided Qinglong cost differs");
        var lord=state.players().get(f.sourceSeat());if(lord.identity()!=dev.sgs.core.Identity.LORD||!lord.hero().skillIds().contains("jijiang")||c.actor()>=state.players().size()||state.players().get(c.actor()).hero().kingdom()!=dev.sgs.core.Kingdom.SHU)throw new IllegalArgumentException("Qinglong provider role differs");
      }
    }
    var paid=state.zones().stream().filter(z->z.location().equals(CardZone.PROCESSING)).flatMap(z->z.cards().stream()).map(StateCheckpoint.Card::id).collect(java.util.stream.Collectors.toSet());
    if(!paid.equals(expected))throw new IllegalArgumentException("attack cost entities differ");
    if(!c.transformSkill().isEmpty()&&cp.bindings().stream().noneMatch(b->b.startsWith("L/CARD_TRANSFORM/"+c.transformSkill()+"/"+c.actor()+"/")))throw new IllegalArgumentException("transform receipt binding absent");
    for(var move:c.movements()){
      var e=move.event();if(e.sequence()>state.movementSequence()||e.actor()!=c.actor()||e.reason()!=c.reason()||!e.to().equals(CardZone.PROCESSING)||e.from().seat()!=c.actor()||!c.cards().containsAll(e.cards())||e.turn()!=state.turn().turnNumber()||e.currentSeat()!=state.turn().currentSeat())throw new IllegalArgumentException("attack cost event differs");
    }
  }
}
