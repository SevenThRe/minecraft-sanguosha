package dev.sgs.core.engine;

import java.util.Optional;

import dev.sgs.core.game.GameState;

/**
 * Answers engine prompts. The Minecraft integration blocks on network input until the deadline;
 * tests script deterministic answers.
 */
@FunctionalInterface
public interface PromptResponder {

  Optional<PlayDecision> respond(GameState state, PromptRequest prompt);

  /** Counterspell-only typed preference, returned on the owning rule thread. */
  record WuxieResponse(Optional<PlayDecision> decision,boolean passScope){
    public WuxieResponse{java.util.Objects.requireNonNull(decision);if(passScope&&decision.isPresent())throw new IllegalArgumentException("cannot counter and pass scope");}
    public static WuxieResponse decline(){return new WuxieResponse(Optional.empty(),false);}
    public static WuxieResponse skipScope(){return new WuxieResponse(Optional.empty(),true);}
    public static WuxieResponse answer(Optional<PlayDecision> decision){return new WuxieResponse(decision,false);}
  }
  default WuxieResponse respondWuxie(GameState state,PromptRequest prompt){return WuxieResponse.answer(respond(state,prompt));}

  /** Compatibility adapter for deterministic responders; live Session overrides with one shared window. */
  default CounterspellResult collectCounterspell(GameState state,CounterspellWindow window){
    for(var prompt:window.requests()){
      if(window.closed() || window.expired())break;
      WuxieResponse response;
      try{response=respondWuxie(state,prompt);}catch(RuntimeException failure){response=WuxieResponse.decline();}
      window.offer(prompt.asker().id(),prompt.id(),response);
    }
    return window.finish();
  }

  /** Physical-card/recipient selection; legacy responders safely decline optional choices. */
  default Optional<SkillChoice> choose(GameState state,PromptRequest prompt){return Optional.empty();}

  /** Safe default for older scripted responders: an optional skill is never auto-accepted. */
  default Optional<dev.sgs.core.card.Suit> chooseSuit(GameState state,PromptRequest prompt){return Optional.empty();}

  default boolean invoke(GameState state,PromptRequest prompt){return false;}
}
