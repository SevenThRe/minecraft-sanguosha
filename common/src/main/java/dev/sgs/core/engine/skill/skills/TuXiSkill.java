package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.SkillChoice;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;
/** Standard Tuxi replaces the complete normal draw with one hand card from up to two others. */
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
    int seat=choice.get().targetSeat();if(!candidates(c,p.selected()).contains(seat)||p.selected().size()>=2)throw new IllegalArgumentException("invalid Tuxi target");
    var chosen=new ArrayList<>(p.selected());chosen.add(seat);boolean took=take(c,seat);
    return new Progress(p.value(),chosen,p.took()||took,false);
  }
  public int finishRule(SkillContext c,Progress p){if(p.took()){c.state().publishSkillActivation(owner.seat(),id());return 0;}return p.value();}
  public void validateRuleProgress(SkillContext c,Progress p){
    if(p.selected().size()>2||p.selected().contains(owner.seat())||p.took()!=!p.selected().isEmpty()
        ||p.selected().stream().anyMatch(s->s>=c.state().players().size()))throw new IllegalArgumentException("invalid Tuxi progress");
  }
  public int modifyInt(SkillContext c,int count){
    var selected=new HashSet<Integer>();boolean took=false;
    for(int i=0;i<2 && owner.alive();i++){
      var candidates=candidates(c,selected);
      if(candidates.isEmpty())break;
      var choice=api.requestSkillChoice(owner,owner,PromptType.TUXI_TARGET,id(),List.of(),candidates,true);
      if(choice.isEmpty())break;
      var source=c.state().players().get(choice.get().targetSeat());selected.add(source.seat());
      if(take(c,source.seat()))took=true;
    }
    if(took){c.state().publishSkillActivation(owner.seat(),id());return 0;}return count;
  }
}
