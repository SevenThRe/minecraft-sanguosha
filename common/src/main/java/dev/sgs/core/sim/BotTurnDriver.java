package dev.sgs.core.sim;

import java.util.ArrayList;
import java.util.List;

import dev.sgs.core.card.CardInstance;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.ActionResult;
import dev.sgs.core.engine.RejectReason;
import dev.sgs.core.engine.SgsGameEngine;
import dev.sgs.core.game.GameState;
import dev.sgs.core.player.SgsPlayer;

/**
 * Deterministic play-window and discard behavior for bot seats. Shared by the headless simulator
 * and the live network match so bots act identically in both. Every call performs at most one
 * engine action; the caller loops until the play phase ends.
 */
public final class BotTurnDriver {

  private BotTurnDriver() {}

  /** Plays one bot action (peach, wined sha, sha in range) or ends the play phase. */
  @FunctionalInterface public interface CardExecutor {ActionResult play(long cardId,CardType declared,SgsPlayer target,SgsPlayer secondary);}
  public static void runPlay(SgsGameEngine engine,GameState state,SgsPlayer player){
    runPlay(engine,state,player,(id,declared,target,secondary)->engine.playCardAs(player,id,declared,target,secondary));
  }
  @FunctionalInterface public interface AttackExecutor {ActionResult play(long cardId,CardType declared,List<SgsPlayer> targets);}
  public static void runPlay(SgsGameEngine engine, GameState state, SgsPlayer player,CardExecutor cards) {
    runPlay(engine,state,player,cards,(id,declared,targets)->engine.playAttackTargets(player,id,declared,targets));
  }
  @FunctionalInterface public interface ActiveSkillExecutor {ActionResult play(String skillId,SgsPlayer target,List<Long> costs);}
  public static void runPlay(SgsGameEngine engine, GameState state, SgsPlayer player,CardExecutor cards,AttackExecutor attacks) {
    runPlay(engine,state,player,cards,attacks,(skill,target,costs)->engine.useActiveSkill(player,skill,target,costs));
  }
  public static void runPlay(SgsGameEngine engine, GameState state, SgsPlayer player,CardExecutor cards,AttackExecutor attacks,ActiveSkillExecutor active) {
    runPlay(engine,state,player,cards,attacks,active,()->engine.endPlayPhase(player));
  }
  public static void runPlay(SgsGameEngine engine, GameState state, SgsPlayer player,CardExecutor cards,AttackExecutor attacks,ActiveSkillExecutor active,java.util.function.Supplier<ActionResult> endPlay) {
    if(runMythic(engine,state,player,cards,active))return;
    var external=dev.sgs.core.addon.AddonRuntime.current().botAction(state,player);
    if(external.isPresent()){
      var plan=external.get();var target=plan.targetSeat()<0?null:plan.targetSeat()<state.players().size()?state.players().get(plan.targetSeat()):null;
      if((plan.targetSeat()<0||target!=null)&&active.play(plan.skillId(),target,plan.costs()).accepted())return;
    }
    if (player.hp() < player.maxHp() && hasType(player, CardType.TAO)) {
      accept(cards.play(firstId(player, CardType.TAO),null,null,null));
      return;
    }
    if(player.hero()!=null && player.hero().skillIds().contains("qingnang") && player.handCount()>0){
      var target=state.alivePlayersFromSeat(player.seat()).stream()
          .filter(p->(p==player || state.areTeammates(player,p) || (player.identity()==dev.sgs.core.Identity.LOYALIST
              && p.identityPublic() && p.identity()==dev.sgs.core.Identity.LORD)) && p.hp()<p.maxHp()).findFirst().orElse(null);
      if(target!=null && active.play("qingnang",target,List.of(player.handView().getFirst().instanceId())).accepted())return;
    }
    if(player.hero()!=null && player.hero().skillIds().contains("jieyin") && player.handCount()>=2){
      var ally=state.alivePlayersAfter(player.seat()).stream().filter(p->p.hero()!=null && p.hero().male() && p.hp()<p.maxHp()
          && (state.areTeammates(player,p) || player.identity()==dev.sgs.core.Identity.LOYALIST
              && p.identityPublic() && p.identity()==dev.sgs.core.Identity.LORD)).findFirst().orElse(null);
      if(ally!=null && active.play("jieyin",ally,player.handView().stream().limit(2).map(CardInstance::instanceId).toList()).accepted())return;
    }
    if(player.hero()!=null && player.hero().skillIds().contains("lijian")){
      var costs=new ArrayList<>(player.handView());costs.addAll(player.equipment().all());
      if(!costs.isEmpty())for(var target:state.alivePlayersAfter(player.seat()))
        if(target.hero()!=null && target.hero().male() && !state.areTeammates(player,target)
            && active.play("lijian",target,List.of(costs.getFirst().instanceId())).accepted())return;
    }
    if(player.hero()!=null && player.hero().skillIds().contains("guose")){
      var costs=new ArrayList<>(player.handView());costs.addAll(player.equipment().all());
      for(var cost:costs)if(cost.suit()==dev.sgs.core.card.Suit.DIAMOND && cost.type()!=CardType.LEBU)
        for(var target:state.alivePlayersAfter(player.seat()))if(!state.areTeammates(player,target)
            && cards.play(cost.instanceId(),CardType.LEBU,target,null).accepted())return;
    }
    if(player.hero()!=null && player.hero().skillIds().contains("fanjian") && player.handCount()>0){
      for(var target:state.alivePlayersAfter(player.seat()))if(!state.areTeammates(player,target)
          && active.play("fanjian",target,List.of()).accepted())return;
    }
    // Baseline policy sees its own hand and public seats only; the engine validates every play.
    for (CardInstance card : player.handView()) {
      if (card.type().isEquipment()) {
        accept(cards.play(card.instanceId(),null,null,null));
        return;
      }
    }
    for (CardType type : List.of(CardType.WUZHONG,CardType.TAOYUAN,CardType.WUGU,CardType.NANMAN,CardType.WANJIAN)) {
      if (hasType(player,type)) {
        accept(cards.play(firstId(player,type),null,null,null));
        return;
      }
    }
    if (hasType(player,CardType.JUEDOU)) {
      var targets=state.alivePlayersAfter(player.seat()).stream().filter(p->!state.areTeammates(player,p)).toList();
      // Public range/team preference is not a legality check (for example Kongcheng).
      for(var target:targets)if(cards.play(firstId(player,CardType.JUEDOU),null,target,null).accepted())return;
    }
    if(hasType(player,CardType.HUOGONG)){
      var targets=state.alivePlayersAfter(player.seat()).stream().filter(p->!state.areTeammates(player,p) && p.handCount()>0).toList();
      if(!targets.isEmpty()){
        accept(cards.play(firstId(player,CardType.HUOGONG),null,targets.getFirst(),null));return;
      }
    }
    for(var type:List.of(CardType.BINGLIANG,CardType.LEBU))if(hasType(player,type)){
      for(var target:state.alivePlayersAfter(player.seat()))if(!state.areTeammates(player,target)){
        var result=cards.play(firstId(player,type),null,target,null);if(result.accepted())return;
      }
    }
    if(hasType(player,CardType.TIESUO)) {
      var chain=player.handView().stream().filter(c->c.type()==CardType.TIESUO).findFirst().orElseThrow();
      if(state.chained(player.seat()))accept(cards.play(chain.instanceId(),null,
          engine.cardTargetAllowed(player,player,chain)?player:null,null));
      else {
        // Public hostility is not legality: black chains cannot target a hero protected by Weimu.
        var targets=state.alivePlayersAfter(player.seat()).stream().filter(p->!state.areTeammates(player,p)
            && !state.chained(p.seat()) && engine.cardTargetAllowed(player,p,chain)).limit(2).toList();
        boolean element=player.handView().stream().anyMatch(c->c.type()==CardType.HUOSHA || c.type()==CardType.LEISHA);
        accept(cards.play(firstId(player,CardType.TIESUO),null,element && !targets.isEmpty()?targets.getFirst():null,element && targets.size()>1?targets.get(1):null));
      }
      return;
    }
    if (!state.jiuPlayedThisTurn()
        && hasType(player, CardType.JIU)
        && hasAnyAttack(player)) {
      accept(cards.play(firstId(player, CardType.JIU),null,null,null));
      return;
    }
    if (hasAnyAttack(player)) {
      SgsPlayer target = targetInRange(state, player);
      if (target != null) {
        ActionResult sha = attack(engine,state,player,cards,attacks,firstAttackId(player),null,target);
        if (sha.accepted()) {
          return;
        }
        // SHA_LIMIT / range / shield: the engine already logged the reason; end the play phase.
        if (sha.reason() != RejectReason.SHA_LIMIT && sha.reason() != RejectReason.OUT_OF_RANGE) {
          state.log("bot{} ends plays after reject {}", player.seat(), sha.reason());
        }
      }
    }
    if(player.hero()!=null && player.hero().skillIds().contains("qixi")){
      for(var target:state.alivePlayersAfter(player.seat()))if(!state.areTeammates(player,target)
          && target.handCount()+target.equipment().all().size()+target.delayZone().size()>0){
        var costs=new ArrayList<>(player.handView());costs.addAll(player.equipment().all());
        for(var card:costs)if(card.isBlack() && cards.play(card.instanceId(),CardType.GUOHE,target,null).accepted())return;
      }
    }
    if(player.hero()!=null && player.hero().skillIds().contains("wusheng")){
      var target=targetInRange(state,player);
      if(target!=null)for(var card:player.handView())if(card.isRed()){
        if(attack(engine,state,player,cards,attacks,card.instanceId(),CardType.SHA,target).accepted())return;
      }
    }
    if(player.hero()!=null && player.hero().skillIds().contains("jijiang") && player.identity()==dev.sgs.core.Identity.LORD){
      var target=targetInRange(state,player);
      if(target!=null && active.play("jijiang",target,List.of()).accepted())return;
    }
    // Conservative public-information policy: never voluntarily enter dying, and stop at 2 HP.
    if(player.hero()!=null && player.hero().skillIds().contains("zhiheng") && player.handCount()>0){
      var costs=player.handView().stream().filter(c->c.type()!=CardType.TAO && c.type()!=CardType.SHAN).limit(2).map(CardInstance::instanceId).toList();
      if(!costs.isEmpty() && active.play("zhiheng",null,costs).accepted())return;
    }
    if(player.hero()!=null && player.hero().skillIds().contains("kurou") && player.hp()>2){
      if(active.play("kurou",null,List.of()).accepted())return;
    }
    accept(endPlay.get());
  }
  private static boolean runMythic(SgsGameEngine engine,GameState state,SgsPlayer p,CardExecutor cards,ActiveSkillExecutor active){
    var ids=engine.effectiveSkillIds(p);
    for(var id:List.of("quhu","tianyi","tiaoxin","jixi","zhiba_pindian"))if(ids.contains(id)){
      for(var t:state.alivePlayersAfter(p.seat()))if(!state.areTeammates(p,t)&&active.play(id,t,List.of()).accepted())return true;
    }
    if(ids.contains("zhijian"))for(var cost:p.handView())if(cost.type().isEquipment())for(var ally:state.alivePlayersAfter(p.seat()))
      if(state.areTeammates(p,ally)&&active.play("zhijian",ally,List.of(cost.instanceId())).accepted())return true;
    if(ids.contains("huangtian_give"))for(var cost:p.handView())if(cost.type()==CardType.SHAN||cost.type()==CardType.SHANDIAN)
      for(var lord:state.players())if(lord.identity()==dev.sgs.core.Identity.LORD&&(state.areTeammates(p,lord)||p.identity()==dev.sgs.core.Identity.LOYALIST)
          &&active.play("huangtian_give",lord,List.of(cost.instanceId())).accepted())return true;
    if(ids.contains("luanji")&&state.alivePlayersAfter(p.seat()).stream().noneMatch(t->state.areTeammates(p,t)))
      for(var suit:dev.sgs.core.card.Suit.values()){
        var costs=p.handView().stream().filter(c->c.suit()==suit&&c.type()!=CardType.TAO).limit(2).map(CardInstance::instanceId).toList();
        if(costs.size()==2&&active.play("luanji",null,costs).accepted())return true;
      }
    if(ids.stream().anyMatch(java.util.Set.of("huoji","shuangxiong","jiuchi","lianhuan")::contains)){
      var options=engine.cardPlayAvailability(p);
      for(var cost:p.handView())for(var option:options.getOrDefault(cost.instanceId(),List.of()))if(option.available()&&option.declaredTypeOrdinal()!=cost.type().ordinal()){
        var declared=CardType.values()[option.declaredTypeOrdinal()];
        if(declared==CardType.HUOGONG||declared==CardType.JUEDOU)for(var t:state.alivePlayersAfter(p.seat()))
          if(!state.areTeammates(p,t)&&cards.play(cost.instanceId(),declared,t,null).accepted())return true;
        if(declared==CardType.JIU&&!state.jiuPlayedThisTurn()&&p.handView().stream().anyMatch(c->c.type()==CardType.SHA||c.type()==CardType.HUOSHA||c.type()==CardType.LEISHA)
            &&cards.play(cost.instanceId(),declared,null,null).accepted())return true;
        if(declared==CardType.TIESUO&&cards.play(cost.instanceId(),declared,null,null).accepted())return true;
      }
    }
    return false;
  }

  private static ActionResult attack(SgsGameEngine engine,GameState state,SgsPlayer player,CardExecutor cards,AttackExecutor attacks,long id,CardType declared,SgsPlayer first){
    if(engine.attackTargetLimit(player,id,declared)>1){
      var targets=state.alivePlayersAfter(player.seat()).stream().filter(p->!state.areTeammates(player,p)
          && engine.attackTargetAvailability(player,id,declared,p).accepted()).limit(3).toList();
      if(!targets.isEmpty())return attacks.play(id,declared,targets);
    }
    return cards.play(id,declared,first,null);
  }

  /** Confirms the exact excess cards (earliest hand positions) for the discard phase. */
  public static void runDiscard(SgsGameEngine engine, SgsPlayer player) {
    int excess = Math.max(player.handCount() - engine.state().handLimit(player), 0);
    List<Long> ids = new ArrayList<>(excess);
    for (CardInstance card : player.handView()) {
      if (ids.size() >= excess) {
        break;
      }
      ids.add(card.instanceId());
    }
    accept(engine.confirmDiscard(player, ids));
  }

  private static SgsPlayer targetInRange(GameState state, SgsPlayer player) {
    for (SgsPlayer candidate : state.alivePlayersAfter(player.seat())) {
      if (!state.areTeammates(player,candidate) && state.inBasicAttackRange(player, candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private static boolean hasType(SgsPlayer player, CardType type) {
    return player.handView().stream().anyMatch(c -> c.type() == type);
  }

  private static boolean hasAnyAttack(SgsPlayer player) {
    return player.handView().stream().anyMatch(c -> isAttack(c.type()));
  }

  private static long firstAttackId(SgsPlayer player) {
    return player.handView().stream()
        .filter(c -> isAttack(c.type()))
        .findFirst()
        .orElseThrow()
        .instanceId();
  }

  private static boolean isAttack(CardType type) {
    return type == CardType.SHA || type == CardType.HUOSHA || type == CardType.LEISHA;
  }

  private static long firstId(SgsPlayer player, CardType type) {
    return player.handView().stream()
        .filter(c -> c.type() == type)
        .findFirst()
        .orElseThrow()
        .instanceId();
  }

  private static void accept(ActionResult result) {
    if (!result.accepted()) {
      throw new IllegalStateException("bot action rejected: " + result.reason());
    }
  }
}
