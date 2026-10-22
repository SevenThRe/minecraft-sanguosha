package dev.sgs.core.persistence;

import dev.sgs.core.engine.*;
import java.util.*;

/** Original group targets remain immutable even after one current target is redirected. */
final class TargetRedirectFrames {
  private TargetRedirectFrames(){}
  static void validate(ContinuationCheckpoint cp){
    var root=cp.continuation();var c=root.redirect();boolean selecting=root.step()==AttackContinuation.AttackStep.TARGET_REDIRECT;
    if(cp.version()<21&&(c!=null||selecting)||selecting&&c==null)throw new IllegalArgumentException("redirect requires v21 child");
    if(c==null)return;
    if(c.source()!=root.sourceSeat()||!c.originalTargets().equals(root.targets())||root.targets().get(root.targetCursor())!=c.owner()
        ||c.source()>=cp.state().players().size()||c.owner()>=cp.state().players().size()
        ||c.targets().stream().anyMatch(v->v>=cp.state().players().size()))
      throw new IllegalArgumentException("redirect caller differs");
    var reserved=new HashSet<Long>(root.parents());reserved.add(root.cardId());reserved.addAll(root.extraCosts());
    if(!Collections.disjoint(reserved,c.costs()))throw new IllegalArgumentException("redirect fee aliases attack");
    var known=cp.state().zones().stream().flatMap(z->z.cards().stream()).map(StateCheckpoint.Card::id).toList();
    if(!known.containsAll(c.costs()))throw new IllegalArgumentException("unknown redirect fee");
    if(c.step()==TargetRedirectContinuation.Step.DONE){
      if(selecting||root.targetSeat()!=(c.activated()?c.chosenTarget():c.owner()))throw new IllegalArgumentException("redirect return target differs");
      return;
    }
    var p=c.pending();var outer=root.pending();
    if(!selecting||root.targetSeat()!=c.owner()||p==null||outer==null||p.id()!=outer.id()||p.type()!=outer.type()||p.askerSeat()!=outer.askerSeat()
        ||p.subjectSeat()!=outer.subjectSeat()||p.deadlineMillis()!=outer.deadlineMillis()||!p.skillId().equals(outer.skillId())
        ||p.stateRevision()!=c.revision()||!p.allowedTypes().isEmpty()||!outer.allowedTypes().isEmpty()
        ||root.useCost()!=null||root.discardCost()!=null||root.ruleQuery()!=null||root.responseProgram()!=null||root.damageProgram()!=null||!root.damageFrames().isEmpty()
        ||!root.providers().isEmpty()||root.providerCursor()!=0||root.dodgeProhibited())throw new IllegalArgumentException("redirect pending caller differs");
    if(c.step()==TargetRedirectContinuation.Step.SELECT){
      if(p.type()!=PromptType.LIULI_TARGET||p.askerSeat()!=c.owner()||p.subjectSeat()!=c.source()||!p.skillId().equals(c.skill())||c.targets().isEmpty())throw new IllegalArgumentException("redirect choice differs");
    }else{
      var payment=c.payment();var q=payment.queue().pending();
      if(payment.step()!=DiscardCostContinuation.Step.MOVEMENTS||payment.actor()!=c.owner()||payment.owner()!=c.owner()
          ||!payment.cards().equals(List.of(c.chosenCost()))||q==null||p.id()!=q.id()||p.type()!=PromptType.SKILL_INVOKE||p.askerSeat()!=q.asker()
          ||p.subjectSeat()!=q.subject()||p.deadlineMillis()!=q.deadline()||!p.skillId().equals(q.skill()))throw new IllegalArgumentException("redirect payment question differs");
    }
  }
}
