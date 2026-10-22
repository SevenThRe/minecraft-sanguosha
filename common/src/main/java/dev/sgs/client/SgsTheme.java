package dev.sgs.client;

/**
 * Palette shared by every SGS screen. Ten-Anniversary Sanguosha style: dark gold and deep brown
 * with classical fantasy accents. All UI files should pull colors from here — no magic numbers
 * outside the constants below.
 */
public final class SgsTheme {

  /** Neutral backgrounds and panels. */
  public static final int FRAME = 0xFF1C1611; // deep brown-black frame
  public static final int PANEL = 0xFF2C2118; // panel fill (lighter frame)
  public static final int PANEL_ALPHA = 0xDD2C2118; // translucent panel (overlays)
  public static final int CARD_BG = 0xFF42311F; // card face
  public static final int CARD_BACK = 0xFF5A2A1A; // card back warm red-brown
  public static final int SLOT_EMPTY = 0xFF3A2C20; // empty hall seat

  /** Gold / classical accent. */
  public static final int GOLD = 0xFFFFD75F; // primary gold
  public static final int GOLD_DIM = 0xFFA8863A; // muted gold borders
  public static final int GOLD_SOFT = 0xFFD4B06A; // soft gold for secondary text

  /** Text. */
  public static final int TEXT = 0xFFEADFC8; // warm off-white
  public static final int TEXT_DIM = 0xFF9A8F78; // dim for metadata
  public static final int TEXT_BRIGHT = 0xFFFFF4D8; // active text

  /** Status tints. */
  public static final int OK = 0xFF5FE07F;
  public static final int WARN = 0xFFFF8C5F;
  public static final int ERROR = 0xFFE05F5F;
  public static final int DEAD = 0xFF555555;

  /** Kingdom / faction. */
  public static final int WEI = 0xFF5F8FE0;
  public static final int SHU = 0xFFE05F5F;
  public static final int WU = 0xFF5FE08A;
  public static final int QUN = 0xFFB0B0B0;
  public static final int SHEN = 0xFFFFB85F;

  /** Identity colors. */
  public static final int LORD = 0xFFFFD75F;
  public static final int LOYALIST = 0xFF5FE07F;
  public static final int REBEL = 0xFFE05F5F;
  public static final int RENEGADE = 0xFFC08AE0;

  /** Button / control. */
  public static final int BTN_NORMAL = 0xFF3B2B1E;
  public static final int BTN_HOVER = 0xFF523A24;
  public static final int BTN_DISABLED = 0xFF2A1F16;
  public static final int BTN_TEXT = 0xFFFFF4D8;
  public static final int BTN_TEXT_DISABLED = 0xFF6A5F48;

  public static final int PANEL_W = 140;
  public static final int PANEL_H = 74;

  private SgsTheme() {}

  public static int kingdomColor(int ordinal) {
    int[] palette = {QUN, SHU, WEI, WU};
    if (ordinal < 0 || ordinal >= palette.length) {
      return QUN;
    }
    return palette[ordinal];
  }

  public static int identityColor(int ordinal) {
    return switch (ordinal) {
      case 0 -> LORD;
      case 1 -> LOYALIST;
      case 2 -> REBEL;
      case 3 -> RENEGADE;
      default -> TEXT_DIM;
    };
  }
}
