package dev.sgs.core.engine;

/** Kinds of questions the engine asks players during settlement. */
public enum PromptType {
  /** Target of a Sha may answer with a Shan. Also used for Volley of Arrows. */
  DODGE_SHA,
  /** A dying character may be saved by a Tao (or self-used Jiu). */
  RESCUE_TAO,
  /** Ring-wide offer to counter a trick with Negation. */
  WUXIE_COUNTER,
  /** The duel participant must answer with a Sha or lose the duel. */
  DUEL_SHA,
  /** Barbarian Invasion: answer with a Sha or take damage. */
  RESPOND_SHA_NANMAN,
  /** Harvest: pick one of the revealed cards (choices carry the pool). */
  WUGU_PICK,
  /** Borrow Sword: the weapon holder answers with a Sha or surrenders the weapon. */
  JIEDAO_RESPONSE,
  /** Optional skill invocation: accept/decline, never a fabricated card response. */
  SKILL_INVOKE,
  /** Another Wei character supplies a Shan to the lord through Hujia. */
  HUJIA_SHAN,
  /** Another Shu character provides a Sha (possibly via a paid view-as cost). */
  JIJIANG_SHA,
  HUOGONG_REVEAL,
  HUOGONG_PAY,
  FANJIAN_SUIT,
  GUI_CAI_REPLACE,
  FANKUI_TAKE(true),
  YIJI_GIVE,
  TUXI_TARGET,
  GANGLIE_DISCARD,
  LIJIAN_TARGET,
  GUANXING_ORDER,
  LIULI_TARGET,
  /** Operator-private selection of equipment/judgment or an opaque hand-position token. */
  CARD_ZONE_SELECT(true),
  /** Target privately discards one hand card or lets the Cixiong attacker draw. */
  WEAPON_DISCARD,
  WEAPON_COST_FIRST,
  WEAPON_COST_SECOND,
  /** Optional use of a new Sha against the just-dodging target. */
  QINGLONG_SHA,
  /** Private card/character choice for the original expansion skills. */
  EXPANSION_CHOICE,
  /** Enumerated public options; targets index options, not character seats. */
  EXPANSION_OPTION;

  private final boolean handPositions;
  PromptType(){this(false);}
  PromptType(boolean handPositions){this.handPositions=handPositions;}
  /** Declared once; clients and every server ingress use the same opaque-hand capability. */
  public boolean supportsHandPositions(){return handPositions;}
  /** Explicit parent input channel, shared by the engine and session transport adapter. */
  public boolean isContinuationChoice(){return switch(this){
    case LIJIAN_TARGET,LIULI_TARGET,YIJI_GIVE,FANKUI_TAKE,GUI_CAI_REPLACE,GANGLIE_DISCARD,CARD_ZONE_SELECT,
        WEAPON_DISCARD,WEAPON_COST_FIRST,WEAPON_COST_SECOND->true;
    default->false;
  };}
}
