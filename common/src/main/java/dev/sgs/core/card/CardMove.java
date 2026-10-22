package dev.sgs.core.card;

import java.util.List;

/** Server-core audit event; private movements must NOT be serialized wholesale to clients. */
public record CardMove(long sequence,CardZone from,CardZone to,List<CardInstance> cards,Reason reason,int actorSeat,boolean publicCards) {
  public enum Reason {
    DRAW, USE, RESPONSE, FINISH, EQUIP, EQUIPMENT_REPLACED, DELAYED_USE, DELAY_PASS,
    JUDGMENT_REVEAL, JUDGMENT_REPLACE, REVEAL, EFFECT_GAIN, SKILL_GIVE, DISCARD, DEATH, RECAST, SHUFFLE
  }
  public CardMove { cards=List.copyOf(cards); }
}
