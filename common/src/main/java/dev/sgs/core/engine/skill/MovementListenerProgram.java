package dev.sgs.core.engine.skill;

import dev.sgs.core.card.*;
import java.util.*;

/** Ordered dispatch of one atomic move, suspended before each optional listener body. */
public final class MovementListenerProgram {
  public record Event(long sequence,CardZone from,CardZone to,List<Long> cards,
      CardMove.Reason reason,int actor,boolean publicCards,int turn,int currentSeat){
    public Event{
      Objects.requireNonNull(from);Objects.requireNonNull(to);Objects.requireNonNull(reason);cards=List.copyOf(cards);
      if(sequence<1||cards.isEmpty()||cards.size()>160||new HashSet<>(cards).size()!=cards.size()
          ||cards.stream().anyMatch(id->id<1)||actor< -1||actor>7||turn<0||currentSeat<0||currentSeat>7)
        throw new IllegalArgumentException("invalid movement listener event");
    }
    public static Event of(SkillContext context){
      if(context.timing()!=Timing.CARD_MOVE_AFTER||context.movement()==null)throw new IllegalArgumentException("movement context required");
      var m=context.movement();return new Event(m.sequence(),m.from(),m.to(),m.cards().stream().map(CardInstance::instanceId).toList(),
          m.reason(),m.actorSeat(),m.publicCards(),context.state().turnNumber(),context.state().currentSeat());
    }
  }
  public record Snapshot(Event event,ListenerProgram.Snapshot listeners,AwaitableMovementEffect.Request awaiting){
    public Snapshot{
      Objects.requireNonNull(event);Objects.requireNonNull(listeners);
      if(listeners.timing()!=Timing.CARD_MOVE_AFTER||listeners.strategy()!=OrderStrategy.MULTI_ENTITY_ORDER
          ||listeners.awaiting()!=null||listeners.damageEvent()!=null)throw new IllegalArgumentException("invalid movement listener plan");
      if(awaiting!=null){
        if(listeners.done())throw new IllegalArgumentException("completed movement still awaiting");
        long sequence=listeners.candidates().get(listeners.cursor());
        var b=listeners.registry().stream().filter(v->v.sequence()==sequence).findFirst().orElseThrow();
        if(b.owner()!=awaiting.owner()||!b.id().equals(awaiting.skill()))throw new IllegalArgumentException("movement awaiting binding differs");
      }
    }
    public boolean done(){return listeners.done();}
  }
  private Snapshot state;
  public MovementListenerProgram(Snapshot state){this.state=Objects.requireNonNull(state);}
  public Snapshot snapshot(){return state;}
  public boolean done(){return state.done();}
  public Optional<AwaitableMovementEffect.Request> advance(SkillEventBus bus,SkillContext context){
    state=bus.advanceMovement(state,context);return Optional.ofNullable(state.awaiting());
  }
  /** The caller supplies exactly one invocation answer while the original body executes. */
  public void complete(SkillEventBus bus,SkillContext context){state=bus.completeMovement(state,context);}
}
