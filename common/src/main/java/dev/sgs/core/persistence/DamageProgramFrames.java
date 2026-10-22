package dev.sgs.core.persistence;

import dev.sgs.core.engine.*;
import java.util.*;

/** Shared root/return/elemental-chain graph validation, independent of any skill body. */
final class DamageProgramFrames {
  static void validate(TrickCheckpoint cp,DamageNature nature){
    var root=cp.continuation();var family=root.family();var program=family.program();
    if(program==null||!program.ordered()||family.response()!=null)throw new IllegalArgumentException("missing ordered damage graph");
    DamageProgramContinuation.validate(program);
    int source=root.effectiveType()==dev.sgs.core.card.CardType.JUEDOU?family.duelOther():root.source(),target=root.effectiveType()==dev.sgs.core.card.CardType.JUEDOU?family.duelResponder():root.target();boolean cardless=false;
    for(var parent:program.parents().reversed()){
      leaf(cp,parent.damage(),source,target,cardless,nature);
      cp.validateGanglie(parent.reaction(),parent.damage(),cp.state().players().size(),true);
      source=parent.reaction().owner();target=parent.reaction().source();cardless=true;nature=DamageNature.NORMAL;
    }
    var d=program.damage();leaf(cp,d,source,target,cardless,nature);
    if(family.ganglie()!=null)cp.validateGanglie(family.ganglie(),d,cp.state().players().size(),false);
    var expected=switch(program.step()){case DAMAGE,RESCUE_COST->TrickContinuation.Step.DAMAGE;case DISTRIBUTE->TrickContinuation.Step.DAMAGE_SKILL;case TAKE->TrickContinuation.Step.DAMAGE_FANKUI;case TAKE_FINISH->TrickContinuation.Step.DAMAGE_FANKUI_FINISH;case REACTION->TrickContinuation.Step.DAMAGE_GANGLIE;case DONE->throw new IllegalArgumentException("finished graph still retained");};
    if(root.step()!=expected)throw new IllegalArgumentException("damage graph parent phase differs");
    if(program.rescueCost()!=null){if(root.pending()==null||!Objects.equals(root.pending(),program.pending()))throw new IllegalArgumentException("rescue graph boundary differs");return;}
    if(d.pending()!=null){cp.checkPrompt(d.pending(),cp.state().players().size());if(d.pending().id()!=cp.promptSequence()||d.pending().stateRevision()!=d.revision())throw new IllegalArgumentException("damage graph request differs");}
    else if(program.effect()==null&&!legacyRescueMovement(cp)&&(root.movements().isEmpty()||d.damageFrames().getFirst().step()!=AttackContinuation.DamageStep.AFTER_DEFERRED))throw new IllegalArgumentException("damage graph has no yielded boundary");
  }
  /** Old roots paid/recovered before dispatching the queued departure. Do not rewrite their order. */
  static boolean legacyRescueMovement(TrickCheckpoint cp){
    var root=cp.continuation();var family=root.family();
    if(cp.version()>11||family==null||family.damage()==null||family.damage().pending()!=null||family.damage().damageFrames().isEmpty()
        ||family.program()!=null&&family.program().awaitRescueCosts()||root.movements().isEmpty()||root.pending()==null||root.pending().type()!=PromptType.SKILL_INVOKE)return false;
    var frame=family.damage().damageFrames().getFirst();var move=root.movements().getFirst();
    if(frame.step()!=AttackContinuation.DamageStep.RESCUE||!frame.playedThisRound()||frame.rescueCursor()<1||frame.rescueCursor()>frame.rescueRing().size())return false;
    int payer=frame.rescueRing().get(frame.rescueCursor()-1);if(payer<0||payer>=cp.state().players().size()||root.pending().asker()!=payer||root.pending().subject()!=payer
        ||move.actor()!=payer||move.reason()!=dev.sgs.core.card.CardMove.Reason.USE||move.from().seat()!=payer
        ||!Set.of(dev.sgs.core.card.CardZone.Kind.HAND,dev.sgs.core.card.CardZone.Kind.EQUIPMENT).contains(move.from().kind())||!move.to().equals(dev.sgs.core.card.CardZone.PROCESSING)||move.cards().size()!=1)return false;
    var card=cp.state().zones().stream().filter(z->z.location().equals(dev.sgs.core.card.CardZone.DISCARD)).flatMap(z->z.cards().stream()).filter(c->c.id()==move.cards().getFirst()).findFirst().orElse(null);
    return card!=null&&(card.type()==dev.sgs.core.card.CardType.TAO||card.type()==dev.sgs.core.card.CardType.JIU&&payer==frame.targetSeat()
        ||card.suit().red()&&payer!=cp.state().turn().currentSeat()&&cp.state().players().get(payer).hero().skillIds().contains("jijiu"));
  }
  private static void leaf(TrickCheckpoint cp,AttackContinuation.Snapshot d,int source,int target,boolean cardless,DamageNature nature){
    var root=cp.continuation();int n=cp.state().players().size();
    if(d.sourceSeat()!=source||d.targetSeat()!=target||d.cardless()!=cardless||d.cardId()!=root.cardId()||d.effectiveType()!=root.effectiveType()||d.baseDamage()!=1||d.damageFrames().isEmpty()||d.step()!=AttackContinuation.AttackStep.DAMAGE)throw new IllegalArgumentException("damage graph cause differs");
    int previous=-1;var seen=new HashSet<Integer>();boolean chain=d.damageFrames().getFirst().chainDamage();
    if(!chain&&(d.damageFrames().size()!=1||d.damageFrames().getFirst().targetSeat()!=target)||chain&&(!nature.elemental()||cardless))throw new IllegalArgumentException("invalid elemental queue origin");
    for(int i=0;i<d.damageFrames().size();i++){
      var frame=d.damageFrames().get(i);int seat=frame.targetSeat();
      if(seat<0||seat>=n||!seen.add(seat)||frame.chainDamage()!=chain||frame.nature()!=nature||frame.amount()<1||frame.amount()>1000000||i>0&&frame.step()!=AttackContinuation.DamageStep.BEFORE)throw new IllegalArgumentException("invalid elemental queue instruction");
      int position=Math.floorMod(seat-cp.state().turn().currentSeat(),n);if(chain&&position<=previous)throw new IllegalArgumentException("elemental queue order differs");previous=position;
      if(new HashSet<>(frame.rescueRing()).size()!=frame.rescueRing().size()||frame.rescueRing().stream().anyMatch(s->s<0||s>=n))throw new IllegalArgumentException("invalid damage rescue actors");
    }
  }
}
