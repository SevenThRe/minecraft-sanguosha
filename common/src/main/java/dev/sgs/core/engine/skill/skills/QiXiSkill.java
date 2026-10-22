package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Standard Gan Ning: a black owned physical card becomes Dismantlement during play. */
public final class QiXiSkill implements SkillListener {
  public static final String ID="qixi";
  private final SgsPlayer owner;
  public QiXiSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return ID;}
  public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.CARD_TRANSFORM;}
  public int priority(){return Priority.PASSIVE_RESPONSE;}
  public boolean applies(SkillContext ctx){return ctx.actor()==owner && owner.alive() && ctx.promptType()==null
      && ctx.declaredAs()==CardType.GUOHE && ctx.card()!=null && !ctx.card().isVirtual() && ctx.card().isBlack();}
  public void execute(SkillContext ctx){ctx.confirmTransform();ctx.confirmPublicActivation();}
}
