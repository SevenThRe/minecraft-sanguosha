package dev.sgs.core.sim;

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
 * In-memory seat used by the headless bot match. It mirrors the test FakePlayer but lives in the
 * main sourceset so the in-game developer command can run full matches without real clients.
 */
public final class SimPlayer implements SgsPlayer {

  private final UUID id = UUID.randomUUID();
  private final int seat;
  private final String name;
  private final List<CardInstance> hand = new ArrayList<>();
  private final Equipment equipment = new Equipment();
  private final DelayZone delayZone = new DelayZone();

  private Identity identity;
  private HeroDefinition hero;
  private int hp;
  private int maxHp;
  private boolean alive = true;
  private boolean identityPublic;

  SimPlayer(int seat) {
    this.seat = seat;
    this.name = "bot" + seat;
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
    return true;
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
