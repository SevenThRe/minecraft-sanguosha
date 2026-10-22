package dev.sgs.core.persistence;

import dev.sgs.core.room.Room;
import java.util.*;

/** One atomic world unit: a room and its matching game, or an idle waiting room. */
public record RoomMatchArchive(RoomCheckpoint room,MatchCheckpoint match){
  public RoomMatchArchive{
    Objects.requireNonNull(room);room.validate();
    if(room.state()==Room.State.PLAYING && match==null || room.state()!=Room.State.PLAYING && match!=null)
      throw new IllegalArgumentException("room and match lifecycle differ");
    if(match!=null){
      var state=match.stateCheckpoint();
      var humans=state.players().stream().filter(StateCheckpoint.Player::human).map(StateCheckpoint.Player::id).collect(java.util.stream.Collectors.toSet());
      if(!humans.equals(new HashSet<>(room.seats().values())) || state.players().size()!=room.mode().playerCount()
          || !room.settings().equals(match.roomSettings()) || room.teammateSharing()!=match.teammateSharing())
        throw new IllegalArgumentException("room and game roster/rules differ");
    }
  }
}
