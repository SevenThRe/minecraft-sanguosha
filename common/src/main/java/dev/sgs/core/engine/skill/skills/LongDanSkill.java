package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.Priority;
import dev.sgs.core.engine.skill.SkillContext;
import dev.sgs.core.engine.skill.SkillListener;
import dev.sgs.core.engine.skill.Timing;
import dev.sgs.core.player.SgsPlayer;

/**
 * Longdan (Zhao Yun): a Sha may be regarded as a Shan and a Shan may be regarded as a Sha, both
 * when playing and when responding.
 *
 * <p>It hooks {@link Timing#CARD_TRANSFORM}: the engine fires that timing after a player declares
 * an alternation (physical type differs from declared type) and before the declared effect settles.
 * Longdan confirms the declaration; without a confirming listener the alternation is rejected. The
 * physical card is still what moves to the discard pile (including its suit/color).
 */
public final class LongDanSkill implements SkillListener {

  public static final String ID = "longdan";

  private final SgsPlayer owner;

  public LongDanSkill(SgsPlayer owner) {
    this.owner = owner;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public Timing timing() {
    return Timing.CARD_TRANSFORM;
  }

  @Override
  public int priority() {
    return Priority.PASSIVE_RESPONSE;
  }

  @Override
  public SgsPlayer owner() {
    return owner;
  }

  @Override
  public boolean applies(SkillContext ctx) {
    if (ctx.actor() != owner || !owner.alive() || ctx.card() == null || ctx.declaredAs() == null) {
      return false;
    }
    CardType physical = ctx.card().type();
    CardType declared = ctx.declaredAs();
    if (((physical == CardType.SHA || physical==CardType.HUOSHA || physical==CardType.LEISHA) && declared == CardType.SHAN)
        || (physical == CardType.SHAN && declared == CardType.SHA)) {
      return legalContext(ctx, physical);
    }
    return false;
  }

  private boolean legalContext(SkillContext ctx, CardType physical) {
    PromptType prompt = ctx.promptType();
    if (prompt == null) {
      // Active play path: only declaring a Shan as a Sha is playable.
      return physical == CardType.SHAN;
    }
    // Response path: the declared type must match the prompt being answered.
    return switch (prompt) {
      case DODGE_SHA,HUJIA_SHAN -> physical == CardType.SHA || physical==CardType.HUOSHA || physical==CardType.LEISHA;
      case DUEL_SHA, RESPOND_SHA_NANMAN, QINGLONG_SHA,JIEDAO_RESPONSE, JIJIANG_SHA -> physical == CardType.SHAN;
      default -> false;
    };
  }

  @Override
  public void execute(SkillContext ctx) {
    ctx.state()
        .log(
            "seat {} used longdan: {} regarded as {}",
            owner.seat(),
            ctx.card().sourceCards().get(0).cardId(),
            ctx.declaredAs().id());
    ctx.confirmTransform();
    ctx.confirmPublicActivation();
  }
}
