package dev.sgs.core.addon;
/** Java provider entry. Server allowlist/manifest checks precede provider class initialization. */
public interface HeroPack {
  void register(HeroPackRegistrar registrar);
  /** Optional read-only AI proposal; never pay costs or mutate game state here. */
  default java.util.Optional<BotSkillAction> botAction(dev.sgs.core.game.GameState state,dev.sgs.core.player.SgsPlayer actor){return java.util.Optional.empty();}
  default java.util.Optional<Boolean> botInvoke(dev.sgs.core.game.GameState state,dev.sgs.core.engine.PromptRequest prompt){return java.util.Optional.empty();}
  default java.util.Optional<dev.sgs.core.engine.SkillChoice> botChoice(dev.sgs.core.game.GameState state,dev.sgs.core.engine.PromptRequest prompt){return java.util.Optional.empty();}
}
