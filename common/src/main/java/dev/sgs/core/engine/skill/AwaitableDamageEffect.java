package dev.sgs.core.engine.skill;

/** A damage-after listener whose body can run outside a blocked listener invocation. */
public interface AwaitableDamageEffect extends SkillListener {
  enum Kind { DISTRIBUTE_NEW_CARDS, TAKE_SOURCE_CARD, JUDGMENT_OR_RETALIATION }
  record Request(int owner,String skill,Kind kind) {
    public Request {if(owner<0 || owner>7 || skill==null || !skill.matches("[a-z][a-z0-9_]{0,63}") || kind==null)throw new IllegalArgumentException("invalid damage effect binding");}
  }
  Kind damageEffect();
}
