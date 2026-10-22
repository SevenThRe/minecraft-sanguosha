package dev.sgs.core.persistence;

import dev.sgs.core.*;
import dev.sgs.core.engine.*;
import dev.sgs.core.game.GameState.PublicSkillActivation;
import java.util.*;

/** A genuine phase parent, independent of attack/card-use/active-skill save formats. */
public record TurnEndCheckpoint(int version,StateCheckpoint state,long promptSequence,int responseTimeoutSeconds,
    Map<String,Map<String,Long>> skillMemory,List<String> bindings,long skillSequence,
    List<PublicSkillActivation> activations,TurnEndContinuation.Snapshot continuation,long capturedAtMillis) {
    public static final int VERSION=1;
    public static final String RULES_VERSION="standard-turn-end-parent-v1";
    public TurnEndCheckpoint {
        Objects.requireNonNull(state);Objects.requireNonNull(continuation);
        var copy=new TreeMap<String,Map<String,Long>>();skillMemory.forEach((k,v)->copy.put(k,Map.copyOf(v)));
        skillMemory=Collections.unmodifiableMap(copy);bindings=List.copyOf(bindings);activations=List.copyOf(activations);
    }
    public static TrickContinuation.Pending pending(TurnEndContinuation.Snapshot c){
        return c.payment()!=null?c.payment().queue().pending():c.pending();
    }
    public void validate(){
        state.validate();var c=continuation;var p=pending(c);int n=state.players().size();
        if(version!=VERSION||state.turn().status()!=GameStatus.RUNNING||!Set.of(Phase.PLAY,Phase.DISCARD,Phase.END).contains(state.turn().phase())
            ||c.actor()!=state.turn().currentSeat()||c.turn()!=state.turn().turnNumber()||c.actor()>=n
            ||promptSequence<1||promptSequence==Long.MAX_VALUE||responseTimeoutSeconds<1||responseTimeoutSeconds>3600
            ||capturedAtMillis<0||skillSequence<0||skillSequence==Long.MAX_VALUE||skillMemory.size()>SkillMemoryLayout.MAX_RECORDS
            ||bindings.size()>256||activations.size()>32||p==null||p.id()<1||p.id()>promptSequence||p.deadline()<0||p.revision()<1
            ||p.type()!=PromptType.SKILL_INVOKE||p.subject()!=c.actor()||p.asker()<0||p.asker()>=n||p.hidden()||!p.choices().isEmpty()
            ||p.deadline()-capturedAtMillis>responseTimeoutSeconds*1000L
            ||c.step()==TurnEndContinuation.Step.DONE||!state.zones().stream().filter(z->z.location().equals(dev.sgs.core.card.CardZone.PROCESSING)).allMatch(z->z.cards().isEmpty()))
            throw new IllegalArgumentException("invalid turn-end checkpoint");
        skillMemory.forEach((key,value)->SkillMemoryLayout.validate(key,value,n,false));
        bindings.forEach(b->StateCheckpoint.text(b,256));long previous=0;
        for(var a:activations){
            if(a.sequence()<=previous||a.sequence()>skillSequence||a.seat()<0||a.seat()>=n)throw new IllegalArgumentException("phase activation history differs");
            StateCheckpoint.text(a.skillId(),128);previous=a.sequence();
        }
        StateCheckpoint.text(p.skill(),128);
    }
}
