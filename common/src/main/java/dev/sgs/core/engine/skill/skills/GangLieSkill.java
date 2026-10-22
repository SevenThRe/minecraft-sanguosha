package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
/** One invocation per damage event, with actual two-hand-card atomic cost or one damage. */
public final class GangLieSkill implements AwaitableDamageEffect {
  public Kind damageEffect(){return Kind.JUDGMENT_OR_RETALIATION;}
  private final SgsPlayer owner;private final SkillEngine api;
  public GangLieSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "ganglie";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.DAMAGE_TAKE_AFTER;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.target()==owner && owner.alive() && owner.hp()>0 && c.damage()>0;}
  public void execute(SkillContext c){
    if(!api.requestSkillInvoke(owner,id(),c.source()))return;
    c.confirmPublicActivation();var judged=api.judgeForSkill(owner,id(),false);var source=c.source();
    if(judged==null || judged.suit()==Suit.HEART || source==null || !source.alive())return;
    var costs=new ArrayList<CardInstance>();
    if(source.handCount()>=2)for(int i=0;i<2;i++){
      var choices=source.handView().stream().filter(card->costs.stream().noneMatch(chosen->chosen.instanceId()==card.instanceId())).toList();
      var choice=api.requestSkillChoice(source,owner,PromptType.GANGLIE_DISCARD,id(),choices,List.of(),false);
      if(choice.isEmpty())break;
      var card=choices.stream().filter(x->x.instanceId()==choice.get().cardId()).findFirst().orElse(null);if(card==null)break;costs.add(card);
    }
    if(costs.size()==2 && costs.stream().allMatch(card->CardZone.hand(source.seat()).equals(c.state().cards().locate(card.instanceId()))))api.discardCards(source,costs);
    else if(source.alive())api.dealSkillDamage(owner,source,1);
  }
}
