package dev.sgs.core.engine.skill.skills;

import dev.sgs.core.engine.PromptType;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
import java.util.List;

/** Standard (2011) feedback: once per damage event, never the source's judgment zone. */
public final class FanKuiSkill implements AwaitableDamageEffect {
  public Kind damageEffect(){return Kind.TAKE_SOURCE_CARD;}
  private final SgsPlayer owner;private final SkillEngine api;
  public FanKuiSkill(SgsPlayer owner,SkillEngine api){this.owner=owner;this.api=api;}
  public String id(){return "fankui";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.DAMAGE_TAKE_AFTER;}public int priority(){return Priority.ACTIVE_CHOICE;}
  public boolean applies(SkillContext c){return c.target()==owner && owner.alive() && owner.hp()>0 && c.damage()>0
      && c.source()!=null && (!c.source().equipment().all().isEmpty() || c.source()!=owner && c.source().handCount()>0);}
  public void execute(SkillContext c){
    var source=c.source();
    var choice=api.requestSkillChoice(owner,source,PromptType.FANKUI_TAKE,id(),source.equipment().all(),List.of(),source!=owner && source.handCount()>0);
    if(choice.isPresent() && api.takeFeedbackCard(owner,source,choice.get()))c.confirmPublicActivation();
  }
}
