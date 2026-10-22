package dev.sgs.core.engine.skill;

import dev.sgs.core.engine.*;
import java.util.*;

/** One resumable rule body. Progress belongs to the fold, never to a suspended Java call. */
public interface AwaitableRuleEffect extends RuleModifier {
    record Progress(int value,List<Integer> selected,boolean took,boolean complete) {
        public Progress {
            selected=List.copyOf(selected);
            if(Math.abs((long)value)>1_000_000||selected.size()>8||new HashSet<>(selected).size()!=selected.size()
                ||selected.stream().anyMatch(s->s<0||s>7)||took&&selected.isEmpty())throw new IllegalArgumentException("invalid rule progress");
        }
    }
    record Request(PromptType type,List<Integer> targets,boolean hidden) {
        public Request {
            targets=List.copyOf(targets);
            if(type!=PromptType.SKILL_INVOKE&&type!=PromptType.TUXI_TARGET||targets.size()>8
                ||new HashSet<>(targets).size()!=targets.size()||targets.stream().anyMatch(s->s<0||s>7)
                ||type==PromptType.SKILL_INVOKE&&(!targets.isEmpty()||hidden)
                ||type==PromptType.TUXI_TARGET&&(targets.isEmpty()||!hidden))throw new IllegalArgumentException("invalid rule request");
        }
    }
    default Progress beginRule(int value){return new Progress(value,List.of(),false,false);}
    Request nextRule(SkillContext c,Progress progress);
    Progress completeRule(SkillContext c,Progress progress,Optional<SkillChoice> choice);
    default int finishRule(SkillContext c,Progress progress){return progress.value();}
    default void validateRuleProgress(SkillContext c,Progress progress){
        if(!progress.selected().isEmpty()||progress.took())throw new IllegalArgumentException("unexpected rule selection");
    }
}
