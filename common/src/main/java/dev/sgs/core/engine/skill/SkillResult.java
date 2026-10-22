package dev.sgs.core.engine.skill;

/** Result of launching an {@link ActiveSkill}. Rejections carry a stable reason suffix. */
public record SkillResult(boolean accepted, String reason) {

  public static SkillResult ok() {
    return new SkillResult(true, null);
  }

  public static SkillResult reject(String reason) {
    return new SkillResult(false, reason);
  }
}
