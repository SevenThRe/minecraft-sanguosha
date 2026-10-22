package dev.sgs.core.persistence;

import dev.sgs.core.room.*;
import java.util.*;

/** Private world state. Contains the room password; never expose this record as a public view. */
public record RoomCheckpoint(int version,long id,UUID owner,String name,RoomMode mode,
    RoomSettings settings,Map<Integer,UUID> seats,Set<Integer> aiSlots,Set<UUID> ready,
    boolean teammateSharing,Room.State state) {
  public static final int VERSION=1;
  public RoomCheckpoint {
    Objects.requireNonNull(owner);Objects.requireNonNull(mode);Objects.requireNonNull(settings);Objects.requireNonNull(state);
    seats=Collections.unmodifiableMap(new TreeMap<>(seats));aiSlots=Collections.unmodifiableSet(new TreeSet<>(aiSlots));
    ready=Set.copyOf(ready);
    validateFields(version,id,owner,name,mode,settings,seats,aiSlots,ready,teammateSharing,state);
  }
  public static RoomCheckpoint capture(Room room){
    Objects.requireNonNull(room);var seats=new TreeMap<Integer,UUID>();var ready=new HashSet<UUID>();
    for(UUID player:room.members()){seats.put(room.seatOf(player),player);if(room.isReady(player))ready.add(player);}
    return new RoomCheckpoint(VERSION,room.id(),room.owner(),room.name(),room.mode(),room.settings(),seats,room.aiSlots(),ready,room.teammateSharing(),room.state());
  }
  public Room restore(){return Room.restoreCheckpoint(this);}
  public void validate(){validateFields(version,id,owner,name,mode,settings,seats,aiSlots,ready,teammateSharing,state);}
  private static void validateFields(int version,long id,UUID owner,String name,RoomMode mode,RoomSettings settings,
      Map<Integer,UUID> seats,Set<Integer> ai,Set<UUID> ready,boolean sharing,Room.State state){
    if(version!=VERSION || id<=0 || id==Long.MAX_VALUE)throw new IllegalArgumentException("invalid room checkpoint header");
    text(name,32);if(name.isBlank() || !name.equals(name.strip()) || name.codePoints().anyMatch(c->Character.isISOControl(c)||c==0x00a7))
      throw new IllegalArgumentException("invalid room name");
    text(settings.password(),32);
    if(settings.bannedHeroes().size()>6)throw new IllegalArgumentException("too many banned heroes");
    for(String hero:settings.bannedHeroes()){
      text(hero,128);if(hero.isBlank() || hero.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||c==0x00a7))
        throw new IllegalArgumentException("invalid banned hero ID");
    }
    int capacity=mode.playerCount();
    if(seats.isEmpty() || seats.size()+ai.size()>capacity || !seats.containsValue(owner)
        || new HashSet<>(seats.values()).size()!=seats.size() || !seats.values().containsAll(ready))throw new IllegalArgumentException("invalid room membership");
    for(var entry:seats.entrySet())if(entry.getKey()<0 || entry.getKey()>=capacity || entry.getValue()==null || ai.contains(entry.getKey()))
      throw new IllegalArgumentException("invalid occupied seat");
    for(int slot:ai)if(slot<0 || slot>=capacity)throw new IllegalArgumentException("invalid AI seat");
    if((mode==RoomMode.TWO_V_TWO && settings.doubleRenegade()) || (mode!=RoomMode.TWO_V_TWO && sharing))throw new IllegalArgumentException("rules do not match room mode");
    // The integration archive independently requires a matching live MatchCheckpoint for PLAYING.
    if(state==Room.State.PLAYING && (seats.size()+ai.size()!=capacity || !ready.containsAll(seats.values())))
      throw new IllegalArgumentException("playing room is not fully configured and ready");
  }
  static void text(String value,int limit){
    if(value==null || value.length()>limit)throw new IllegalArgumentException("invalid room text length");
    for(int i=0;i<value.length();i++){
      char c=value.charAt(i);if(Character.isHighSurrogate(c)){
        if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("invalid room text Unicode");
      }else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("invalid room text Unicode");
    }
  }
}
