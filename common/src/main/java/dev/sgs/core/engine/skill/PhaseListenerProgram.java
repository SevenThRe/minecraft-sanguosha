package dev.sgs.core.engine.skill;

import dev.sgs.core.Phase;
import java.util.*;

/** Ordered phase dispatch with a stable event and cursor; no suspended Java listener call. */
public final class PhaseListenerProgram {
    public record Event(Timing timing,Phase phase,int actor,int turn) {
        public Event {
            Objects.requireNonNull(phase);Objects.requireNonNull(timing);
            if(!Set.of(Timing.PHASE_DISCARD_BEFORE,Timing.PHASE_DISCARD_START,
                Timing.PHASE_DISCARD_END,Timing.PHASE_TURN_END).contains(timing)
                ||actor<0||actor>7||turn<0)throw new IllegalArgumentException("invalid phase event");
        }
        static Event of(SkillContext c){
            if(c.actor()==null||c.actor()!=c.state().currentPlayer())throw new IllegalArgumentException("phase actor differs");
            return new Event(c.timing(),c.state().phase(),c.actor().seat(),c.state().turnNumber());
        }
    }
    public record Snapshot(Event event,ListenerProgram.Snapshot listeners,boolean skipped,AwaitablePhaseEffect.Request awaiting) {
        public Snapshot {
            Objects.requireNonNull(event);Objects.requireNonNull(listeners);
            if(listeners.timing()!=event.timing()||listeners.awaiting()!=null||listeners.damageEvent()!=null
                ||listeners.strategy()!=OrderStrategy.REGISTRATION_PRIORITY)throw new IllegalArgumentException("phase listener plan differs");
            if(awaiting!=null){
                if(listeners.done())throw new IllegalArgumentException("phase await after completion");
                long seq=listeners.candidates().get(listeners.cursor());
                var b=listeners.registry().stream().filter(v->v.sequence()==seq).findFirst().orElseThrow();
                if(b.owner()!=awaiting.owner()||!b.id().equals(awaiting.skill()))throw new IllegalArgumentException("phase await binding differs");
            }
        }
        public boolean done(){return listeners.done();}
    }
    private Snapshot state;
    public PhaseListenerProgram(Snapshot state){this.state=Objects.requireNonNull(state);}
    public Snapshot snapshot(){return state;}
    public boolean done(){return state.done();}
    public Optional<AwaitablePhaseEffect.Request> advance(SkillEventBus bus,SkillContext c){
        state=bus.advancePhase(state,c);return Optional.ofNullable(state.awaiting());
    }
    public void complete(SkillEventBus bus,SkillContext c){state=bus.completePhase(state,c);}
}
