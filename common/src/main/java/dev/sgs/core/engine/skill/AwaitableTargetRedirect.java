package dev.sgs.core.engine.skill;

/** The original registered body can instead be driven by a data-owned discard/redirect program. */
public interface AwaitableTargetRedirect extends SkillListener {
  record Request(int owner,String skill){
    public Request{if(owner<0||owner>7||skill==null||!skill.matches("(?:[a-z][a-z0-9_]{1,31}:)?[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("invalid redirect binding");}
  }
}
