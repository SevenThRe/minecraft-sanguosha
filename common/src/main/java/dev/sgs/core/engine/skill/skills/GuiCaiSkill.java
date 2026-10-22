package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.List;

/** Standard GuiCai uses exactly one real hand card, unlike the later Jie equipment revision. */
public final class GuiCaiSkill implements SkillListener {
  private final SgsPlayer owner;private final SkillEngine api;
  public GuiCaiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "guicai";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.JUDGE_CARD_MODIFY;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return owner.alive() && owner.hp()>0 && owner.handCount()>0 && c.judgeCard()!=null;}
  public void execute(SkillContext c){
    var choice=api.requestSkillChoice(owner,c.target(),PromptType.GUI_CAI_REPLACE,id(),owner.handView(),List.of(),false);
    if(choice.isEmpty())return;
    var replacement=owner.handView().stream().filter(card->card.instanceId()==choice.get().cardId()).findFirst().orElse(null);
    if(replacement!=null){c.replaceJudgment(owner,replacement);c.confirmPublicActivation();}
  }
}
