package dev.sgs.core.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import dev.sgs.core.Identity;
import dev.sgs.core.card.CardInstance;
import dev.sgs.core.hero.HeroDefinition;
import dev.sgs.core.player.DelayZone;
import dev.sgs.core.player.Equipment;
import dev.sgs.core.player.SgsPlayer;

/**
 * Seat of a live network match. The seat number is mutable on purpose: participants are shuffled
 * before identities are dealt and the table is rotated so the lord anchors seat one, therefore the
 * fixed lobby order never equals the in-game numbering.
 */
public final class MatchPlayer implements SgsPlayer {

  private final UUID id;
  private final String name;
  private final boolean human;
  private final List<CardInstance> hand = new ArrayList<>();
  private final Equipment equipment = new Equipment();
  private final DelayZone delayZone = new DelayZone();

  private int seat;
  private Identity identity;
  private HeroDefinition hero;
  private int hp;
  private int maxHp;
  private boolean alive = true;
  private boolean identityPublic;
  private boolean online = true;

  public MatchPlayer(UUID id, String name, int initialSeat, boolean human) {
    this.id = id;
    this.name = name;
    this.seat = initialSeat;
    this.human = human;
  }

  public boolean human() {
    return human;
  }

  public void setSeat(int seat) {
    this.seat = seat;
  }

  public void setOnline(boolean online) {
    this.online = online;
  }

  @Override
  public UUID id() {
    return id;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public int seat() {
    return seat;
  }

  @Override
  public Identity identity() {
    return identity;
  }

  @Override
  public void assignIdentity(Identity identity) {
    this.identity = identity;
  }

  @Override
  public HeroDefinition hero() {
    return hero;
  }

  @Override
  public void assignHero(HeroDefinition hero) {
    this.hero = hero;
  }

  @Override
  public int maxHp() {
    return maxHp;
  }

  @Override
  public void setMaxHp(int maxHp) {
    this.maxHp = maxHp;
  }

  @Override
  public int hp() {
    return hp;
  }

  @Override
  public void setHp(int hp) {
    this.hp = hp;
  }

  @Override
  public boolean alive() {
    return alive;
  }

  @Override
  public void setAlive(boolean alive) {
    this.alive = alive;
  }

  @Override
  public boolean identityPublic() {
    return identityPublic;
  }

  @Override
  public void setIdentityPublic(boolean identityPublic) {
    this.identityPublic = identityPublic;
  }

  @Override
  public boolean online() {
    return online;
  }

  @Override
  public List<CardInstance> handView() {
    return Collections.unmodifiableList(hand);
  }

  @Override
  public int handCount() {
    return hand.size();
  }

  @Override
  public void addCard(CardInstance card) {
    hand.add(card);
  }

  @Override
  public CardInstance removeCard(long instanceId) {
    for (int i = 0; i < hand.size(); i++) {
      if (hand.get(i).instanceId() == instanceId) {
        return hand.remove(i);
      }
    }
    return null;
  }

  @Override
  public List<CardInstance> clearHand() {
    List<CardInstance> all = new ArrayList<>(hand);
    hand.clear();
    return all;
  }

  @Override
  public Equipment equipment() {
    return equipment;
  }

  @Override
  public DelayZone delayZone() {
    return delayZone;
  }
}
