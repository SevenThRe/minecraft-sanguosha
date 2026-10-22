package dev.sgs.core.hero;

import java.util.List;
import java.util.Objects;

import dev.sgs.core.Kingdom;

/**
 * Code-side hero model. Skill ids reference code-registered skill implementations; JSON only
 * carries these ids, never effect logic.
 */
public final class HeroDefinition {

  private final String id;
  private final Kingdom kingdom;
  private final int maxHp;
  private final List<String> skillIds;
  private final boolean male;

  public HeroDefinition(String id, Kingdom kingdom, int maxHp, List<String> skillIds) {
    this(id,kingdom,maxHp,skillIds,!java.util.Set.of("huangyueying","zhenji","daqiao","sunshangxiang","diaochan").contains(id));
  }

  /** Explicit sex for extension heroes; the legacy constructor preserves standard hero identities. */
  public HeroDefinition(String id, Kingdom kingdom, int maxHp, List<String> skillIds, boolean male) {
    this.id = Objects.requireNonNull(id);
    this.kingdom = Objects.requireNonNull(kingdom);
    if (maxHp <= 0) {
      throw new IllegalArgumentException("hero maxHp must be positive");
    }
    this.maxHp = maxHp;
    this.skillIds = List.copyOf(skillIds);
    this.male = male;
  }

  public String id() {
    return id;
  }

  public Kingdom kingdom() {
    return kingdom;
  }

  public int maxHp() {
    return maxHp;
  }

  public List<String> skillIds() {
    return skillIds;
  }

  public boolean male(){return male;}
}
