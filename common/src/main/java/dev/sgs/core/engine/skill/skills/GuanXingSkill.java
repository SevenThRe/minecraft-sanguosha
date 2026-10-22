package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
/** Privately reorder up to min(alive,5) physical cards; no cards ever enter the owner's hand. */
public final class GuanXingSkill implements AwaitableDeckOrderEffect {
  private final SgsPlayer owner;private final SkillEngine api;
  public GuanXingSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "guanxing";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.PHASE_PREPARE_START;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.actor()==owner && owner.alive() && owner.hp()>0;}
  public int privateDeckCount(SkillContext c){return Math.min(5,(int)c.state().players().stream().filter(SgsPlayer::alive).count());}
  public void execute(SkillContext c){
    if(!api.requestSkillInvoke(owner,id(),owner))return;
    var pending=new ArrayList<>(revealPrivateDeck(c));
    var top=new ArrayList<CardInstance>();var bottom=new ArrayList<CardInstance>();
    try{while(!pending.isEmpty() && owner.alive()){
      var choice=api.requestSkillChoice(owner,owner,PromptType.GUANXING_ORDER,id(),pending,List.of(0,1),false);
      if(choice.isEmpty())break;
      var card=pending.stream().filter(x->x.instanceId()==choice.get().cardId()).findFirst().orElse(null);
      if(card==null || choice.get().targetSeat()<0 || choice.get().targetSeat()>1)break;
      pending.remove(card);if(choice.get().targetSeat()==0)top.add(card);else bottom.add(card);
    }}finally{top.addAll(pending);returnPrivateDeck(c,top,bottom);}
    finishPrivateDeck(c);
  }
}
