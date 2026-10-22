package dev.sgs.core.engine.skill;

/**
 * Every settlement moment a skill, equipment effect or delayed trick can hook. The numeric order is
 * the foundation of the whole skill system: a smaller value fires earlier.
 *
 * <p>Append-only contract: existing constants must never be renumbered or reordered. New timings
 * are always added at the end (in a free numeric band), so listeners compiled against older values
 * keep firing in the same relative order. Leave numeric gaps so future timings can be inserted
 * between existing ones without renumbering.
 *
 * <p>The damage chain is fixed by the official rules and reflected strictly here: cause-before
 * (造成伤害时) &rarr; take-before (受到伤害时) &rarr; cause-after (造成伤害后) &rarr; take-after (受到伤害后).
 */
public enum Timing {

  // ---- Turn / phase skeleton (10-199) ----
  TURN_START(10),
  PHASE_PREPARE_START(20),
  PHASE_JUDGMENT_START(30),

  // Judgment card pipeline. JUDGE_CARD_MODIFY is arbitrated in multi-entity ring order.
  JUDGE_CARD_REVEALED(40),
  JUDGE_CARD_MODIFY(50),
  JUDGE_CARD_EFFECT(60),

  PHASE_DRAW_START(70),
  PHASE_DRAW_END(80),
  PHASE_PLAY_START(100),

  // Card use pipeline. CARD_TRANSFORM is where view-as skills (Longdan) confirm an alternation.
  CARD_PLAY_BEFORE(110),
  CARD_TRANSFORM(120),
  CARD_PLAY_AFTER(130),

  // ---- Damage chain (300-399), order is part of the official rules ----
  DAMAGE_CAUSE_BEFORE(300),
  DAMAGE_TAKE_BEFORE(310),
  DAMAGE_CAUSE_AFTER(320),
  DAMAGE_TAKE_AFTER(330),

  // ---- Dying / death (400-499). ASK_RESCUE is arbitrated in multi-entity ring order. ----
  DYING(400),
  ASK_RESCUE(410),
  DEATH(420),

  // ---- End of turn (500-599) ----
  PHASE_DISCARD_START(500),
  PHASE_TURN_END(510),
  HP_LOST_AFTER(350),
  CARD_MOVE_AFTER(150),
  CARD_USE_COMMITTED(125),
  CARD_RESPONSE_COMMITTED(135),
  PHASE_DISCARD_BEFORE(490),
  SHA_TARGET_BEFORE(140),
  CARD_TARGET_CONFIRMED(145),
  SHA_DODGED(146),
  CARD_FINISHED(160),
  PHASE_PLAY_BEFORE(90),
  PHASE_JUDGMENT_BEFORE(25),
  PHASE_DRAW_BEFORE(65),
  DEATH_BEFORE_CLEANUP(415),
  PHASE_DISCARD_END(505);

  private final int order;

  Timing(int order) {
    this.order = order;
  }

  /** Smaller fires earlier. */
  public int order() {
    return order;
  }
}
