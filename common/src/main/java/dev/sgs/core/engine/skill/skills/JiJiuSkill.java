package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Standard Hua Tuo: red owned hand/equipment cards can rescue only outside the owner's turn. */
public final class JiJiuSkill implements SkillListener {
  public static final String ID="jijiu";
  private final SgsPlayer owner;
  public JiJiuSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return ID;}
  public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.CARD_TRANSFORM;}
  public int priority(){return Priority.PASSIVE_RESPONSE;}
  public boolean applies(SkillContext ctx){return ctx.actor()==owner && owner.alive() && ctx.state().currentPlayer()!=owner
      && ctx.promptType()==PromptType.RESCUE_TAO && ctx.declaredAs()==CardType.TAO && ctx.card()!=null
      && !ctx.card().isVirtual() && ctx.card().isRed();}
  public void execute(SkillContext ctx){ctx.confirmTransform();ctx.confirmPublicActivation();}
}
