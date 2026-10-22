package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import java.util.*;

/** One atomic owned hand/equipment discard, preserving both initiator and losing owner. */
public final class DiscardCostContinuation {
  public enum Step { PAY, MOVEMENTS, DONE }
  public record Snapshot(int actor,int owner,List<Long> cards,Step step,CostMovementQueue.Snapshot queue){
    public Snapshot(int actor,List<Long> cards,Step step,CostMovementQueue.Snapshot queue){this(actor,actor,cards,step,queue);}
    public Snapshot{
      cards=List.copyOf(cards);Objects.requireNonNull(step);Objects.requireNonNull(queue);
      if(actor<0||actor>7||owner<0||owner>7||cards.isEmpty()||cards.size()>160||new HashSet<>(cards).size()!=cards.size()||cards.stream().anyMatch(id->id<1))throw new IllegalArgumentException("invalid discard payment");
      if(step!=Step.MOVEMENTS&&(!queue.movements().isEmpty()||queue.pending()!=null))throw new IllegalArgumentException("discard phase differs");
    }
  }
  private final int actor,owner;private final List<Long> cards;private Step step=Step.PAY;private CostMovementQueue queue=new CostMovementQueue();
  DiscardCostContinuation(int actor,List<CardInstance> cards){this(actor,actor,cards);}
  DiscardCostContinuation(int actor,int owner,List<CardInstance> cards){this.actor=actor;this.owner=owner;this.cards=cards.stream().map(CardInstance::instanceId).toList();snapshot();}
  public DiscardCostContinuation(Snapshot s,long captured,long now){actor=s.actor();owner=s.owner();cards=s.cards();step=s.step();queue=new CostMovementQueue(s.queue(),captured,now);}
  public Snapshot snapshot(){return new Snapshot(actor,owner,cards,step,queue.snapshot());}
  boolean done(){return step==Step.DONE;}
  TrickContinuation.Pending pending(){return queue.pending();}
  private CardInstance physical(SgsGameEngine e,long id){return e.state().cards().zones().stream().flatMap(z->e.state().cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow();}
  void advance(SgsGameEngine e){
    if(done()||pending()!=null)return;
    if(step==Step.PAY){e.commitDiscardCost(queue,e.state().players().get(owner),e.state().players().get(actor),cards.stream().map(id->physical(e,id)).toList());step=Step.MOVEMENTS;}
    if(queue.advance(e))step=Step.DONE;
  }
  boolean invoke(SgsGameEngine e,boolean answer){return queue.invoke(e,answer);}
  void validate(SgsGameEngine e){
    if(step!=Step.MOVEMENTS||pending()==null||actor>=e.state().players().size()||owner>=e.state().players().size()||cards.stream().anyMatch(id->!CardZone.DISCARD.equals(e.state().cards().locate(id))))throw new IllegalArgumentException("discard payment not at a committed boundary");
    queue.validate(e);
    for(var m:queue.snapshot().movements()){var ev=m.event();
      if(ev.actor()!=actor||ev.reason()!=CardMove.Reason.DISCARD||!ev.to().equals(CardZone.DISCARD)||ev.from().seat()!=owner
          ||!Set.of(CardZone.Kind.HAND,CardZone.Kind.EQUIPMENT).contains(ev.from().kind())||!cards.containsAll(ev.cards()))throw new IllegalArgumentException("unrelated discard movement");
    }
  }
}
