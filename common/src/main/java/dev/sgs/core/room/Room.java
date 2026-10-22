package dev.sgs.core.room;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * One waiting room. Hall seats are sparse slots 0..capacity-1: players may sit at any empty slot
 * and swap freely. Explicit AI seats belong to the room configuration and are never real players.
 * Match start must account for every seat; empty seats are not silently filled.
 */
public final class Room {

  /** What occupies one hall slot in the client snapshot. */
  public static final int SLOT_EMPTY = 0;

  public static final int SLOT_HUMAN = 1;
  public static final int SLOT_AI = 2;

  public enum State {
    WAITING,
    PLAYING
  }

  private final long id;
  private String name;
  private final UUID owner;
  private final RoomMode mode;
  private final TreeMap<Integer, UUID> seats = new TreeMap<>();
  private final Set<Integer> aiSlots = new TreeSet<>();
  private final Set<UUID> readyMembers = new java.util.HashSet<>();
  private State state = State.WAITING;
  private boolean teammateSharing;
  private RoomSettings settings;
  public RoomSettings settings() { return settings; }
  void setSettings(RoomSettings settings) {
    this.settings = java.util.Objects.requireNonNull(settings);
    readyMembers.clear();
  }
  void clearAiSlots() { aiSlots.clear(); }
  public boolean teammateSharing(){return teammateSharing;}
  void setTeammateSharing(boolean enabled){teammateSharing=enabled;}

  Room(long id, String name, UUID owner, RoomMode mode) {
    this(id, name, owner, mode, RoomSettings.defaults());
  }

  Room(long id, String name, UUID owner, RoomMode mode, RoomSettings settings) {
    this.id = id;
    this.name = name;
    this.owner = owner;
    this.mode = mode;
    this.settings = java.util.Objects.requireNonNull(settings);
    this.seats.put(0, owner);
  }

  /** Fresh bootstrap only: no joining, notifications, random seating or start event is replayed. */
  public static Room restoreCheckpoint(dev.sgs.core.persistence.RoomCheckpoint saved){
    java.util.Objects.requireNonNull(saved).validate();
    var room=new Room(saved.id(),saved.name(),saved.owner(),saved.mode(),saved.settings());
    room.seats.clear();room.seats.putAll(saved.seats());room.aiSlots.addAll(saved.aiSlots());
    room.readyMembers.addAll(saved.ready());room.teammateSharing=saved.teammateSharing();room.state=saved.state();return room;
  }

  public long id() {
    return id;
  }

  public String name() {
    return name;
  }
  void setName(String name) { this.name = name; }

  public UUID owner() {
    return owner;
  }

  public RoomMode mode() {
    return mode;
  }

  public State state() {
    return state;
  }

  public void markPlaying() {
    state = State.PLAYING;
  }
  void returnToWaiting() {state=State.WAITING;readyMembers.clear();}

  /** Swaps two members' lobby seat positions while waiting for the match to start. */
  boolean swapSeats(UUID a, UUID b) {
    if (state != State.WAITING) {
      return false;
    }
    Integer slotA = seatOf(a);
    Integer slotB = seatOf(b);
    if (slotA == null || slotB == null) {
      return false;
    }
    seats.put(slotA, b);
    seats.put(slotB, a);
    return true;
  }

  /** Moves the caller to an empty (non-AI-reserved) hall slot. */
  boolean moveToSeat(UUID player, int slot) {
    if (state != State.WAITING || slot < 0 || slot >= capacity()) {
      return false;
    }
    Integer from = seatOf(player);
    if (from == null || seats.containsKey(slot) || aiSlots.contains(slot)) {
      return false;
    }
    seats.remove(from);
    seats.put(slot, player);
    return true;
  }

  /** Reserves or releases an empty slot for a bot. Owner-only, enforced by the manager. */
  boolean setAiSlot(int slot, boolean reserved) {
    if (state != State.WAITING || slot < 0 || slot >= capacity()) {
      return false;
    }
    if (reserved) {
      if (seats.containsKey(slot)) {
        return false;
      }
      aiSlots.add(slot);
    } else {
      aiSlots.remove(slot);
    }
    return true;
  }

  /** Reserves every still-empty slot for a bot; returns the number of new reservations. */
  int fillAiSlots() {
    if (state != State.WAITING) {
      return 0;
    }
    int added = 0;
    for (int slot = 0; slot < capacity(); slot++) {
      if (!seats.containsKey(slot) && aiSlots.add(slot)) {
        added++;
      }
    }
    return added;
  }

  /** Members ordered by hall slot. */
  public List<UUID> members() {
    return List.copyOf(seats.values());
  }

  public int memberCount() {
    return seats.size();
  }

  public boolean isReady(UUID player) { return readyMembers.contains(player); }

  public int readyCount() { return readyMembers.size(); }

  boolean setReady(UUID player, boolean ready) {
    if (state != State.WAITING || !isMember(player)) return false;
    if (ready) readyMembers.add(player); else readyMembers.remove(player);
    return true;
  }

  /** Product start gate: reserved AI seats never count as actual connected members. */
  public boolean allHumanMembersReady() {
    return seats.size() == capacity() && aiSlots.isEmpty() && readyMembers.containsAll(seats.values());
  }

  public int capacity() {
    return mode.playerCount();
  }

  public boolean isFull() {
    return seats.size() + aiSlots.size() >= capacity();
  }

  public boolean isMember(UUID player) {
    return seats.containsValue(player);
  }

  public Integer seatOf(UUID player) {
    for (Map.Entry<Integer, UUID> entry : seats.entrySet()) {
      if (entry.getValue().equals(player)) {
        return entry.getKey();
      }
    }
    return null;
  }

  public UUID occupant(int slot) {
    return seats.get(slot);
  }

  public Set<Integer> aiSlots() {
    return Collections.unmodifiableSet(aiSlots);
  }

  boolean addMember(UUID player) {
    if (isFull() || seats.containsValue(player)) {
      return false;
    }
    for (int slot = 0; slot < capacity(); slot++) {
      if (!seats.containsKey(slot) && !aiSlots.contains(slot)) {
        seats.put(slot, player);
        return true;
      }
    }
    return false;
  }

  boolean removeMember(UUID player) {
    Integer slot = seatOf(player);
    if (slot == null) {
      return false;
    }
    seats.remove(slot);
    readyMembers.remove(player);
    return true;
  }
}
