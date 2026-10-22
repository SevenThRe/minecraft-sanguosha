package dev.sgs.core.player;

import java.util.List;
import java.util.UUID;

import dev.sgs.core.Identity;
import dev.sgs.core.card.CardInstance;
import dev.sgs.core.hero.HeroDefinition;

/** Seat participant. The integration layer implements this over Minecraft players. */
public interface SgsPlayer {

  UUID id();

  String name();

  int seat();

  Identity identity();

  void assignIdentity(Identity identity);

  HeroDefinition hero();

  void assignHero(HeroDefinition hero);

  int maxHp();

  void setMaxHp(int maxHp);

  int hp();

  void setHp(int hp);

  boolean alive();

  void setAlive(boolean alive);

  /** Whether other seats may see this player's identity (lord or dead). */
  boolean identityPublic();

  void setIdentityPublic(boolean identityPublic);

  boolean online();

  /** Unmodifiable view of the hand. */
  List<CardInstance> handView();

  int handCount();

  void addCard(CardInstance card);

  default void addCards(List<CardInstance> cards) {
    cards.forEach(this::addCard);
  }

  /** Removes and returns the card with the given instance id, or null if absent. */
  CardInstance removeCard(long instanceId);

  /** Removes every card from the hand and returns it. */
  List<CardInstance> clearHand();

  /** The four equipment slots of this seat. */
  Equipment equipment();

  /** Delayed trick cards waiting for judgment on this seat. */
  DelayZone delayZone();
}
