package dev.sgs.core.engine.skill;

import java.util.Map;

/** Explicit version-1 integer memory. Never serialize a skill instance or reflect over its fields. */
public interface PersistentSkillMemory {
  /** Hook adapters for one skill may point to one shared, versioned state owner. */
  default PersistentSkillMemory memoryOwner(){return this;}
  Map<String,Long> saveMemory();
  void validateMemory(Map<String,Long> values,int turnNumber);
  void restoreMemory(Map<String,Long> values,int turnNumber);
}
