package dev.sgs.core.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dev.sgs.core.Identity;
import dev.sgs.core.player.SgsPlayer;

/**
 * Identity multiset for 2-8 players per FR-3.3. Games with 2-3 players use the simplified layout
 * (one lord, everyone else a rebel, no loyalist/renegade).
 */
public final class IdentityTable {

  private static final Map<Integer, List<Identity>> TABLES = new HashMap<>();

  static {
    put(2, Identity.LORD, Identity.REBEL);
    put(3, Identity.LORD, Identity.REBEL, Identity.REBEL);
    put(4, Identity.LORD, Identity.LOYALIST, Identity.REBEL, Identity.RENEGADE);
    put(5, Identity.LORD, Identity.LOYALIST, Identity.REBEL, Identity.REBEL, Identity.RENEGADE);
    put(
        6,
        Identity.LORD,
        Identity.LOYALIST,
        Identity.REBEL,
        Identity.REBEL,
        Identity.REBEL,
        Identity.RENEGADE);
    put(
        7,
        Identity.LORD,
        Identity.LOYALIST,
        Identity.LOYALIST,
        Identity.REBEL,
        Identity.REBEL,
        Identity.REBEL,
        Identity.RENEGADE);
    put(
        8,
        Identity.LORD,
        Identity.LOYALIST,
        Identity.LOYALIST,
        Identity.REBEL,
        Identity.REBEL,
        Identity.REBEL,
        Identity.REBEL,
        Identity.RENEGADE);
  }

  private IdentityTable() {}

  private static void put(int count, Identity... identities) {
    List<Identity> list = new ArrayList<>(count);
    Collections.addAll(list, identities);
    TABLES.put(count, List.copyOf(list));
  }

  public static List<Identity> forPlayerCount(int playerCount) {
    List<Identity> table = TABLES.get(playerCount);
    if (table == null) {
      throw new IllegalArgumentException("unsupported player count: " + playerCount);
    }
    return table;
  }

  /** Shuffles the table and assigns one identity per player. */
  public static void assign(List<SgsPlayer> players, Random random) {
    assign(players, random, false);
  }

  /** Double-renegade rooms replace one rebel; each renegade still wins only as sole survivor. */
  public static void assign(List<SgsPlayer> players, Random random, boolean doubleRenegade) {
    List<Identity> identities = new ArrayList<>(forPlayerCount(players.size()));
    if (doubleRenegade) {
      if (players.size() < 4) throw new IllegalArgumentException("double renegade requires four players");
      identities.set(identities.indexOf(Identity.REBEL), Identity.RENEGADE);
    }
    Collections.shuffle(identities, random);
    for (int i = 0; i < players.size(); i++) {
      players.get(i).assignIdentity(identities.get(i));
    }
  }
}
