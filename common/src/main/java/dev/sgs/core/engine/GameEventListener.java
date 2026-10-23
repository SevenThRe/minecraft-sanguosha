package dev.sgs.core.engine;

import dev.sgs.core.card.CardInstance;
import dev.sgs.core.player.SgsPlayer;

/** Settlement events used by death rewards, victory checks and skills. */
public interface GameEventListener {
  default void onCardMoved(dev.sgs.core.card.CardMove move) {}

  default void onDamageApplied(SgsPlayer target, SgsPlayer source, int amount) {}
  /** Explicit damage information; the legacy observer is invoked through this default adapter. */
  default void onDamageApplied(SgsPlayer target,SgsPlayer source,int amount,DamageNature nature,boolean chainDamage) {
    onDamageApplied(target,source,amount);
  }

  /** One elemental damage recipient completed and left the chain, before the next recipient. */
  default void onChainReleased(SgsPlayer target) {}

  default void onHpLost(SgsPlayer target,int amount) {}

  default void onDyingResolved(SgsPlayer victim, boolean rescued) {}

  default void onPlayerDied(SgsPlayer victim, SgsPlayer source) {}

  /** A judgment card was revealed for a seat. The card is public information while revealed. */
  default void onJudgmentRevealed(SgsPlayer judge, CardInstance judgmentCard) {}

  /** The effective public card after every replacement, before judgment effects/obtaining it. */
  default void onJudgmentFinalized(SgsPlayer judge, CardInstance judgmentCard) {}
}
