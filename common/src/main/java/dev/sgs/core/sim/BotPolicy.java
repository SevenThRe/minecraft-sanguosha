package dev.sgs.core.sim;

import java.util.Optional;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.PlayDecision;
import dev.sgs.core.engine.PromptResponder;
import dev.sgs.core.player.SgsPlayer;

/**
 * Deterministic answer policy for bot seats: dodge a Sha with a Shan, answer Duels and Barbarian
 * Invasion with a Sha, and self-rescue while dying (Jiu preferred on one's own turn, Tao
 * otherwise). Every other prompt is declined, which lets damage settle normally.
 */
public final class BotPolicy implements PromptResponder {

  /** Shared instance; the policy holds no per-match state. */
  public static final BotPolicy INSTANCE = new BotPolicy();

  public BotPolicy() {}

  @Override
  public Optional<PlayDecision> respond(
      dev.sgs.core.game.GameState state, dev.sgs.core.engine.PromptRequest prompt) {
    return switch (prompt.type()) {
      case DODGE_SHA -> firstOf(prompt.asker(), CardType.SHAN);
      case WUXIE_COUNTER -> Optional.empty(); // No effect/parity context: do not spend a card blindly.
      case HUJIA_SHAN -> prompt.asker().identity()==dev.sgs.core.Identity.LOYALIST
          ? firstOf(prompt.asker(),CardType.SHAN):Optional.empty();
      case DUEL_SHA, RESPOND_SHA_NANMAN, JIEDAO_RESPONSE -> attack(prompt.asker());
      case QINGLONG_SHA -> !state.areTeammates(prompt.asker(),prompt.subject())?attack(prompt.asker()):Optional.empty();
      case JIJIANG_SHA -> prompt.asker().identity()==dev.sgs.core.Identity.LOYALIST
          ? attack(prompt.asker()):Optional.empty();
      case HUOGONG_REVEAL -> prompt.asker().handView().stream().findFirst().map(PlayDecision::asIs);
      case HUOGONG_PAY -> prompt.choices().size()==1 && !state.areTeammates(prompt.asker(),prompt.subject())
          ?prompt.asker().handView().stream().filter(c->c.suit()==prompt.choices().getFirst().suit()).findFirst().map(PlayDecision::asIs):Optional.empty();
      case RESCUE_TAO -> {
        boolean supportsPublicLord=prompt.asker().identity()==dev.sgs.core.Identity.LOYALIST
            && prompt.subject()!=null && prompt.subject().identityPublic() && prompt.subject().identity()==dev.sgs.core.Identity.LORD;
        if (prompt.asker() != prompt.subject() && !state.areTeammates(prompt.asker(),prompt.subject()) && !supportsPublicLord) {
          yield Optional.empty(); // bots never spend peaches on others
        }
        Optional<PlayDecision> jiu = prompt.asker()==prompt.subject()?firstOf(prompt.asker(), CardType.JIU):Optional.empty();
        if(jiu.isPresent())yield jiu;
        var tao=firstOf(prompt.asker(),CardType.TAO);if(tao.isPresent())yield tao;
        var hero=prompt.asker().hero();
        if(state.currentPlayer()!=prompt.asker() && hero!=null && hero.skillIds().contains("jijiu")){
          var red=java.util.stream.Stream.concat(prompt.asker().handView().stream(),prompt.asker().equipment().all().stream())
              .filter(dev.sgs.core.card.CardInstance::isRed).findFirst();
          yield red.map(c->PlayDecision.alternate(c,CardType.TAO));
        }
        yield Optional.empty();
      }
      default -> Optional.empty();
    };
  }

  /** Shared live/simulation policy uses public effect, current parity and public relationships. */
  public Optional<PlayDecision> counterspell(dev.sgs.core.game.GameState state,dev.sgs.core.engine.PromptRequest prompt,CardType effect,int depth){
    var me=prompt.asker();var target=prompt.subject();if(target==null||effect==null)return Optional.empty();
    if(me.hero()==null||!me.hero().skillIds().contains("kanpo"))return Optional.empty();
    boolean friend=me==target||state.areTeammates(me,target);
    boolean enemy=state.teamMode()&&!friend;
    if(!state.teamMode()&&target.identityPublic()){
      var own=me.identity();var other=target.identity();
      boolean royal=own==dev.sgs.core.Identity.LORD||own==dev.sgs.core.Identity.LOYALIST;
      boolean otherRoyal=other==dev.sgs.core.Identity.LORD||other==dev.sgs.core.Identity.LOYALIST;
      friend|=royal&&otherRoyal||own==dev.sgs.core.Identity.REBEL&&other==dev.sgs.core.Identity.REBEL;
      enemy=royal&&other==dev.sgs.core.Identity.REBEL||own==dev.sgs.core.Identity.REBEL&&otherRoyal;
    }
    if(!friend&&!enemy)return Optional.empty();
    Boolean helpful=switch(effect){
      case WUZHONG,WUGU,TAOYUAN->true;
      case NANMAN,WANJIAN,JUEDOU,HUOGONG,SHUNSHOU,GUOHE,JIEDAO,LEBU,BINGLIANG,SHANDIAN->false;
      case TIESUO->state.chained(target.seat());
      default->null;
    };
    if(helpful==null)return Optional.empty();
    boolean wantEffect=friend==helpful;
    boolean currentlyResolves=depth%2==0;
    if(wantEffect==currentlyResolves)return Optional.empty();
    var physical=firstOf(me,CardType.WUXIE);if(physical.isPresent())return physical;
    if(me.hero()!=null&&me.hero().skillIds().contains("kanpo"))return me.handView().stream().filter(dev.sgs.core.card.CardInstance::isBlack).findFirst().map(c->PlayDecision.alternate(c,CardType.WUXIE));
    return Optional.empty();
  }
  @Override public dev.sgs.core.engine.CounterspellResult collectCounterspell(dev.sgs.core.game.GameState state,dev.sgs.core.engine.CounterspellWindow window){
    for(var prompt:window.requests()){
      if(window.closed()||window.expired())break;
      window.offer(prompt.asker().id(),prompt.id(),WuxieResponse.answer(counterspell(state,prompt,window.effectType(),window.chainDepth())));
    }
    return window.finish();
  }

  @Override public boolean invoke(dev.sgs.core.game.GameState state,dev.sgs.core.engine.PromptRequest prompt){
    var external=dev.sgs.core.addon.AddonRuntime.current().botInvoke(state,prompt);if(external.isPresent())return external.get();
    return switch(prompt.skillId()){
      case "guanshi"->prompt.subject()!=null&&!state.areTeammates(prompt.asker(),prompt.subject());
      case "hanbing"->prompt.subject()!=null && !state.areTeammates(prompt.asker(),prompt.subject()) && prompt.subject().handCount()+prompt.subject().equipment().all().size()>=2;
      case "cixiong"->true;
      case "qilin_gong"->prompt.subject()!=null && !state.areTeammates(prompt.asker(),prompt.subject());
      case "jianxiong"->true;
      case "jizhi"->true;
      case "keji","yingzi","lianying","tiandu","yiji","xiaoji","biyue","luoshen","ganglie","tieji","guanxing"->true;
      case "luoyi"->prompt.asker().handView().stream().anyMatch(c->c.type()==CardType.SHA || c.type()==CardType.HUOSHA || c.type()==CardType.LEISHA || c.type()==CardType.JUEDOU);
      case "zhuque"->prompt.subject()!=null && prompt.subject().equipment().armor()!=null
          && prompt.subject().equipment().armor().type()==CardType.TENGJIA;
      case "hujia"->prompt.asker().handView().stream().noneMatch(c->c.type()==CardType.SHAN);
      case "jijiang"->attack(prompt.asker()).isEmpty();
      case "bagua"->prompt.asker().identity()==dev.sgs.core.Identity.LOYALIST;
      case "jushou","buqu","niepan","jieming","leiji","fangzhu","bazhen","shuangxiong","xingshang","songwei","zaiqi","lieren","haoshi","tuntian","beige","xinsheng","zhiba_gain"->true;
      case "fangquan"->prompt.asker().handCount()>0 && prompt.asker().handView().stream().noneMatch(c->c.type()==CardType.SHA||c.type()==CardType.HUOSHA||c.type()==CardType.LEISHA);
      case "baonue"->state.areTeammates(prompt.asker(),prompt.subject())||prompt.asker().identity()==dev.sgs.core.Identity.LOYALIST;
      case "zhiba_accept"->true;
      default->false;
    };
  }

  @Override public Optional<dev.sgs.core.card.Suit> chooseSuit(dev.sgs.core.game.GameState state,dev.sgs.core.engine.PromptRequest prompt){
    return Optional.of(dev.sgs.core.card.Suit.SPADE); // Never inspect the source's hidden hand.
  }

  @Override public Optional<dev.sgs.core.engine.SkillChoice> choose(dev.sgs.core.game.GameState state,dev.sgs.core.engine.PromptRequest prompt){
    var external=dev.sgs.core.addon.AddonRuntime.current().botChoice(state,prompt);if(external.isPresent())return external;
    if(prompt.type()==dev.sgs.core.engine.PromptType.EXPANSION_OPTION){
      int value=0;if(prompt.skillId().equals("yinghun"))value=0;
      if(prompt.skillId().equals("zhiji"))value=prompt.asker().hp()<prompt.asker().maxHp()?0:1;
      return Optional.of(new dev.sgs.core.engine.SkillChoice(0,value));
    }
    if(prompt.type()==dev.sgs.core.engine.PromptType.EXPANSION_CHOICE){
      var id=prompt.skillId();boolean aid=java.util.Set.of("jieming","haoshi","zhijian","fangquan").contains(id);
      var targets=prompt.allowedTargetSeats().stream().map(seat->state.players().get(seat)).toList();
      var selected=targets.stream().filter(p->aid?(p==prompt.asker()||state.areTeammates(prompt.asker(),p)):!state.areTeammates(prompt.asker(),p)&&p!=prompt.asker()).findFirst().orElse(null);
      if(!targets.isEmpty()&&selected==null)return Optional.empty();
      var cards=prompt.choices();var cost=cards.stream().max(java.util.Comparator.comparingInt(c->c.rank().value())).orElse(null);
      if(java.util.Set.of("qiaobian","huashen").contains(id))return Optional.empty();
      if(id.equals("beige") && prompt.subject()!=null&&!state.areTeammates(prompt.asker(),prompt.subject())&&prompt.asker()!=prompt.subject())return Optional.empty();
      if(id.equals("shensu") && !cards.isEmpty())return Optional.empty();
      if(cards.isEmpty()&&selected==null)return Optional.empty();
      return Optional.of(new dev.sgs.core.engine.SkillChoice(cost==null?0:cost.instanceId(),selected==null?-1:selected.seat()));
    }
    if(prompt.type()==dev.sgs.core.engine.PromptType.WEAPON_COST_FIRST || prompt.type()==dev.sgs.core.engine.PromptType.WEAPON_COST_SECOND)
      return prompt.choices().stream().findFirst().map(c->new dev.sgs.core.engine.SkillChoice(c.instanceId(),-1));
    if(prompt.type()==dev.sgs.core.engine.PromptType.WEAPON_DISCARD)
      return prompt.choices().stream().filter(c->c.type()!=CardType.SHAN && c.type()!=CardType.TAO && c.type()!=CardType.JIU).findFirst()
          .map(c->new dev.sgs.core.engine.SkillChoice(c.instanceId(),-1));
    if(prompt.type()==dev.sgs.core.engine.PromptType.CARD_ZONE_SELECT && prompt.skillId().equals("qilin_gong"))
      return prompt.choices().stream().filter(c->c.type().category()==dev.sgs.core.card.CardCategory.HORSE_PLUS).findFirst()
          .or(()->prompt.choices().stream().findFirst()).map(c->new dev.sgs.core.engine.SkillChoice(c.instanceId(),-1));
    if(prompt.type()==dev.sgs.core.engine.PromptType.LIULI_TARGET){
      var owner=prompt.asker();
      for(var cost:prompt.choices())for(int seat:prompt.allowedTargetSeats()){
        var target=state.players().get(seat);if(state.areTeammates(owner,target))continue;
        var weapon=owner.equipment().weapon();var horse=owner.equipment().horseMinus();
        int range=weapon!=null && weapon.instanceId()!=cost.instanceId()?weapon.type().weaponRange():1;
        int distance=state.ringDistance(owner,target)-(horse!=null && horse.instanceId()!=cost.instanceId()?1:0)+(target.equipment().hasDefensiveHorse()?1:0);
        if(owner.hero()!=null && owner.hero().skillIds().contains("mashu"))distance--;
        if(Math.max(1,distance)<=range)return Optional.of(new dev.sgs.core.engine.SkillChoice(cost.instanceId(),seat));
      }
      return Optional.empty();
    }
    if(prompt.type()==dev.sgs.core.engine.PromptType.TUXI_TARGET){
      var targets=prompt.allowedTargetSeats().stream().filter(seat->!state.areTeammates(prompt.asker(),state.players().get(seat)))
          .sorted(java.util.Comparator.<Integer>comparingInt(seat->state.players().get(seat).handCount()).reversed()).limit(2).toList();
      return targets.isEmpty()?Optional.empty():Optional.of(dev.sgs.core.engine.SkillChoice.targets(targets));
    }
    if(prompt.type()==dev.sgs.core.engine.PromptType.GANGLIE_DISCARD && !prompt.choices().isEmpty() && prompt.asker().hp()<=2)
      return Optional.of(new dev.sgs.core.engine.SkillChoice(prompt.choices().getFirst().instanceId(),-1));
    if(prompt.type()==dev.sgs.core.engine.PromptType.LIJIAN_TARGET)
      return prompt.allowedTargetSeats().stream().filter(seat->!state.areTeammates(prompt.asker(),state.players().get(seat)))
          .findFirst().map(seat->new dev.sgs.core.engine.SkillChoice(0,seat));
    if(prompt.type()==dev.sgs.core.engine.PromptType.GUANXING_ORDER && !prompt.choices().isEmpty())
      return Optional.of(new dev.sgs.core.engine.SkillChoice(prompt.choices().getFirst().instanceId(),0));
    if(prompt.type()==dev.sgs.core.engine.PromptType.FANKUI_TAKE){
      if(!prompt.choices().isEmpty())return Optional.of(new dev.sgs.core.engine.SkillChoice(prompt.choices().getFirst().instanceId(),-1));
      return prompt.hiddenHandAvailable()?Optional.of(new dev.sgs.core.engine.SkillChoice(0,-1)):Optional.empty();
    }
    if(prompt.type()==dev.sgs.core.engine.PromptType.YIJI_GIVE && !prompt.choices().isEmpty()){
      // Public-team knowledge only. Keep cards if no known teammate needs assistance.
      var recipient=state.players().stream().filter(p->p.alive() && p!=prompt.asker() && state.areTeammates(prompt.asker(),p))
          .min(java.util.Comparator.comparingInt(SgsPlayer::handCount)).filter(p->p.handCount()<prompt.asker().handCount()).orElse(prompt.asker());
      return Optional.of(new dev.sgs.core.engine.SkillChoice(prompt.choices().getFirst().instanceId(),recipient.seat()));
    }
    if(prompt.type()==dev.sgs.core.engine.PromptType.GUI_CAI_REPLACE && !prompt.choices().isEmpty()){
      boolean ally=prompt.subject()==prompt.asker() || state.areTeammates(prompt.asker(),prompt.subject());
      // Read only public processing cards and our own choice list, never an opponent's hand.
      var judgment=state.cards().publicProcessingCards();
      if(judgment.isEmpty())return Optional.empty();
      var current=judgment.getLast();
      var cause=judgment.size()>1?judgment.get(judgment.size()-2).type():null;
      if(cause==CardType.LEBU || cause==CardType.BINGLIANG || cause==CardType.SHANDIAN){
        java.util.function.Predicate<dev.sgs.core.card.CardInstance> safe=card->cause==CardType.LEBU?card.suit()==dev.sgs.core.card.Suit.HEART
            :cause==CardType.BINGLIANG?card.suit()==dev.sgs.core.card.Suit.CLUB
            :!(card.suit()==dev.sgs.core.card.Suit.SPADE && card.rank().value()>=2 && card.rank().value()<=9);
        if(safe.test(current)==ally)return Optional.empty();
        return prompt.choices().stream().filter(card->safe.test(card)==ally).findFirst().map(card->new dev.sgs.core.engine.SkillChoice(card.instanceId(),-1));
      }
      boolean bagua=prompt.subject().equipment().armor()!=null && prompt.subject().equipment().armor().type()==CardType.BAGUA;
      if(bagua && ally && !current.isRed())return prompt.choices().stream().filter(dev.sgs.core.card.CardInstance::isRed).findFirst()
          .map(c->new dev.sgs.core.engine.SkillChoice(c.instanceId(),-1));
    }
    return Optional.empty();
  }

  private static Optional<PlayDecision> firstOf(SgsPlayer player, CardType type) {
    var natural=player.handView().stream()
        .filter(c -> c.type() == type)
        .findFirst()
        .map(PlayDecision::asIs);
    if(natural.isPresent())return natural;
    if(type==CardType.SHAN && player.hero()!=null && player.hero().skillIds().contains("longdan")){
      var converted=player.handView().stream().filter(c->c.type()==CardType.SHA || c.type()==CardType.HUOSHA || c.type()==CardType.LEISHA)
          .findFirst().map(c->PlayDecision.alternate(c,CardType.SHAN));
      if(converted.isPresent())return converted;
    }
    if(type==CardType.SHAN && player.hero()!=null && player.hero().skillIds().contains("qingguo"))
      return player.handView().stream().filter(c->c.suit()==dev.sgs.core.card.Suit.CLUB || c.suit()==dev.sgs.core.card.Suit.SPADE)
          .findFirst().map(c->PlayDecision.alternate(c,CardType.SHAN));
    return Optional.empty();
  }

  private static Optional<PlayDecision> attack(SgsPlayer player){
    var natural=player.handView().stream().filter(c->c.type()==CardType.SHA || c.type()==CardType.HUOSHA || c.type()==CardType.LEISHA)
        .findFirst().map(PlayDecision::asIs);
    if(natural.isPresent())return natural;
    if(player.hero()==null)return Optional.empty();
    if(player.hero().skillIds().contains("longdan")){
      var shan=player.handView().stream().filter(c->c.type()==CardType.SHAN).findFirst();if(shan.isPresent())return Optional.of(PlayDecision.alternate(shan.get(),CardType.SHA));
    }
    if(player.hero().skillIds().contains("wusheng")){
      var red=player.handView().stream().filter(dev.sgs.core.card.CardInstance::isRed).findFirst();
      if(red.isPresent())return Optional.of(PlayDecision.alternate(red.get(),CardType.SHA));
    }
    var weapon=player.equipment().weapon();
    if(weapon!=null && weapon.type()==CardType.ZHANGBA && player.handCount()>=2)
      return Optional.of(PlayDecision.asIs(new dev.sgs.core.card.VirtualCard(CardType.SHA,player.handView().subList(0,2))));
    return Optional.empty();
  }
}
