package dev.sgs.network;

import java.util.ArrayList;
import java.util.List;

import dev.sgs.Sgs;
import dev.sgs.core.match.MatchAction;
import dev.sgs.core.match.dto.MatchViews.CardView;
import dev.sgs.core.match.dto.MatchViews.JudgmentView;
import dev.sgs.core.match.dto.MatchViews.MatchSnapshot;
import dev.sgs.core.match.dto.MatchViews.PromptView;
import dev.sgs.core.match.dto.MatchViews.SeatView;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Packet ids and FriendlyByteBuf codecs for live match traffic. */
public final class GamePackets {

  public static final ResourceLocation GAME_ACTION =
      new ResourceLocation(Sgs.MOD_ID, "game_action");
  public static final ResourceLocation GAME_SNAPSHOT =
      new ResourceLocation(Sgs.MOD_ID, "game_snapshot");
  public static final ResourceLocation GAME_ERROR = new ResourceLocation(Sgs.MOD_ID, "game_error");

  private static final int MAX_CARDS = 200;
  private static final int MAX_LOGS = 64;

  private GamePackets() {}

  // ---------------------------------------------------------------- C2S action

  public static FriendlyByteBuf encodeAction(MatchAction action) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeByte(action.kind().ordinal());
    buf.writeInt(action.nonce());
    buf.writeLong(action.promptId());
    buf.writeBoolean(action.accept());
    buf.writeLong(action.cardId());
    buf.writeInt(action.declaredTypeOrdinal());
    buf.writeInt(action.targetSeat());
    buf.writeInt(action.secondarySeat());
    writeNullableUtf(buf, action.skillId());
    List<Long> cardIds = action.cardIds();
    buf.writeVarInt(cardIds.size());
    for (long cardId : cardIds) {
      buf.writeLong(cardId);
    }
    writeNullableUtf(buf, action.heroId());
    if(action.kind()==MatchAction.Kind.PLAY_ATTACK_TARGETS){buf.writeVarInt(action.targetSeats().size());for(int target:action.targetSeats())buf.writeInt(target);}
    return buf;
  }

  public static MatchAction decodeAction(FriendlyByteBuf buf) {
    int kindOrdinal = buf.readByte();
    int nonce = buf.readInt();
    long promptId = buf.readLong();
    boolean accept = buf.readBoolean();
    long cardId = buf.readLong();
    int declaredTypeOrdinal = buf.readInt();
    int targetSeat = buf.readInt();
    int secondarySeat = buf.readInt();
    String skillId = readNullableUtf(buf);
    int cardCount = buf.readVarInt();
    if(cardCount<0 || cardCount>MAX_CARDS)throw new IllegalArgumentException("invalid card count");
    List<Long> cardIds = new ArrayList<>(cardCount);
    for (int i = 0; i < cardCount; i++) {
      cardIds.add(buf.readLong());
    }
    String heroId = readNullableUtf(buf);
    MatchAction.Kind[] kinds = MatchAction.Kind.values();
    if (kindOrdinal < 0 || kindOrdinal >= kinds.length) {
      throw new IllegalArgumentException("unknown action kind " + kindOrdinal);
    }
    var targets=new ArrayList<Integer>();
    if(kinds[kindOrdinal]==MatchAction.Kind.PLAY_ATTACK_TARGETS){int count=buf.readVarInt();if(count<1 || count>3)throw new IllegalArgumentException("invalid attack target count");for(int i=0;i<count;i++)targets.add(buf.readInt());}
    return MatchAction.of(
        kinds[kindOrdinal],
        nonce,
        promptId,
        accept,
        cardId,
        declaredTypeOrdinal,
        targetSeat,
        secondarySeat,
        skillId,
        List.copyOf(cardIds),
        heroId,targets);
  }

  // ---------------------------------------------------------------- S2C snapshot

  public static FriendlyByteBuf encodeSnapshot(MatchSnapshot snapshot) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeInt(snapshot.status());
    buf.writeInt(snapshot.viewerSeat());
    buf.writeInt(snapshot.turnNumber());
    buf.writeInt(snapshot.phaseOrdinal());
    buf.writeInt(snapshot.currentSeat());
    buf.writeInt(snapshot.lordSeatOrdinal());
    buf.writeInt(snapshot.drawPileCount());
    buf.writeInt(snapshot.discardPileCount());
    buf.writeLong(snapshot.phaseDeadlineMillis());
    buf.writeInt(snapshot.outcomeOrdinal());

    List<String> logs = snapshot.logLines();
    buf.writeVarInt(Math.min(logs.size(), MAX_LOGS));
    for (int i = Math.max(0, logs.size() - MAX_LOGS); i < logs.size(); i++) {
      buf.writeUtf(logs.get(i), 512);
    }

    buf.writeVarInt(snapshot.seats().size());
    for (SeatView seat : snapshot.seats()) {
      encodeSeat(buf, seat);
    }

    PromptView prompt = snapshot.prompt();
    buf.writeBoolean(prompt != null);
    if (prompt != null) {
      buf.writeLong(prompt.promptId());
      buf.writeInt(prompt.typeOrdinal());
      buf.writeInt(prompt.askerSeat());
      buf.writeInt(prompt.subjectSeat());
      buf.writeLong(prompt.deadlineMillis());
      buf.writeVarInt(prompt.allowedTypeOrdinals().size());
      for (int ordinal : prompt.allowedTypeOrdinals()) {
        buf.writeInt(ordinal);
      }
      encodeCards(buf, prompt.choices());
    }

    buf.writeVarInt(snapshot.heroCandidates().size());
    for (String heroId : snapshot.heroCandidates()) {
      buf.writeUtf(heroId, 32);
    }
    buf.writeLong(snapshot.selectionDeadlineMillis());

    JudgmentView judgment = snapshot.judgment();
    buf.writeBoolean(judgment != null);
    if (judgment != null) {
      buf.writeInt(judgment.seat());
      encodeCard(buf, judgment.card());
    }
    return buf;
  }

  public static MatchSnapshot decodeSnapshot(FriendlyByteBuf buf) {
    int status = buf.readInt();
    int viewerSeat = buf.readInt();
    int turnNumber = buf.readInt();
    int phaseOrdinal = buf.readInt();
    int currentSeat = buf.readInt();
    int lordSeatOrdinal = buf.readInt();
    int drawPileCount = buf.readInt();
    int discardPileCount = buf.readInt();
    long phaseDeadlineMillis = buf.readLong();
    int outcomeOrdinal = buf.readInt();

    int logCount = Math.min(buf.readVarInt(), MAX_LOGS);
    List<String> logs = new ArrayList<>(logCount);
    for (int i = 0; i < logCount; i++) {
      logs.add(buf.readUtf(512));
    }

    int seatCount = buf.readVarInt();
    List<SeatView> seats = new ArrayList<>(seatCount);
    for (int i = 0; i < seatCount; i++) {
      seats.add(decodeSeat(buf));
    }

    PromptView prompt = null;
    if (buf.readBoolean()) {
      long promptId = buf.readLong();
      int typeOrdinal = buf.readInt();
      int askerSeat = buf.readInt();
      int subjectSeat = buf.readInt();
      long deadlineMillis = buf.readLong();
      int allowedCount = buf.readVarInt();
      List<Integer> allowed = new ArrayList<>(allowedCount);
      for (int i = 0; i < allowedCount; i++) {
        allowed.add(buf.readInt());
      }
      List<CardView> choices = decodeCards(buf);
      prompt =
          new PromptView(
              promptId,
              typeOrdinal,
              askerSeat,
              subjectSeat,
              deadlineMillis,
              List.copyOf(allowed),
              choices);
    }

    int candidateCount = buf.readVarInt();
    List<String> heroCandidates = new ArrayList<>(candidateCount);
    for (int i = 0; i < candidateCount; i++) {
      heroCandidates.add(buf.readUtf(32));
    }
    long selectionDeadlineMillis = buf.readLong();

    JudgmentView judgment = null;
    if (buf.readBoolean()) {
      judgment = new JudgmentView(buf.readInt(), decodeCard(buf));
    }

    return new MatchSnapshot(
        status,
        viewerSeat,
        turnNumber,
        phaseOrdinal,
        currentSeat,
        lordSeatOrdinal,
        drawPileCount,
        discardPileCount,
        phaseDeadlineMillis,
        outcomeOrdinal,
        List.copyOf(logs),
        List.copyOf(seats),
        prompt,
        List.copyOf(heroCandidates),
        selectionDeadlineMillis,
        judgment);
  }

  private static void encodeSeat(FriendlyByteBuf buf, SeatView seat) {
    buf.writeInt(seat.seat());
    buf.writeUtf(seat.name(), 32);
    buf.writeBoolean(seat.human());
    buf.writeBoolean(seat.online());
    buf.writeBoolean(seat.alive());
    writeNullableUtf(buf, seat.heroId());
    buf.writeInt(seat.kingdomOrdinal());
    buf.writeInt(seat.hp());
    buf.writeInt(seat.maxHp());
    buf.writeInt(seat.identityOrdinal());
    buf.writeInt(seat.handCount());
    encodeNullableCard(buf, seat.weapon());
    encodeNullableCard(buf, seat.armor());
    encodeNullableCard(buf, seat.horsePlus());
    encodeNullableCard(buf, seat.horseMinus());
    encodeCards(buf, seat.delay());
    encodeCards(buf, seat.hand());
    buf.writeBoolean(seat.self());
  }

  private static SeatView decodeSeat(FriendlyByteBuf buf) {
    int seat = buf.readInt();
    String name = buf.readUtf(32);
    boolean human = buf.readBoolean();
    boolean online = buf.readBoolean();
    boolean alive = buf.readBoolean();
    String heroId = readNullableUtf(buf);
    int kingdomOrdinal = buf.readInt();
    int hp = buf.readInt();
    int maxHp = buf.readInt();
    int identityOrdinal = buf.readInt();
    int handCount = buf.readInt();
    CardView weapon = decodeNullableCard(buf);
    CardView armor = decodeNullableCard(buf);
    CardView horsePlus = decodeNullableCard(buf);
    CardView horseMinus = decodeNullableCard(buf);
    List<CardView> delay = decodeCards(buf);
    List<CardView> hand = decodeCards(buf);
    boolean self = buf.readBoolean();
    return new SeatView(
        seat,
        name,
        human,
        online,
        alive,
        heroId,
        kingdomOrdinal,
        hp,
        maxHp,
        identityOrdinal,
        handCount,
        weapon,
        armor,
        horsePlus,
        horseMinus,
        delay,
        hand,
        self);
  }

  private static void encodeCards(FriendlyByteBuf buf, List<CardView> cards) {
    buf.writeVarInt(cards.size());
    for (CardView card : cards) {
      encodeCard(buf, card);
    }
  }

  private static List<CardView> decodeCards(FriendlyByteBuf buf) {
    int count = Math.min(buf.readVarInt(), MAX_CARDS);
    List<CardView> cards = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      cards.add(decodeCard(buf));
    }
    return List.copyOf(cards);
  }

  private static void encodeNullableCard(FriendlyByteBuf buf, CardView card) {
    buf.writeBoolean(card != null);
    if (card != null) {
      encodeCard(buf, card);
    }
  }

  private static CardView decodeNullableCard(FriendlyByteBuf buf) {
    return buf.readBoolean() ? decodeCard(buf) : null;
  }

  private static void encodeCard(FriendlyByteBuf buf, CardView card) {
    buf.writeLong(card.instanceId());
    buf.writeInt(card.typeOrdinal());
    buf.writeInt(card.suitOrdinal());
    buf.writeInt(card.rankOrdinal());
  }

  private static CardView decodeCard(FriendlyByteBuf buf) {
    return new CardView(buf.readLong(), buf.readInt(), buf.readInt(), buf.readInt());
  }

  // ---------------------------------------------------------------- S2C error

  public static FriendlyByteBuf encodeError(int nonce, String reason) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeInt(nonce);
    buf.writeUtf(reason, 64);
    return buf;
  }

  public static int decodeErrorNonce(FriendlyByteBuf buf) {
    return buf.readInt();
  }

  public static String decodeErrorReason(FriendlyByteBuf buf) {
    return buf.readUtf(64);
  }

  private static void writeNullableUtf(FriendlyByteBuf buf, String value) {
    buf.writeBoolean(value != null);
    if (value != null) {
      buf.writeUtf(value, 64);
    }
  }

  private static String readNullableUtf(FriendlyByteBuf buf) {
    return buf.readBoolean() ? buf.readUtf(64) : null;
  }
}
