package dev.sgs.core.engine.skill;

import dev.sgs.core.card.CardInstance;
import dev.sgs.core.card.CardType;
import dev.sgs.core.card.GameCard;
import dev.sgs.core.card.VirtualCard;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.DamageNature;
import dev.sgs.core.game.GameState;
import dev.sgs.core.player.SgsPlayer;

/**
 * Mutable snapshot of "to whom, with what" for one trigger. {@code timing} answers when; the
 * remaining fields answer who/what. The bus fills the fields relevant to each timing; listeners
 * must defensively ignore fields they do not need.
 */
public final class SkillContext {

  private final GameState state;
  private final Timing timing;
  private final SgsPlayer turnPlayer;

  private SgsPlayer owner;
  private SgsPlayer actor;
  private SgsPlayer source;
  private SgsPlayer target;
  private GameCard card;
  private CardType declaredAs;
  private PromptType promptType;
  private CardInstance judgeCard;
  private int damage;
  private int damageDistance=-1;
  public int damageDistance(){return damageDistance;}
  public SkillContext withDamageDistance(int value){damageDistance=value;return this;}
  private int hpLossAmount;
  private dev.sgs.core.card.CardMove movement;
  private DamageNature nature = DamageNature.NORMAL;
  private boolean chainDamage;
  private boolean transferredDamage;
  public boolean transferredDamage(){return transferredDamage;}
  public SkillContext withTransferredDamage(boolean value){transferredDamage=value;return this;}
  private boolean transformConfirmed;
  private boolean publicActivationConfirmed;
  private boolean phaseSkipped;
  public void skipPhase(){if(timing!=Timing.PHASE_DISCARD_BEFORE && timing!=Timing.PHASE_JUDGMENT_BEFORE && timing!=Timing.PHASE_DRAW_BEFORE && timing!=Timing.PHASE_PLAY_BEFORE)throw new IllegalStateException("not a skippable phase window");phaseSkipped=true;}
  public boolean phaseSkipped(){return phaseSkipped;}
  public static SkillContext cardTarget(GameState state,SgsPlayer source,SgsPlayer target,GameCard card){
    var c=forPlayer(state,RuleId.CARD_TARGET_ALLOWED,source);c.source=source;c.target=target;c.card=card;return c;
  }
  public static SkillContext attackTarget(GameState state,SgsPlayer source,SgsPlayer target,GameCard card){
    var c=new SkillContext(state,Timing.SHA_TARGET_BEFORE);c.source=source;c.target=target;c.card=card;return c;
  }
  public void redirectAttackTarget(SgsPlayer replacement){
    if(timing!=Timing.SHA_TARGET_BEFORE || replacement==null || !state.players().contains(replacement)
        || !replacement.alive() || replacement==source || replacement==target)throw new IllegalArgumentException("invalid attack redirect");
    target=replacement;
  }
  public static SkillContext rescueRecovery(GameState state,SgsPlayer healer,SgsPlayer victim,GameCard card){
    var ctx=forPlayer(state,RuleId.RESCUE_RECOVERY,victim);ctx.source=healer;ctx.target=victim;ctx.card=card;return ctx;
  }
  /** Optional listeners call this only after their actual effect/cost succeeded. */
  public void confirmPublicActivation(){publicActivationConfirmed=true;}
  boolean consumePublicActivation(){boolean confirmed=publicActivationConfirmed;publicActivationConfirmed=false;return confirmed;}

  private SkillContext(GameState state, Timing timing) {
    this.state = state;
    this.timing = timing;
    this.turnPlayer = state.currentPlayer();
  }

  public static SkillContext phase(GameState state, Timing timing, SgsPlayer player) {
    SkillContext ctx = new SkillContext(state, timing);
    ctx.owner = player;
    ctx.actor = player;
    return ctx;
  }

  public static SkillContext forPlayer(GameState state, RuleId rule, SgsPlayer player) {
    SkillContext ctx = new SkillContext(state, null);
    ctx.ruleId = rule;
    ctx.owner = player;
    ctx.actor = player;
    return ctx;
  }

  public static SkillContext transform(
      GameState state, SgsPlayer actor, GameCard card, CardType declaredAs, PromptType promptType) {
    SkillContext ctx = new SkillContext(state, Timing.CARD_TRANSFORM);
    ctx.actor = actor;
    ctx.card = card;
    ctx.declaredAs = declaredAs;
    ctx.promptType = promptType;
    return ctx;
  }

  public static SkillContext judge(
      GameState state, SgsPlayer judgeTarget, CardInstance judgmentCause, CardInstance judgeCard) {
    SkillContext ctx = new SkillContext(state, Timing.JUDGE_CARD_REVEALED);
    ctx.target = judgeTarget;
    ctx.card = judgmentCause;
    ctx.judgeCard = judgeCard;
    return ctx;
  }

  /** Reuses one snapshot across the three judge pipeline timings. */
  public SkillContext at(Timing newTiming) {
    SkillContext copy = new SkillContext(state, newTiming);
    copy.owner = owner;
    copy.actor = actor;
    copy.source = source;
    copy.target = target;
    copy.card = card;
    copy.declaredAs = declaredAs;
    copy.promptType = promptType;
    copy.judgeCard = judgeCard;
    copy.damage = damage;
    copy.damageDistance=damageDistance;
    copy.hpLossAmount = hpLossAmount;
    copy.movement = movement;
    copy.nature = nature;
    copy.chainDamage = chainDamage;
    copy.transferredDamage = transferredDamage;
    return copy;
  }

  /**
   * Creates a damage context. {@code causeCard} is the card that deals the damage (a Sha, Duel,
   * etc.) and may be {@code null} for card-less skill damage; direct HP loss has its own timing and
   * amount. Listeners at damage timings must tolerate {@code card() == null}. Acquisition
   * skills such as Jianxiong read {@code card().sourceCards()} — an empty list (pure virtual
   * damage) means nothing physical can be obtained.
   */
  public static SkillContext damage(
      GameState state,
      Timing timing,
      SgsPlayer source,
      SgsPlayer target,
      int damage,
      GameCard causeCard) {
    return damage(state,timing,source,target,damage,causeCard,DamageNature.of(causeCard),false);
  }

  public static SkillContext damage(GameState state,Timing timing,SgsPlayer source,SgsPlayer target,
      int damage,GameCard causeCard,DamageNature nature,boolean chainDamage) {
    SkillContext ctx = new SkillContext(state, timing);
    ctx.source = source;
    ctx.target = target;
    ctx.damage = damage;
    ctx.card = causeCard;
    ctx.nature = java.util.Objects.requireNonNull(nature);
    ctx.chainDamage = chainDamage;
    return ctx;
  }

  public static SkillContext dying(GameState state, SgsPlayer victim, SgsPlayer source) {
    SkillContext ctx = new SkillContext(state, Timing.DYING);
    ctx.target = victim;
    ctx.source = source;
    return ctx;
  }

  private RuleId ruleId;
  public static SkillContext cardUse(GameState state,SgsPlayer actor,GameCard card){
    var ctx=new SkillContext(state,Timing.CARD_USE_COMMITTED);ctx.actor=actor;ctx.card=card;return ctx;
  }

  public GameState state() {
    return state;
  }

  public Timing timing() {
    return timing;
  }

  public RuleId ruleId() {
    return ruleId;
  }

  public SgsPlayer turnPlayer() {
    return turnPlayer;
  }

  public SgsPlayer owner() {
    return owner;
  }

  public SkillContext withOwner(SgsPlayer player) {
    this.owner = player;
    return this;
  }

  public SgsPlayer actor() {
    return actor;
  }

  public SgsPlayer source() {
    return source;
  }

  public SgsPlayer target() {
    return target;
  }

  /**
   * The card being settled: physical when untouched, virtual after a confirmed view-as. May be null
   * for timings without a card (e.g. card-less damage).
   */
  public GameCard card() {
    return card;
  }

  public CardType declaredAs() {
    return declaredAs;
  }

  /** Null on the active play path; set when the alternation answers a prompt. */
  public PromptType promptType() {
    return promptType;
  }

  public CardInstance judgeCard() {
    return judgeCard;
  }

  /** Judge-modification skills replace the effective judgment card here. */
  public void setJudgeCard(CardInstance judgeCard) {
    if(judgeCard!=this.judgeCard)throw new IllegalStateException("use replaceJudgment to pay the physical replacement cost");
    this.judgeCard = judgeCard;
  }
  public void replaceJudgment(SgsPlayer actor,CardInstance replacement){
    this.judgeCard=state.cards().replaceJudgment(actor,judgeCard,replacement);
  }
  public void exchangeJudgment(SgsPlayer actor,CardInstance replacement){judgeCard=state.cards().exchangeJudgment(actor,judgeCard,replacement);}
  public static SkillContext movement(GameState state,dev.sgs.core.card.CardMove move){
    var ctx=new SkillContext(state,Timing.CARD_MOVE_AFTER);ctx.movement=move;
    if(move.actorSeat()>=0)ctx.actor=state.players().get(move.actorSeat());
    if(move.from().seat()>=0)ctx.source=state.players().get(move.from().seat());
    if(move.to().seat()>=0)ctx.target=state.players().get(move.to().seat());
    return ctx;
  }
  public dev.sgs.core.card.CardMove movement(){return movement;}

  public int damage() {
    return damage;
  }

  public DamageNature nature() { return nature; }
  public void setNature(DamageNature nature) { this.nature=java.util.Objects.requireNonNull(nature); }
  public boolean chainDamage() { return chainDamage; }

  public static SkillContext hpLoss(GameState state,SgsPlayer target,int amount) {
    var ctx=new SkillContext(state,Timing.HP_LOST_AFTER);
    ctx.target=target;ctx.hpLossAmount=amount;return ctx;
  }
  public int hpLossAmount(){return hpLossAmount;}

  public void setDamage(int damage) {
    this.damage = damage;
  }

  public boolean transformConfirmed() {
    return transformConfirmed;
  }

  /**
   * Confirms the pending view-as declaration and atomically swaps the settled card to its virtual
   * counterpart built from the same physical sources. A card may be transformed at most once per
   * settlement: confirming a card that is already virtual throws {@link IllegalStateException}, as
   * does confirming without a pending declaration.
   */
  public void confirmTransform() {
    confirmTransform(false);
  }

  public void confirmTransform(boolean allowRecast){
    if (card == null || declaredAs == null || card.type() == declaredAs) {
      throw new IllegalStateException("no pending transform");
    }
    if (card.isVirtual()) {
      throw new IllegalStateException("chained transformation is forbidden");
    }
    this.card = new VirtualCard(declaredAs,card.sourceCards(),allowRecast);
    this.transformConfirmed = true;
  }
}
