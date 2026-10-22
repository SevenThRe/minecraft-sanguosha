package dev.sgs.core.persistence;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.*;
import java.util.*;

/** Physical ownership and phase contract of the data-owned reveal/cost child. */
final class RevealCostFrames {
  static void validate(TrickCheckpoint cp){
    var root=cp.continuation();var f=root.family();var child=f==null?null:f.revealCost();
    if(cp.version()<9&&(child!=null||root.step()==TrickContinuation.Step.REVEAL_COST)||root.effectiveType()!=CardType.HUOGONG&&(child!=null||root.step()==TrickContinuation.Step.REVEAL_COST))throw new IllegalArgumentException("unexpected reveal/cost child");
    if(root.effectiveType()!=CardType.HUOGONG)return;
    if(cp.version()<9)throw new IllegalArgumentException("fire attack needs typed reveal/cost format");
    boolean damage=f.damage()!=null;
    if((root.step()==TrickContinuation.Step.REVEAL_COST||damage)&&child==null)throw new IllegalArgumentException("missing reveal/cost child");
    if(child==null)return;
    if(root.window()!=null||!root.counters().isEmpty()||root.depth()%2!=0||!Set.of(TrickContinuation.Step.REVEAL_COST,TrickContinuation.Step.DAMAGE,TrickContinuation.Step.DAMAGE_SKILL,TrickContinuation.Step.DAMAGE_FANKUI,TrickContinuation.Step.DAMAGE_FANKUI_FINISH,TrickContinuation.Step.DAMAGE_GANGLIE,TrickContinuation.Step.TARGET_NEXT).contains(root.step()))throw new IllegalArgumentException("reveal/cost overlaps a different parent phase");
    if(child.source()!=root.source()||child.target()!=root.target()||child.step()==null||child.shown()<0||child.cost()<0||child.shown()==root.cardId()||child.cost()==root.cardId())throw new IllegalArgumentException("invalid reveal/cost ownership");
    var cards=new HashMap<Long,StateCheckpoint.Card>();var zones=new HashMap<Long,CardZone>();for(var zone:cp.state().zones())for(var card:zone.cards()){cards.put(card.id(),card);zones.put(card.id(),zone.location());}
    if(child.shown()>0&&!cards.containsKey(child.shown())||child.cost()>0&&!cards.containsKey(child.cost()))throw new IllegalArgumentException("missing reveal/cost physical entity");
    boolean waiting=child.step()==RevealCostContinuation.Step.REVEAL||child.step()==RevealCostContinuation.Step.PAY;
    if(waiting!=(child.pending()!=null)||waiting&&root.step()!=TrickContinuation.Step.REVEAL_COST||damage&&child.step()!=RevealCostContinuation.Step.COMMITTED)throw new IllegalArgumentException("reveal/cost phase differs");
    if(child.step()==RevealCostContinuation.Step.REVEAL&&(child.shown()!=0||child.cost()!=0)||child.step()==RevealCostContinuation.Step.PAY&&(child.shown()==0||child.cost()!=0)||child.step()==RevealCostContinuation.Step.COMMITTED&&(child.shown()==0||child.cost()==0)||child.step()==RevealCostContinuation.Step.DECLINED&&child.cost()!=0)throw new IllegalArgumentException("invalid reveal/cost payment progress");
    if(child.step()==RevealCostContinuation.Step.COMMITTED&&cards.get(child.shown()).suit()!=cards.get(child.cost()).suit())throw new IllegalArgumentException("cost suit does not match revealed card");
    if(!waiting)return;
    var actor=cp.state().players().get(child.source());var victim=cp.state().players().get(child.target());
    if(!actor.alive()||actor.hp()<1||!victim.alive()||victim.hp()<1||cp.state().zones().stream().filter(z->z.location().equals(CardZone.hand(child.target()))).mapToInt(z->z.cards().size()).sum()==0)throw new IllegalArgumentException("reveal/cost actor is not available");
    boolean reveal=child.step()==RevealCostContinuation.Step.REVEAL;var p=child.pending();
    if(p.id()!=cp.promptSequence()||p.revision()!=root.revision()||p.type()!=(reveal?PromptType.HUOGONG_REVEAL:PromptType.HUOGONG_PAY)||p.asker()!=(reveal?child.target():child.source())||p.subject()!=(reveal?child.source():child.target())||!p.skill().equals("huogong")||p.hidden()||!p.choices().equals(reveal?List.of():List.of(child.shown()))||p.deadline()<0||p.deadline()-cp.capturedAtMillis()>cp.responseTimeoutSeconds()*1000L)throw new IllegalArgumentException("invalid reveal/cost request");
    if(!reveal&&!CardZone.hand(child.target()).equals(zones.get(child.shown())))throw new IllegalArgumentException("revealed card left target hand before payment");
  }
}
