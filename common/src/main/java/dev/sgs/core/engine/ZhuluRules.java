package dev.sgs.core.engine;

import dev.sgs.core.card.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;

/** First implemented ZhuLu rules. This is not a declaration that the 32-card pack is complete. */
final class ZhuluRules {
  private ZhuluRules() {}

  static ActionResult validateDisarm(SgsGameEngine e,SgsPlayer target) {
    if(target==null)return ActionResult.rejected(RejectReason.TARGET_REQUIRED);
    if(!e.state().players().contains(target))return ActionResult.rejected(RejectReason.TARGET_NOT_IN_GAME);
    if(!target.alive())return ActionResult.rejected(RejectReason.TARGET_DEAD);
    return target.equipment().all().isEmpty()?ActionResult.rejected(RejectReason.TARGET_NO_CARDS):ActionResult.ok();
  }
  static void disarm(SgsGameEngine e,int source,int target) {
    var player=e.state().players().get(target);
    var cards=List.copyOf(player.equipment().all());
    if(!cards.isEmpty())e.state().cards().move(cards,CardZone.equipment(target),CardZone.hand(target),CardMove.Reason.EFFECT_GAIN,source);
    e.state().log("seat {} recovered {} equipment from jiejia",target,cards.size());
  }
  static boolean nightArmorBlocks(SgsPlayer target,GameCard card) {
    return target!=null && target.equipment().armor()!=null && target.equipment().armor().type()==CardType.YEXINGYI
        && card!=null && card.isBlack() && (card.type().category()==CardCategory.TRICK || card.type().category()==CardCategory.DELAYED_TRICK);
  }
  static List<CardInstance> reveal(SgsGameEngine e,int source,int count) {
    var moves=new ArrayList<CardMovement.Transfer>();var selected=new ArrayList<CardInstance>();
    for(var zone:List.of(CardZone.DRAW,CardZone.DISCARD)) {
      var cards=e.state().cards().cards(zone).stream().filter(c->c.type().isEquipment()).limit(count-selected.size()).toList();
      selected.addAll(cards);
      if(!cards.isEmpty())moves.add(new CardMovement.Transfer(cards,zone,CardZone.PROCESSING,CardMove.Reason.REVEAL,source,true));
    }
    if(!moves.isEmpty())e.state().cards().moveBatch(moves);
    return List.copyOf(selected);
  }
  static void equip(SgsGameEngine e,int seat,CardInstance card) {
    if(!card.type().isEquipment())throw new IllegalArgumentException("draft requires equipment");
    var moves=new ArrayList<CardMovement.Transfer>();
    e.state().players().get(seat).equipment().all().stream().filter(c->c.type().category()==card.type().category()).findFirst()
        .ifPresent(old->moves.add(new CardMovement.Transfer(List.of(old),CardZone.equipment(seat),CardZone.DISCARD,CardMove.Reason.EQUIPMENT_REPLACED,seat,true)));
    moves.add(new CardMovement.Transfer(List.of(card),CardZone.PROCESSING,CardZone.equipment(seat),CardMove.Reason.EQUIP,seat,true));
    e.state().cards().moveBatch(moves);
    e.state().log("seat {} equipped {} from zhulu",seat,card.cardId());
  }
  static void draft(SgsGameEngine e,SgsPlayer source,GameCard cause) {
    var targets=e.state().alivePlayersFromSeat(source.seat()).stream().filter(p->e.cardTargetAllowed(source,p,cause)).toList();
    var pool=new ArrayList<>(reveal(e,source.seat(),targets.size()));
    for(var target:targets) {
      if(pool.isEmpty())break;
      if(!target.alive() || e.isCounteredByWuxie(target))continue;
      var prompt=new PromptRequest(e.nextPromptId(),PromptType.WUGU_PICK,target,source,List.of(),pool,e.responseDeadlineMillis(),"zhulu");
      var answer=e.ask(prompt);var chosen=answer.isEmpty()?pool.getFirst():answer.get().card().sourceCards().getFirst();
      pool.removeIf(c->c.instanceId()==chosen.instanceId());equip(e,target.seat(),chosen);
    }
    if(!pool.isEmpty())e.state().cards().move(pool,CardZone.PROCESSING,CardZone.DISCARD,CardMove.Reason.FINISH,source.seat());
  }
}
