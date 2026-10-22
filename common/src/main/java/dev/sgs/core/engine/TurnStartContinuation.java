package dev.sgs.core.engine;

import dev.sgs.core.*;
import dev.sgs.core.engine.skill.*;
import java.util.*;

/** Owns the draw fold and its movement children before opening the next human action window. */
public final class TurnStartContinuation {
    public enum Step { RULES, DRAW, MOVEMENTS, FINISH, DONE, PREPARE }
    public record Snapshot(int actor,int turn,Step step,RuleFoldProgram.Snapshot fold,
        CostMovementQueue.Snapshot movements,TrickContinuation.Pending pending,long revision,int drawn,PreparationProgram.Snapshot prepare) {
        public Snapshot(int actor,int turn,Step step,RuleFoldProgram.Snapshot fold,CostMovementQueue.Snapshot movements,TrickContinuation.Pending pending,long revision,int drawn){this(actor,turn,step,fold,movements,pending,revision,drawn,null);}
        public Snapshot {
            Objects.requireNonNull(step);Objects.requireNonNull(movements);
            if(actor<0||actor>7||turn<0||revision<0||revision==Long.MAX_VALUE||drawn<0||drawn>160
                ||(step==Step.PREPARE)!=(prepare!=null)||step==Step.PREPARE&&(fold!=null||drawn!=0||prepare.actor()!=actor||prepare.turn()!=turn)
                ||step!=Step.PREPARE&&(fold==null||fold.actor()!=actor||fold.turn()!=turn||fold.rule()!=RuleId.DRAW_COUNT||step!=Step.RULES&&!fold.done()))throw new IllegalArgumentException("invalid turn-start graph");
        }
    }
    private final int actor,turn;private Step step;private RuleFoldProgram fold;private final CostMovementQueue movements;
    private TrickContinuation.Pending pending;private long revision;private int drawn;private PreparationProgram prepare;
    TurnStartContinuation(SgsGameEngine e){actor=e.state().currentSeat();turn=e.state().turnNumber();step=Step.PREPARE;
        prepare=e.skillBus().beginPreparation(preparationContext(e));movements=new CostMovementQueue();}
    TurnStartContinuation(Snapshot s,long captured,long now){actor=s.actor();turn=s.turn();step=s.step();fold=s.fold()==null?null:new RuleFoldProgram(s.fold());prepare=s.prepare()==null?null:new PreparationProgram(s.prepare());
        movements=new CostMovementQueue(s.movements(),captured,now);revision=s.revision();drawn=s.drawn();var p=s.pending();
        if(p!=null)pending=new TrickContinuation.Pending(p.id(),p.type(),p.asker(),p.subject(),DecisionBoundary.restoredDeadline(p.deadline(),captured,now),p.choices(),p.hidden(),p.skill(),p.revision());}
    public Snapshot snapshot(){return new Snapshot(actor,turn,step,fold==null?null:fold.snapshot(),movements.snapshot(),pending,revision,drawn,prepare==null?null:prepare.snapshot());}
    private SkillContext context(SgsGameEngine e){return SkillContext.forPlayer(e.state(),RuleId.DRAW_COUNT,e.state().players().get(actor));}
    private SkillContext preparationContext(SgsGameEngine e){return SkillContext.phase(e.state(),Timing.PHASE_PREPARE_START,e.state().players().get(actor));}
    boolean done(){return step==Step.DONE;}
    TrickContinuation.Pending pending(){return movements.pending()!=null?movements.pending():pending;}
    void advance(SgsGameEngine e){
        if(pending()!=null||done())return;
        for(int budget=0;budget<512;budget++){
            if(!movements.advance(e))return;
            switch(step){
                case PREPARE->{
                    var request=e.captureRuleMovements(movements,()->prepare.advance(e.skillBus(),preparationContext(e)));
                    if(request.isPresent()){
                        var a=request.get();pending=new TrickContinuation.Pending(e.nextPromptId(),a.type(),a.owner(),actor,e.responseDeadlineMillis(),a.cards(),false,a.skill(),++revision);return;
                    }
                    if(prepare.done()){prepare=null;e.enterResumableDrawPrefix();fold=e.skillBus().beginRule(RuleId.DRAW_COUNT,context(e),e.state().normalDrawCount());step=Step.RULES;}
                }
                case RULES->{
                    var request=fold.advance(e.skillBus(),context(e));
                    if(request.isPresent()){
                        var a=request.get();var binding=fold.snapshot().registry().get(fold.snapshot().cursor());
                        if(binding.owner()!=actor)throw new IllegalStateException("draw rule owner differs");
                        pending=new TrickContinuation.Pending(e.nextPromptId(),a.type(),actor,actor,e.responseDeadlineMillis(),List.of(),a.hidden(),binding.id(),++revision);return;
                    }
                    if(!fold.done())throw new IllegalStateException("draw fold failed to yield");step=Step.DRAW;
                }
                case DRAW->{drawn=e.captureRuleMovements(movements,()->e.drawCards(e.state().players().get(actor),Math.max(0,fold.snapshot().value())).size());step=Step.MOVEMENTS;}
                case MOVEMENTS->{e.state().log("seat {} drew {} cards",actor,drawn);step=Step.FINISH;}
                case FINISH->{e.finishResumableDraw();step=Step.DONE;return;}
                case DONE->{return;}
            }
        }
        throw new IllegalStateException("draw fold loop exceeded");
    }
    boolean answer(SgsGameEngine e,boolean invoke,Optional<SkillChoice> choice){
        if(movements.pending()!=null)return movements.invoke(e,invoke);
        if(pending==null)return false;var p=pending;boolean expired=e.continuationNow()>p.deadline();
        e.captureRuleMovements(movements,()->{
            if(prepare!=null)prepare.answer(e.skillBus(),preparationContext(e),invoke&&!expired,expired?Optional.empty():choice);
            else e.completeRuleInput(fold,context(e),invoke&&!expired,expired?Optional.empty():choice);return 0;});
        pending=null;revision++;return true;
    }
    PromptRequest request(SgsGameEngine e){
        var p=pending();if(p==null)throw new IllegalStateException("draw has no decision");
        var targets=movements.pending()!=null?List.<Integer>of():prepare!=null?prepare.request().orElseThrow().destinations():fold.snapshot().awaiting().targets();
        var choices=p.choices().stream().map(id->e.state().cards().processingCards().stream().filter(c->c.instanceId()==id).findFirst().orElseThrow()).toList();
        return new PromptRequest(p.id(),p.type(),e.state().players().get(p.asker()),e.state().players().get(p.subject()),List.of(),choices,p.deadline(),p.skill(),targets,p.hidden());
    }
    void validate(SgsGameEngine e){
        var s=e.state();var p=pending();
        if(prepare!=null){
            e.skillBus().validatePreparation(prepare.snapshot(),preparationContext(e));var request=prepare.request().orElseThrow(()->new IllegalArgumentException("preparation not awaiting"));
            if(s.status()!=GameStatus.RUNNING||s.currentSeat()!=actor||s.turnNumber()!=turn||s.phase()!=Phase.PREPARE||p==null||movements.pending()!=null||!movements.snapshot().movements().isEmpty()
                ||p.type()!=request.type()||p.asker()!=actor||request.owner()!=actor||p.subject()!=actor||p.hidden()||p.revision()!=revision||!p.skill().equals(request.skill())||!p.choices().equals(request.cards()))
                throw new IllegalArgumentException("preparation request linkage differs");
            if(prepare.snapshot().order()==null&&!s.cards().processingCards().isEmpty())throw new IllegalArgumentException("cards revealed before preparation invocation");
            return;
        }
        if(actor>=s.players().size()||s.status()!=GameStatus.RUNNING||s.currentSeat()!=actor||s.turnNumber()!=turn||s.phase()!=Phase.DRAW
            ||s.skipPlayPhase()||s.skipDrawPhase()||p==null||!Set.of(Step.RULES,Step.MOVEMENTS).contains(step)
            ||step==Step.RULES&&drawn!=0)throw new IllegalArgumentException("draw parent context differs");
        e.skillBus().validateRule(fold.snapshot(),context(e));
        if(movements.pending()!=null){if(pending!=null)throw new IllegalArgumentException("two draw questions");movements.validate(e);}
        else{
            var a=fold.snapshot().awaiting();var binding=fold.snapshot().registry().get(fold.snapshot().cursor());
            if(step!=Step.RULES||a==null||p.type()!=a.type()||p.asker()!=actor||p.subject()!=actor||p.hidden()!=a.hidden()
                ||p.revision()!=revision||!p.skill().equals(binding.id())||binding.owner()!=actor||!movements.snapshot().movements().isEmpty())
                throw new IllegalArgumentException("draw question linkage differs");
        }
    }
}
