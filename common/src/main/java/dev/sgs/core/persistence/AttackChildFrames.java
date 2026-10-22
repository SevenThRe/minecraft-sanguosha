package dev.sgs.core.persistence;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import java.util.*;

/** Structural contracts for typed attack children; engine restore checks the live rule context. */
final class AttackChildFrames {
  private AttackChildFrames(){}
  static boolean same(AttackContinuation.PendingSnapshot root,AttackContinuation.PendingSnapshot child){return root!=null&&child!=null&&root.id()==child.id()&&root.type()==child.type()&&root.askerSeat()==child.askerSeat()&&root.subjectSeat()==child.subjectSeat()&&root.allowedTypes().equals(child.allowedTypes())&&root.deadlineMillis()==child.deadlineMillis()&&root.skillId().equals(child.skillId());}
  static void validate(ContinuationCheckpoint cp){
    var root=cp.continuation();var response=root.responseProgram();var damage=root.damageProgram();
    int count=(response==null?0:1)+(damage==null?0:1)+(root.ruleQuery()==null?0:1);
    if(count>1||cp.version()<14&&(response!=null||damage!=null||root.step()==AttackContinuation.AttackStep.RESPONSE_CHILD))throw new IllegalArgumentException("invalid attack child version or overlap");
    if(cp.version()>=14&&((root.step()==AttackContinuation.AttackStep.RESPONSE_CHILD)!=(response!=null)||(root.step()==AttackContinuation.AttackStep.DAMAGE)!=(damage!=null)))throw new IllegalArgumentException("missing typed attack child");
    if(response!=null)response(cp,response);
    if(damage!=null){
      DamageProgramContinuation.validate(damage);
      if(damage.awaitRescueCosts()!=(cp.version()>=20))throw new IllegalArgumentException("attack rescue capability version differs");
      var retained=new HashSet<Long>(root.parents());retained.add(root.cardId());retained.addAll(root.extraCosts());RescueCostFrames.validate(cp.state(),cp.bindings(),damage,retained,cp.promptSequence());
      if(!damage.ordered()||!root.damageFrames().equals(damage.damage().damageFrames())||root.cardId()!=damage.damage().cardId()||root.effectiveType()!=damage.damage().effectiveType())throw new IllegalArgumentException("attack damage projection differs");
      if(damage.parents().isEmpty()&&(damage.damage().sourceSeat()!=root.sourceSeat()||damage.damage().targetSeat()!=root.targetSeat()||damage.damage().baseDamage()!=root.baseDamage()||damage.damage().cardless()))throw new IllegalArgumentException("attack damage root differs");
      var child=damage.pending()!=null?new AttackContinuation.PendingSnapshot(damage.pending().id(),damage.pending().type(),damage.pending().asker(),damage.pending().subject(),List.of(),damage.pending().deadline(),damage.pending().revision(),damage.pending().skill()):damage.reaction()!=null?damage.reaction().pending():damage.damage().pending();
      if(!same(root.pending(),child))throw new IllegalArgumentException("attack damage request projection differs");
    }
  }
  private static void response(ContinuationCheckpoint cp,TrickResponseContinuation.Snapshot r){
    var root=cp.continuation();int n=cp.state().players().size();
    if((r.step()==TrickResponseContinuation.Step.COST)!=(r.cost()!=null)||r.ownCardPaid()||cp.version()<16&&r.cost()!=null)throw new IllegalArgumentException("invalid response cost phase or version");
    if(r.actor()<0||r.actor()>=n||!cp.state().players().get(r.actor()).alive()||cp.state().players().get(r.actor()).hp()<1)throw new IllegalArgumentException("response actor is not alive");
    if(r.actor()!=root.targetSeat()||r.subject()!=root.sourceSeat()||r.type()!=PromptType.DODGE_SHA||r.revision()<1||r.revision()==Long.MAX_VALUE||r.success()||r.supplied()||r.pending()==null||!root.damageFrames().isEmpty()
        ||!r.providers().equals(root.providers())||r.cursor()!=root.providerCursor()||!same(root.pending(),r.pending())||!Set.of(TrickResponseContinuation.Step.LORD,TrickResponseContinuation.Step.PROVIDER_BAGUA,TrickResponseContinuation.Step.PROVIDER_CARD,TrickResponseContinuation.Step.OWN_BAGUA,TrickResponseContinuation.Step.OWN_CARD,TrickResponseContinuation.Step.OWN_JUDGMENT,TrickResponseContinuation.Step.PROVIDER_JUDGMENT,TrickResponseContinuation.Step.OWN_TIANDU,TrickResponseContinuation.Step.PROVIDER_TIANDU,TrickResponseContinuation.Step.COST).contains(r.step()))throw new IllegalArgumentException("invalid attack response child");
    if(r.cost()!=null)responseCost(cp,r);
    pending(cp,r.pending());if(r.own()!=null){pending(cp,r.own());if(r.own().askerSeat()!=r.actor()||r.own().subjectSeat()!=r.subject()||r.own().type()!=PromptType.DODGE_SHA||!r.own().allowedTypes().equals(List.of(CardType.SHAN))||!r.own().skillId().isEmpty())throw new IllegalArgumentException("invalid fallback response token");}
    boolean judged=Set.of(TrickResponseContinuation.Step.OWN_JUDGMENT,TrickResponseContinuation.Step.PROVIDER_JUDGMENT).contains(r.step());
    boolean legacyJudge=Set.of(TrickResponseContinuation.Step.OWN_TIANDU,TrickResponseContinuation.Step.PROVIDER_TIANDU).contains(r.step());
    if(judged!=(r.judgment()!=null)||(judged||legacyJudge)!=(r.judgmentCard()>0))throw new IllegalArgumentException("missing response judgment child");
    if(r.judgment()!=null){var j=r.judgment();var p=r.pending();
      if(j.owner()<0||j.owner()>=n||j.cause()<1||j.revealed()<1||j.effective()!=r.judgmentCard()||j.modifiers().size()>n||new HashSet<>(j.modifiers()).size()!=j.modifiers().size()||j.modifiers().stream().anyMatch(v->v<0||v>=n)||j.cursor()<0||j.cursor()>j.modifiers().size()||!Objects.equals(j.pending(),p)||!Set.of(JudgmentContinuation.Step.MODIFY,JudgmentContinuation.Step.TIANDU).contains(j.step()))throw new IllegalArgumentException("invalid attack response judgment");
      if(j.step()==JudgmentContinuation.Step.MODIFY&&(j.cursor()==j.modifiers().size()||p.askerSeat()!=j.modifiers().get(j.cursor())||p.type()!=PromptType.GUI_CAI_REPLACE||!p.skillId().equals("guicai"))||j.step()==JudgmentContinuation.Step.TIANDU&&(j.cursor()!=j.modifiers().size()||p.askerSeat()!=j.owner()||p.type()!=PromptType.SKILL_INVOKE||!p.skillId().equals("tiandu")))throw new IllegalArgumentException("response judgment cursor differs");
    }
  }
  private static void responseCost(ContinuationCheckpoint cp,TrickResponseContinuation.Snapshot r){
    var c=r.cost();var p=c.pending();var q=r.pending();var root=cp.continuation();
    if(c.step()!=CardUseCostContinuation.Step.MOVEMENTS||c.type()!=CardType.SHAN||c.reason()!=CardMove.Reason.RESPONSE
        ||c.actor()>=cp.state().players().size()||c.actor()!=r.actor()&&(r.cursor()>=r.providers().size()||c.actor()!=r.providers().get(r.cursor()))
        ||c.actor()==r.actor()&&r.own()==null||p==null||q.type()!=PromptType.SKILL_INVOKE||q.stateRevision()!=r.revision()
        ||q.id()!=p.id()||q.askerSeat()!=p.asker()||q.subjectSeat()!=p.subject()||q.deadlineMillis()!=p.deadline()||!q.skillId().equals(p.skill())||!q.allowedTypes().isEmpty()
        ||r.judgment()!=null||r.judgmentCard()!=0)throw new IllegalArgumentException("response payment or question differs");
    var reserved=new HashSet<Long>(root.parents());reserved.add(root.cardId());reserved.addAll(root.extraCosts());
    var fees=new HashSet<Long>(c.cards());if(!Collections.disjoint(reserved,fees))throw new IllegalArgumentException("aliased response payment");
    var processing=cp.state().zones().stream().filter(z->z.location().equals(CardZone.PROCESSING)).flatMap(z->z.cards().stream()).map(StateCheckpoint.Card::id).toList();
    if(!processing.containsAll(fees)||processing.stream().anyMatch(id->!reserved.contains(id)&&!fees.contains(id)))throw new IllegalArgumentException("response payment not in processing");
    for(var move:c.movements()){
      var e=move.event();
      if(e.actor()!=c.actor()||e.reason()!=CardMove.Reason.RESPONSE||!e.to().equals(CardZone.PROCESSING)||e.from().seat()!=c.actor()
          ||e.sequence()>cp.state().movementSequence()||!fees.containsAll(e.cards())||e.turn()!=cp.state().turn().turnNumber()||e.currentSeat()!=cp.state().turn().currentSeat())throw new IllegalArgumentException("response payment event differs");
    }
  }
  private static void pending(ContinuationCheckpoint cp,AttackContinuation.PendingSnapshot p){int n=cp.state().players().size();if(p.id()<1||p.id()>cp.promptSequence()||p.askerSeat()<0||p.askerSeat()>=n||p.subjectSeat()<0||p.subjectSeat()>=n||p.deadlineMillis()<0||p.deadlineMillis()-cp.capturedAtMillis()>cp.responseTimeoutSeconds()*1000L||p.stateRevision()<0||p.stateRevision()==Long.MAX_VALUE||p.allowedTypes().size()>8)throw new IllegalArgumentException("invalid child response token");}
}
