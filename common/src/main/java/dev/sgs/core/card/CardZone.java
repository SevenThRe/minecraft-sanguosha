package dev.sgs.core.card;

/** A location in one match, never a Minecraft object or an inventory slot. */
public record CardZone(Kind kind,int seat) {
  public enum Kind { DRAW, DISCARD, PROCESSING, HAND, EQUIPMENT, JUDGMENT, SPECIAL }
  public static final CardZone DRAW=new CardZone(Kind.DRAW,-1);
  public static final CardZone DISCARD=new CardZone(Kind.DISCARD,-1);
  public static final CardZone PROCESSING=new CardZone(Kind.PROCESSING,-1);
  public CardZone {
    java.util.Objects.requireNonNull(kind);
    boolean personal=kind==Kind.HAND || kind==Kind.EQUIPMENT || kind==Kind.JUDGMENT || kind==Kind.SPECIAL;
    if(personal?seat<0:seat!=-1)throw new IllegalArgumentException("invalid zone seat");
  }
  public static CardZone hand(int seat){return new CardZone(Kind.HAND,seat);}
  public static CardZone equipment(int seat){return new CardZone(Kind.EQUIPMENT,seat);}
  public static CardZone judgment(int seat){return new CardZone(Kind.JUDGMENT,seat);}
  public static CardZone special(int seat){return new CardZone(Kind.SPECIAL,seat);}
}
