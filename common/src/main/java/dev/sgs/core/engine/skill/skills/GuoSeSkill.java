package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
/** Standard diamond hand/equipment as acedia; not a judgment-zone conversion. */
public final class GuoSeSkill implements SkillListener {
  private final SgsPlayer owner;public GuoSeSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "guose";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.CARD_TRANSFORM;}public int priority(){return Priority.PASSIVE_RESPONSE;}
  public boolean applies(SkillContext c){
    if(c.actor()!=owner || !owner.alive() || c.card()==null || c.card().isVirtual() || c.card().suit()!=Suit.DIAMOND
        || c.card().sourceCards().size()!=1 || c.declaredAs()!=CardType.LEBU || c.promptType()!=null)return false;
    var zone=c.state().cards().locate(c.card().sourceCards().getFirst().instanceId());
    return CardZone.hand(owner.seat()).equals(zone) || CardZone.equipment(owner.seat()).equals(zone);
  }
  public void execute(SkillContext c){c.confirmTransform();c.confirmPublicActivation();}
}
