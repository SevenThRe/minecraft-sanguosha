package dev.sgs.core.engine.skill;

import java.util.List;

import dev.sgs.core.card.CardInstance;
import dev.sgs.core.game.GameState;
import dev.sgs.core.player.SgsPlayer;

/**
 * Engine operations exposed to {@link ActiveSkill}s. Kept minimal so skills express game effects in
 * engine vocabulary instead of touching player/state internals directly.
 */
public interface SkillEngine {

  GameState gameState();
  default void versionedSkillLayoutChanged(){}
  default boolean faceDown(SgsPlayer player){throw new UnsupportedOperationException("face-down query unavailable");}
  /** Trusted dying-skill hook: victim must still be alive and at nonpositive HP. */
  default java.util.Optional<dev.sgs.core.engine.SkillChoice> requestDyingSkillChoice(SgsPlayer actor,SgsPlayer victim,
      dev.sgs.core.engine.PromptType type,String skill,List<CardInstance> cards,boolean hiddenHand){return java.util.Optional.empty();}
  default void turnOver(SgsPlayer player){throw new UnsupportedOperationException("turn over unavailable");}
  default void dealElementalSkillDamage(SgsPlayer source,SgsPlayer target,int amount,dev.sgs.core.engine.DamageNature nature){throw new UnsupportedOperationException("elemental skill damage unavailable");}
  default void transferSkillDamage(SgsPlayer source,SgsPlayer target,int amount,dev.sgs.core.card.GameCard cause,dev.sgs.core.engine.DamageNature nature){throw new UnsupportedOperationException("damage transfer unavailable");}
  default boolean useVirtualAttack(SgsPlayer source,SgsPlayer target,boolean ignoreDistance){throw new UnsupportedOperationException("virtual attack unavailable");}
  default boolean useVirtualCard(SgsPlayer source,dev.sgs.core.card.GameCard card,SgsPlayer target,SgsPlayer secondary){throw new UnsupportedOperationException("virtual card unavailable");}
  default boolean useProvidedSkillAttack(SgsPlayer source,SgsPlayer target,dev.sgs.core.card.GameCard card){throw new UnsupportedOperationException("provided skill attack unavailable");}
  default java.util.Optional<dev.sgs.core.engine.SkillChoice> requestSkillChoice(SgsPlayer actor,
      SgsPlayer subject,dev.sgs.core.engine.PromptType type,String skill,List<CardInstance> cards,
      List<Integer> targets,boolean hiddenHand){return java.util.Optional.empty();}
  /** Negative tokens select an opaque hand position; zero keeps the legacy random choice. Never expose hand ids. */
  default boolean takeFeedbackCard(SgsPlayer actor,SgsPlayer source,dev.sgs.core.engine.SkillChoice choice){return false;}
  default dev.sgs.core.card.Suit requestFanJianSuit(SgsPlayer target,SgsPlayer source){throw new UnsupportedOperationException("fanjian choice unavailable");}
  default void dealSkillDamage(SgsPlayer source,SgsPlayer target,int amount){throw new UnsupportedOperationException("skill damage unavailable");}
  /** Complete judge pipeline; black acquisition occurs after final judgment effect, before cleanup. */
  default CardInstance judgeForSkill(SgsPlayer target,String skillId,boolean obtainBlack){throw new UnsupportedOperationException("skill judgment unavailable");}
  default CardInstance judgeForSkill(SgsPlayer target,String skillId,java.util.function.Predicate<dev.sgs.core.card.GameCard> keep,dev.sgs.core.card.CardZone destination){throw new UnsupportedOperationException("skill judgment acquisition unavailable");}
  /** Skill-generated duel target prohibition is checked before paying the skill cost. */
  default boolean canSkillDuel(SgsPlayer source,SgsPlayer target){throw new UnsupportedOperationException("skill duel unavailable");}
  /** Standard Lijian: pure virtual duel with source attribution and no negation window. */
  default void useSkillDuel(SgsPlayer source,SgsPlayer target){throw new UnsupportedOperationException("skill duel unavailable");}
  /** Liuli distance/attack range must be checked after excluding the exact discarded equipment. */
  default boolean canRedirectAttack(SgsPlayer attacker,SgsPlayer defender,SgsPlayer replacement,
      dev.sgs.core.card.GameCard attack,CardInstance cost){throw new UnsupportedOperationException("attack redirection unavailable");}

  /**
   * Moves a card from one seat's hand to another seat's hand; no-op-safe when the card is absent.
   */
  void transferCard(SgsPlayer from, SgsPlayer to, CardInstance card);
  /** Atomically discard distinct actual hand/equipment costs owned by this character. */
  void discardCards(SgsPlayer owner,List<CardInstance> cards);

  /** Heals up to the maximum hp and returns the amount actually recovered. */
  int heal(SgsPlayer player, int amount);

  /** Loses HP directly, bypassing damage modifiers and damage-trigger skills. Can still cause dying. */
  void loseHp(SgsPlayer player,int amount);

  /** Draws up to {@code count} cards from the deck (recycling the discard pile as usual). */
  List<CardInstance> drawCards(SgsPlayer recipient,int count);

  /** Obtain all physical sources only if every one is still in processing. */
  int obtainProcessingCards(SgsPlayer recipient,dev.sgs.core.card.GameCard card);

  /** An explicit optional invocation with its own prompt ID and deadline. */
  boolean requestSkillInvoke(SgsPlayer actor,String skillId,SgsPlayer subject);

  /** Ask this character for a provided Shan, including its own transformation/armor effects. */
  boolean requestProvidedDodge(SgsPlayer helper,SgsPlayer lord);
  /** Accepted physical sources enter processing and remain there until the lord settles them. */
  java.util.Optional<dev.sgs.core.card.GameCard> requestProvidedAttack(SgsPlayer helper,SgsPlayer lord);
  SkillResult useJijiangAttack(SgsPlayer lord,SgsPlayer target);
  /** Pure target/range/quota validation, shared by supplied and ordinary active attacks. */
  default boolean canUseActiveAttack(SgsPlayer source,SgsPlayer target){return false;}
}
