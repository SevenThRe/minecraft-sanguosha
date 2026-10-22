package dev.sgs.core.addon;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.List;
/** Reuses the existing event/rule/active APIs, with no second event abstraction. */
public record SkillBinding(List<ActiveSkill> active,List<SkillListener> listeners,List<RuleModifier> modifiers){
  public SkillBinding{active=List.copyOf(active);listeners=List.copyOf(listeners);modifiers=List.copyOf(modifiers);}
  public static SkillBinding active(ActiveSkill skill){return new SkillBinding(List.of(skill),List.of(),List.of());}
  public static SkillBinding listener(SkillListener skill){return new SkillBinding(List.of(),List.of(skill),List.of());}
  public void validate(String id,SgsPlayer owner){
    if(active.size()>1 || active.size()+listeners.size()+modifiers.size()==0)throw new IllegalArgumentException("invalid skill binding count");
    for(var s:active)if(!s.id().equals(id) || s.owner()!=owner)throw new IllegalArgumentException("active binding ID/owner mismatch");
    for(var s:listeners)if(!s.id().equals(id) || s.owner()!=owner)throw new IllegalArgumentException("listener binding ID/owner mismatch");
    for(var s:modifiers)if(!s.id().equals(id) || s.owner()!=owner)throw new IllegalArgumentException("modifier binding ID/owner mismatch");
  }
}
