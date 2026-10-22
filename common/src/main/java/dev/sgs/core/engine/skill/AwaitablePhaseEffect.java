package dev.sgs.core.engine.skill;

/** One optional invocation followed by a synchronous phase body, on its original registration. */
public interface AwaitablePhaseEffect extends SkillListener {
    record Request(int owner,String skill) {
        public Request {
            if(owner<0||owner>7||skill==null||!skill.matches("[a-z][a-z0-9_]{0,63}"))
                throw new IllegalArgumentException("invalid phase effect binding");
        }
    }
}
