package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.SkillChoice;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
/** Tuxi replaces each chosen draw with one hand card from a different target. */
public final class TuXiSkill implements AwaitableRuleEffect {
  private final SgsPlayer owner;private final SkillEngine api;
  public TuXiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "tuxi";}public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.DRAW_COUNT;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.actor()==owner && owner.alive();}
  private List<Integer> candidates(SkillContext c,Collection<Integer> selected){return c.state().players().stream()
      .filter(p->p!=owner&&p.alive()&&p.handCount()>0&&!selected.contains(p.seat())).map(SgsPlayer::seat).toList();}
  private boolean take(SkillContext c,int seat){return api.takeFeedbackCard(owner,c.state().players().get(seat),new SkillChoice(0,-1));}
  public Request nextRule(SkillContext c,Progress p){
    var targets=candidates(c,p.selected());return p.complete()||p.selected().size()==2||!owner.alive()||targets.isEmpty()?null:new Request(PromptType.TUXI_TARGET,targets,true);
  }
  public Progress completeRule(SkillContext c,Progress p,Optional<SkillChoice> choice){
    if(choice.isEmpty())return new Progress(p.value(),p.selected(),p.took(),true);
    var answer=choice.get();var allowed=candidates(c,p.selected());
    if(!answer.validTuxi(allowed)||p.selected().size()+answer.selectedTargets().size()>2)throw new IllegalArgumentException("invalid Tuxi targets");
    var chosen=new ArrayList<>(p.selected());
    for(int seat:answer.selectedTargets())if(take(c,seat))chosen.add(seat);
    return new Progress(p.value(),chosen,!chosen.isEmpty(),true);
  }
  public int finishRule(SkillContext c,Progress p){
    if(p.took())c.state().publishSkillActivation(owner.seat(),id());
    return Math.max(0,p.value()-p.selected().size());
  }
  public void validateRuleProgress(SkillContext c,Progress p){
    if(p.selected().size()>2||p.selected().contains(owner.seat())||p.took()!=!p.selected().isEmpty()
        ||p.selected().stream().anyMatch(s->s>=c.state().players().size()))throw new IllegalArgumentException("invalid Tuxi progress");
  }
  public int modifyInt(SkillContext c,int count){
    var p=beginRule(count);var request=nextRule(c,p);
    if(request==null)return count;
    var choice=api.requestSkillChoice(owner,owner,PromptType.TUXI_TARGET,id(),List.of(),request.targets(),true);
    return finishRule(c,completeRule(c,p,choice));
  }
}
