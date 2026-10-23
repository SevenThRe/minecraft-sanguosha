package dev.sgs.core.persistence;

import java.util.*;

/** Session transport state paired with an explicitly restorable rules program. Never a client view. */
public record MatchCheckpoint(int version,long roomId,String contentId,boolean attack,byte[] engine,
    long phaseRemainingMillis,boolean teammateSharing,Map<UUID,Integer> nonceFloors,
    dev.sgs.core.room.RoomSettings roomSettings,long botStepDelayMillis,Set<UUID> userAutoplay,Map<UUID,dev.sgs.core.match.SeatControl.Snapshot> seatControls,List<String> journal){
  public static final int VERSION=4;
  public MatchCheckpoint(int version,long roomId,String contentId,boolean attack,byte[] engine,long phaseRemainingMillis,boolean teammateSharing,Map<UUID,Integer> nonceFloors,dev.sgs.core.room.RoomSettings roomSettings,long botStepDelayMillis,Set<UUID> userAutoplay,Map<UUID,dev.sgs.core.match.SeatControl.Snapshot> seatControls){
    this(version,roomId,contentId,attack,engine,phaseRemainingMillis,teammateSharing,nonceFloors,roomSettings,botStepDelayMillis,userAutoplay,seatControls,List.of());
  }
  public MatchCheckpoint(int version,long roomId,String contentId,boolean attack,byte[] engine,long phaseRemainingMillis,boolean teammateSharing,Map<UUID,Integer> nonceFloors,dev.sgs.core.room.RoomSettings roomSettings,long botStepDelayMillis,Set<UUID> userAutoplay){
    this(version,roomId,contentId,attack,engine,phaseRemainingMillis,teammateSharing,nonceFloors,roomSettings,botStepDelayMillis,userAutoplay,Map.of());
  }
  public MatchCheckpoint(int version,long roomId,String contentId,boolean attack,byte[] engine,long phaseRemainingMillis,boolean teammateSharing,Map<UUID,Integer> nonceFloors,dev.sgs.core.room.RoomSettings roomSettings,long botStepDelayMillis){
    this(version,roomId,contentId,attack,engine,phaseRemainingMillis,teammateSharing,nonceFloors,roomSettings,botStepDelayMillis,Set.of());
  }
  public MatchCheckpoint{
    journal=List.copyOf(journal);
    if(journal.size()>dev.sgs.core.game.MatchJournal.MAX_LINES || journal.stream().anyMatch(line->line.length()>4000)
        || journal.stream().mapToInt(String::length).sum()>dev.sgs.core.game.MatchJournal.MAX_CHARS || version<4&&!journal.isEmpty())throw new IllegalArgumentException("invalid journal");
    userAutoplay=Set.copyOf(userAutoplay);engine=engine.clone();nonceFloors=Map.copyOf(nonceFloors);Objects.requireNonNull(roomSettings);
    if((version<1 || version>VERSION) || roomId<0 || contentId==null || contentId.length()>128 || contentId.isEmpty()
        || engine.length<40 || engine.length>4_000_000 || phaseRemainingMillis<0 || phaseRemainingMillis>3_600_000
        || botStepDelayMillis<0 || botStepDelayMillis>60_000)
      throw new IllegalArgumentException("invalid match checkpoint");
    StateCheckpoint state;int response;
    if(attack){var saved=ContinuationCheckpointCodec.decode(engine);state=saved.state();response=saved.responseTimeoutSeconds();}
    else if(TrickCheckpointCodec.isPayload(engine)){var saved=TrickCheckpointCodec.decode(engine);state=saved.state();response=saved.responseTimeoutSeconds();}
    else if(ActiveSkillCheckpointCodec.isPayload(engine)){var saved=ActiveSkillCheckpointCodec.decode(engine);state=saved.state();response=saved.responseTimeoutSeconds();}
    else if(TurnStartCheckpointCodec.isPayload(engine)){var saved=TurnStartCheckpointCodec.decode(engine);state=saved.state();response=saved.responseTimeoutSeconds();}
    else if(TurnEndCheckpointCodec.isPayload(engine)){var saved=TurnEndCheckpointCodec.decode(engine);state=saved.state();response=saved.responseTimeoutSeconds();}
    else{var saved=IdleEngineCheckpointCodec.decode(engine);state=saved.state();response=saved.responseTimeoutSeconds();}
    if(response!=roomSettings.responseSeconds() || phaseRemainingMillis>Math.max(roomSettings.turnSeconds(),roomSettings.responseSeconds())*1000L)
      throw new IllegalArgumentException("saved timing mismatch");
    if(!state.contentVersion().equals(contentId))throw new IllegalArgumentException("session content mismatch");
    var ids=state.players().stream().map(StateCheckpoint.Player::id).collect(java.util.stream.Collectors.toSet());
    var humanIds=state.players().stream().filter(StateCheckpoint.Player::human).map(StateCheckpoint.Player::id).collect(java.util.stream.Collectors.toSet());
    if(!humanIds.containsAll(userAutoplay) || version==1 && !userAutoplay.isEmpty())throw new IllegalArgumentException("invalid saved autoplay");
    Objects.requireNonNull(seatControls);
    if(version>=3 && seatControls.isEmpty()){
      var defaults=new HashMap<UUID,dev.sgs.core.match.SeatControl.Snapshot>();
      for(var p:state.players())if(p.human())defaults.put(p.id(),new dev.sgs.core.match.SeatControl.Snapshot(userAutoplay.contains(p.id())?dev.sgs.core.match.SeatControl.Source.USER:dev.sgs.core.match.SeatControl.Source.MANUAL,0,false,p.online()?-1:10000));
      seatControls=defaults;
    }
    seatControls=Map.copyOf(seatControls);
    if(version<3 && !seatControls.isEmpty() || version>=3 && !seatControls.keySet().equals(humanIds))throw new IllegalArgumentException("saved control roster mismatch");
    if(version>=3){
      var voluntary=seatControls.entrySet().stream().filter(e->e.getValue().source()==dev.sgs.core.match.SeatControl.Source.USER).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
      if(!voluntary.equals(userAutoplay))throw new IllegalArgumentException("saved voluntary control mismatch");
      for(var p:state.players())if(p.human()){
        var c=seatControls.get(p.id());
        if(p.online() && (c.awaitingReconnect() || c.offlineRemainingMillis()!=-1 || c.source()==dev.sgs.core.match.SeatControl.Source.DISCONNECT)
            || !p.online() && !c.awaitingReconnect() && c.offlineRemainingMillis()<0)throw new IllegalArgumentException("saved control presence mismatch");
      }
    }
    if(!nonceFloors.keySet().equals(ids) || nonceFloors.values().stream().anyMatch(n->n==null || n<0))
      throw new IllegalArgumentException("invalid session nonce floors");
  }
  public boolean trick(){return !attack && TrickCheckpointCodec.isPayload(engine);}
  public boolean activeSkill(){return !attack && ActiveSkillCheckpointCodec.isPayload(engine);}
  public boolean turnStart(){return !attack && TurnStartCheckpointCodec.isPayload(engine);}
  public boolean turnEnd(){return !attack && TurnEndCheckpointCodec.isPayload(engine);}
  public StateCheckpoint stateCheckpoint(){return attack?ContinuationCheckpointCodec.decode(engine).state():trick()?TrickCheckpointCodec.decode(engine).state():activeSkill()?ActiveSkillCheckpointCodec.decode(engine).state():turnEnd()?TurnEndCheckpointCodec.decode(engine).state():turnStart()?TurnStartCheckpointCodec.decode(engine).state():IdleEngineCheckpointCodec.decode(engine).state();}
  @Override public byte[] engine(){return engine.clone();}
  public dev.sgs.core.match.MatchConfig config(){
    var pool=Arrays.stream(dev.sgs.core.match.ContentPool.values()).filter(p->p.id().equals(contentId)).findFirst()
        .orElseThrow(()->new IllegalArgumentException("unsupported saved content"));
    return new dev.sgs.core.match.MatchConfig(roomSettings.responseSeconds(),roomSettings.turnSeconds(),
        roomSettings.selectionSeconds(),botStepDelayMillis,dev.sgs.core.addon.AddonRuntime.current().heroes(),roomSettings,pool);
  }
}
