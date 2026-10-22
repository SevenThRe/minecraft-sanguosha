package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** The engine carries the pre-damage distance through dying/death settlement. */
public final class KuangGuSkill implements SkillListener {
  private final SgsPlayer owner;private final SkillEngine api;
  public KuangGuSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "kuanggu";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.DAMAGE_CAUSE_AFTER;}public int priority(){return Priority.PASSIVE_RESPONSE;}
  public boolean applies(SkillContext c){return c.source()==owner && owner.alive() && owner.hp()<owner.maxHp()
      && c.damage()>0 && c.damageDistance()>=0 && c.damageDistance()<=1;}
  public void execute(SkillContext c){if(api.heal(owner,c.damage())>0)c.confirmPublicActivation();}
}
