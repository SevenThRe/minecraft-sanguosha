package dev.sgs.core.persistence;

import dev.sgs.core.GameStatus;
import dev.sgs.core.Phase;
import dev.sgs.core.game.GameState.PublicSkillActivation;
import java.util.*;

/**
 * Versioned engine data at an idle PLAY/DISCARD boundary. This is a prerequisite for the
 * continuation protocol, not a pending-request/session save: no thread, lambda or Java stack.
 */
public record IdleEngineCheckpoint(int version,StateCheckpoint state,long promptSequence,
    int responseTimeoutSeconds,Map<String,Map<String,Long>> skillMemory,List<String> bindings,
    long skillSequence,List<PublicSkillActivation> activations){
  public static final int VERSION=1;
  public static final String RULES_VERSION="historical-standard-2011-idle-v1";
  public IdleEngineCheckpoint{
    Objects.requireNonNull(state);Objects.requireNonNull(skillMemory);
    var copy=new TreeMap<String,Map<String,Long>>();
    skillMemory.forEach((key,value)->copy.put(key,Map.copyOf(value)));
    skillMemory=Collections.unmodifiableMap(copy);bindings=List.copyOf(bindings);activations=List.copyOf(activations);
  }
  public void validate(){
    state.validate();
    if(version!=VERSION || promptSequence<0 || promptSequence==Long.MAX_VALUE || responseTimeoutSeconds<1
        || responseTimeoutSeconds>3600 || skillSequence<0 || skillMemory.size()>SkillMemoryLayout.MAX_RECORDS || bindings.size()>256
        || activations.size()>32 || state.turn().status()!=GameStatus.RUNNING
        || (state.turn().phase()!=Phase.PLAY && state.turn().phase()!=Phase.DISCARD)
        || state.zones().stream().anyMatch(z->z.location().equals(dev.sgs.core.card.CardZone.PROCESSING) && !z.cards().isEmpty())
        || state.players().stream().anyMatch(p->p.alive() && p.hp()<1 && !buquAlive(p)))throw new IllegalArgumentException("invalid idle engine checkpoint");
    skillMemory.forEach((key,values)->{
      SkillMemoryLayout.validate(key,values,state.players().size(),true);
    });
    bindings.forEach(value->StateCheckpoint.text(value,256));
    long previous=0;
    for(var activation:activations){
      if(activation.sequence()<=previous || activation.sequence()>skillSequence || activation.seat()<0
          || activation.seat()>=state.players().size())throw new IllegalArgumentException("invalid activation history");
      StateCheckpoint.text(activation.skillId(),128);previous=activation.sequence();
    }
  }
  private boolean buquAlive(StateCheckpoint.Player player){
    var m=skillMemory.getOrDefault("0:mythic",Map.of());
    if(m.getOrDefault(player.seat()+":buqu",0L)!=1||m.getOrDefault(player.seat()+":silenced",0L)!=0)return false;
    var pile=state.zones().stream().filter(z->z.location().equals(dev.sgs.core.card.CardZone.special(player.seat()))).findFirst().orElse(null);
    return pile!=null && pile.cards().size()==1-player.hp() && pile.cards().stream().map(StateCheckpoint.Card::rank).distinct().count()==pile.cards().size();
  }
}
