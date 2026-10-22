package dev.sgs.core.engine.skill;

import java.util.*;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.DamageNature;

/** One ordered event dispatch. Bindings and progress are data; bodies remain registered on the bus. */
public final class ListenerProgram {
  /** Immutable identity of a suspended damage event; distinct targets/causes cannot reuse its cursor. */
  public record DamageEvent(int source,int target,int amount,CardType cardType,List<Long> cards,
      DamageNature nature,boolean chain,int turn,int currentSeat) {
    public DamageEvent {
      cards=List.copyOf(cards);Objects.requireNonNull(nature);
      if(source< -1||source>7||target<0||target>7||amount<0||amount>1_000_000||turn<0||currentSeat<0||currentSeat>7
          ||cards.size()>160||new HashSet<>(cards).size()!=cards.size()||cards.stream().anyMatch(v->v<1)
          ||cardType==null&&!cards.isEmpty())throw new IllegalArgumentException("invalid listener damage event");
    }
    static DamageEvent of(SkillContext c){
      if(c.timing()!=Timing.DAMAGE_TAKE_AFTER||c.target()==null)throw new IllegalArgumentException("not an awaitable damage timing");
      return new DamageEvent(c.source()==null?-1:c.source().seat(),c.target().seat(),c.damage(),c.card()==null?null:c.card().type(),
          c.card()==null?List.of():c.card().sourceCards().stream().map(v->v.instanceId()).toList(),c.nature(),c.chainDamage(),c.state().turnNumber(),c.state().currentSeat());
    }
  }
  public record Binding(String id,int owner,int priority,long sequence) {
    public Binding {
      if(id==null||id.isBlank()||id.length()>128||owner< -1||owner>7||sequence<1)
        throw new IllegalArgumentException("invalid listener binding");
    }
  }
  public record Snapshot(Timing timing,OrderStrategy strategy,List<Binding> registry,
      List<Long> candidates,int cursor,AwaitableDamageEffect.Request awaiting,DamageEvent damageEvent) {
    public Snapshot(Timing timing,OrderStrategy strategy,List<Binding> registry,List<Long> candidates,int cursor,AwaitableDamageEffect.Request awaiting){this(timing,strategy,registry,candidates,cursor,awaiting,null);}
    public Snapshot {
      Objects.requireNonNull(timing);Objects.requireNonNull(strategy);
      registry=List.copyOf(registry);candidates=List.copyOf(candidates);
      if(registry.size()>256||candidates.size()>registry.size()||cursor<0||cursor>candidates.size())
        throw new IllegalArgumentException("invalid listener program bounds");
      var ids=new HashSet<Long>();
      for(var binding:registry)if(!ids.add(binding.sequence()))throw new IllegalArgumentException("duplicate listener registration");
      if(!registry.equals(registry.stream().sorted(Comparator.comparingInt(Binding::priority).thenComparingLong(Binding::sequence)).toList()))throw new IllegalArgumentException("listener registry out of priority order");
      if(new HashSet<>(candidates).size()!=candidates.size()||!ids.containsAll(candidates))
        throw new IllegalArgumentException("invalid listener candidate plan");
      if(awaiting!=null){
        if(timing!=Timing.DAMAGE_TAKE_AFTER||damageEvent==null||cursor==candidates.size())throw new IllegalArgumentException("invalid awaited timing cursor");
        long sequence=candidates.get(cursor);var binding=registry.stream().filter(b->b.sequence()==sequence).findFirst().orElseThrow();
        if(binding.owner()!=awaiting.owner()||!binding.id().equals(awaiting.skill()))throw new IllegalArgumentException("awaited binding differs from cursor");
      }
      if(damageEvent!=null&&timing!=Timing.DAMAGE_TAKE_AFTER)throw new IllegalArgumentException("damage event on another timing");
      if(strategy!=OrderStrategy.MULTI_ENTITY_ORDER){
        var order=registry.stream().map(Binding::sequence).filter(candidates::contains).toList();
        if(!order.equals(candidates))throw new IllegalArgumentException("listener candidate priority changed");
      }
    }
    public boolean done(){return cursor==candidates.size();}
  }
  private Snapshot state;
  public ListenerProgram(Snapshot state){this.state=Objects.requireNonNull(state);}
  public Snapshot snapshot(){return state;}
  public boolean done(){return state.done();}
  /** Execute pure/synchronous listeners, or stop before a data-owned damage body. */
  public Optional<AwaitableDamageEffect.Request> advance(SkillEventBus bus,SkillContext context,boolean awaitDamageBodies){
    state=bus.advanceListeners(state,context,awaitDamageBodies);return Optional.ofNullable(state.awaiting());
  }
  /** A body has finished, including its child effects. Publish exactly one receipt before advancing. */
  public void complete(SkillEventBus bus,SkillContext context,boolean activated){
    state=bus.completeListener(state,context,activated);
  }
}
