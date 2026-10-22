package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.Phase;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.List;
/** Tracks actual committed effective Sha uses/responses, never mere declarations or other turns. */
public final class KeJiSkill implements AwaitablePhaseEffect,PersistentSkillMemory {
  private final SgsPlayer owner;private final SkillEngine api;private int usedTurn=-1;
  public KeJiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public java.util.Map<String,Long> saveMemory(){return java.util.Map.of("usedTurn",(long)usedTurn);}
  public void validateMemory(java.util.Map<String,Long> values,int turnNumber){
    if(values==null || !values.keySet().equals(java.util.Set.of("usedTurn")) || values.get("usedTurn")==null
        || turnNumber<0 || values.get("usedTurn")< -1 || values.get("usedTurn")>turnNumber)
      throw new IllegalArgumentException("invalid keji memory");
  }
  public void restoreMemory(java.util.Map<String,Long> values,int turnNumber){validateMemory(values,turnNumber);usedTurn=values.get("usedTurn").intValue();}
  public String id(){return "keji";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.PHASE_DISCARD_BEFORE;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return owner.alive() && c.actor()==owner && usedTurn!=c.state().turnNumber();}
  public void execute(SkillContext c){if(api.requestSkillInvoke(owner,id(),owner)){c.skipPhase();c.confirmPublicActivation();}}
  public List<SkillListener> usageHooks(){return List.of(usage(Timing.CARD_USE_COMMITTED),usage(Timing.CARD_RESPONSE_COMMITTED));}
  private SkillListener usage(Timing timing){return new SkillListener(){
    public String id(){return "keji";}public SgsPlayer owner(){return owner;}public Timing timing(){return timing;}public int priority(){return Priority.RULE_MODIFIER;}
    public boolean applies(SkillContext c){return c.actor()==owner && c.state().currentPlayer()==owner && c.state().phase()==Phase.PLAY && c.card()!=null
      && (c.card().type()==CardType.SHA || c.card().type()==CardType.HUOSHA || c.card().type()==CardType.LEISHA);}
    public void execute(SkillContext c){usedTurn=c.state().turnNumber();}
  };}
}
