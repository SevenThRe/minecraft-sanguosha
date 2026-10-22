package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** Targeted attack supply: optional lord providers, then an optional own cost. Does not clean costs. */
public final class AttackSupplyContinuation {
  public enum Step { START, INVOKE, PROVIDER_NEXT, PROVIDER, OWN, COMMITTED, DECLINED }
  public enum Conversion { PHYSICAL, WUSHENG, LONGDAN, ZHANGBA }
  public record Cost(long id,CardZone from){}
  public record Receipt(int supplier,CardType type,List<Cost> costs,Conversion conversion,long weapon,long sequence){
    public Receipt{costs=List.copyOf(costs);}
  }
  public record Snapshot(int user,int target,PromptType ownType,boolean offerProviders,boolean allowOwn,Step step,List<Integer> providers,int cursor,AttackContinuation.PendingSnapshot pending,long revision,Receipt receipt){
    public Snapshot{providers=List.copyOf(providers);}
  }
  private final int user,target;private final PromptType ownType;private final boolean offerProviders,allowOwn;
  private Step step=Step.START;private List<Integer> providers=List.of();private int cursor;private long revision;private AttackContinuation.PendingSnapshot pending;private Receipt receipt;
  AttackSupplyContinuation(int user,int target,PromptType ownType,boolean offer,boolean allowOwn){this.user=user;this.target=target;this.ownType=ownType;offerProviders=offer;this.allowOwn=allowOwn;validate(snapshot());}
  AttackSupplyContinuation(Snapshot s,long captured,long now){validate(s);user=s.user();target=s.target();ownType=s.ownType();offerProviders=s.offerProviders();allowOwn=s.allowOwn();step=s.step();providers=s.providers();cursor=s.cursor();revision=s.revision();receipt=s.receipt();var p=s.pending();pending=p==null?null:new AttackContinuation.PendingSnapshot(p.id(),p.type(),p.askerSeat(),p.subjectSeat(),p.allowedTypes(),DecisionBoundary.restoredDeadline(p.deadlineMillis(),captured,now),p.stateRevision(),p.skillId());}
  public Snapshot snapshot(){return new Snapshot(user,target,ownType,offerProviders,allowOwn,step,providers,cursor,pending,revision,receipt);}
  public static void validate(Snapshot s){
    if(s.user()<0||s.user()>7||s.target()<0||s.target()>7||s.user()==s.target()||s.ownType()==null||!Set.of(PromptType.JIEDAO_RESPONSE,PromptType.QINGLONG_SHA,PromptType.JIJIANG_SHA).contains(s.ownType())||s.step()==null||s.revision()<0||s.revision()==Long.MAX_VALUE||s.providers().size()>7||s.cursor()<0||s.cursor()>s.providers().size()||new HashSet<>(s.providers()).size()!=s.providers().size()||s.providers().stream().anyMatch(p->p<0||p>7||p==s.user())
        ||s.ownType()==PromptType.JIJIANG_SHA&&(s.offerProviders()||s.allowOwn()))throw new IllegalArgumentException("invalid attack supply header");
    boolean waiting=Set.of(Step.INVOKE,Step.PROVIDER,Step.OWN).contains(s.step());
    if(waiting!=(s.pending()!=null)||(s.step()==Step.COMMITTED)!=(s.receipt()!=null)||s.step()==Step.OWN&&!s.allowOwn()||s.step()!=Step.START&&s.revision()<1||s.step()==Step.START&&(s.revision()!=0||!s.providers().isEmpty()||s.cursor()!=0))throw new IllegalArgumentException("invalid attack supply phase");
    if(waiting){var p=s.pending();boolean invoke=s.step()==Step.INVOKE,provider=s.step()==Step.PROVIDER;
      if(s.revision()<1||p.id()<1||p.deadlineMillis()<0||p.stateRevision()!=s.revision()||p.type()!=(invoke?PromptType.SKILL_INVOKE:provider?PromptType.JIJIANG_SHA:s.ownType())||p.askerSeat()!=(provider?(s.cursor()<s.providers().size()?s.providers().get(s.cursor()):-1):s.user())||p.subjectSeat()!=(s.step()==Step.OWN?s.target():s.user())||!p.skillId().equals(s.step()==Step.OWN?"":"jijiang")||!p.allowedTypes().equals(invoke?List.of():List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA))||invoke&&(!s.offerProviders()||s.cursor()!=0||s.providers().isEmpty()))throw new IllegalArgumentException("invalid attack supply request");
    }
    if(s.receipt()!=null){var r=s.receipt();
      if(r.supplier()<0||r.supplier()>7||r.supplier()==s.user()&&!s.allowOwn()||r.type()==null||!Set.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA).contains(r.type())||r.conversion()==null||r.sequence()<1||r.weapon()<0||r.costs().size()!=(r.conversion()==Conversion.ZHANGBA?2:1)||r.conversion()==Conversion.ZHANGBA&&(r.type()!=CardType.SHA||r.weapon()<1)||r.conversion()!=Conversion.ZHANGBA&&r.weapon()!=0||r.conversion()!=Conversion.PHYSICAL&&r.type()!=CardType.SHA||r.supplier()!=s.user()&&(s.cursor()>=s.providers().size()||s.providers().get(s.cursor())!=r.supplier()))throw new IllegalArgumentException("invalid attack supply receipt");
      var ids=new HashSet<Long>();for(var cost:r.costs())if(cost.id()<1||!ids.add(cost.id())||cost.from()==null||cost.from().seat()!=r.supplier()||!Set.of(CardZone.Kind.HAND,CardZone.Kind.EQUIPMENT).contains(cost.from().kind())||r.conversion()!=Conversion.WUSHENG&&cost.from().kind()!=CardZone.Kind.HAND||cost.id()==r.weapon())throw new IllegalArgumentException("invalid attack supply cost origin");
    }
  }
  void validateAgainst(SgsGameEngine e){
    validate(snapshot());int n=e.state().players().size();if(user>=n||target>=n||providers.stream().anyMatch(p->p>=n))throw new IllegalArgumentException("supply actors not in game");
    if(step==Step.START)return;
    if(step!=Step.COMMITTED&&!providers.equals(e.trickLordProviders(e.state().players().get(user),true)))throw new IllegalArgumentException("supply provider order changed");
    if(pending!=null&&(!e.state().players().get(user).alive()||!e.state().players().get(target).alive()||!e.state().players().get(pending.askerSeat()).alive()))throw new IllegalArgumentException("supply decision actor unavailable");
    if(!providers.isEmpty()){
      var lord=e.state().players().get(user);if(lord.identity()!=dev.sgs.core.Identity.LORD||!lord.hero().skillIds().contains("jijiang"))throw new IllegalArgumentException("supply provider binding missing");
      int previous=-1;for(int seat:providers){int order=Math.floorMod(seat-e.state().currentSeat(),n);if(order<=previous||e.state().players().get(seat).hero().kingdom()!=dev.sgs.core.Kingdom.SHU)throw new IllegalArgumentException("supply provider plan differs");previous=order;}
    }
    if(receipt!=null){var r=receipt;if(r.sequence()>e.state().cards().checkpointSequence())throw new IllegalArgumentException("future supply receipt");var who=e.state().players().get(r.supplier());var cards=r.costs().stream().map(c->physical(e,c.id())).toList();
      if(r.conversion()==Conversion.PHYSICAL&&cards.getFirst().type()!=r.type()||r.conversion()==Conversion.WUSHENG&&(!who.hero().skillIds().contains("wusheng")||!cards.getFirst().isRed())||r.conversion()==Conversion.LONGDAN&&(!who.hero().skillIds().contains("longdan")||cards.getFirst().type()!=CardType.SHAN)||r.conversion()==Conversion.ZHANGBA&&physical(e,r.weapon()).type()!=CardType.ZHANGBA)throw new IllegalArgumentException("supply cost capability differs");
    }
  }
  boolean done(){return step==Step.COMMITTED||step==Step.DECLINED;}boolean supplied(){return receipt!=null&&receipt.supplier()!=user;}
  Optional<PromptRequest> prompt(SgsGameEngine e){return pending==null?Optional.empty():Optional.of(new PromptRequest(pending.id(),pending.type(),e.state().players().get(pending.askerSeat()),e.state().players().get(pending.subjectSeat()),pending.allowedTypes(),List.of(),pending.deadlineMillis(),pending.skillId()));}
  private void ask(SgsGameEngine e,Step phase){step=phase;revision++;boolean provider=phase==Step.PROVIDER,invoke=phase==Step.INVOKE;pending=new AttackContinuation.PendingSnapshot(e.nextPromptId(),invoke?PromptType.SKILL_INVOKE:provider?PromptType.JIJIANG_SHA:ownType,provider?providers.get(cursor):user,phase==Step.OWN?target:user,invoke?List.of():List.of(CardType.SHA,CardType.HUOSHA,CardType.LEISHA),e.responseDeadlineMillis(),revision,phase==Step.OWN?"":"jijiang");}
  private void own(SgsGameEngine e){if(allowOwn)ask(e,Step.OWN);else{step=Step.DECLINED;revision++;}}
  void advance(SgsGameEngine e){
    if(pending!=null||done())return;
    if(!e.state().players().get(user).alive()||!e.state().players().get(target).alive()){step=Step.DECLINED;revision++;return;}
    if(step==Step.START){providers=e.trickLordProviders(e.state().players().get(user),true);if(!providers.isEmpty()&&offerProviders){ask(e,Step.INVOKE);return;}step=Step.PROVIDER_NEXT;}
    while(cursor<providers.size()){if(e.state().players().get(providers.get(cursor)).alive()){ask(e,Step.PROVIDER);return;}cursor++;}
    own(e);
  }
  boolean invoke(SgsGameEngine e,UUID actor,long token,long expected,boolean accepted){
    if(step!=Step.INVOKE||pending==null||!DecisionBoundary.of(prompt(e).orElseThrow(),revision).matches(actor,token,expected))return false;
    boolean yes=accepted&&!DecisionBoundary.of(prompt(e).orElseThrow(),revision).expired(e.continuationNow())&&e.state().players().get(user).alive();pending=null;revision++;
    if(yes)step=Step.PROVIDER_NEXT;else own(e);return true;
  }
  boolean answer(SgsGameEngine e,UUID actor,long token,long expected,Optional<PlayDecision> decision){
    if(pending==null||!Set.of(Step.OWN,Step.PROVIDER).contains(step)||!DecisionBoundary.of(prompt(e).orElseThrow(),revision).matches(actor,token,expected))return false;
    if(DecisionBoundary.of(prompt(e).orElseThrow(),revision).expired(e.continuationNow()))decision=Optional.empty();
    if(decision.isPresent()){var canonical=e.validateAttackSupplyDecision(prompt(e).orElseThrow(),decision.get(),target);if(canonical.isEmpty())return false;decision=canonical;}
    boolean provided=step==Step.PROVIDER;int supplier=pending.askerSeat();pending=null;revision++;
    if(decision.isEmpty()){if(provided){cursor++;step=Step.PROVIDER_NEXT;}else step=Step.DECLINED;return true;}
    var card=decision.get().card();var who=e.state().players().get(supplier);var costs=card.sourceCards().stream().map(c->new Cost(c.instanceId(),e.state().cards().locate(c.instanceId()))).toList();
    Conversion conversion=card.sourceCards().size()==2?Conversion.ZHANGBA:card.type()==card.sourceCards().getFirst().type()?Conversion.PHYSICAL:card.sourceCards().getFirst().type()==CardType.SHAN&&who.hero().skillIds().contains("longdan")?Conversion.LONGDAN:Conversion.WUSHENG;
    long weapon=conversion==Conversion.ZHANGBA?who.equipment().weapon().instanceId():0;
    e.commitAttackSupply(who,card,provided,user);receipt=new Receipt(supplier,card.type(),costs,conversion,weapon,e.state().cards().checkpointSequence());step=Step.COMMITTED;return true;
  }
  GameCard card(SgsGameEngine e){if(receipt==null)throw new IllegalStateException("attack supply not committed");var costs=receipt.costs().stream().map(c->physical(e,c.id())).toList();return receipt.conversion()==Conversion.PHYSICAL?costs.getFirst():new VirtualCard(receipt.type(),costs);}
  private static CardInstance physical(SgsGameEngine e,long id){return e.state().cards().zones().stream().flatMap(z->e.state().cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow(()->new IllegalArgumentException("supply physical entity missing"));}
}
