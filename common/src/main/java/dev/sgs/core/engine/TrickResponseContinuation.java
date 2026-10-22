package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** Shared attack/AOE/Duel response unit: providers, optional armor judgment and real hand costs.
 * The historical class name and snapshot layout remain for existing trick archives.
 */
public final class TrickResponseContinuation {
  public enum Step { START, LORD, PROVIDER, PROVIDER_BAGUA, PROVIDER_CARD, OWN_BAGUA, OWN_CARD, DONE, PROVIDER_TIANDU, OWN_TIANDU, PROVIDER_JUDGMENT, OWN_JUDGMENT, COST }
  public record Snapshot(int actor,int subject,PromptType type,Step step,List<Integer> providers,int cursor,
      AttackContinuation.PendingSnapshot own,AttackContinuation.PendingSnapshot pending,long revision,boolean success,boolean supplied,long judgmentCard,JudgmentContinuation.Snapshot judgment,CardUseCostContinuation.Snapshot cost,boolean ownCardPaid){
    public Snapshot(int actor,int subject,PromptType type,Step step,List<Integer> providers,int cursor,AttackContinuation.PendingSnapshot own,AttackContinuation.PendingSnapshot pending,long revision,boolean success,boolean supplied,long judgmentCard,JudgmentContinuation.Snapshot judgment){this(actor,subject,type,step,providers,cursor,own,pending,revision,success,supplied,judgmentCard,judgment,null,false);}
    public Snapshot{providers=List.copyOf(providers);}
    public Snapshot(int actor,int subject,PromptType type,Step step,List<Integer> providers,int cursor,AttackContinuation.PendingSnapshot own,AttackContinuation.PendingSnapshot pending,long revision,boolean success,boolean supplied){this(actor,subject,type,step,providers,cursor,own,pending,revision,success,supplied,0);}
    public Snapshot(int actor,int subject,PromptType type,Step step,List<Integer> providers,int cursor,AttackContinuation.PendingSnapshot own,AttackContinuation.PendingSnapshot pending,long revision,boolean success,boolean supplied,long judgmentCard){this(actor,subject,type,step,providers,cursor,own,pending,revision,success,supplied,judgmentCard,null);}
  }
  private final int actor,subject;private final PromptType type;private final boolean attack,awaitCosts;
  private Step step=Step.START;private List<Integer> providers=List.of();private int cursor;private long revision;
  private AttackContinuation.PendingSnapshot own,pending;private boolean success,supplied;
  private long judgmentCard;
  private JudgmentContinuation judgment;
  private CardUseCostContinuation cost;private boolean ownCardPaid;
  TrickResponseContinuation(int actor,int subject,PromptType type){this(actor,subject,type,false);}
  private TrickResponseContinuation(int actor,int subject,PromptType type,boolean attack){this(actor,subject,type,attack,false);}
  private TrickResponseContinuation(int actor,int subject,PromptType type,boolean attack,boolean awaitCosts){this.actor=actor;this.subject=subject;this.type=type;this.attack=attack;this.awaitCosts=awaitCosts;}
  static TrickResponseContinuation forAttack(int target,int source){return new TrickResponseContinuation(target,source,PromptType.DODGE_SHA,true);}
  static TrickResponseContinuation forSkillDuel(int target,int source){return new TrickResponseContinuation(target,source,PromptType.DUEL_SHA,false,true);}
  static TrickResponseContinuation forAttack(int target,int source,boolean awaitCosts){return new TrickResponseContinuation(target,source,PromptType.DODGE_SHA,true,awaitCosts);}
  TrickResponseContinuation(Snapshot s,long captured,long now){this(s,captured,now,false);}
  TrickResponseContinuation(Snapshot s,long captured,long now,boolean attack){this(s,captured,now,attack,false);}
  TrickResponseContinuation(Snapshot s,long captured,long now,boolean attack,boolean awaitCosts){actor=s.actor();subject=s.subject();type=s.type();this.attack=attack;this.awaitCosts=awaitCosts;step=s.step();providers=s.providers();cursor=s.cursor();revision=s.revision();success=s.success();supplied=s.supplied();own=shift(s.own(),captured,now);pending=shift(s.pending(),captured,now);judgmentCard=s.judgmentCard();if(s.judgment()!=null)judgment=new JudgmentContinuation(s.judgment(),captured,now);if(s.cost()!=null)cost=new CardUseCostContinuation(s.cost(),captured,now);ownCardPaid=s.ownCardPaid();if(!awaitCosts&&(cost!=null||ownCardPaid))throw new IllegalArgumentException("response costs require an opted-in parent");}
  private static AttackContinuation.PendingSnapshot shift(AttackContinuation.PendingSnapshot p,long captured,long now){return p==null?null:new AttackContinuation.PendingSnapshot(p.id(),p.type(),p.askerSeat(),p.subjectSeat(),p.allowedTypes(),DecisionBoundary.restoredDeadline(p.deadlineMillis(),captured,now),p.stateRevision(),p.skillId());}
  Snapshot snapshot(){return new Snapshot(actor,subject,type,step,providers,cursor,own,pending,revision,success,supplied,judgmentCard,judgment==null?null:judgment.snapshot(),cost==null?null:cost.snapshot(),ownCardPaid);}
  boolean ownCardPaid(){return ownCardPaid;}
  void validateCost(SgsGameEngine e){if(!awaitCosts||cost==null||step!=Step.COST)throw new IllegalArgumentException("missing response cost");cost.validate(e);}
  boolean done(){return step==Step.DONE;}boolean success(){return success;}boolean supplied(){return supplied;}
  private boolean slash(){return type!=PromptType.DODGE_SHA;}
  private List<CardType> allowed(){return slash()?List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA):List.of(CardType.SHAN);}
  private void ask(SgsGameEngine e,PromptType type,int asker,int target,String skill,List<CardType> allowed){revision++;pending=new AttackContinuation.PendingSnapshot(e.nextPromptId(),type,asker,target,allowed,e.responseDeadlineMillis(),revision,skill);}
  private void makeOwn(SgsGameEngine e){if(own==null){revision++;own=new AttackContinuation.PendingSnapshot(e.nextPromptId(),type,actor,subject,allowed(),e.responseDeadlineMillis(),revision,"");}}
  private void ownCard(SgsGameEngine e){makeOwn(e);revision++;pending=new AttackContinuation.PendingSnapshot(own.id(),own.type(),own.askerSeat(),own.subjectSeat(),own.allowedTypes(),own.deadlineMillis(),revision,own.skillId());step=Step.OWN_CARD;}
  private void own(SgsGameEngine e){
    if(!slash() && (attack?e.continuationOwnBaguaAvailable(e.state().players().get(subject),e.state().players().get(actor)):bagua(e,actor))){step=Step.OWN_BAGUA;ask(e,PromptType.SKILL_INVOKE,actor,subject,"bagua",List.of());}else ownCard(e);
  }
  private boolean bagua(SgsGameEngine e,int seat){var armor=e.state().players().get(seat).equipment().armor();return armor!=null&&armor.type()==CardType.BAGUA;}
  void advance(SgsGameEngine e){
    if(pending!=null || done())return;
    for(int limit=0;limit<100;limit++){
      switch(step){
        case COST->{advanceCost(e);return;}
        case START->{
          // Legacy Duel allocates its own question before asking lord providers; preserve its ID and budget.
          if(type==PromptType.DUEL_SHA)makeOwn(e);
          providers=e.trickLordProviders(e.state().players().get(actor),slash());
          if(providers.isEmpty()){own(e);return;}
          step=Step.LORD;ask(e,PromptType.SKILL_INVOKE,actor,actor,slash()?"jijiang":"hujia",List.of());return;
        }
        case PROVIDER->{
          if(cursor>=providers.size()){own(e);return;}
          int helper=providers.get(cursor);if(!e.state().players().get(helper).alive()){cursor++;continue;}
          if(!slash()&&bagua(e,helper)){step=Step.PROVIDER_BAGUA;ask(e,PromptType.SKILL_INVOKE,helper,actor,"bagua",List.of());}
          else providerCard(e);return;
        }
        case DONE->{return;}
        default->throw new IllegalStateException("response step without a request");
      }
    }
    throw new IllegalStateException("provider loop failed to yield");
  }
  private void providerCard(SgsGameEngine e){step=Step.PROVIDER_CARD;ask(e,slash()?PromptType.JIJIANG_SHA:PromptType.HUJIA_SHAN,providers.get(cursor),actor,slash()?"jijiang":"hujia",allowed());}
  private void judge(SgsGameEngine e,boolean provided){
    int seat=provided?providers.get(cursor):actor;judgment=new JudgmentContinuation(seat,e.state().players().get(seat).equipment().armor().instanceId());step=provided?Step.PROVIDER_JUDGMENT:Step.OWN_JUDGMENT;advanceJudgment(e);
  }
  private void advanceJudgment(SgsGameEngine e){
    boolean provided=step==Step.PROVIDER_JUDGMENT;judgment.advance(e,revision);judgmentCard=judgment.snapshot().effective();pending=judgment.pending();
    if(judgment.done()){boolean red=judgment.red();judgment=null;judgmentCard=0;if(red){if(attack&&!provided)e.state().log("seat {}'s bagua armor judged red and dodged the sha",actor);success(e,provided);}else if(provided)providerCard(e);else ownCard(e);}
  }
  private void finishJudge(SgsGameEngine e,boolean provided,boolean accepted){
    int seat=provided?providers.get(cursor):actor;boolean red=e.trickCompleteBaguaJudgment(seat,judgmentCard,accepted);judgmentCard=0;
    if(red)success(e,provided);else if(provided)providerCard(e);else ownCard(e);
  }
  private void success(SgsGameEngine e,boolean provided){
    success=true;supplied=provided;step=Step.DONE;
    if(provided){String skill=slash()?"jijiang":"hujia";e.state().publishSkillActivation(actor,skill);e.state().log("seat {} used "+skill+": "+(slash()?"sha":"shan")+" supplied by seat {}",actor,providers.get(cursor));}
  }
  Optional<PromptRequest> prompt(SgsGameEngine e){if(judgment!=null&&pending!=null)return Optional.of(judgment.request(e));return pending==null?Optional.empty():Optional.of(new PromptRequest(pending.id(),pending.type(),e.state().players().get(pending.askerSeat()),e.state().players().get(pending.subjectSeat()),pending.allowedTypes(),List.of(),pending.deadlineMillis(),pending.skillId()));}
  boolean choose(SgsGameEngine e,Optional<SkillChoice> answer){if(judgment==null||!judgment.choose(e,answer))return false;pending=null;revision++;advanceJudgment(e);return true;}
  boolean invoke(SgsGameEngine e,boolean accepted){
    if(pending==null || pending.type()!=PromptType.SKILL_INVOKE)return false;
    boolean answer=accepted&&e.continuationNow()<=pending.deadlineMillis();pending=null;revision++;
    switch(step){
      case COST->{if(!cost.invoke(e,answer))throw new IllegalStateException("response cost rejected invocation");advanceCost(e);}
      case LORD->{if(answer)step=Step.PROVIDER;else own(e);}
      case PROVIDER_BAGUA->{if(answer)judge(e,true);else providerCard(e);}
      case OWN_BAGUA->{if(answer)judge(e,false);else ownCard(e);}
      case PROVIDER_TIANDU->finishJudge(e,true,answer);
      case OWN_TIANDU->finishJudge(e,false,answer);
      case PROVIDER_JUDGMENT,OWN_JUDGMENT->{if(!judgment.invoke(e,answer))throw new IllegalStateException("invalid judgment invoke");advanceJudgment(e);}
      default->throw new IllegalStateException("invalid response invoke step");
    }
    return true;
  }
  boolean answer(SgsGameEngine e,Optional<PlayDecision> decision){
    if(pending==null || pending.type()==PromptType.SKILL_INVOKE)return false;pending=null;revision++;
    if(step==Step.PROVIDER_CARD){
      if(decision.isPresent()){
        var helper=e.state().players().get(providers.get(cursor));
        if(awaitCosts){startCost(e,helper.seat(),decision.get());return true;}
        if(slash()){e.trickBeginResponse(helper,decision.get().card());success(e,true);e.state().cards().finish(decision.get().card(),actor);}
        else{e.continuationSpendResponse(helper,decision.get());success(e,true);}
      }else{cursor++;step=Step.PROVIDER;}
    }else if(step==Step.OWN_CARD){if(decision.isPresent()){if(awaitCosts){startCost(e,actor,decision.get());return true;}e.continuationSpendResponse(e.state().players().get(actor),decision.get());success(e,false);}else step=Step.DONE;}
    else throw new IllegalStateException("invalid response answer step");
    return true;
  }
  private void startCost(SgsGameEngine e,int supplier,PlayDecision decision){cost=CardUseCostContinuation.accepted(e,supplier,decision.card(),CardMove.Reason.RESPONSE);step=Step.COST;advanceCost(e);}
  private void advanceCost(SgsGameEngine e){
    cost.advance(e);
    if(cost.done()){
      var card=cost.card(e);int payer=cost.snapshot().actor();boolean provided=payer!=actor;cost=null;
      if(slash()&&provided){success(e,true);e.state().cards().finish(card,actor);}
      else{e.state().cards().finish(card,payer);ownCardPaid=!provided;success(e,provided);}
    }else{var p=cost.pending();if(p==null)throw new IllegalStateException("response cost failed to yield");revision++;pending=new AttackContinuation.PendingSnapshot(p.id(),p.type(),p.asker(),p.subject(),List.of(),p.deadline(),revision,p.skill());}
  }
}
