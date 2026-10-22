package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.Identity;
import dev.sgs.core.Kingdom;
import dev.sgs.core.engine.skill.SkillEngine;
import dev.sgs.core.player.SgsPlayer;

/** Standard lord skill: ask other living Wei characters in current-turn action order. */
public final class HuJiaSkill {
  public static final String ID="hujia";
  private final SgsPlayer owner;
  private final SkillEngine engine;
  public HuJiaSkill(SgsPlayer owner,SkillEngine engine){this.owner=owner;this.engine=engine;}
  public boolean provide(){
    if(!owner.alive() || owner.identity()!=Identity.LORD)return false;
    var helpers=engine.gameState().alivePlayersFromCurrent().stream()
        .filter(p->p!=owner && p.hero()!=null && engine.gameState().kingdom(p)==Kingdom.WEI).toList();
    if(helpers.isEmpty() || !engine.requestSkillInvoke(owner,ID,owner))return false;
    for(var helper:helpers){
      if(!owner.alive())return false;
      if(helper.alive() && engine.requestProvidedDodge(helper,owner)){
        engine.gameState().publishSkillActivation(owner.seat(),ID);
        engine.gameState().log("seat {} used hujia: shan supplied by seat {}",owner.seat(),helper.seat());return true;
      }
    }
    return false;
  }
}
