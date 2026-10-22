package dev.sgs.core.persistence;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import dev.sgs.core.game.GameState;
import java.util.*;

/** Parent ownership and shared attack validation without inventing a turn or duplicating world data. */
public final class StrikeFrames {
  private StrikeFrames(){}
  public static AttackContinuation.PendingSnapshot pending(TrickContinuation.Snapshot c){
    var f=c.family();if(f==null||f.strike()==null)return null;var s=f.strike();return s.attack()!=null?s.attack().pending():s.supply().pending();
  }
  public static int dying(TrickContinuation.Snapshot c,int fallback){var f=c.family();if(f==null||f.strike()==null||f.strike().attack()==null)return fallback;var a=f.strike().attack();return a.damageFrames().isEmpty()?fallback:a.damageFrames().getFirst().targetSeat();}
  private static Set<Long> candidates(StrikeContinuation.Snapshot s){
    var ids=new HashSet<Long>();if(s.supply().receipt()!=null)s.supply().receipt().costs().forEach(c->ids.add(c.id()));
    var a=s.attack();if(a!=null){ids.add(a.cardId());ids.addAll(a.extraCosts());ids.addAll(a.parents());ids.addAll(DamageProgramContinuation.processingCosts(a.damageProgram()));if(a.ruleQuery()!=null&&a.ruleQuery().judgment()!=null)ids.add(a.ruleQuery().judgment().effective());if(a.responseProgram()!=null&&a.responseProgram().judgmentCard()>0)ids.add(a.responseProgram().judgmentCard());}
    return ids;
  }
  public static Set<Long> processing(StrikeContinuation.Snapshot s,GameState state){var ids=candidates(s);ids.removeIf(id->!CardZone.PROCESSING.equals(state.cards().locate(id)));return ids;}
  public static void validate(TrickCheckpoint root){
    var c=root.continuation();var f=c.family();var s=f==null?null:f.strike();
    if(c.effectiveType()!=CardType.JIEDAO){if(s!=null||c.step()==TrickContinuation.Step.STRIKE)throw new IllegalArgumentException("unexpected strike child");return;}
    if(root.version()<10||s==null)throw new IllegalArgumentException("missing strike child");StrikeContinuation.validate(s);
    if(s.supply().ownType()!=PromptType.JIEDAO_RESPONSE)throw new IllegalArgumentException("borrowed strike lost its own-card/provider policy");
    int n=root.state().players().size();var supply=s.supply();
    if(supply.user()!=c.target()||supply.target()>=n||supply.user()==c.source()||f.targets().size()!=1||f.response()!=null||f.damage()!=null||f.revealCost()!=null||f.program()!=null)throw new IllegalArgumentException("invalid borrowed strike ownership");
    if(c.step()!=TrickContinuation.Step.STRIKE){
      if(s.attack()!=null||supply.pending()!=null||supply.step()!=AttackSupplyContinuation.Step.START)throw new IllegalArgumentException("strike escaped its parent phase");
    }else if(c.window()!=null||!c.counters().isEmpty()||c.depth()%2!=0)throw new IllegalArgumentException("strike overlaps counterspell window");
    var p=pending(c);if(p!=null&&(p.id()!=root.promptSequence()||p.deadlineMillis()<0))throw new IllegalArgumentException("strike input sequence mismatch");
    var a=s.attack();if(a!=null){
      new ContinuationCheckpoint(a.version(),root.state(),root.promptSequence(),root.responseTimeoutSeconds(),root.skillMemory(),root.bindings(),root.skillSequence(),root.activations(),a,root.capturedAtMillis()).validate();
      var initial=supply.receipt().costs().stream().map(AttackSupplyContinuation.Cost::id).toList();
      var actual=a.parents().isEmpty()?new ArrayList<Long>():new ArrayList<>(a.parents().subList(0,a.parentSizes().getFirst()));if(a.parents().isEmpty()){actual.add(a.cardId());actual.addAll(a.extraCosts());}
      if(!initial.equals(actual)||initial.contains(c.cardId())||a.parents().contains(c.cardId())||a.cardId()==c.cardId()||a.extraCosts().contains(c.cardId()))throw new IllegalArgumentException("aliased strike payment or changed initial cost");
    }
  }
}
