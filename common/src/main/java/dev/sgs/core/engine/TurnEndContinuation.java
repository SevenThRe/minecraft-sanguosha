package dev.sgs.core.engine;

import dev.sgs.core.*;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import java.util.*;

/** End-play/compulsory-discard/phase listeners/handover share one data-owned parent. */
public final class TurnEndContinuation {
    public enum Step { BEFORE_DISCARD, ENTER_DISCARD, DISCARD_START, PAYMENT, DISCARD_END, TURN_END, HANDOVER, DONE }
    public record Snapshot(int actor,int turn,Step step,boolean skipped,boolean automaticDiscard,List<Long> discarded,
        DiscardCostContinuation.Snapshot payment,PhaseListenerProgram.Snapshot listeners,
        TrickContinuation.Pending pending,long revision) {
        public Snapshot {
            discarded=List.copyOf(discarded);Objects.requireNonNull(step);
            if(actor<0||actor>7||turn<0||revision<0||revision==Long.MAX_VALUE||discarded.size()>160
                ||new HashSet<>(discarded).size()!=discarded.size()||discarded.stream().anyMatch(id->id<1))
                throw new IllegalArgumentException("invalid turn-end parent");
        }
    }
    private final int actor,turn;private final boolean automaticDiscard;private Step step;private boolean skipped;private final List<Long> discarded;
    private DiscardCostContinuation payment;private PhaseListenerProgram listeners;
    private TrickContinuation.Pending pending;private long revision;
    TurnEndContinuation(SgsGameEngine e,boolean endPlay,List<CardInstance> cards,boolean automatic){
        automaticDiscard=automatic;
        actor=e.state().currentSeat();turn=e.state().turnNumber();step=endPlay?Step.BEFORE_DISCARD:Step.PAYMENT;
        discarded=cards.stream().map(CardInstance::instanceId).toList();
        if(!cards.isEmpty())payment=new DiscardCostContinuation(actor,cards);
    }
    public TurnEndContinuation(Snapshot s,long captured,long now){
        actor=s.actor();turn=s.turn();automaticDiscard=s.automaticDiscard();step=s.step();skipped=s.skipped();discarded=s.discarded();revision=s.revision();
        if(s.payment()!=null)payment=new DiscardCostContinuation(s.payment(),captured,now);
        if(s.listeners()!=null)listeners=new PhaseListenerProgram(s.listeners());
        var p=s.pending();if(p!=null)pending=new TrickContinuation.Pending(p.id(),p.type(),p.asker(),p.subject(),
            DecisionBoundary.restoredDeadline(p.deadline(),captured,now),p.choices(),p.hidden(),p.skill(),p.revision());
    }
    public Snapshot snapshot(){return new Snapshot(actor,turn,step,skipped,automaticDiscard,discarded,payment==null?null:payment.snapshot(),listeners==null?null:listeners.snapshot(),pending,revision);}
    public boolean done(){return step==Step.DONE;}
    TrickContinuation.Pending pending(){return payment!=null?payment.pending():pending;}
    private SkillContext context(SgsGameEngine e,Timing timing){
        var c=SkillContext.phase(e.state(),timing,e.state().players().get(actor));
        if(listeners!=null&&listeners.snapshot().skipped())c.skipPhase();return c;
    }
    private Timing timing(){return switch(step){
        case BEFORE_DISCARD->Timing.PHASE_DISCARD_BEFORE;case DISCARD_START->Timing.PHASE_DISCARD_START;
        case DISCARD_END->Timing.PHASE_DISCARD_END;case TURN_END->Timing.PHASE_TURN_END;
        default->throw new IllegalStateException("not a phase listener step");};}
    private boolean dispatch(SgsGameEngine e){
        var c=context(e,timing());if(listeners==null)listeners=e.skillBus().beginPhase(c);
        var request=listeners.advance(e.skillBus(),c);
        if(request.isPresent()){
            var a=request.get();pending=new TrickContinuation.Pending(e.nextPromptId(),PromptType.SKILL_INVOKE,a.owner(),actor,
                e.responseDeadlineMillis(),List.of(),false,a.skill(),++revision);return false;
        }
        if(!listeners.done())throw new IllegalStateException("phase failed to yield");
        skipped|=listeners.snapshot().skipped();listeners=null;return true;
    }
    void advance(SgsGameEngine e){
        if(pending()!=null||done())return;
        for(int budget=0;budget<32;budget++){
            if(e.state().status()!=GameStatus.RUNNING){step=Step.DONE;return;}
            switch(step){
                case BEFORE_DISCARD->{if(!dispatch(e))return;step=Step.ENTER_DISCARD;}
                case ENTER_DISCARD->{e.state().advancePhase();
                    if(skipped){e.state().advancePhase();step=Step.DISCARD_END;}else step=Step.DISCARD_START;}
                case DISCARD_START->{if(!dispatch(e))return;
                    if(e.state().currentPlayer().handCount()>e.state().handLimit(e.state().currentPlayer())){step=Step.DONE;return;}
                    e.state().advancePhase();step=Step.DISCARD_END;}
                case PAYMENT->{if(payment!=null){payment.advance(e);if(!payment.done())return;payment=null;}
                    if(!automaticDiscard)e.state().log("seat {} discarded {} confirmed cards",actor,discarded.size());
                    else if(!discarded.isEmpty())e.state().log("seat {} auto-discarded {} cards",actor,discarded.size());
                    if(e.state().currentPlayer().handCount()>e.state().handLimit(e.state().currentPlayer())){step=Step.DONE;return;}
                    e.state().advancePhase();step=Step.DISCARD_END;}
                case DISCARD_END->{if(!dispatch(e))return;step=Step.TURN_END;}
                case TURN_END->{if(!dispatch(e))return;step=Step.HANDOVER;}
                case HANDOVER->{if(e.state().status()==GameStatus.RUNNING)e.state().advancePhase();step=Step.DONE;return;}
                case DONE->{return;}
            }
        }
        throw new IllegalStateException("turn-end loop exceeded");
    }
    boolean invoke(SgsGameEngine e,boolean answer){
        if(payment!=null)return payment.invoke(e,answer);
        if(pending==null||listeners==null)return false;
        e.completePhaseInvoke(listeners,context(e,timing()),answer&&e.continuationNow()<=pending.deadline());
        pending=null;revision++;return true;
    }
    PromptRequest request(SgsGameEngine e){
        var p=pending();if(p==null)throw new IllegalStateException("turn-end has no decision");
        return new PromptRequest(p.id(),p.type(),e.state().players().get(p.asker()),e.state().players().get(p.subject()),
            List.of(),List.of(),p.deadline(),p.skill());
    }
    void validate(SgsGameEngine e){
        var s=e.state();var p=pending();
        if(actor>=s.players().size()||s.currentSeat()!=actor||s.turnNumber()!=turn||s.status()!=GameStatus.RUNNING||p==null
            ||p.id()<1||p.deadline()<0||p.type()!=PromptType.SKILL_INVOKE||!p.choices().isEmpty()||p.hidden()
            ||p.asker()<0||p.asker()>=s.players().size()||p.subject()!=actor)throw new IllegalArgumentException("turn-end input differs");
        if((step==Step.BEFORE_DISCARD||step==Step.DISCARD_START)&&(skipped||automaticDiscard||!discarded.isEmpty())
            ||skipped&&(automaticDiscard||!discarded.isEmpty())
            ||discarded.stream().anyMatch(id->s.cards().zones().stream().flatMap(z->s.cards().cards(z).stream()).noneMatch(c->c.instanceId()==id)))
            throw new IllegalArgumentException("turn-end payment mode differs");
        if(step==Step.PAYMENT){
            if(s.phase()!=Phase.DISCARD||listeners!=null||pending!=null||payment==null
                ||payment.snapshot().actor()!=actor||payment.snapshot().owner()!=actor||!payment.snapshot().cards().equals(discarded))
                throw new IllegalArgumentException("turn-end payment linkage differs");
            payment.validate(e);
            for(var move:payment.snapshot().queue().movements())if(!move.event().from().equals(CardZone.hand(actor)))
                throw new IllegalArgumentException("compulsory discard cannot pay equipment");
        }else{
            Phase expected=step==Step.BEFORE_DISCARD?Phase.PLAY:step==Step.DISCARD_START?Phase.DISCARD:Phase.END;
            if(!Set.of(Step.BEFORE_DISCARD,Step.DISCARD_START,Step.DISCARD_END,Step.TURN_END).contains(step)||s.phase()!=expected
                ||listeners==null||listeners.snapshot().awaiting()==null||payment!=null||p.asker()!=actor||p.revision()!=revision
                ||!p.skill().equals(listeners.snapshot().awaiting().skill()))throw new IllegalArgumentException("turn-end phase linkage differs");
            e.skillBus().validatePhase(listeners.snapshot(),context(e,timing()));
        }
    }
}
