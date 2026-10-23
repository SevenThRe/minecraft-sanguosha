package dev.sgs.core.engine.skill;

import java.util.List;

import dev.sgs.core.card.CardInstance;
import dev.sgs.core.player.SgsPlayer;

/**
 * A skill the player launches explicitly during a legal phase (Rende is the prototype). It does not
 * listen to timings and therefore carries no priority: it runs only when its owner chooses it. The
 * engine performs the structural validation (turn/phase/ownership/target/cards) before calling
 * {@link #activate}; the skill validates its own extra conditions and must not mutate state when it
 * returns a rejection.
 */
public interface ActiveSkill {

  String id();

  SgsPlayer owner();

  /** Read-only owner availability; empty means no additional restriction beyond normal phase checks. */
  default String unavailableReason(SkillEngine api) { return ""; }

  /** Pure selection preview. Extensions must implement this before exposing selectable targets. */
  default SkillResult preview(SkillEngine api,SgsPlayer actor,SgsPlayer target,List<CardInstance> cards){return SkillResult.reject("preview_unavailable");}

  SkillResult activate(
      SkillEngine api, SgsPlayer owner, SgsPlayer target, List<CardInstance> cards);
}
