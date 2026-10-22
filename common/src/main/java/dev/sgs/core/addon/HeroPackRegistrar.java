package dev.sgs.core.addon;
import dev.sgs.core.hero.HeroDefinition;
/** Each provider receives a namespace-scoped registrar; collisions are errors, never replacement. */
public interface HeroPackRegistrar {
  void skill(SkillPresentation presentation,SkillFactory factory);
  void hero(HeroDefinition definition,String name,String description,String portrait);
  /** Explicit opt-in to idle saves; every external hook must also implement PersistentSkillMemory. */
  default void idleCheckpoint(String heroId){throw new UnsupportedOperationException("idle checkpoint contract unavailable");}
  /** Opt in to host-owned active programs; every own skill must be a persistent PlannedActiveSkill, with no extra hooks. Implies idleCheckpoint. */
  default void programmedCheckpoint(String heroId){throw new UnsupportedOperationException("programmed checkpoint contract unavailable");}
}
