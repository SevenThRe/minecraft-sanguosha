package dev.sgs.client.game;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.sgs.client.LobbyScreen;
import dev.sgs.client.SgsScreen;
import dev.sgs.client.SgsTheme;
import dev.sgs.core.Phase;
import dev.sgs.core.card.CardType;
import dev.sgs.core.card.Rank;
import dev.sgs.core.card.Suit;
import dev.sgs.core.engine.GameOutcome;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.match.MatchAction;
import dev.sgs.core.match.dto.MatchViews.CardView;
import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.core.match.dto.MatchViews.PromptView;
import dev.sgs.core.match.dto.MatchViews.SeatView;
import dev.sgs.network.SgsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Fullscreen placeholder card table: hero pick, seat ring, hand bar, prompts and settlement. */
public final class GameScreen extends SgsScreen {

  private static final Set<CardType> TARGETED_CARDS =
      EnumSet.of(
          CardType.SHA,
          CardType.HUOSHA,
          CardType.LEISHA,
          CardType.JUEDOU,
          CardType.SHUNSHOU,
          CardType.GUOHE,
          CardType.JIEDAO);

  private MatchSnapshot builtFor;
  private long pendingCardId = MatchAction.NO_CARD;
  private final Set<Long> discardSelection = new HashSet<>();
  private final Map<Integer, int[]> seatRects = new HashMap<>();

  // local-only identity guess marks (seat ordinal -> Identity ordinal, -1 = cleared)
  private final Map<Integer, Integer> guessMarks = new HashMap<>();
  private Integer openGuessSeat;

  public GameScreen() {
    super(Component.translatable("screen.sgs.game.title"));
  }

  // ---------------------------------------------------------------- lifecycle

  @Override
  public boolean shouldCloseOnEsc() {
    MatchSnapshot snap = ClientGameState.snapshot();
    return snap == null || snap.status() == dev.sgs.core.match.dto.MatchViews.STATUS_FINISHED;
  }

  @Override
  public void onClose() {
    MatchSnapshot snap = ClientGameState.snapshot();
    if (snap != null && snap.status() != dev.sgs.core.match.dto.MatchViews.STATUS_FINISHED) {
      return;
    }
    leaveAndExit();
  }

  private void leaveAndExit() {
    SgsNetwork.sendGameAction(MatchAction.leave(ClientGameState.nextNonce()));
    ClientGameState.clear();
    if (minecraft != null) {
      minecraft.setScreen(new LobbyScreen());
    }
  }

  // ---------------------------------------------------------------- widget rebuild

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderSgsBackground(graphics);
    MatchSnapshot snap = ClientGameState.snapshot();
    if (snap == null) {
      renderTitle(graphics, width, this.title);
      graphics.drawCenteredString(
          this.font,
          Component.translatable("screen.sgs.game.waiting"),
          width / 2,
          height / 2,
          SgsTheme.TEXT_DIM);
      return;
    }
    if (snap != builtFor) {
      builtFor = snap;
      rebuildWidgetsFor(snap);
    }
    computeSeatRects(snap);

    if (snap != null && snap.status() == dev.sgs.core.match.dto.MatchViews.STATUS_SELECTING) {
      super.render(graphics, mouseX, mouseY, partialTick);
      renderHeroSelection(graphics, snap);
      return;
    }

    renderTable(graphics, snap);
    super.render(graphics, mouseX, mouseY, partialTick);
    EffectsManager.tick();
    renderEffects(graphics, snap);
    renderHandBar(graphics, snap);
    renderPrompt(graphics, snap);
    renderJudgment(graphics, snap);
    renderGuessPanel(graphics, mouseX, mouseY);
    if (snap.status() == dev.sgs.core.match.dto.MatchViews.STATUS_FINISHED) {
      renderFinished(graphics, snap);
    }
  }

  private void rebuildWidgetsFor(MatchSnapshot snap) {
    clearWidgets();
    pendingCardId = MatchAction.NO_CARD;
    SeatView selfAtPrune = selfSeat(snap);
    if (selfAtPrune != null) {
      Set<Long> handIds = new HashSet<>();
      for (CardView card : selfAtPrune.hand()) {
        handIds.add(card.instanceId());
      }
      discardSelection.retainAll(handIds);
    }

    if (snap.status() == dev.sgs.core.match.dto.MatchViews.STATUS_SELECTING) {
      buildHeroButtons(snap);
      return;
    }
    if (snap.status() == dev.sgs.core.match.dto.MatchViews.STATUS_FINISHED) {
      addRenderableWidget(
          Button.builder(Component.translatable("screen.sgs.game.leave"), b -> leaveAndExit())
              .bounds(width / 2 - 60, height / 2 + 18, 120, 20)
              .build());
    }

    SeatView self = selfSeat(snap);
    boolean myPlay =
        self != null
            && self.alive()
            && snap.currentSeat() == snap.viewerSeat()
            && snap.phaseOrdinal() == Phase.PLAY.ordinal();
    boolean myDiscard =
        self != null
            && self.alive()
            && snap.currentSeat() == snap.viewerSeat()
            && snap.phaseOrdinal() == Phase.DISCARD.ordinal();

    buildHandButtons(snap, self, myPlay, myDiscard);

    if (myPlay) {
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.sgs.game.end_play"),
                  b -> SgsNetwork.sendGameAction(MatchAction.endPlay(ClientGameState.nextNonce())))
              .bounds(width / 2 + 180, height - 30, 80, 20)
              .build());
    }
    if (myDiscard) {
      int required = requiredDiscard(self);
      boolean enabled = discardSelection.size() == required;
      Button confirm =
          addRenderableWidget(
              Button.builder(
                      Component.translatable("screen.sgs.game.confirm_discard", required),
                      b ->
                          SgsNetwork.sendGameAction(
                              MatchAction.confirmDiscard(
                                  ClientGameState.nextNonce(), List.copyOf(discardSelection))))
                  .bounds(width / 2 + 170, height - 30, 100, 20)
                  .build());
      confirm.active = enabled;
    }

    buildPromptButtons(snap);
  }

  private void buildHeroButtons(MatchSnapshot snap) {
    List<String> candidates = snap.heroCandidates();
    int btnW = 96;
    int btnH = 20;
    int cols = 3;
    int panelW = 320;
    int panelH = 40 + ((candidates.size() + cols - 1) / cols) * 26;
    int left = width / 2 - panelW / 2;
    int top = height / 2 - panelH / 2;
    for (int i = 0; i < candidates.size(); i++) {
      String heroId = candidates.get(i);
      int col = i % cols;
      int row = i / cols;
      addRenderableWidget(
          Button.builder(
                  heroLabel(heroId),
                  b ->
                      SgsNetwork.sendGameAction(
                          MatchAction.selectHero(ClientGameState.nextNonce(), heroId)))
              .bounds(left + 16 + col * (btnW + 8), top + 34 + row * 26, btnW, btnH)
              .build());
    }
  }

  private void buildHandButtons(
      MatchSnapshot snap, SeatView self, boolean myPlay, boolean myDiscard) {
    if (self == null) {
      return;
    }
    List<CardView> hand = self.hand();
    int cardW = 78;
    int total = hand.size() * cardW - 6;
    int x = width / 2 - total / 2;
    int y = height - 28;
    for (CardView card : hand) {
      final long cardId = card.instanceId();
      Button button =
          addRenderableWidget(
              Button.builder(cardLabel(card), b -> onHandCardClicked(card, myPlay, myDiscard))
                  .bounds(x, y, cardW - 6, 20)
                  .build());
      button.active = myPlay || myDiscard;
      x += cardW;
    }
  }

  private void onHandCardClicked(CardView card, boolean myPlay, boolean myDiscard) {
    long cardId = card.instanceId();
    if (myDiscard) {
      if (!discardSelection.remove(cardId)) {
        discardSelection.add(cardId);
      }
      rebuildWidgetsFor(ClientGameState.snapshot());
      return;
    }
    if (!myPlay) {
      return;
    }
    CardType type = typeOf(card);
    if (TARGETED_CARDS.contains(type)) {
      pendingCardId = pendingCardId == cardId ? MatchAction.NO_CARD : cardId;
    } else {
      SgsNetwork.sendGameAction(
          MatchAction.playCard(
              ClientGameState.nextNonce(), cardId, MatchAction.NO_SEAT, MatchAction.NO_SEAT));
    }
  }

  private void buildPromptButtons(MatchSnapshot snap) {
    PromptView prompt = snap.prompt();
    if (!isPromptForMe(snap, prompt)) {
      return;
    }
    int x = width / 2 - 196;
    int y = 74;
    for (CardView choice : prompt.choices()) {
      final long cardId = choice.instanceId();
      addRenderableWidget(
          Button.builder(
                  cardLabel(choice),
                  b ->
                      SgsNetwork.sendGameAction(
                          MatchAction.promptResponse(
                              ClientGameState.nextNonce(), prompt.promptId(), true, cardId, -1)))
              .bounds(x, y, 96, 18)
              .build());
      x += 100;
      if (x > width / 2 + 100) {
        x = width / 2 - 196;
        y += 20;
      }
    }
    int declineX = prompt.choices().isEmpty() ? width / 2 - 100 : width / 2 + 110;
    addRenderableWidget(
        Button.builder(
                Component.translatable("screen.sgs.game.prompt.decline"),
                b ->
                    SgsNetwork.sendGameAction(
                        MatchAction.promptResponse(
                            ClientGameState.nextNonce(),
                            prompt.promptId(),
                            false,
                            MatchAction.NO_CARD,
                            -1)))
            .bounds(declineX, 74, 90, 18)
            .build());
  }

  // ---------------------------------------------------------------- mouse

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    int mx = (int) mouseX;
    int my = (int) mouseY;
    // 1) Guess-panel buttons consume the click first.
    if (openGuessSeat != null && tryHandleGuessClick(mx, my)) {
      return true;
    }

    MatchSnapshot snap = ClientGameState.snapshot();
    // 2) Play-a-targeted-card interaction (existing behaviour).
    if (snap != null && pendingCardId != MatchAction.NO_CARD) {
      for (Map.Entry<Integer, int[]> entry : seatRects.entrySet()) {
        if (entry.getKey() == snap.viewerSeat()) {
          continue;
        }
        int[] rect = entry.getValue();
        if (mx >= rect[0] && mx <= rect[2] && my >= rect[1] && my <= rect[3]) {
          SgsNetwork.sendGameAction(
              MatchAction.playCard(
                  ClientGameState.nextNonce(), pendingCardId, entry.getKey(), MatchAction.NO_SEAT));
          pendingCardId = MatchAction.NO_CARD;
          return true;
        }
      }
    }
    // 3) Click on a seat card whose identity is hidden → open guess panel.
    if (snap != null
        && snap.status() != dev.sgs.core.match.dto.MatchViews.STATUS_SELECTING
        && openGuessSeat == null) {
      for (Map.Entry<Integer, int[]> e : seatRects.entrySet()) {
        int seat = e.getKey();
        int[] rect = e.getValue();
        if (mx >= rect[0] && mx <= rect[2] && my >= rect[1] && my <= rect[3]) {
          if (button == 0) {
            SeatView seatView = seatByNumber(snap, seat);
            if (seatView != null
                && !seatView.self()
                && seatView.alive()
                && seatView.identityOrdinal() < 0) {
              openGuessSeat = seat;
              return true;
            }
          }
        }
      }
    }
    // 4) Click elsewhere closes the guess panel.
    if (openGuessSeat != null) {
      openGuessSeat = null;
      return true;
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  // ---------------------------------------------------------------- table render

  private void renderTable(GuiGraphics graphics, MatchSnapshot snap) {
    int cx = width / 2;
    int feltTop = 40;
    int feltBottom = height - 52;
    graphics.fill(60, feltTop, width - 60, feltBottom, SgsTheme.PANEL);
    graphics.fill(60, feltTop, width - 60, feltTop + 2, SgsTheme.FRAME);
    graphics.fill(60, feltBottom - 2, width - 60, feltBottom, SgsTheme.FRAME);

    renderTopBar(graphics, snap);

    for (SeatView seat : snap.seats()) {
      int[] rect = seatRects.get(seat.seat());
      if (rect != null) {
        renderSeat(graphics, snap, seat, rect);
      }
    }

    renderLog(graphics, snap);
  }

  private void renderTopBar(GuiGraphics graphics, MatchSnapshot snap) {
    int cx = width / 2;
    Phase phase = Phase.values()[snap.phaseOrdinal()];
    SeatView current = seatByNumber(snap, snap.currentSeat());
    String currentName = current == null ? "-" : current.name();
    Component line =
        Component.translatable(
            "screen.sgs.game.topbar",
            snap.turnNumber(),
            Component.translatable("sgs.phase." + phase.id()),
            currentName,
            snap.drawPileCount());
    graphics.drawCenteredString(this.font, line, cx, 22, SgsTheme.TEXT);

    long remaining = (snap.phaseDeadlineMillis() - System.currentTimeMillis()) / 1000;
    if (remaining > 0) {
      graphics.drawString(
          this.font,
          Component.translatable("screen.sgs.game.deadline", remaining),
          cx + 240,
          22,
          remaining <= 3 ? SgsTheme.ERROR : SgsTheme.TEXT_DIM);
    }
  }

  private void renderSeat(GuiGraphics graphics, MatchSnapshot snap, SeatView seat, int[] rect) {
    int x1 = rect[0];
    int y1 = rect[1];
    int x2 = rect[2];
    int y2 = rect[3];
    int kingdom = SgsTheme.kingdomColor(seat.kingdomOrdinal());
    boolean isLord = snap.lordSeatOrdinal() == seat.seat();

    // Lord seat gets the warm gold rim regardless of turn highlight.
    int frameColor = isLord ? SgsTheme.GOLD : SgsTheme.FRAME;
    graphics.fill(x1, y1, x2, y2, frameColor);
    graphics.fill(x1 + 2, y1 + 2, x2 - 2, y2 - 2, 0xFF20241F);
    graphics.fill(x1 + 2, y1 + 2, x2 - 2, y1 + 5, kingdom);

    if (!seat.alive()) {
      graphics.fill(x1 + 2, y1 + 5, x2 - 2, y2 - 2, 0xCC101010);
    }
    if (seat.seat() == snap.currentSeat()) {
      graphics.renderOutline(x1, y1, x2 - x1, y2 - y1, SgsTheme.GOLD);
    } else if (pendingCardId != MatchAction.NO_CARD
        && seat.alive()
        && seat.seat() != snap.viewerSeat()) {
      graphics.renderOutline(x1, y1, x2 - x1, y2 - y1, SgsTheme.OK);
    }

    // Lord badge on the top-left corner.
    if (isLord) {
      int badgeW = 26;
      graphics.fill(x1 + 2, y1 - 1, x1 + badgeW + 2, y1 + 9, SgsTheme.GOLD);
      graphics.drawString(this.font, "主", x1 + 4, y1 + 1, 0xFF2A1E10);
      graphics.drawString(this.font, "公", x1 + 16, y1 + 1, 0xFF2A1E10);
    }

    int textColor = seat.online() ? SgsTheme.TEXT : SgsTheme.TEXT_DIM;
    graphics.drawString(this.font, seat.name(), x1 + 5, y1 + 7, textColor);
    graphics.drawString(
        this.font, "x" + seat.handCount(), x2 - 18, y1 + 7, SgsTheme.CARD_BACK | 0xFF000000);
    if (seat.heroId() != null) {
      graphics.drawString(this.font, heroLabel(seat.heroId()), x1 + 5, y1 + 17, kingdom);
    }

    for (int i = 0; i < seat.maxHp(); i++) {
      int color = i < seat.hp() ? SgsTheme.ERROR : 0xFF442222;
      graphics.drawString(this.font, "♥", x1 + 6 + i * 9, y1 + 28, color);
    }

    // Identity line: real / guess / unknown.
    int identityColor = SgsTheme.TEXT_DIM;
    String identityLabel;
    if (seat.identityOrdinal() >= 0) {
      dev.sgs.core.Identity ident = dev.sgs.core.Identity.values()[seat.identityOrdinal()];
      identityColor = identityColor(seat.identityOrdinal());
      identityLabel = Component.translatable("sgs.identity." + ident.id()).getString();
    } else if (guessMarks.containsKey(seat.seat())) {
      Integer guess = guessMarks.get(seat.seat());
      if (guess != null && guess >= 0) {
        dev.sgs.core.Identity guessed = dev.sgs.core.Identity.values()[guess];
        identityColor = identityColor(guess);
        identityLabel = "?" + Component.translatable("sgs.identity." + guessed.id()).getString();
      } else {
        identityLabel = seat.self() ? "??" : "?";
      }
    } else {
      identityLabel = seat.self() ? "??" : "?";
    }
    graphics.drawString(this.font, identityLabel, x1 + 5, y1 + 39, identityColor);

    int tagX = x1 + 40;
    tagX = drawEquipTag(graphics, seat.weapon(), "W", tagX, y1 + 39);
    tagX = drawEquipTag(graphics, seat.armor(), "A", tagX, y1 + 39);
    tagX = drawEquipTag(graphics, seat.horsePlus(), "+", tagX, y1 + 39);
    tagX = drawEquipTag(graphics, seat.horseMinus(), "-", tagX, y1 + 39);
    if (!seat.delay().isEmpty()) {
      graphics.drawString(this.font, "D" + seat.delay().size(), tagX, y1 + 39, SgsTheme.GOLD);
    }

    if (!seat.alive()) {
      graphics.drawCenteredString(
          this.font,
          Component.translatable("screen.sgs.game.dead"),
          (x1 + x2) / 2,
          y2 - 11,
          SgsTheme.DEAD);
    } else if (seat.self()) {
      graphics.drawString(
          this.font,
          Component.translatable("screen.sgs.game.you"),
          x2 - 16,
          y2 - 11,
          SgsTheme.GOLD);
    }
  }

  // ---------------------------------------------------------------- effects (kill flash / turn sweep)

  private void renderEffects(GuiGraphics graphics, MatchSnapshot snap) {
    for (EffectsManager.Effect effect : EffectsManager.active()) {
      int[] rect = seatRects.get(effect.seat());
      if (rect == null) {
        continue;
      }
      float p = effect.progress();
      int w = rect[2] - rect[0];
      int h = rect[3] - rect[1];
      if (effect.kind() == EffectsManager.Kind.KILL) {
        // Red flash fading from opaque to transparent over 1.5s.
        int alpha = (int) ((1f - p) * 180);
        graphics.fill(rect[0], rect[1], rect[2], rect[3], (alpha << 24) | 0x881010);
      } else if (effect.kind() == EffectsManager.Kind.TURN) {
        // Gold ring outline expanding outward — thin gold border that fades.
        int alpha = (int) ((1f - p) * 200);
        int inset = (int) (p * 8);
        graphics.renderOutline(
            rect[0] - inset, rect[1] - inset, w + inset * 2, h + inset * 2,
            (alpha << 24) | SgsTheme.GOLD);
      }
    }
  }

  private int drawEquipTag(GuiGraphics graphics, CardView card, String tag, int x, int y) {
    if (card == null) {
      return x;
    }
    graphics.drawString(this.font, tag, x, y, SgsTheme.TEXT_DIM);
    return x + 11;
  }

  private void renderLog(GuiGraphics graphics, MatchSnapshot snap) {
    List<String> logs = snap.logLines();
    int x = 70;
    int y = height - 150;
    int start = Math.max(0, logs.size() - 8);
    for (int i = start; i < logs.size(); i++) {
      graphics.drawString(this.font, logs.get(i), x, y, SgsTheme.TEXT_DIM);
      y += 10;
    }
  }

  private void renderHandBar(GuiGraphics graphics, MatchSnapshot snap) {
    SeatView self = selfSeat(snap);
    if (self == null) {
      return;
    }
    int cx = width / 2;
    if (snap.currentSeat() == snap.viewerSeat() && snap.phaseOrdinal() == Phase.PLAY.ordinal()) {
      if (pendingCardId != MatchAction.NO_CARD) {
        graphics.drawCenteredString(
            this.font,
            Component.translatable("screen.sgs.game.pick_target"),
            cx,
            height - 48,
            SgsTheme.OK);
      } else {
        graphics.drawCenteredString(
            this.font,
            Component.translatable("screen.sgs.game.your_play"),
            cx,
            height - 48,
            SgsTheme.GOLD);
      }
    } else if (snap.currentSeat() == snap.viewerSeat()
        && snap.phaseOrdinal() == Phase.DISCARD.ordinal()) {
      graphics.drawCenteredString(
          this.font,
          Component.translatable("screen.sgs.game.your_discard", requiredDiscard(self)),
          cx,
          height - 48,
          SgsTheme.GOLD);
    }
  }

  private void renderPrompt(GuiGraphics graphics, MatchSnapshot snap) {
    PromptView prompt = snap.prompt();
    if (prompt == null) {
      return;
    }
    int panelW = 404;
    int x = width / 2 - panelW / 2;
    int y = 46;
    boolean forMe = isPromptForMe(snap, prompt);
    graphics.fill(x, y, x + panelW, y + 50, forMe ? 0xEE202838 : 0xAA181820);
    PromptType type = PromptType.values()[prompt.typeOrdinal()];
    long remaining = (prompt.deadlineMillis() - System.currentTimeMillis()) / 1000;
    Component line =
        Component.translatable(
            "sgs.prompt." + type.name().toLowerCase(),
            seatName(snap, prompt.askerSeat()),
            seatName(snap, prompt.subjectSeat()),
            Math.max(remaining, 0));
    graphics.drawCenteredString(this.font, line, width / 2, y + 6, SgsTheme.GOLD);
  }

  private void renderJudgment(GuiGraphics graphics, MatchSnapshot snap) {
    if (snap.judgment() == null) {
      return;
    }
    int x = width / 2 - 30;
    int y = height / 2 - 42;
    graphics.fill(x, y, x + 60, y + 84, 0xFFF5E9D0);
    graphics.renderOutline(x, y, 60, 84, SgsTheme.GOLD);
    CardView card = snap.judgment().card();
    Suit suit = Suit.values()[card.suitOrdinal()];
    Rank rank = Rank.values()[card.rankOrdinal()];
    int color = suit.red() ? SgsTheme.ERROR : SgsTheme.TEXT;
    graphics.drawString(this.font, rank.label(), x + 4, y + 4, color);
    graphics.drawCenteredString(this.font, cardLabel(card), x + 30, y + 38, color);
  }

  private void renderHeroSelection(GuiGraphics graphics, MatchSnapshot snap) {
    int panelW = 320;
    int rows = Math.max(1, (snap.heroCandidates().size() + 2) / 3);
    int panelH = 40 + rows * 26 + 26;
    int left = width / 2 - panelW / 2;
    int top = height / 2 - panelH / 2;
    graphics.fill(left, top, left + panelW, top + panelH, 0xEE1A2028);
    graphics.renderOutline(left, top, panelW, panelH, SgsTheme.GOLD);
    graphics.drawCenteredString(
        this.font,
        Component.translatable("screen.sgs.game.select_hero"),
        width / 2,
        top + 10,
        SgsTheme.GOLD);
    long remaining = (snap.selectionDeadlineMillis() - System.currentTimeMillis()) / 1000;
    graphics.drawCenteredString(
        this.font,
        Component.translatable("screen.sgs.game.deadline", Math.max(remaining, 0)),
        width / 2,
        top + panelH - 18,
        SgsTheme.TEXT_DIM);
  }

  private void renderFinished(GuiGraphics graphics, MatchSnapshot snap) {
    graphics.fill(0, 0, width, height, 0xAA000000);
    int panelW = 280;
    int panelH = 96;
    int left = width / 2 - panelW / 2;
    int top = height / 2 - panelH / 2;
    graphics.fill(left, top, left + panelW, top + panelH, 0xEE1A2028);
    graphics.renderOutline(left, top, panelW, panelH, SgsTheme.GOLD);
    GameOutcome outcome = GameOutcome.values()[snap.outcomeOrdinal()];
    graphics.drawCenteredString(
        this.font,
        Component.translatable("sgs.outcome." + outcome.name().toLowerCase()),
        width / 2,
        top + 18,
        SgsTheme.GOLD);
  }

  // ---------------------------------------------------------------- helpers

  private void computeSeatRects(MatchSnapshot snap) {
    seatRects.clear();
    int players = snap.seats().size();
    int viewer = snap.viewerSeat();
    boolean spectating = viewer < 0;
    for (SeatView seat : snap.seats()) {
      int relative = spectating ? seat.seat() : Math.floorMod(seat.seat() - viewer, players);
      int[] anchor = seatAnchor(relative, players, width, height, spectating);
      int w = relative == 0 && !spectating ? PANEL_W + 60 : PANEL_W;
      int h = relative == 0 && !spectating ? PANEL_H + 30 : PANEL_H;
      seatRects.put(
          seat.seat(),
          new int[] {anchor[0] - w / 2, anchor[1] - h / 2, anchor[0] + w / 2, anchor[1] + h / 2});
    }
  }

  /**
   * Ten-Anniversary style seat placement: the viewer's own seat (relative == 0) docks at the bottom
   * center with a slightly larger card. Spectators show every seat in a full ring.
   */
  private static int[] seatAnchor(
      int relativeSeat, int players, int screenWidth, int screenHeight, boolean spectating) {
    double cx = screenWidth / 2.0;
    double cy;
    if (spectating) {
      cy = screenHeight * 0.42;
    } else if (relativeSeat == 0) {
      cy = screenHeight * 0.70;
    } else {
      cy = screenHeight * 0.40;
    }
    double rx = Math.min(380, screenWidth / 2.0 - 80);
    double ry = spectating ? screenHeight * 0.28 : screenHeight * 0.22;
    // For viewers (not spectating) the first row wraps 1..(players-1) clockwise above.
    int slotCount = spectating ? players : players - 1;
    if (relativeSeat == 0 && !spectating) {
      return new int[] {(int) cx, (int) cy};
    }
    int slot = spectating ? relativeSeat : relativeSeat - 1;
    // Offset so the first guest sits top-left and the lord's seat is bottom (already above).
    double startAngle = spectating ? 90.0 : 160.0; // degrees from top for ring
    double angle = Math.toRadians(startAngle + slot * (320.0 / Math.max(1, slotCount)));
    double x = cx + rx * Math.cos(angle);
    double y = cy - ry * Math.sin(angle);
    return new int[] {(int) Math.round(x), (int) Math.round(y)};
  }

  // ---------------------------------------------------------------- identity guess overlay

  /**
   * Small 5-button overlay (4 identities + clear) that pops up next to a seat card when the player
   * clicks an unknown-identity seat. Pure client side — no packets are sent.
   */
  private void renderGuessPanel(GuiGraphics graphics, int mouseX, int mouseY) {
    if (openGuessSeat == null) {
      return;
    }
    int[] rect = seatRects.get(openGuessSeat);
    if (rect == null) {
      openGuessSeat = null;
      return;
    }
    MatchSnapshot snap = ClientGameState.snapshot();
    if (snap == null) {
      openGuessSeat = null;
      return;
    }
    SeatView seatView = seatByNumber(snap, openGuessSeat);
    if (seatView == null || seatView.identityOrdinal() >= 0) {
      openGuessSeat = null;
      return;
    }

    int panelW = 110;
    int panelH = 130;
    // Anchor just above the target seat card, or below if there is not enough space.
    int[] anchor = seatRects.get(openGuessSeat);
    int x = anchor[0];
    int y = anchor[1] - panelH - 4;
    if (y < 20) {
      y = anchor[3] + 4;
    }
    x = Math.max(5, Math.min(width - panelW - 5, x));

    // Panel background.
    graphics.fill(x, y, x + panelW, y + panelH, 0xCC1C140F);
    graphics.fill(x, y, x + panelW, y + 1, SgsTheme.GOLD_DIM);
    graphics.fill(x, y + panelH - 1, x + panelW, y + panelH, SgsTheme.GOLD_DIM);
    graphics.fill(x, y, x + 1, y + panelH, SgsTheme.GOLD_DIM);
    graphics.fill(x + panelW - 1, y, x + panelW, y + panelH, SgsTheme.GOLD_DIM);

    graphics.drawString(this.font, "推测 " + seatView.name(), x + 5, y + 4, SgsTheme.GOLD);

    // 4 identity buttons.
    int bx = x + 6;
    int by = y + 16;
    int bw = panelW - 12;
    int bh = 22;
    int[] options = {
      dev.sgs.core.Identity.LORD.ordinal(),
      dev.sgs.core.Identity.LOYALIST.ordinal(),
      dev.sgs.core.Identity.REBEL.ordinal(),
      dev.sgs.core.Identity.RENEGADE.ordinal(),
    };
    for (int i = 0; i < options.length; i++) {
      int yy = by + i * (bh + 2);
      int ord = options[i];
      boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= yy && mouseY <= yy + bh;
      int bg = hovered ? 0xFF42341F : 0xFF261C14;
      int fg = identityColor(ord);
      graphics.fill(bx, yy, bx + bw, yy + bh, bg);
      graphics.renderOutline(bx, yy, bw, bh, SgsTheme.GOLD_DIM);
      dev.sgs.core.Identity ident = dev.sgs.core.Identity.values()[ord];
      graphics.drawString(
          this.font,
          Component.translatable("sgs.identity." + ident.id()).getString(),
          bx + 6,
          yy + 6,
          fg);
    }
    // Clear button.
    int clearY = by + options.length * (bh + 2);
    boolean clearHover =
        mouseX >= bx && mouseX <= bx + bw && mouseY >= clearY && mouseY <= clearY + bh;
    graphics.fill(bx, clearY, bx + bw, clearY + bh, clearHover ? 0xFF421F1F : 0xFF261414);
    graphics.renderOutline(bx, clearY, bw, bh, 0xFF663333);
    graphics.drawString(this.font, "清除", bx + 6, clearY + 6, SgsTheme.ERROR);
  }

  /** Returns true if the guess panel consumed this click. */
  private boolean tryHandleGuessClick(int mx, int my) {
    if (openGuessSeat == null) {
      return false;
    }
    int[] anchor = seatRects.get(openGuessSeat);
    if (anchor == null) {
      openGuessSeat = null;
      return false;
    }
    int panelW = 110;
    int panelH = 130;
    int x = anchor[0];
    int y = anchor[1] - panelH - 4;
    if (y < 20) {
      y = anchor[3] + 4;
    }
    x = Math.max(5, Math.min(width - panelW - 5, x));
    if (mx < x || mx > x + panelW || my < y || my > y + panelH) {
      return false;
    }
    // Find which button was hit.
    int bx = x + 6;
    int by = y + 16;
    int bw = panelW - 12;
    int bh = 22;
    int[] options = {
      dev.sgs.core.Identity.LORD.ordinal(),
      dev.sgs.core.Identity.LOYALIST.ordinal(),
      dev.sgs.core.Identity.REBEL.ordinal(),
      dev.sgs.core.Identity.RENEGADE.ordinal(),
    };
    for (int i = 0; i < options.length; i++) {
      int yy = by + i * (bh + 2);
      if (mx >= bx && mx <= bx + bw && my >= yy && my <= yy + bh) {
        guessMarks.put(openGuessSeat, options[i]);
        openGuessSeat = null;
        return true;
      }
    }
    // Clear button.
    int clearY = by + options.length * (bh + 2);
    if (mx >= bx && mx <= bx + bw && my >= clearY && my <= clearY + bh) {
      guessMarks.remove(openGuessSeat);
      openGuessSeat = null;
      return true;
    }
    // Click inside panel but on no button → keep it open.
    return true;
  }

  private static boolean isPromptForMe(MatchSnapshot snap, PromptView prompt) {
    if (prompt == null) {
      return false;
    }
    return prompt.subjectSeat() == snap.viewerSeat()
        || prompt.typeOrdinal() == PromptType.WUXIE_COUNTER.ordinal();
  }

  private static int requiredDiscard(SeatView self) {
    return Math.max(self.hand().size() - Math.max(self.hp(), 0), 0);
  }

  private SeatView selfSeat(MatchSnapshot snap) {
    return seatByNumber(snap, snap.viewerSeat());
  }

  private static SeatView seatByNumber(MatchSnapshot snap, int seatNumber) {
    for (SeatView seat : snap.seats()) {
      if (seat.seat() == seatNumber) {
        return seat;
      }
    }
    return null;
  }

  private String seatName(MatchSnapshot snap, int seatNumber) {
    SeatView seat = seatByNumber(snap, seatNumber);
    return seat == null ? "-" : seat.name();
  }

  private static CardType typeOf(CardView card) {
    CardType[] types = CardType.values();
    int ordinal = card.typeOrdinal();
    return ordinal >= 0 && ordinal < types.length ? types[ordinal] : CardType.SHA;
  }

  private static Component cardLabel(CardView card) {
    CardType type = typeOf(card);
    Suit suit = Suit.values()[card.suitOrdinal()];
    Rank rank = Rank.values()[card.rankOrdinal()];
    String suitLetter =
        switch (suit) {
          case SPADE -> "S";
          case HEART -> "H";
          case CLUB -> "C";
          case DIAMOND -> "D";
          case NONE -> "";
        };
    return Component.translatable("sgs.card." + type.id()).append(" " + rank.label() + suitLetter);
  }

  private static Component heroLabel(String heroId) {
    return Component.translatable("sgs.hero." + heroId);
  }

  private static int identityColor(int ordinal) {
    return switch (dev.sgs.core.Identity.values()[ordinal]) {
      case LORD -> SgsTheme.GOLD;
      case LOYALIST -> SgsTheme.OK;
      case REBEL -> SgsTheme.ERROR;
      case RENEGADE -> 0xFFC08AE0;
    };
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  // ---------------------------------------------------------------- UI constants

  private static final int BUTTON_H = 20;
  private static final int PANEL_W = 108;
  private static final int PANEL_H = 54;
}
