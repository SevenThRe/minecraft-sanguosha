package dev.sgs.core.persistence;

import dev.sgs.core.*;
import dev.sgs.core.engine.*;
import dev.sgs.core.game.GameState.PublicSkillActivation;
import java.util.*;

/** A genuine phase parent, independent of attack/card-use/active-skill save formats. */
public record TurnStartCheckpoint(int version,StateCheckpoint state,long promptSequence,int responseTimeoutSeconds,
    Map<String,Map<String,Long>> skillMemory,List<String> bindings,long skillSequence,
    List<PublicSkillActivation> activations,TurnStartContinuation.Snapshot continuation,long capturedAtMillis) {
    public static final int VERSION=2;
    public static final String RULES_VERSION="standard-turn-start-draw-v1";
    public static String rulesVersion(int version){return version==1?RULES_VERSION:version==2?"standard-turn-start-preparation-v2":"unsupported";}
    public TurnStartCheckpoint {
        Objects.requireNonNull(state);Objects.requireNonNull(continuation);
        var copy=new TreeMap<String,Map<String,Long>>();skillMemory.forEach((k,v)->copy.put(k,Map.copyOf(v)));
        skillMemory=Collections.unmodifiableMap(copy);bindings=List.copyOf(bindings);activations=List.copyOf(activations);
    }
    public static TrickContinuation.Pending pending(TurnStartContinuation.Snapshot c){
        return c.movements().pending()!=null?c.movements().pending():c.pending();
    }
    public void validate(){
        state.validate();var c=continuation;var p=pending(c);int n=state.players().size();
        if((version!=1&&version!=VERSION)||state.turn().status()!=GameStatus.RUNNING
            ||(version==1?(state.turn().phase()!=Phase.DRAW||c.prepare()!=null):(state.turn().phase()!=Phase.PREPARE||c.prepare()==null))
            ||c.actor()!=state.turn().currentSeat()||c.turn()!=state.turn().turnNumber()||c.actor()>=n
            ||promptSequence<1||promptSequence==Long.MAX_VALUE||responseTimeoutSeconds<1||responseTimeoutSeconds>3600
            ||capturedAtMillis<0||skillSequence<0||skillSequence==Long.MAX_VALUE||skillMemory.size()>SkillMemoryLayout.MAX_RECORDS
            ||bindings.size()>256||activations.size()>32||p==null||p.id()<1||p.id()>promptSequence||p.deadline()<0||p.revision()<1
            ||(p.type()!=PromptType.SKILL_INVOKE&&p.type()!=(version==1?PromptType.TUXI_TARGET:PromptType.GUANXING_ORDER))||p.subject()<0||p.subject()>=n||p.asker()<0||p.asker()>=n||version==1&&!p.choices().isEmpty()
            ||p.deadline()-capturedAtMillis>responseTimeoutSeconds*1000L
            ||c.step()==TurnStartContinuation.Step.DONE||!state.faceUpProcessing().isEmpty())
            throw new IllegalArgumentException("invalid turn-start checkpoint");
        var processing=state.zones().stream().filter(z->z.location().equals(dev.sgs.core.card.CardZone.PROCESSING)).flatMap(z->z.cards().stream()).map(StateCheckpoint.Card::id).toList();
        var held=c.prepare()!=null&&c.prepare().order()!=null?c.prepare().order().original():List.<Long>of();
        if(!processing.equals(held))throw new IllegalArgumentException("turn-start private card ownership differs");
        skillMemory.forEach((key,value)->SkillMemoryLayout.validate(key,value,n,false));
        bindings.forEach(b->StateCheckpoint.text(b,256));long previous=0;
        for(var a:activations){
            if(a.sequence()<=previous||a.sequence()>skillSequence||a.seat()<0||a.seat()>=n)throw new IllegalArgumentException("phase activation history differs");
            StateCheckpoint.text(a.skillId(),128);previous=a.sequence();
        }
        StateCheckpoint.text(p.skill(),128);
    }
}
