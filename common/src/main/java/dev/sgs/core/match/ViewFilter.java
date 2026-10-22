package dev.sgs.core.match;

import java.util.ArrayList;
import java.util.List;

import dev.sgs.core.card.CardInstance;
import dev.sgs.core.engine.GameOutcome;
import dev.sgs.core.engine.PromptRequest;
import dev.sgs.core.game.GameState;
import dev.sgs.core.hero.HeroDefinition;
import dev.sgs.core.match.dto.MatchViews.CardView;
import dev.sgs.core.match.dto.MatchViews.JudgmentView;
import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.core.match.dto.MatchViews.PromptView;
import dev.sgs.core.match.dto.MatchViews.SeatView;
import dev.sgs.core.player.Equipment;
import dev.sgs.core.player.SgsPlayer;

/**
 * Builds a {@link MatchSnapshot} for one specific receiver. Hand contents are only included for the
 * viewer's own seat by default; explicit public-team sharing can grant teammate access. Other seats
 * expose hand counts; the draw pile is never exposed; identities are
 * visible only to the owner or when public (lord, dead seats, game over); Wugu choices only go to
 * the asking seat.
 */
public final class ViewFilter {

  private static final int LOG_LINES = 30;

  private ViewFilter() {}

  /** Final seating is known before selection; only self/public teammates see chosen heroes. */
  public static MatchSnapshot selecting(List<? extends SgsPlayer> players,boolean teamMode,
      int viewerSeat,List<String> candidates,long deadline){return selecting(players,teamMode,viewerSeat,candidates,deadline,false);}
  public static MatchSnapshot selecting(List<? extends SgsPlayer> players,boolean teamMode,
      int viewerSeat,List<String> candidates,long deadline,boolean publicLord){
    List<SeatView> seats=new ArrayList<>(players.size());
    int viewerTeam=selectionTeam(teamMode,viewerSeat);
    int lord=dev.sgs.core.match.dto.MatchViews.HIDDEN;
    for(var player:players){
      int team=selectionTeam(teamMode,player.seat());boolean self=player.seat()==viewerSeat;
      boolean chosenVisible=self || (viewerTeam>=0 && viewerTeam==team) || publicLord&&!teamMode&&player.identity()==dev.sgs.core.Identity.LORD;
      var hero=chosenVisible?player.hero():null;
      boolean roleVisible=!teamMode && (self || player.identityPublic());
      if(!teamMode && player.identity()==dev.sgs.core.Identity.LORD)lord=player.seat();
      int hp=hero==null?0:hero.maxHp()+(!teamMode && player.identity()==dev.sgs.core.Identity.LORD?GameState.LORD_EXTRA_HP:0);
      seats.add(new SeatView(player.seat(),player.name(),player instanceof MatchPlayer m && m.human(),player.online(),
          true,hero==null?null:hero.id(),hero==null?-1:hero.kingdom().ordinal(),hp,hp,
          roleVisible?player.identity().ordinal():-1,0,null,null,null,null,List.of(),List.of(),self,false,false,team));
    }
    return new MatchSnapshot(dev.sgs.core.match.dto.MatchViews.STATUS_SELECTING,viewerSeat,0,0,
        dev.sgs.core.match.dto.MatchViews.HIDDEN,lord,0,dev.sgs.core.match.dto.MatchViews.HIDDEN,0L,
        dev.sgs.core.match.dto.MatchViews.HIDDEN,List.of(),List.copyOf(seats),null,
        viewerSeat<0?List.of():List.copyOf(candidates),deadline,null);
  }
  /** Before reveal, even a private owner's hidden role is omitted. Public lord visibility remains. */
  public static MatchSnapshot identityIntro(List<? extends SgsPlayer> players,int viewerSeat,int stage,long deadline,int chosenBackSlot){
    var seats=new ArrayList<SeatView>();int lord=dev.sgs.core.match.dto.MatchViews.HIDDEN;
    for(var player:players){
      boolean self=viewerSeat==player.seat();boolean visible=player.identityPublic() || self&&stage>=1;
      if(player.identity()==dev.sgs.core.Identity.LORD)lord=player.seat();
      seats.add(new SeatView(player.seat(),player.name(),player instanceof MatchPlayer m&&m.human(),player.online(),true,
          null,-1,0,0,visible?player.identity().ordinal():-1,0,null,null,null,null,List.of(),List.of(),self,false,false,-1));
    }
    return new MatchSnapshot(dev.sgs.core.match.dto.MatchViews.STATUS_SELECTING,viewerSeat,0,0,-1,lord,0,-1,0,-1,
        List.of(),seats,null,List.of(),0,null,List.of(),List.of(),List.of(),List.of(),
        viewerSeat<0?null:new dev.sgs.core.match.dto.MatchViews.IdentityIntroView(stage,deadline,chosenBackSlot));
  }
  private static int selectionTeam(boolean teamMode,int seat){return teamMode && seat>=0 && seat<4?(seat==0 || seat==3?0:1):-1;}

  /** Snapshot during hero selection: only the viewer's candidate offer matters. */
  public static MatchSnapshot selecting(
      int viewerSeat, List<String> candidates, long selectionDeadlineMillis) {
    return new MatchSnapshot(
        dev.sgs.core.match.dto.MatchViews.STATUS_SELECTING,
        viewerSeat,
        0,
        0,
        dev.sgs.core.match.dto.MatchViews.HIDDEN,
        dev.sgs.core.match.dto.MatchViews.HIDDEN,
        0,
        0,
        0L,
        dev.sgs.core.match.dto.MatchViews.HIDDEN,
        List.of(),
        List.of(),
        null,
        List.copyOf(candidates),
        selectionDeadlineMillis,
        null);
  }

  /** Per-viewer snapshot of a running or finished match. */
  public static MatchSnapshot running(
      GameState state,
      int viewerSeat,
      long phaseDeadlineMillis,
      PromptRequest prompt,
      GameOutcome outcome,
      JudgmentView judgment) {
    return running(state,viewerSeat,phaseDeadlineMillis,prompt,outcome,judgment,HandVisibility.PRIVATE);
  }

  /** Team modes must supply authoritative public teams and the room sharing setting. */
  public static MatchSnapshot running(GameState state,int viewerSeat,long phaseDeadlineMillis,
      PromptRequest prompt,GameOutcome outcome,JudgmentView judgment,HandVisibility visibility) {
    boolean finished = outcome != null && outcome != GameOutcome.ONGOING;
    List<SeatView> seats = new ArrayList<>(state.players().size());
    var viewer=state.players().stream().filter(p->p.seat()==viewerSeat && p.alive()).findFirst().orElse(null);
    for (SgsPlayer player : state.players()) {
      Integer distance=viewer!=null && player.alive() && player!=viewer?state.distance(viewer,player):null;
      seats.add(seatView(player,viewerSeat,finished,visibility,state.chained(player.seat()),state.teamOf(player.seat()),state.publicStatuses(player.seat())).withDistanceFromViewer(distance).withKingdom(state.kingdom(player).ordinal()));
    }
    List<String> logs = new ArrayList<>(state.recentLogs());
    while (logs.size() > LOG_LINES) {
      logs.remove(0);
    }
    int lordSeat =
        state.players().isEmpty() ? dev.sgs.core.match.dto.MatchViews.HIDDEN : state.lordSeat();
    return new MatchSnapshot(
        finished
            ? dev.sgs.core.match.dto.MatchViews.STATUS_FINISHED
            : dev.sgs.core.match.dto.MatchViews.STATUS_RUNNING,
        viewerSeat,
        state.turnNumber(),
        state.phase().ordinal(),
        state.currentSeat(),
        lordSeat,
        state.deck().drawPileCount(),
        dev.sgs.core.match.dto.MatchViews.HIDDEN,
        phaseDeadlineMillis,
        finished ? outcome.ordinal() : dev.sgs.core.match.dto.MatchViews.HIDDEN,
        List.copyOf(logs),
        List.copyOf(seats),
        promptView(prompt, viewerSeat),
        List.of(),
        0L,
        judgment,
        state.cards().publicProcessingCards().stream().map(CardView::of).toList(),
        state.cards().recentPublicMoves().stream().skip(Math.max(0, state.cards().recentPublicMoves().size() - 12))
            .map(move -> new dev.sgs.core.match.dto.MatchViews.PublicMoveView(move.sequence(), move.actorSeat(),
                move.reason().name(), move.cards().stream().map(CardView::of).toList(),move.from().kind().name(),move.from().seat(),move.to().kind().name(),move.to().seat())).toList(),
        state.publicSkillActivations().stream().map(event->
            new dev.sgs.core.match.dto.MatchViews.PublicSkillActivationView(event.sequence(),event.seat(),event.skillId())).toList()).withCardMotions(motionViews(state,viewerSeat)).withPublicCardReveals(state.publicCardReveals().stream().map(dev.sgs.core.match.dto.MatchViews.PublicCardRevealView::of).toList());
  }

  private static List<dev.sgs.core.match.dto.MatchViews.CardMotionView> motionViews(GameState state,int viewerSeat){
    return state.cards().recentMoves().stream().skip(Math.max(0,state.cards().recentMoves().size()-32))
      // Internal piles/special selections reveal neither their identity nor their ordering.
      .filter(m->m.reason()!=dev.sgs.core.card.CardMove.Reason.SHUFFLE
          && m.from().kind()!=dev.sgs.core.card.CardZone.Kind.SPECIAL && m.to().kind()!=dev.sgs.core.card.CardZone.Kind.SPECIAL
          && m.to().kind()!=dev.sgs.core.card.CardZone.Kind.DRAW)
      .map(m->{
        boolean ownHand=viewerSeat>=0 && (m.from().kind()==dev.sgs.core.card.CardZone.Kind.HAND&&m.from().seat()==viewerSeat
            ||m.to().kind()==dev.sgs.core.card.CardZone.Kind.HAND&&m.to().seat()==viewerSeat);
        var faces=m.publicCards()||ownHand?m.cards().stream().map(CardView::of).toList():List.<CardView>of();
        return new dev.sgs.core.match.dto.MatchViews.CardMotionView(m.sequence(),m.actorSeat(),m.reason().name(),
            m.from().kind().name(),m.from().seat(),m.to().kind().name(),m.to().seat(),m.cards().size(),faces);
      }).toList();
  }

  /** Each recipient receives only their real counterspell token, never another actor's status. */
  public static MatchSnapshot counterspell(GameState state,int viewerSeat,long phaseDeadline,
      PromptRequest ownPrompt,dev.sgs.core.match.dto.MatchViews.CounterspellWindowView window,
      GameOutcome outcome,JudgmentView judgment,HandVisibility visibility){
    if(ownPrompt!=null && (viewerSeat<0 || ownPrompt.asker().seat()!=viewerSeat))throw new IllegalArgumentException("foreign counterspell token");
    return running(state,viewerSeat,phaseDeadline,ownPrompt,outcome,judgment,visibility).withCounterspellWindow(window);
  }

  private static SeatView seatView(SgsPlayer player,int viewerSeat,boolean finished,HandVisibility visibility,boolean chained,int teamId,List<dev.sgs.core.match.dto.MatchViews.StatusView> statuses) {
    boolean self = player.seat() == viewerSeat;
    HeroDefinition hero = player.hero();
    boolean identityVisible = teamId<0 && (finished || self || player.identityPublic());
    List<CardView> hand = new ArrayList<>();
    boolean handVisible = visibility.maySee(viewerSeat,player.seat());
    if (handVisible) {
      for (CardInstance card : player.handView()) {
        hand.add(CardView.of(card,self,true));
      }
    }
    List<CardView> delay = new ArrayList<>();
    for (CardInstance card : player.delayZone().cards()) {
      delay.add(new CardView(card.instanceId(),player.delayZone().effectiveType(card).ordinal(),card.suit().ordinal(),card.rank().ordinal()));
    }
    Equipment equipment = player.equipment();
    return new SeatView(
        player.seat(),
        player.name(),
        player instanceof MatchPlayer matchPlayer && matchPlayer.human(),
        player.online(),
        player.alive(),
        hero == null ? null : hero.id(),
        hero == null ? dev.sgs.core.match.dto.MatchViews.HIDDEN : hero.kingdom().ordinal(),
        player.hp(),
        player.maxHp(),
        identityVisible ? player.identity().ordinal() : dev.sgs.core.match.dto.MatchViews.HIDDEN,
        player.handCount(),
        equipment.weapon() == null ? null : CardView.of(equipment.weapon()),
        equipment.armor() == null ? null : CardView.of(equipment.armor()),
        equipment.horsePlus() == null ? null : CardView.of(equipment.horsePlus()),
        equipment.horseMinus() == null ? null : CardView.of(equipment.horseMinus()),
        List.copyOf(delay),
        List.copyOf(hand),
        self,handVisible,chained,teamId,equipment.treasure()==null?null:CardView.of(equipment.treasure()),statuses);
  }

  private static PromptView promptView(PromptRequest prompt, int viewerSeat) {
    if (prompt == null) {
      return null;
    }
    boolean asker = prompt.asker().seat() == viewerSeat;
    List<Integer> allowed = new ArrayList<>();
    List<CardView> choices = new ArrayList<>();
    if (asker) {
      prompt.allowedTypes().forEach(type -> allowed.add(type.ordinal()));
    }
    if(asker || prompt.type()==dev.sgs.core.engine.PromptType.HUOGONG_PAY){
      if(prompt.type()==dev.sgs.core.engine.PromptType.CARD_ZONE_SELECT){
        // Never serialize target hand IDs, even if an upstream producer accidentally offers them.
        var subject=prompt.subject();
        if(subject!=null){
          var publicCards=new java.util.HashMap<Long,CardView>();
          for(var card:subject.equipment().all())publicCards.put(card.instanceId(),CardView.of(card));
          for(var card:subject.delayZone().cards())publicCards.put(card.instanceId(),new CardView(card.instanceId(),
              subject.delayZone().effectiveType(card).ordinal(),card.suit().ordinal(),card.rank().ordinal()));
          for(var card:prompt.choices()){var visible=publicCards.get(card.instanceId());if(visible!=null)choices.add(visible);}
        }
      }else prompt.choices().forEach(card -> choices.add(CardView.of(card)));
    }
    return new PromptView(
        prompt.id(),
        prompt.type().ordinal(),
        prompt.asker().seat(),
        prompt.subject() == null
            ? dev.sgs.core.match.dto.MatchViews.HIDDEN
            : prompt.subject().seat(),
        prompt.deadlineMillis(),
        List.copyOf(allowed),
        List.copyOf(choices),!asker&&prompt.type()==dev.sgs.core.engine.PromptType.EXPANSION_OPTION?prompt.skillId().split("\\|",2)[0]:prompt.skillId(),asker?prompt.allowedTargetSeats():List.of(),asker && prompt.hiddenHandAvailable());
  }
}
