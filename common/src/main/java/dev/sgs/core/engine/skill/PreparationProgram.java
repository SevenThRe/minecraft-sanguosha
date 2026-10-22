package dev.sgs.core.engine.skill;

import dev.sgs.core.engine.*;
import java.util.*;

/** Registered preparation listeners and private card-order bodies own all progress as data. */
public final class PreparationProgram {
    public enum Step { NEXT, INVOKE, ORDER, RETURN, COMPLETE }
    public record Order(List<Long> original,List<Long> pending,List<Long> top,List<Long> bottom) {
        public Order {
            original=List.copyOf(original);pending=List.copyOf(pending);top=List.copyOf(top);bottom=List.copyOf(bottom);
            var all=new ArrayList<Long>();all.addAll(pending);all.addAll(top);all.addAll(bottom);
            if(original.size()>160||original.stream().anyMatch(id->id<1)||new HashSet<>(original).size()!=original.size()
                ||all.size()!=original.size()||new HashSet<>(all).size()!=all.size()||!new HashSet<>(all).equals(new HashSet<>(original))
                ||!original.stream().filter(pending::contains).toList().equals(pending))throw new IllegalArgumentException("invalid private deck partition");
        }
        public List<Long> returnedTop(){var all=new ArrayList<>(top);all.addAll(pending);return List.copyOf(all);}
    }
    public record Snapshot(int actor,int turn,ListenerProgram.Snapshot listeners,Step step,Order order) {
        public Snapshot {
            Objects.requireNonNull(listeners);Objects.requireNonNull(step);
            if(actor<0||actor>7||turn<0||listeners.timing()!=Timing.PHASE_PREPARE_START||listeners.strategy()!=OrderStrategy.REGISTRATION_PRIORITY
                ||listeners.awaiting()!=null||listeners.damageEvent()!=null||listeners.done()&&step!=Step.NEXT
                ||(step==Step.NEXT||step==Step.INVOKE)&&order!=null||(step==Step.ORDER||step==Step.RETURN)&&order==null)
                throw new IllegalArgumentException("invalid preparation graph");
        }
        public boolean done(){return listeners.done()&&step==Step.NEXT;}
        public ListenerProgram.Binding binding(){
            long seq=listeners.candidates().get(listeners.cursor());return listeners.registry().stream().filter(b->b.sequence()==seq).findFirst().orElseThrow();
        }
    }
    public record Request(PromptType type,int owner,String skill,List<Long> cards,List<Integer> destinations){
        public Request {cards=List.copyOf(cards);destinations=List.copyOf(destinations);}
    }
    private Snapshot state;
    public PreparationProgram(Snapshot saved){state=Objects.requireNonNull(saved);}
    public Snapshot snapshot(){return state;}
    public boolean done(){return state.done();}
    public Optional<Request> request(){
        if(state.done())return Optional.empty();var b=state.binding();
        return switch(state.step()){
            case INVOKE->Optional.of(new Request(PromptType.SKILL_INVOKE,b.owner(),b.id(),List.of(),List.of()));
            case ORDER->state.order().pending().isEmpty()?Optional.empty():Optional.of(new Request(PromptType.GUANXING_ORDER,b.owner(),b.id(),state.order().pending(),List.of(0,1)));
            default->Optional.empty();};
    }
    public Optional<Request> advance(SkillEventBus bus,SkillContext c){state=bus.advancePreparation(state,c);return request();}
    public void answer(SkillEventBus bus,SkillContext c,boolean accepted,Optional<SkillChoice> choice){state=bus.answerPreparation(state,c,accepted,choice);}
}
