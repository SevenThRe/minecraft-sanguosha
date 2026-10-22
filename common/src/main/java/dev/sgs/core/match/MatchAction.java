package dev.sgs.core.match;

import java.util.List;

/**
 * One client-to-server action envelope. {@code nonce} is a strictly increasing per-player counter
 * used to drop replays; {@code promptId} must match the active prompt for prompt responses.
 */
public final class MatchAction {

  public enum Kind {
    SELECT_HERO,
    PLAY_CARD,
    PLAY_CARD_AS,
    ACTIVE_SKILL,
    END_PLAY,
    CONFIRM_DISCARD,
    PROMPT_RESPONSE,
    LEAVE,
    REVEAL_IDENTITY,
    CONFIRM_IDENTITY,
    PASS_WUXIE_SCOPE,
    REPLACE_OPENING_HAND,
    KEEP_OPENING_HAND,
    PLAY_ATTACK_TARGETS,
    BAN_HERO
  }

  public static final int NO_SEAT = -1;
  public static final long NO_CARD = -1L;

  private final Kind kind;
  private final int nonce;
  private final long promptId;
  private final boolean accept;
  private final long cardId;
  private final int declaredTypeOrdinal;
  private final int targetSeat;
  private final int secondarySeat;
  private final String skillId;
  private final List<Long> cardIds;
  private final String heroId;
  private final List<Integer> targetSeats;

  private MatchAction(
      Kind kind,
      int nonce,
      long promptId,
      boolean accept,
      long cardId,
      int declaredTypeOrdinal,
      int targetSeat,
      int secondarySeat,
      String skillId,
      List<Long> cardIds,
      String heroId) {
    this(kind,nonce,promptId,accept,cardId,declaredTypeOrdinal,targetSeat,secondarySeat,skillId,cardIds,heroId,List.of());
  }
  private MatchAction(Kind kind,int nonce,long promptId,boolean accept,long cardId,int declaredTypeOrdinal,
      int targetSeat,int secondarySeat,String skillId,List<Long> cardIds,String heroId,List<Integer> targetSeats){
    this.kind = kind;
    this.nonce = nonce;
    this.promptId = promptId;
    this.accept = accept;
    this.cardId = cardId;
    this.declaredTypeOrdinal = declaredTypeOrdinal;
    this.targetSeat = targetSeat;
    this.secondarySeat = secondarySeat;
    this.skillId = skillId;
    this.cardIds = cardIds == null ? List.of() : List.copyOf(cardIds);
    this.heroId = heroId;
    this.targetSeats=targetSeats==null?List.of():List.copyOf(targetSeats);
  }

  public static MatchAction revealIdentity(int nonce,int backSlot){
    return new MatchAction(Kind.REVEAL_IDENTITY,nonce,0L,true,NO_CARD,-1,backSlot,NO_SEAT,null,List.of(),null);
  }
  public static MatchAction confirmIdentity(int nonce){
    return new MatchAction(Kind.CONFIRM_IDENTITY,nonce,0L,true,NO_CARD,-1,NO_SEAT,NO_SEAT,null,List.of(),null);
  }

  public static MatchAction selectHero(int nonce, String heroId) {
    return new MatchAction(
        Kind.SELECT_HERO, nonce, 0L, true, NO_CARD, -1, NO_SEAT, NO_SEAT, null, List.of(), heroId);
  }
  public static MatchAction banHero(int nonce,String heroId){
    return new MatchAction(Kind.BAN_HERO,nonce,0L,true,NO_CARD,-1,NO_SEAT,NO_SEAT,null,List.of(),heroId);
  }

  public static MatchAction playCard(int nonce, long cardId, int targetSeat, int secondarySeat) {
    return new MatchAction(
        Kind.PLAY_CARD,
        nonce,
        0L,
        true,
        cardId,
        -1,
        targetSeat,
        secondarySeat,
        null,
        List.of(),
        null);
  }

  public static MatchAction playCardAs(
      int nonce, long cardId, int declaredTypeOrdinal, int targetSeat, int secondarySeat) {
    return new MatchAction(
        Kind.PLAY_CARD_AS,
        nonce,
        0L,
        true,
        cardId,
        declaredTypeOrdinal,
        targetSeat,
        secondarySeat,
        null,
        List.of(),
        null);
  }

  public static MatchAction activeSkill(
      int nonce, String skillId, int targetSeat, List<Long> cardIds) {
    return new MatchAction(
        Kind.ACTIVE_SKILL,
        nonce,
        0L,
        true,
        NO_CARD,
        -1,
        targetSeat,
        NO_SEAT,
        skillId,
        cardIds,
        null);
  }

  /** Explicit target seats; cardIds always remain physical costs, never overloaded as seats. */
  public static MatchAction playAttackTargets(int nonce,long cardId,int declaredTypeOrdinal,List<Integer> targets){
    return new MatchAction(Kind.PLAY_ATTACK_TARGETS,nonce,0,true,cardId,declaredTypeOrdinal,
        NO_SEAT,NO_SEAT,null,List.of(),null,targets);
  }
  public List<Integer> targetSeats(){return targetSeats;}
  public static MatchAction of(Kind kind,int nonce,long promptId,boolean accept,long cardId,int declaredTypeOrdinal,
      int targetSeat,int secondarySeat,String skillId,List<Long> cardIds,String heroId,List<Integer> targets){
    return new MatchAction(kind,nonce,promptId,accept,cardId,declaredTypeOrdinal,targetSeat,secondarySeat,skillId,cardIds,heroId,targets);
  }

  public static MatchAction endPlay(int nonce) {
    return new MatchAction(
        Kind.END_PLAY, nonce, 0L, true, NO_CARD, -1, NO_SEAT, NO_SEAT, null, List.of(), null);
  }

  public static MatchAction confirmDiscard(int nonce, List<Long> cardIds) {
    return new MatchAction(
        Kind.CONFIRM_DISCARD, nonce, 0L, true, NO_CARD, -1, NO_SEAT, NO_SEAT, null, cardIds, null);
  }

  /** Pass every remaining counterspell opportunity in the server-owned current trick use. */
  public static MatchAction replaceOpeningHand(int nonce){return openingHand(Kind.REPLACE_OPENING_HAND,nonce);}
  public static MatchAction keepOpeningHand(int nonce){return openingHand(Kind.KEEP_OPENING_HAND,nonce);}
  private static MatchAction openingHand(Kind kind,int nonce){return new MatchAction(kind,nonce,0,false,NO_CARD,-1,NO_SEAT,NO_SEAT,null,List.of(),null);}

  public static MatchAction passWuxieScope(int nonce,long promptId){
    return new MatchAction(Kind.PASS_WUXIE_SCOPE,nonce,promptId,false,NO_CARD,-1,NO_SEAT,NO_SEAT,null,List.of(),null);
  }

  public static MatchAction promptResponse(
      int nonce, long promptId, boolean accept, long cardId, int declaredTypeOrdinal) {
    return new MatchAction(
        Kind.PROMPT_RESPONSE,
        nonce,
        promptId,
        accept,
        cardId,
        declaredTypeOrdinal,
        NO_SEAT,
        NO_SEAT,
        null,
        List.of(),
        null);
  }

  /** Choice data only; the server validates the connection, request, physical card and recipient. */
  public static MatchAction skillChoice(int nonce,long promptId,long cardId,int targetSeat) {
    return new MatchAction(Kind.PROMPT_RESPONSE,nonce,promptId,true,cardId,-1,targetSeat,
        NO_SEAT,null,List.of(),null);
  }

  public static MatchAction leave(int nonce) {
    return new MatchAction(
        Kind.LEAVE, nonce, 0L, true, NO_CARD, -1, NO_SEAT, NO_SEAT, null, List.of(), null);
  }

  /** Two physical hand-card costs supplied through the provider's Zhangba spear. */
  public static MatchAction promptCardsResponse(int nonce,long promptId,List<Long> cardIds) {
    return new MatchAction(Kind.PROMPT_RESPONSE,nonce,promptId,true,NO_CARD,
        dev.sgs.core.card.CardType.SHA.ordinal(),NO_SEAT,NO_SEAT,null,cardIds,null);
  }

  /** Reconstructs an action from its wire fields (network codec only). */
  public static MatchAction of(
      Kind kind,
      int nonce,
      long promptId,
      boolean accept,
      long cardId,
      int declaredTypeOrdinal,
      int targetSeat,
      int secondarySeat,
      String skillId,
      List<Long> cardIds,
      String heroId) {
    return new MatchAction(
        kind,
        nonce,
        promptId,
        accept,
        cardId,
        declaredTypeOrdinal,
        targetSeat,
        secondarySeat,
        skillId,
        cardIds,
        heroId);
  }

  public Kind kind() {
    return kind;
  }

  public int nonce() {
    return nonce;
  }

  public long promptId() {
    return promptId;
  }

  public boolean accept() {
    return accept;
  }

  public long cardId() {
    return cardId;
  }

  public int declaredTypeOrdinal() {
    return declaredTypeOrdinal;
  }

  public int targetSeat() {
    return targetSeat;
  }

  public int secondarySeat() {
    return secondarySeat;
  }

  public String skillId() {
    return skillId;
  }

  public List<Long> cardIds() {
    return cardIds;
  }

  public String heroId() {
    return heroId;
  }
}
