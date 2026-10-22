package dev.sgs.core.card;

import static dev.sgs.core.card.CardType.BAGUA;
import static dev.sgs.core.card.CardType.CHITU;
import static dev.sgs.core.card.CardType.CIXIONG;
import static dev.sgs.core.card.CardType.DAWAN;
import static dev.sgs.core.card.CardType.DILU;
import static dev.sgs.core.card.CardType.FANGTIAN;
import static dev.sgs.core.card.CardType.GUANSHI;
import static dev.sgs.core.card.CardType.GUOHE;
import static dev.sgs.core.card.CardType.HANBING;
import static dev.sgs.core.card.CardType.HUOSHA;
import static dev.sgs.core.card.CardType.JIEDAO;
import static dev.sgs.core.card.CardType.JUEDOU;
import static dev.sgs.core.card.CardType.LEISHA;
import static dev.sgs.core.card.CardType.JUEYING;
import static dev.sgs.core.card.CardType.LEBU;
import static dev.sgs.core.card.CardType.NANMAN;
import static dev.sgs.core.card.CardType.QILIN_GONG;
import static dev.sgs.core.card.CardType.QINGGANG;
import static dev.sgs.core.card.CardType.QINGLONG;
import static dev.sgs.core.card.CardType.RENWANG;
import static dev.sgs.core.card.CardType.SHA;
import static dev.sgs.core.card.CardType.SHAN;
import static dev.sgs.core.card.CardType.SHANDIAN;
import static dev.sgs.core.card.CardType.SHUNSHOU;
import static dev.sgs.core.card.CardType.TAO;
import static dev.sgs.core.card.CardType.TAOYUAN;
import static dev.sgs.core.card.CardType.WANJIAN;
import static dev.sgs.core.card.CardType.WUGU;
import static dev.sgs.core.card.CardType.WUXIE;
import static dev.sgs.core.card.CardType.WUZHONG;
import static dev.sgs.core.card.CardType.ZHANGBA;
import static dev.sgs.core.card.CardType.ZHUAHUANG;
import static dev.sgs.core.card.CardType.ZHUGE_NU;
import static dev.sgs.core.card.CardType.ZIXING;
import static dev.sgs.core.card.Rank.ACE;
import static dev.sgs.core.card.Rank.EIGHT;
import static dev.sgs.core.card.Rank.FIVE;
import static dev.sgs.core.card.Rank.FOUR;
import static dev.sgs.core.card.Rank.JACK;
import static dev.sgs.core.card.Rank.KING;
import static dev.sgs.core.card.Rank.NINE;
import static dev.sgs.core.card.Rank.QUEEN;
import static dev.sgs.core.card.Rank.SEVEN;
import static dev.sgs.core.card.Rank.SIX;
import static dev.sgs.core.card.Rank.TEN;
import static dev.sgs.core.card.Rank.THREE;
import static dev.sgs.core.card.Rank.TWO;
import static dev.sgs.core.card.Suit.CLUB;
import static dev.sgs.core.card.Suit.DIAMOND;
import static dev.sgs.core.card.Suit.HEART;
import static dev.sgs.core.card.Suit.SPADE;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Legacy development deck: 53 basic + 36 trick + 19 equipment = 108 physical cards.
 * Includes custom elemental Sha substitutions and is NOT an audited standard+EX+Junzheng deck.
 * Kept temporarily for existing simulations; production content requires a versioned physical-card table.
 */
public final class StandardDeckFactory {

  public static final int TOTAL_CARDS = 108;

  private StandardDeckFactory() {}

  private record Slot(CardType type, Suit suit, Rank rank) {}

  private static final List<Slot> SLOTS = buildSlots();

  private static List<Slot> buildSlots() {
    List<Slot> s = new ArrayList<>(TOTAL_CARDS);
    // Sha, 20 (the other 10 basic attacks are split into 火杀/雷杀 below).
    add(s, SHA, SPADE, SEVEN, EIGHT, NINE, TEN);
    add(s, SHA, CLUB, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK);
    add(s, SHA, HEART, TEN, JACK);
    add(s, SHA, DIAMOND, SIX, SEVEN, EIGHT, KING);
    // Legacy custom 火杀 substitutions, 5; NOT the audited Junzheng distribution.
    add(s, HUOSHA, SPADE, ACE);
    add(s, HUOSHA, HEART, TWO);
    add(s, HUOSHA, CLUB, KING);
    add(s, HUOSHA, DIAMOND, ACE);
    add(s, HUOSHA, SPADE, KING);
    // Legacy custom 雷杀 substitutions, 5; element alone does not negate armor.
    add(s, LEISHA, HEART, ACE);
    add(s, LEISHA, CLUB, SEVEN);
    add(s, LEISHA, DIAMOND, TWO);
    add(s, LEISHA, SPADE, QUEEN);
    add(s, LEISHA, HEART, KING);
    // Shan, 15.
    add(s, SHAN, HEART, TWO, TWO, KING);
    add(s, SHAN, DIAMOND, TWO, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, JACK);
    // Tao, 8.
    add(s, TAO, HEART, THREE, FOUR, SIX, SEVEN, EIGHT, NINE, QUEEN);
    add(s, TAO, DIAMOND, QUEEN);
    // Non-delayed tricks, 31.
    add(s, WUZHONG, HEART, SEVEN, EIGHT, NINE, JACK);
    add(s, GUOHE, SPADE, THREE, FOUR, QUEEN);
    add(s, GUOHE, CLUB, THREE, FOUR);
    add(s, GUOHE, HEART, QUEEN);
    add(s, SHUNSHOU, SPADE, THREE, FOUR, JACK);
    add(s, SHUNSHOU, DIAMOND, THREE, FOUR);
    add(s, WUXIE, SPADE, JACK);
    add(s, WUXIE, CLUB, QUEEN, KING);
    add(s, WUXIE, DIAMOND, ACE);
    add(s, JUEDOU, SPADE, ACE);
    add(s, JUEDOU, CLUB, ACE);
    add(s, JUEDOU, DIAMOND, ACE);
    add(s, NANMAN, SPADE, SEVEN, KING);
    add(s, NANMAN, CLUB, SEVEN);
    add(s, WANJIAN, HEART, ACE);
    add(s, TAOYUAN, HEART, ACE);
    add(s, WUGU, HEART, THREE, FOUR);
    add(s, JIEDAO, CLUB, QUEEN, KING);
    // Delayed tricks, 5.
    add(s, SHANDIAN, SPADE, ACE);
    add(s, SHANDIAN, HEART, QUEEN);
    add(s, LEBU, SPADE, SIX);
    add(s, LEBU, HEART, SIX);
    add(s, LEBU, CLUB, SIX);
    // Weapons, 10.
    add(s, ZHUGE_NU, DIAMOND, ACE);
    add(s, ZHUGE_NU, CLUB, ACE);
    add(s, CIXIONG, SPADE, TWO);
    add(s, QINGGANG, SPADE, SIX);
    add(s, HANBING, SPADE, TWO);
    add(s, QINGLONG, SPADE, FIVE);
    add(s, ZHANGBA, SPADE, QUEEN);
    add(s, GUANSHI, DIAMOND, FIVE);
    add(s, FANGTIAN, DIAMOND, QUEEN);
    add(s, QILIN_GONG, HEART, FIVE);
    // Armor, 3.
    add(s, BAGUA, SPADE, TWO);
    add(s, BAGUA, CLUB, TWO);
    add(s, RENWANG, CLUB, TWO);
    // Horses, 6.
    add(s, JUEYING, SPADE, FIVE);
    add(s, DILU, CLUB, FIVE);
    add(s, ZHUAHUANG, HEART, KING);
    add(s, CHITU, HEART, FIVE);
    add(s, DAWAN, SPADE, KING);
    add(s, ZIXING, DIAMOND, KING);
    return List.copyOf(s);
  }

  private static void add(List<Slot> slots, CardType type, Suit suit, Rank... ranks) {
    for (Rank rank : ranks) {
      slots.add(new Slot(type, suit, rank));
    }
  }

  public static List<CardInstance> create(LongSupplier ids) {
    List<CardInstance> cards = new ArrayList<>(TOTAL_CARDS);
    for (Slot slot : SLOTS) {
      cards.add(
          new CardInstance(
              ids.getAsLong(), CardDefinition.basic(slot.type()), slot.suit(), slot.rank()));
    }
    return cards;
  }
}
