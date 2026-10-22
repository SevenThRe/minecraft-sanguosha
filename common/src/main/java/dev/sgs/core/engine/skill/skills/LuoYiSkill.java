package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.*;

/** Historical standard Xu Chu: choose before drawing, not after seeing the drawn card. */
public final class LuoYiSkill implements AwaitableRuleInvoke, PersistentSkillMemory {
  private final SgsPlayer owner; private final SkillEngine api; private int activeTurn=-1;
  public LuoYiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;
    api.gameState().registerPublicStatus(owner.seat(),id(),"裸衣","本回合出牌阶段，杀或决斗造成的非传导伤害+1",
        ()->api.gameState().currentPlayer()==owner && api.gameState().turnNumber()==activeTurn
            && api.gameState().phase()!=dev.sgs.core.Phase.END);
  }
  public String id(){return "luoyi";} public SgsPlayer owner(){return owner;}
  public RuleId rule(){return RuleId.DRAW_COUNT;} public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.actor()==owner && owner.alive();}
  public Request nextRule(SkillContext c,Progress p){return p.value()<=0?null:AwaitableRuleInvoke.super.nextRule(c,p);}
  public int modifyInt(SkillContext c,int count){
    if(count<=0 || !api.requestSkillInvoke(owner,id(),owner))return count;
    activeTurn=c.state().turnNumber();c.state().publishSkillActivation(owner.seat(),id());return count-1;
  }
  public SkillListener damageHook(){return new SkillListener(){
    public String id(){return "luoyi";}public SgsPlayer owner(){return owner;}
    public Timing timing(){return Timing.DAMAGE_CAUSE_BEFORE;}public int priority(){return Priority.PASSIVE_RESPONSE;}
    public boolean applies(SkillContext c){return c.source()==owner && owner.alive() && c.state().currentPlayer()==owner
        && c.state().turnNumber()==activeTurn && c.state().phase()==dev.sgs.core.Phase.PLAY && c.damage()>0 && !c.chainDamage() && c.card()!=null
        && switch(c.card().type()){case SHA,HUOSHA,LEISHA,JUEDOU->true;default->false;};}
    public void execute(SkillContext c){c.setDamage(c.damage()+1);}
  };}
  public Map<String,Long> saveMemory(){return Map.of("activeTurn",(long)activeTurn);}
  public void validateMemory(Map<String,Long> v,int turn){
    if(v==null || !v.keySet().equals(Set.of("activeTurn")) || v.get("activeTurn")==null || turn<0
        || v.get("activeTurn")< -1 || v.get("activeTurn")>turn)throw new IllegalArgumentException("invalid luoyi memory");
  }
  public void restoreMemory(Map<String,Long> v,int turn){validateMemory(v,turn);activeTurn=v.get("activeTurn").intValue();}
}
