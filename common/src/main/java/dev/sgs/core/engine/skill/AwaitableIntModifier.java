package dev.sgs.core.engine.skill;

/** Declares a choice-bearing rule effect without calling a responder during a resumable query.
 * modifyInt remains the independent synchronous implementation for legacy execution.
 */
public interface AwaitableIntModifier extends RuleModifier {
  enum Effect { INVOKE_SET, INVOKE_RED_JUDGMENT_SET }
  record Request(int owner,String skill,Effect effect,int value) {
    public Request {if(owner<0 || owner>7 || skill==null || !skill.matches("[a-z][a-z0-9_]{0,63}") || effect==null)throw new IllegalArgumentException("invalid awaited rule effect");}
  }
  Request awaitedEffect(SkillContext context,int current);
}
