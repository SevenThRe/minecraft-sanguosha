package dev.sgs.core.card;

import static dev.sgs.core.card.CardCategory.ARMOR;
import static dev.sgs.core.card.CardCategory.BASIC;
import static dev.sgs.core.card.CardCategory.DELAYED_TRICK;
import static dev.sgs.core.card.CardCategory.HORSE_MINUS;
import static dev.sgs.core.card.CardCategory.HORSE_PLUS;
import static dev.sgs.core.card.CardCategory.TRICK;
import static dev.sgs.core.card.CardCategory.WEAPON;

/**
 * Every card kind in the standard deck. {@code id} doubles as the effect id. Weapon range is the
 * attack range granted when equipped (0 for non-weapons). Jiu is kept for skill/expansion use; the
 * standard deck contains no jiu cards.
 */
public enum CardType {
  SHA("sha", BASIC),
  SHAN("shan", BASIC),
  TAO("tao", BASIC),
  JIU("jiu", BASIC),

  WUZHONG("wuzhong", TRICK),
  GUOHE("guohe", TRICK),
  SHUNSHOU("shunshou", TRICK),
  WUXIE("wuxie", TRICK),
  JUEDOU("juedou", TRICK),
  NANMAN("nanman", TRICK),
  WANJIAN("wanjian", TRICK),
  TAOYUAN("taoyuan", TRICK),
  WUGU("wugu", TRICK),
  JIEDAO("jiedao", TRICK),

  SHANDIAN("shandian", DELAYED_TRICK),
  LEBU("lebu", DELAYED_TRICK),

  ZHUGE_NU("zhuge_nu", WEAPON, 1),
  CIXIONG("cixiong", WEAPON, 2),
  QINGGANG("qinggang", WEAPON, 2),
  HANBING("hanbing", WEAPON, 2),
  QINGLONG("qinglong", WEAPON, 3),
  ZHANGBA("zhangba", WEAPON, 3),
  GUANSHI("guanshi", WEAPON, 3),
  FANGTIAN("fangtian", WEAPON, 4),
  QILIN_GONG("qilin_gong", WEAPON, 5),

  BAGUA("bagua", ARMOR),
  RENWANG("renwang", ARMOR),

  JUEYING("jueying", HORSE_PLUS),
  DILU("dilu", HORSE_PLUS),
  ZHUAHUANG("zhuahuang", HORSE_PLUS),
  CHITU("chitu", HORSE_MINUS),
  DAWAN("dawan", HORSE_MINUS),
  ZIXING("zixing", HORSE_MINUS),

  // Elemental attack variants of 杀. They share the dodge window and the per-turn usage limit with
  // 杀, but carry a damage element. Neither element grants inherent armor negation.
  HUOSHA("huosha", BASIC),
  LEISHA("leisha", BASIC),
  // Append-only: enum ordinals are used in the current development wire format.
  TIESUO("tiesuo", TRICK),
  // Append-only physical table candidates. Declaring a type does not implement its effect.
  GUDING("guding", WEAPON, 2),
  ZHUQUE("zhuque", WEAPON, 4),
  TENGJIA("tengjia", ARMOR),
  BAIYIN("baiyin", ARMOR),
  HUOGONG("huogong", TRICK),
  BINGLIANG("bingliang", DELAYED_TRICK),
  HUALIU("hualiu", HORSE_PLUS);

  private final String id;
  private final CardCategory category;
  private final int weaponRange;

  CardType(String id, CardCategory category) {
    this(id, category, 0);
  }

  CardType(String id, CardCategory category, int weaponRange) {
    this.id = id;
    this.category = category;
    this.weaponRange = weaponRange;
  }

  public String id() {
    return id;
  }

  public CardCategory category() {
    return category;
  }

  /** Attack range granted when this weapon is equipped; 0 for non-weapons. */
  public int weaponRange() {
    return weaponRange;
  }

  public boolean isEquipment() {
    return category.isEquipment();
  }
}
