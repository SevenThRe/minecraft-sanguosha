package dev.sgs.core.card;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Temporary playable migration content, explicitly NOT the final 160-card Junzheng table. */
public final class DevelopmentDeckFactory {
  public static final String ID="dev-119-20261017";
  public static final int TOTAL_CARDS=StandardDeckFactory.TOTAL_CARDS+11;
  private DevelopmentDeckFactory(){}
  public static List<CardInstance> create(LongSupplier ids){
    var cards=new ArrayList<>(StandardDeckFactory.create(ids));
    add(cards,ids,CardType.JIU,Suit.SPADE,Rank.THREE,Rank.NINE);
    add(cards,ids,CardType.JIU,Suit.CLUB,Rank.THREE,Rank.NINE);
    add(cards,ids,CardType.JIU,Suit.DIAMOND,Rank.NINE);
    add(cards,ids,CardType.TIESUO,Suit.SPADE,Rank.JACK,Rank.QUEEN);
    add(cards,ids,CardType.TIESUO,Suit.CLUB,Rank.TEN,Rank.JACK,Rank.QUEEN,Rank.KING);
    return List.copyOf(cards);
  }
  private static void add(List<CardInstance> out,LongSupplier ids,CardType type,Suit suit,Rank... ranks){
    for(var rank:ranks)out.add(new CardInstance(ids.getAsLong(),CardDefinition.basic(type),suit,rank));
  }
}
