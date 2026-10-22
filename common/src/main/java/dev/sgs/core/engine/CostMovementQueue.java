package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import java.util.*;

/** Shared ordered departure queue for use/response payments and atomic discard payments. */
public final class CostMovementQueue {
  public record Snapshot(List<MovementListenerProgram.Snapshot> movements,TrickContinuation.Pending pending,long revision){
    public Snapshot{
      movements=List.copyOf(movements);
      if(movements.size()>160||revision<0||revision==Long.MAX_VALUE)throw new IllegalArgumentException("invalid cost queue bounds");
      long previous=0;for(var m:movements){if(m.event().sequence()<=previous)throw new IllegalArgumentException("cost queue out of order");previous=m.event().sequence();}
      if(pending!=null){var a=movements.isEmpty()?null:movements.getFirst().awaiting();
        if(a==null||pending.type()!=PromptType.SKILL_INVOKE||pending.asker()!=a.owner()||pending.subject()!=a.owner()||!pending.skill().equals(a.skill())
            ||!pending.choices().isEmpty()||pending.hidden()||pending.id()<1||pending.deadline()<0||pending.revision()!=revision)throw new IllegalArgumentException("cost queue request mismatch");}
    }
  }
  private final Deque<MovementListenerProgram> movements=new ArrayDeque<>();
  private TrickContinuation.Pending pending;private long revision;
  CostMovementQueue(){}
  CostMovementQueue(Snapshot saved,long captured,long now){
    saved.movements().forEach(m->movements.addLast(new MovementListenerProgram(m)));revision=saved.revision();pending=saved.pending();
    if(pending!=null)pending=new TrickContinuation.Pending(pending.id(),pending.type(),pending.asker(),pending.subject(),DecisionBoundary.restoredDeadline(pending.deadline(),captured,now),pending.choices(),pending.hidden(),pending.skill(),pending.revision());
  }
  Snapshot snapshot(){return new Snapshot(movements.stream().map(MovementListenerProgram::snapshot).toList(),pending,revision);}
  TrickContinuation.Pending pending(){return pending;}
  void enqueue(SgsGameEngine e,CardMove move){if(pending!=null)throw new IllegalStateException("capture while awaiting");movements.addLast(e.skillBus().beginMovement(SkillContext.movement(e.state(),move)));}
  private CardMove movement(SgsGameEngine e,MovementListenerProgram program){
    var m=program.snapshot().event();var physical=m.cards().stream().map(id->e.state().cards().zones().stream().flatMap(z->e.state().cards().cards(z).stream()).filter(c->c.instanceId()==id).findFirst().orElseThrow()).toList();
    return new CardMove(m.sequence(),m.from(),m.to(),physical,m.reason(),m.actor(),m.publicCards());
  }
  boolean advance(SgsGameEngine e){
    if(pending!=null)return false;
    for(int budget=0;budget<10000;budget++){
      if(movements.isEmpty())return true;var program=movements.getFirst();var move=movement(e,program);var request=program.advance(e.skillBus(),SkillContext.movement(e.state(),move));
      if(request.isPresent()){var a=request.get();revision++;pending=new TrickContinuation.Pending(e.nextPromptId(),PromptType.SKILL_INVOKE,a.owner(),a.owner(),e.responseDeadlineMillis(),List.of(),false,a.skill(),revision);return false;}
      if(!program.done())throw new IllegalStateException("cost queue failed to yield");movements.removeFirst();e.movementEventFinished(move);
    }
    throw new IllegalStateException("cost queue loop exceeded");
  }
  boolean invoke(SgsGameEngine e,boolean accepted){
    if(pending==null)return false;var p=movements.getFirst();e.completeMovementInvoke(p,movement(e,p),accepted&&e.continuationNow()<=pending.deadline());pending=null;revision++;return true;
  }
  void validate(SgsGameEngine e){
    snapshot();if(pending==null||movements.isEmpty())throw new IllegalArgumentException("cost queue is not awaiting");
    var p=movements.getFirst();e.skillBus().validateMovement(p.snapshot(),SkillContext.movement(e.state(),movement(e,p)));
  }
}
