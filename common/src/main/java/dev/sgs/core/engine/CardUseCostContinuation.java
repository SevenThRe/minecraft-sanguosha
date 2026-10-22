package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import java.util.*;

/** Physical use/response payment, then ordered departure listeners, before the caller commits use. */
public final class CardUseCostContinuation {
  public enum Step { PAY, MOVEMENTS, DONE }
  public record Snapshot(int actor,CardType type,List<Long> cards,boolean virtual,boolean recast,CardMove.Reason reason,Step step,
      List<MovementListenerProgram.Snapshot> movements,TrickContinuation.Pending pending,long revision,String transformSkill){
    public Snapshot(int actor,CardType type,List<Long> cards,boolean virtual,boolean recast,CardMove.Reason reason,Step step,List<MovementListenerProgram.Snapshot> movements,TrickContinuation.Pending pending,long revision){this(actor,type,cards,virtual,recast,reason,step,movements,pending,revision,"");}
    public Snapshot{
      cards=List.copyOf(cards);movements=List.copyOf(movements);
      if(actor<0||actor>7||type==null||cards.isEmpty()||cards.size()>160||new HashSet<>(cards).size()!=cards.size()
          ||cards.stream().anyMatch(id->id<1)||!Set.of(CardMove.Reason.USE,CardMove.Reason.RESPONSE).contains(reason)
          ||step==null||revision<0||revision==Long.MAX_VALUE||movements.size()>160)
        throw new IllegalArgumentException("invalid use cost program");
      if(!virtual&&cards.size()!=1||recast&&(type!=CardType.TIESUO||cards.size()!=1))throw new IllegalArgumentException("invalid paid card shape");
      if(step==Step.PAY&&(!movements.isEmpty()||pending!=null)||step==Step.DONE&&(!movements.isEmpty()||pending!=null))throw new IllegalArgumentException("cost phase differs from progress");
      new CostMovementQueue.Snapshot(movements,pending,revision);
      Objects.requireNonNull(transformSkill);if(!transformSkill.isEmpty()&&(!transformSkill.matches("(?:[a-z][a-z0-9_]{1,31}:)?[a-z][a-z0-9_]{0,63}")||!virtual||cards.size()!=1||step==Step.DONE))throw new IllegalArgumentException("invalid detached transform receipt");
    }
  }
  private final int actor;private final CardType type;private final List<Long> cards;private final CardMove.Reason reason;
  private final boolean virtual,recast;
  private GameCard original;
  private String transformSkill="";
  private Step step=Step.PAY;private CostMovementQueue queue=new CostMovementQueue();
  CardUseCostContinuation(int actor,GameCard card,CardMove.Reason reason){
    this.actor=actor;this.type=card.type();this.cards=card.sourceCards().stream().map(CardInstance::instanceId).toList();this.reason=reason;
    virtual=card.isVirtual();recast=card.canRecast();original=card;snapshot();
  }
  /** Own the accepted transform receipt before returning to an outer continuation ingress. */
  static CardUseCostContinuation accepted(SgsGameEngine e,int actor,GameCard card,CardMove.Reason reason){
    var cost=new CardUseCostContinuation(actor,card,reason);
    cost.transformSkill=e.skillBus().takeTransformReceipt(e.state().players().get(actor),card);return cost;
  }
  public CardUseCostContinuation(Snapshot saved,long captured,long now){
    actor=saved.actor();type=saved.type();cards=saved.cards();virtual=saved.virtual();recast=saved.recast();reason=saved.reason();step=saved.step();transformSkill=saved.transformSkill();
    queue=new CostMovementQueue(new CostMovementQueue.Snapshot(saved.movements(),saved.pending(),saved.revision()),captured,now);
  }
  public Snapshot snapshot(){var q=queue.snapshot();return new Snapshot(actor,type,cards,virtual,recast,reason,step,q.movements(),q.pending(),q.revision(),transformSkill);}
  boolean done(){return step==Step.DONE;}
  TrickContinuation.Pending pending(){return queue.pending();}
  CostMovementQueue movementQueue(){return queue;}
  private CardInstance physical(SgsGameEngine e,long id){return e.state().cards().zones().stream().flatMap(z->e.state().cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow();}
  GameCard card(SgsGameEngine e){if(original!=null)return original;var physical=cards.stream().map(id->physical(e,id)).toList();return virtual?new VirtualCard(type,physical,recast):physical.getFirst();}
  void advance(SgsGameEngine e){
    if(queue.pending()!=null||done())return;
    if(step==Step.PAY){if(transformSkill.isEmpty())transformSkill=e.skillBus().takeTransformReceipt(e.state().players().get(actor),card(e));e.commitUseCost(this,e.state().players().get(actor),card(e),reason);step=Step.MOVEMENTS;}
    if(queue.advance(e)){e.finishOwnedUse(e.state().players().get(actor),card(e),reason,transformSkill);transformSkill="";original=null;step=Step.DONE;}
  }
  boolean invoke(SgsGameEngine e,boolean accepted){return queue.invoke(e,accepted);}
  void validate(SgsGameEngine e){
    var s=snapshot();if(step!=Step.MOVEMENTS||s.pending()==null||actor>=e.state().players().size())throw new IllegalArgumentException("cost is not at a pending departure");
    if(cards.stream().anyMatch(id->!CardZone.PROCESSING.equals(e.state().cards().locate(id))))throw new IllegalArgumentException("use cost not in processing");
    if(!virtual&&physical(e,cards.getFirst()).type()!=type)throw new IllegalArgumentException("physical paid card type differs");
    if(!transformSkill.isEmpty()&&!e.skillBus().hasTransformReceiptBinding(e.state().players().get(actor),transformSkill))throw new IllegalArgumentException("detached transform binding differs");
    queue.validate(e);
    for(var program:s.movements()){var ev=program.event();
      if(ev.actor()!=actor||ev.reason()!=reason||!ev.to().equals(CardZone.PROCESSING)||ev.from().seat()!=actor
          ||!Set.of(CardZone.Kind.HAND,CardZone.Kind.EQUIPMENT,CardZone.Kind.SPECIAL).contains(ev.from().kind())
          ||!cards.containsAll(ev.cards()))throw new IllegalArgumentException("unrelated movement in use cost");
    }
  }
}
