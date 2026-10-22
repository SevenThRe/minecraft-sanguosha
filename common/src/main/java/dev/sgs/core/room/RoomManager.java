package dev.sgs.core.room;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Server-authoritative lobby: rooms, invitations and the matchmaking queue. Pure Java; the
 * Minecraft layer mirrors outcomes into network packets via the listener.
 */
public final class RoomManager {

  /** Server-side hooks so the MC layer can notify affected players. */
  public interface Listener {
    default void onInvite(UUID inviter, UUID target, Room room) {}

    default void onMatchmade(Room room) {}

    default void onRoomDisbanded(Room room) {}
  }

  private final LongSupplier roomIds;
  private long lastAllocatedRoomId;
  private final Map<Long, Room> rooms = new LinkedHashMap<>();
  private final Map<UUID, Long> pendingInvites = new HashMap<>();
  private final Map<RoomMode, Deque<UUID>> queues = new HashMap<>();
  private Listener listener = new Listener() {};

  public RoomManager(LongSupplier roomIds) {
    this.roomIds = roomIds;
    for (RoomMode mode : RoomMode.values()) {
      queues.put(mode, new ArrayDeque<>());
    }
  }

  /** Private world snapshots in stable creation/restoration order. Invitations and queues are ephemeral. */
  public List<dev.sgs.core.persistence.RoomCheckpoint> checkpoints(){
    return rooms.values().stream().map(dev.sgs.core.persistence.RoomCheckpoint::capture).toList();
  }

  public Room restore(dev.sgs.core.persistence.RoomCheckpoint saved){
    restoreAll(List.of(saved));return rooms.get(saved.id());
  }

  /** Validate every room and global membership before publishing any part of a restored batch. */
  public void restoreAll(List<dev.sgs.core.persistence.RoomCheckpoint> snapshots){
    java.util.Objects.requireNonNull(snapshots);
    var ids=new java.util.HashSet<>(rooms.keySet());var players=new java.util.HashSet<UUID>();
    for(var room:rooms.values())players.addAll(room.members());
    var restored=new ArrayList<Room>();long highWater=lastAllocatedRoomId;
    for(var saved:List.copyOf(snapshots)){
      saved.validate();
      if(!ids.add(saved.id()))throw new IllegalArgumentException("duplicate restored room ID");
      for(UUID player:saved.seats().values())
        if(!players.add(player) || queuedMode(player).isPresent())throw new IllegalArgumentException("restored player already belongs to a room or queue");
      restored.add(saved.restore());highWater=Math.max(highWater,saved.id());
    }
    for(var room:restored)rooms.put(room.id(),room);
    lastAllocatedRoomId=highWater;
  }

  /** The injected clock/sequence can restart below saved IDs; the restored high-water mark wins. */
  private long nextRoomId(){
    long supplied=roomIds.getAsLong();
    if(lastAllocatedRoomId>=Long.MAX_VALUE-1 || supplied==Long.MAX_VALUE)throw new IllegalStateException("room ID space exhausted");
    long next=Math.max(lastAllocatedRoomId+1,supplied);
    if(next<=0 || rooms.containsKey(next))throw new IllegalStateException("invalid room ID allocator");
    lastAllocatedRoomId=next;return next;
  }

  public void setListener(Listener listener) {
    this.listener = listener == null ? new Listener() {} : listener;
  }

  public Optional<Room> room(long id) {
    return Optional.ofNullable(rooms.get(id));
  }

  public Optional<Room> roomOf(UUID player) {
    for (Room room : rooms.values()) {
      if (room.isMember(player)) {
        return Optional.of(room);
      }
    }
    return Optional.empty();
  }

  /** Joinable rooms (waiting, not full) in creation order. */
  public List<RoomSummary> joinableRooms() {
    List<RoomSummary> list = new ArrayList<>();
    for (Room room : rooms.values()) {
      if (room.state() == Room.State.WAITING && !room.isFull()) {
        list.add(RoomSummary.of(room));
      }
    }
    return list;
  }

  public RoomResult<Room> createRoom(UUID owner, String name, RoomMode mode) {
    return createRoom(owner, name, mode, RoomSettings.defaults());
  }

  public RoomResult<Room> createRoom(UUID owner, String name, RoomMode mode, RoomSettings settings) {
    if (settings == null || (mode == RoomMode.TWO_V_TWO && settings.doubleRenegade()))
      return RoomResult.fail(RoomError.INVALID_SETTINGS);
    if (roomOf(owner).isPresent()) {
      return RoomResult.fail(RoomError.ALREADY_IN_ROOM);
    }
    leaveQueueSilently(owner);
    String roomName =
        name == null || name.isBlank() ? "Room " + owner.toString().substring(0, 8) : name;
    Room room = new Room(nextRoomId(), roomName, owner, mode, settings);
    rooms.put(room.id(), room);
    return RoomResult.ok(room);
  }

  public RoomResult<Room> joinRoom(UUID player, long roomId) {
    return joinRoom(player, roomId, "");
  }

  public RoomResult<Room> joinRoom(UUID player, long roomId, String password) {
    return joinRoomInternal(player, roomId, password, false);
  }

  private RoomResult<Room> joinRoomInternal(UUID player, long roomId, String password, boolean invited) {
    Room room = rooms.get(roomId);
    if (room == null) {
      return RoomResult.fail(RoomError.ROOM_NOT_FOUND);
    }
    if (roomOf(player).isPresent()) {
      return RoomResult.fail(RoomError.ALREADY_IN_ROOM);
    }
    if (room.state() != Room.State.WAITING) {
      return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    }
    if (room.isFull()) {
      return RoomResult.fail(RoomError.ROOM_FULL);
    }
    if (!invited && !room.settings().password().equals(java.util.Objects.requireNonNullElse(password, "")))
      return RoomResult.fail(RoomError.WRONG_PASSWORD);
    leaveQueueSilently(player);
    room.addMember(player);
    return RoomResult.ok(room);
  }

  /** Leaves the current room. Owner leaving disbands the room for everyone. */
  public RoomResult<Room> leaveRoom(UUID player) {
    Optional<Room> found = roomOf(player);
    if (found.isEmpty()) {
      return RoomResult.fail(RoomError.NOT_IN_ROOM);
    }
    Room room = found.get();
    if (room.owner().equals(player)) {
      rooms.remove(room.id());
      pendingInvites.values().removeIf(id -> id == room.id());
      listener.onRoomDisbanded(room);
      return RoomResult.ok(room);
    }
    room.removeMember(player);
    return RoomResult.ok(room);
  }

  public RoomResult<Void> invite(UUID inviter, UUID target) {
    Optional<Room> found = roomOf(inviter);
    if (found.isEmpty()) {
      return RoomResult.fail(RoomError.NOT_IN_ROOM);
    }
    Room room = found.get();
    if (!room.owner().equals(inviter)) {
      return RoomResult.fail(RoomError.NOT_OWNER);
    }
    if (room.state() != Room.State.WAITING) return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    if (room.isFull()) return RoomResult.fail(RoomError.ROOM_FULL);
    if (inviter.equals(target)) {
      return RoomResult.fail(RoomError.CANNOT_INVITE_SELF);
    }
    if (roomOf(target).isPresent()) {
      return RoomResult.fail(RoomError.TARGET_IN_ROOM);
    }
    pendingInvites.put(target, room.id());
    listener.onInvite(inviter, target, room);
    return RoomResult.ok();
  }

  public RoomResult<Room> acceptInvite(UUID target) {
    Long roomId = pendingInvites.remove(target);
    if (roomId == null) {
      return RoomResult.fail(RoomError.NOT_INVITED);
    }
    return joinRoomInternal(target, roomId, "", true);
  }

  public RoomResult<Void> declineInvite(UUID target) {
    if (pendingInvites.remove(target) == null) {
      return RoomResult.fail(RoomError.NOT_INVITED);
    }
    return RoomResult.ok();
  }

  /**
   * Swaps the caller's lobby seat with another member of the same room. Fixed-seat matches preserve
   * this relative order; the lord still anchors seat one.
   */
  public RoomResult<Room> swapSeat(UUID player, UUID target) {
    Room room = roomOf(player).orElse(null);
    if (room == null) {
      return RoomResult.fail(RoomError.NOT_IN_ROOM);
    }
    if (room.state() != Room.State.WAITING) {
      return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    }
    if (!room.isMember(target)) {
      return RoomResult.fail(RoomError.ROOM_NOT_FOUND);
    }
    room.swapSeats(player, target);
    return RoomResult.ok(room);
  }

  /** Moves the caller onto a specific empty hall slot ("sit down"). */
  public RoomResult<Room> sit(UUID player, int slot) {
    Room room = roomOf(player).orElse(null);
    if (room == null) {
      return RoomResult.fail(RoomError.NOT_IN_ROOM);
    }
    if (room.state() != Room.State.WAITING) {
      return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    }
    if (slot < 0 || slot >= room.capacity()) {
      return RoomResult.fail(RoomError.INVALID_SEAT);
    }
    if (!room.moveToSeat(player, slot)) {
      return RoomResult.fail(RoomError.SEAT_TAKEN);
    }
    return RoomResult.ok(room);
  }

  /** Owner-only reservation of an empty hall slot for a bot. */
  public RoomResult<Room> reserveAiSlot(UUID owner, long roomId, int slot, boolean reserved) {
    Room room = rooms.get(roomId);
    if (room == null) {
      return RoomResult.fail(RoomError.ROOM_NOT_FOUND);
    }
    if (!room.owner().equals(owner)) {
      return RoomResult.fail(RoomError.NOT_OWNER);
    }
    if (room.state() != Room.State.WAITING) return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    if (slot < 0 || slot >= room.capacity()) {
      return RoomResult.fail(RoomError.INVALID_SEAT);
    }
    if (!room.setAiSlot(slot, reserved)) {
      return RoomResult.fail(RoomError.SEAT_TAKEN);
    }
    return RoomResult.ok(room);
  }

  /** Owner-only one-click reservation of every empty hall slot for bots. */
  public RoomResult<Room> fillAllAiSlots(UUID owner, long roomId) {
    Room room = rooms.get(roomId);
    if (room == null) {
      return RoomResult.fail(RoomError.ROOM_NOT_FOUND);
    }
    if (!room.owner().equals(owner)) {
      return RoomResult.fail(RoomError.NOT_OWNER);
    }
    if (room.state()!=Room.State.WAITING) return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    room.fillAiSlots();
    return RoomResult.ok(room);
  }

  /** Removes only explicit AI reservations, preserving every human seat. */
  public RoomResult<Room> clearAiSlots(UUID owner, long roomId) {
    Room room = rooms.get(roomId);
    if (room == null) return RoomResult.fail(RoomError.ROOM_NOT_FOUND);
    if (!room.owner().equals(owner)) return RoomResult.fail(RoomError.NOT_OWNER);
    if (room.state() != Room.State.WAITING) return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    room.clearAiSlots();
    return RoomResult.ok(room);
  }

  public RoomResult<Room> updateSettings(UUID owner, RoomSettings settings) {
    Room room = roomOf(owner).orElse(null);
    return updateRoom(owner, room == null ? "" : room.name(), settings);
  }

  public RoomResult<Room> renameRoom(UUID owner, String name) {
    Room room = roomOf(owner).orElse(null);
    return updateRoom(owner, name, room == null ? null : room.settings());
  }

  /** Validate the complete edit before touching either name or rules, then reset readiness once. */
  public RoomResult<Room> updateRoom(UUID owner, String name, RoomSettings settings) {
    Room room = roomOf(owner).orElse(null);
    if (room == null) return RoomResult.fail(RoomError.NOT_IN_ROOM);
    if (!room.owner().equals(owner)) return RoomResult.fail(RoomError.NOT_OWNER);
    if (room.state() != Room.State.WAITING) return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    if (!validRoomName(name) || settings == null || (room.mode() == RoomMode.TWO_V_TWO && settings.doubleRenegade()))
      return RoomResult.fail(RoomError.INVALID_SETTINGS);
    room.setName(name.strip());
    room.setSettings(settings);
    return RoomResult.ok(room);
  }

  private static boolean validRoomName(String name) {
    return name != null && !name.isBlank() && name.strip().length() <= 32
        && name.codePoints().noneMatch(cp -> Character.isISOControl(cp) || cp == '\u00a7');
  }

  /** Owner-only removal of a member back to the lobby. */
  public RoomResult<Room> kick(UUID owner, UUID target) {
    Room room = roomOf(owner).orElse(null);
    if (room == null) {
      return RoomResult.fail(RoomError.NOT_IN_ROOM);
    }
    if (!room.owner().equals(owner)) {
      return RoomResult.fail(RoomError.NOT_OWNER);
    }
    if (room.state() != Room.State.WAITING) return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    if (room.owner().equals(target)) {
      return RoomResult.fail(RoomError.CANNOT_KICK_OWNER);
    }
    if (!room.removeMember(target)) {
      return RoomResult.fail(RoomError.NOT_IN_ROOM);
    }
    return RoomResult.ok(room);
  }

  /** Every existing room (including in-progress ones), for the lobby browser and commands. */
  public List<RoomSummary> allRooms() {
    List<RoomSummary> list = new ArrayList<>();
    for (Room room : rooms.values()) {
      list.add(RoomSummary.of(room));
    }
    return list;
  }

  /** Owner-only transition of a waiting room into the playing state. */
  public RoomResult<Room> beginMatch(UUID owner, long roomId) {
    Room room = rooms.get(roomId);
    if (room == null) {
      return RoomResult.fail(RoomError.ROOM_NOT_FOUND);
    }
    if (!room.owner().equals(owner)) {
      return RoomResult.fail(RoomError.NOT_OWNER);
    }
    if (room.state() != Room.State.WAITING) {
      return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    }
    room.markPlaying();
    return RoomResult.ok(room);
  }

  public Optional<Long> pendingInvite(UUID target) {
    return Optional.ofNullable(pendingInvites.get(target));
  }

  public RoomResult<Room> setReady(UUID player, boolean ready) {
    Room room=roomOf(player).orElse(null);
    if (room==null) return RoomResult.fail(RoomError.NOT_IN_ROOM);
    if (!room.setReady(player,ready)) return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    return RoomResult.ok(room);
  }

  /** Strict product gate; legacy beginMatch remains only for the old developer bot harness. */
  public RoomResult<Room> beginReadyMatch(UUID owner,long roomId) {
    Room room=rooms.get(roomId);
    if(room==null) return RoomResult.fail(RoomError.ROOM_NOT_FOUND);
    if(!room.owner().equals(owner)) return RoomResult.fail(RoomError.NOT_OWNER);
    if(room.state()!=Room.State.WAITING) return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    if(room.memberCount()!=room.capacity() || !room.aiSlots().isEmpty()) return RoomResult.fail(RoomError.ROOM_NOT_FULL);
    if(!room.allHumanMembersReady()) return RoomResult.fail(RoomError.MEMBERS_NOT_READY);
    room.markPlaying();
    return RoomResult.ok(room);
  }

  /** Explicitly configured room: every seat is a real member or owner-added AI. */
  public RoomResult<Room> beginConfiguredMatch(UUID owner,long roomId) {
    var validation=validateConfiguredStart(owner,roomId);
    if(validation.isOk())validation.value().markPlaying();
    return validation;
  }

  public RoomResult<Room> validateConfiguredStart(UUID owner,long roomId) {
    Room room=rooms.get(roomId);
    if(room==null)return RoomResult.fail(RoomError.ROOM_NOT_FOUND);
    if(!room.owner().equals(owner))return RoomResult.fail(RoomError.NOT_OWNER);
    if(room.state()!=Room.State.WAITING)return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    if(room.memberCount()+room.aiSlots().size()!=room.capacity())return RoomResult.fail(RoomError.ROOM_NOT_FULL);
    if(room.members().stream().anyMatch(player->!room.isReady(player)))return RoomResult.fail(RoomError.MEMBERS_NOT_READY);
    return RoomResult.ok(room);
  }

  public RoomResult<Room> setTeammateSharing(UUID owner,boolean enabled){
    var room=roomOf(owner).orElse(null);
    if(room==null)return RoomResult.fail(RoomError.NOT_IN_ROOM);
    if(!room.owner().equals(owner))return RoomResult.fail(RoomError.NOT_OWNER);
    if(room.mode()!=RoomMode.TWO_V_TWO)return RoomResult.fail(RoomError.ROOM_NOT_WAITING);
    room.setTeammateSharing(enabled);return RoomResult.ok(room);
  }

  public void finishConfiguredMatch(long roomId){var room=rooms.get(roomId);if(room!=null)room.returnToWaiting();}

  public Optional<RoomMode> queuedMode(UUID player) {
    for (Map.Entry<RoomMode, Deque<UUID>> entry : queues.entrySet()) {
      if (entry.getValue().contains(player)) {
        return Optional.of(entry.getKey());
      }
    }
    return Optional.empty();
  }

  public int queueSize(RoomMode mode) {
    return queues.get(mode).size();
  }

  /**
   * Enqueues the player. When the queue reaches the mode's player count, a room is created
   * automatically for the whole group.
   */
  public RoomResult<Integer> joinQueue(UUID player, RoomMode mode) {
    if (roomOf(player).isPresent()) {
      return RoomResult.fail(RoomError.ALREADY_IN_ROOM);
    }
    if (queuedMode(player).isPresent()) {
      return RoomResult.fail(RoomError.ALREADY_QUEUED);
    }
    Deque<UUID> queue = queues.get(mode);
    queue.addLast(player);
    if (queue.size() >= mode.playerCount()) {
      List<UUID> group = new ArrayList<>(mode.playerCount());
      for (int i = 0; i < mode.playerCount(); i++) {
        group.add(queue.pollFirst());
      }
      Room room =
          new Room(nextRoomId(), "Matchmaking-" + mode.playerCount(), group.get(0), mode);
      for (int i = 1; i < group.size(); i++) {
        room.addMember(group.get(i));
      }
      rooms.put(room.id(), room);
      listener.onMatchmade(room);
    }
    return RoomResult.ok(queue.size());
  }

  public RoomResult<Void> leaveQueue(UUID player) {
    RoomMode mode = queuedMode(player).orElse(null);
    if (mode == null) {
      return RoomResult.fail(RoomError.NOT_QUEUED);
    }
    queues.get(mode).remove(player);
    return RoomResult.ok();
  }

  /** Disconnect cleanup: leave queue and room. Returns the affected room if any. */
  public Optional<Room> handleDisconnect(UUID player) {
    leaveQueueSilently(player);
    pendingInvites.remove(player);
    RoomResult<Room> result = leaveRoom(player);
    return result.isOk() ? Optional.of(result.value()) : Optional.empty();
  }

  private void leaveQueueSilently(UUID player) {
    queuedMode(player).ifPresent(mode -> queues.get(mode).remove(player));
  }
}
