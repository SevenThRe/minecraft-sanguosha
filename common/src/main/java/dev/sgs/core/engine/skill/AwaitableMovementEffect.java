package dev.sgs.core.engine.skill;

/**
 * A departure listener with one optional invocation before its synchronous effect body.
 * The body must consume exactly that invocation through SkillEngine.requestSkillInvoke.
 * This capability belongs to the registered listener, rather than a hero-name switch in a parent.
 * Multi-choice or multiple simultaneous listeners require an ordered movement program instead.
 */
public interface AwaitableMovementEffect extends SkillListener {
  record Request(int owner, String skill) {
    public Request {
      if (owner < 0 || owner > 7 || skill == null || !skill.matches("[a-z][a-z0-9_]{0,63}"))
        throw new IllegalArgumentException("invalid movement effect binding");
    }
  }
}
