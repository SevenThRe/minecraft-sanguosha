package dev.sgs.core.engine.skill;

import java.util.*;

/** Static-priority numeric fold with explicit body progress and an input boundary. */
public final class RuleFoldProgram {
    public record Snapshot(RuleId rule,int actor,int turn,List<ListenerProgram.Binding> registry,
        int cursor,int value,AwaitableRuleEffect.Progress progress,AwaitableRuleEffect.Request awaiting) {
        public Snapshot {
            Objects.requireNonNull(rule);registry=List.copyOf(registry);
            if(actor<0||actor>7||turn<0||registry.size()>256||cursor<0||cursor>registry.size()||Math.abs((long)value)>1_000_000
                ||cursor==registry.size()&&(progress!=null||awaiting!=null)||awaiting!=null&&progress==null)
                throw new IllegalArgumentException("invalid rule fold bounds");
            if(new HashSet<>(registry.stream().map(ListenerProgram.Binding::sequence).toList()).size()!=registry.size()
                ||!registry.equals(registry.stream().sorted(Comparator.comparingInt(ListenerProgram.Binding::priority).thenComparingLong(ListenerProgram.Binding::sequence)).toList()))
                throw new IllegalArgumentException("invalid rule fold registry");
        }
        public boolean done(){return cursor==registry.size();}
    }
    private Snapshot state;
    public RuleFoldProgram(Snapshot saved){state=Objects.requireNonNull(saved);}
    public Snapshot snapshot(){return state;}
    public boolean done(){return state.done();}
    public Optional<AwaitableRuleEffect.Request> advance(SkillEventBus bus,SkillContext c){state=bus.advanceRule(state,c);return Optional.ofNullable(state.awaiting());}
    public void complete(SkillEventBus bus,SkillContext c,Optional<dev.sgs.core.engine.SkillChoice> choice){state=bus.completeRule(state,c,choice);}
}
