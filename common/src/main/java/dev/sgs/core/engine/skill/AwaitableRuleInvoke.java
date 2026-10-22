package dev.sgs.core.engine.skill;

import dev.sgs.core.engine.*;
import java.util.*;

/** Existing modifyInt body consumes exactly one injected invocation, preserving its rules. */
public interface AwaitableRuleInvoke extends AwaitableRuleEffect {
    default Request nextRule(SkillContext c,Progress p){return p.complete()?null:new Request(PromptType.SKILL_INVOKE,List.of(),false);}
    default Progress completeRule(SkillContext c,Progress p,Optional<SkillChoice> ignored){
        if(p.complete())throw new IllegalStateException("rule body already completed");
        return new Progress(modifyInt(c,p.value()),List.of(),false,true);
    }
}
