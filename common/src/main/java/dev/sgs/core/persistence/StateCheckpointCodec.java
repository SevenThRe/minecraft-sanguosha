package dev.sgs.core.persistence;

import dev.sgs.core.*;
import dev.sgs.core.card.*;
import dev.sgs.core.game.GameState;
import dev.sgs.core.hero.HeroDefinition;
import dev.sgs.core.room.RoomMode;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bounded binary v2 (explicit hero sex). Outer SHA256 detects corruption; it is NOT a signature/authentication gate. */
public final class StateCheckpointCodec {
  private static final int MAGIC=0x53475343;
  public static final int MAX_BYTES=4*1024*1024;
  private StateCheckpointCodec(){}
  public static byte[] encode(StateCheckpoint checkpoint){
    checkpoint.validate();
    try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)){
      out.writeInt(MAGIC);out.writeInt(checkpoint.version());text(out,checkpoint.contentVersion());
      var t=checkpoint.turn();text(out,t.status().name());out.writeInt(t.currentSeat());text(out,t.phase().name());
      out.writeInt(t.turnNumber());out.writeInt(t.shaPlayed());out.writeBoolean(t.jiuPlayed());out.writeBoolean(t.jiuBoost());
      out.writeBoolean(t.skipPlay());out.writeBoolean(t.skipDraw());text(out,t.mode()==null?"":t.mode().name());
      out.writeInt(t.chained().size());for(int seat:new TreeSet<>(t.chained()))out.writeInt(seat);
      out.writeInt(t.logs().size());for(var line:t.logs())text(out,line);
      out.writeInt(checkpoint.players().size());
      for(var p:checkpoint.players()){
        out.writeLong(p.id().getMostSignificantBits());out.writeLong(p.id().getLeastSignificantBits());text(out,p.name());out.writeInt(p.seat());
        out.writeBoolean(p.human());out.writeBoolean(p.online());text(out,p.identity().name());
        var h=p.hero();text(out,h.id());text(out,h.kingdom().name());out.writeInt(h.maxHp());out.writeInt(h.skillIds().size());
        for(var skill:h.skillIds())text(out,skill);out.writeBoolean(h.male());
        out.writeInt(p.hp());out.writeInt(p.maxHp());out.writeBoolean(p.alive());out.writeBoolean(p.identityPublic());
      }
      out.writeInt(checkpoint.zones().size());
      for(var z:checkpoint.zones()){
        text(out,z.location().kind().name());out.writeInt(z.location().seat());out.writeInt(z.cards().size());
        for(var c:z.cards()){
          out.writeLong(c.id());text(out,c.definitionId());text(out,c.type().name());text(out,c.effectId());text(out,c.suit().name());text(out,c.rank().name());
          text(out,c.judgmentType()==null?"":c.judgmentType().name());
        }
      }
      out.writeInt(checkpoint.faceUpProcessing().size());for(long id:new TreeSet<>(checkpoint.faceUpProcessing()))out.writeLong(id);
      out.writeLong(checkpoint.movementSequence());out.writeBoolean(checkpoint.sharedRandom());
      blob(out,checkpoint.gameRandom());blob(out,checkpoint.deckRandom());if(checkpoint.version()>=3){out.writeBoolean(checkpoint.completeStatistics());for(long total:checkpoint.damageTotals())out.writeLong(total);
      out.writeBoolean(checkpoint.draft()!=null);if(checkpoint.draft()!=null){var d=checkpoint.draft();out.writeLong(d.id());out.writeInt(d.source());out.writeInt(d.picker());text(out,d.title());out.writeInt(d.slots().size());for(var slot:d.slots()){out.writeLong(slot.card().instanceId());out.writeInt(slot.recipient());}}
      }out.flush();
      byte[] payload=bytes.toByteArray();if(payload.length+32>MAX_BYTES)throw new IllegalArgumentException("checkpoint too large");
      bytes.write(digest(payload));return bytes.toByteArray();
    }catch(IOException e){throw new IllegalArgumentException("cannot encode checkpoint",e);}
  }
  public static StateCheckpoint decode(byte[] encoded){
    if(encoded==null || encoded.length<40 || encoded.length>MAX_BYTES)throw new IllegalArgumentException("invalid checkpoint length");
    byte[] payload=Arrays.copyOf(encoded,encoded.length-32),hash=Arrays.copyOfRange(encoded,encoded.length-32,encoded.length);
    if(!MessageDigest.isEqual(digest(payload),hash))throw new IllegalArgumentException("checkpoint checksum mismatch");
    try(var in=new DataInputStream(new ByteArrayInputStream(payload))){
      if(in.readInt()!=MAGIC)throw new IllegalArgumentException("bad checkpoint magic");
      int version=in.readInt();if(version!=2&&version!=StateCheckpoint.VERSION)throw new IllegalArgumentException("unsupported checkpoint version "+version);
      String content=text(in,128);var status=enumeration(in,GameStatus.class);int current=in.readInt();var phase=enumeration(in,Phase.class);
      int turn=in.readInt(),sha=in.readInt();boolean jiu=in.readBoolean(),boost=in.readBoolean(),skipPlay=in.readBoolean(),skipDraw=in.readBoolean();
      String mode=text(in,32);var chained=new HashSet<Integer>();int size=count(in,8);
      for(int i=0;i<size;i++)if(!chained.add(in.readInt()))throw new IllegalArgumentException("duplicate chained seat");
      var logs=new ArrayList<String>();size=count(in,100);for(int i=0;i<size;i++)logs.add(text(in,4096));
      var runtime=new GameState.TurnState(status,current,phase,turn,sha,jiu,boost,skipPlay,skipDraw,
        mode.isEmpty()?null:RoomMode.valueOf(mode),chained,logs);
      var players=new ArrayList<StateCheckpoint.Player>();size=count(in,8);
      for(int i=0;i<size;i++){
        UUID id=new UUID(in.readLong(),in.readLong());String name=text(in,256);int seat=in.readInt();
        boolean human=in.readBoolean(),online=in.readBoolean();var identity=enumeration(in,Identity.class);
        String heroId=text(in,128);var kingdom=enumeration(in,Kingdom.class);int maxHero=in.readInt(),skills=count(in,64);
        var skillIds=new ArrayList<String>();for(int k=0;k<skills;k++)skillIds.add(text(in,128));
        var hero=new HeroDefinition(heroId,kingdom,maxHero,skillIds,in.readBoolean());
        players.add(new StateCheckpoint.Player(id,name,seat,human,online,identity,hero,in.readInt(),in.readInt(),in.readBoolean(),in.readBoolean()));
      }
      var zones=new ArrayList<StateCheckpoint.Zone>();size=count(in,35);int total=0;
      for(int i=0;i<size;i++){
        var kind=enumeration(in,CardZone.Kind.class);var zone=new CardZone(kind,in.readInt());int cards=count(in,StateCheckpoint.MAX_CARDS);
        total+=cards;if(total>StateCheckpoint.MAX_CARDS)throw new IllegalArgumentException("too many physical cards");
        var data=new ArrayList<StateCheckpoint.Card>();
        for(int k=0;k<cards;k++){
          long id=in.readLong();String definition=text(in,256);var type=enumeration(in,CardType.class);String effect=text(in,256);
          var suit=enumeration(in,Suit.class);var rank=enumeration(in,Rank.class);String judgment=text(in,64);
          data.add(new StateCheckpoint.Card(id,definition,type,effect,suit,rank,judgment.isEmpty()?null:CardType.valueOf(judgment)));
        }
        zones.add(new StateCheckpoint.Zone(zone,data));
      }
      var faceUp=new HashSet<Long>();size=count(in,StateCheckpoint.MAX_CARDS);
      for(int i=0;i<size;i++)if(!faceUp.add(in.readLong()))throw new IllegalArgumentException("duplicate processing visibility");
      long sequence=in.readLong();boolean shared=in.readBoolean();byte[] gameRandom=blob(in),deckRandom=blob(in);
      boolean complete=version>=3&&in.readBoolean();var totals=new ArrayList<Long>();for(int i=0;i<8;i++)totals.add(version>=3?in.readLong():0L);
      dev.sgs.core.match.dto.MatchViews.DraftView draft=null;
      if(version>=3&&in.readBoolean()){long id=in.readLong();int source=in.readInt(),picker=in.readInt();String title=text(in,64);int n=count(in,8);var slots=new ArrayList<dev.sgs.core.match.dto.MatchViews.DraftSlot>();
        for(int i=0;i<n;i++){long card=in.readLong();int recipient=in.readInt();var physical=zones.stream().flatMap(z->z.cards().stream()).filter(c->c.id()==card).findFirst().orElseThrow();slots.add(new dev.sgs.core.match.dto.MatchViews.DraftSlot(dev.sgs.core.match.dto.MatchViews.CardView.of(physical.create()),recipient));}
        draft=new dev.sgs.core.match.dto.MatchViews.DraftView(id,source,picker,title,slots,true);
      }
      if(in.available()!=0)throw new IllegalArgumentException("trailing checkpoint bytes");
      var checkpoint=new StateCheckpoint(version,content,runtime,players,zones,faceUp,sequence,gameRandom,deckRandom,shared,totals,complete,draft);
      checkpoint.validate();return checkpoint;
    }catch(IOException|RuntimeException e){throw new IllegalArgumentException("invalid checkpoint: "+e.getMessage(),e);}
  }
  private static int count(DataInputStream in,int max)throws IOException{
    int count=in.readInt();if(count<0 || count>max)throw new IllegalArgumentException("invalid collection length");return count;
  }
  private static void text(DataOutputStream out,String value)throws IOException{
    byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);
  }
  private static String text(DataInputStream in,int max)throws IOException{
    int length=count(in,max*4);byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException();
    String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    if(value.length()>max)throw new IllegalArgumentException("text too long");return value;
  }
  private static <T extends Enum<T>> T enumeration(DataInputStream in,Class<T> type)throws IOException{return Enum.valueOf(type,text(in,64));}
  private static void blob(DataOutputStream out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
  private static byte[] blob(DataInputStream in)throws IOException{
    int length=count(in,4096);byte[] data=in.readNBytes(length);if(data.length!=length)throw new EOFException();return data;
  }
  private static byte[] digest(byte[] bytes){
    try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
}
