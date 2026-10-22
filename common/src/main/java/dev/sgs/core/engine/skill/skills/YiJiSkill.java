package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;

/** Original YiJi: privately inspect two NEW cards per damage point, then assign them one by one. */
public final class YiJiSkill implements AwaitableDamageEffect {
  public Kind damageEffect(){return Kind.DISTRIBUTE_NEW_CARDS;}
  private final SgsPlayer owner;private final SkillEngine api;
  public YiJiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "yiji";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.DAMAGE_TAKE_AFTER;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.target()==owner && owner.alive() && owner.hp()>0 && c.damage()>0;}
  public void execute(SkillContext c){
    for(int point=0;point<c.damage() && owner.alive() && owner.hp()>0;point++){
      if(!api.requestSkillInvoke(owner,id(),owner))continue;
      var pending=new ArrayList<>(api.drawCards(owner,2));
      // Each accepted point is a distinct activation, rather than one banner for an entire hit.
      c.state().publishSkillActivation(owner.seat(),id());
      while(!pending.isEmpty() && owner.alive()){
        pending.removeIf(card->!CardZone.hand(owner.seat()).equals(c.state().cards().locate(card.instanceId())));
        if(pending.isEmpty())break;
        var seats=c.state().players().stream().filter(SgsPlayer::alive).map(SgsPlayer::seat).toList();
        var choice=api.requestSkillChoice(owner,owner,PromptType.YIJI_GIVE,id(),pending,seats,false);
        if(choice.isEmpty())break; // timeout/decline keeps the remaining newly drawn cards
        var card=pending.stream().filter(x->x.instanceId()==choice.get().cardId()).findFirst().orElse(null);
        var target=c.state().players().stream().filter(p->p.seat()==choice.get().targetSeat() && p.alive()).findFirst().orElse(null);
        if(card==null || target==null)break;
        pending.remove(card);if(target!=owner)api.transferCard(owner,target,card);
      }
    }
  }
}
