package dev.sgs.core.engine.skill.skills;
import dev.sgs.core.card.*;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;
public final class DuanLiangSkill implements SkillListener {
  private final SgsPlayer owner;
  public DuanLiangSkill(SgsPlayer owner){this.owner=owner;}
  public String id(){return "duanliang";}public SgsPlayer owner(){return owner;}
  public Timing timing(){return Timing.CARD_TRANSFORM;}public int priority(){return Priority.PASSIVE_RESPONSE;}
  public boolean applies(SkillContext c){
    if(c.actor()!=owner || !owner.alive() || c.promptType()!=null || c.declaredAs()!=CardType.BINGLIANG
        || c.card()==null || c.card().isVirtual() || !c.card().isBlack() || c.card().sourceCards().size()!=1)return false;
    var card=c.card().sourceCards().getFirst();var zone=c.state().cards().locate(card.instanceId());
    return (card.type().category()==CardCategory.BASIC || card.type().isEquipment())
        && (CardZone.hand(owner.seat()).equals(zone)||CardZone.equipment(owner.seat()).equals(zone));
  }
  public void execute(SkillContext c){c.confirmTransform();c.confirmPublicActivation();}
  public RuleModifier distanceRule(){return new RuleModifier(){
    public String id(){return "duanliang";}public SgsPlayer owner(){return owner;}
    public RuleId rule(){return RuleId.TRICK_DISTANCE_LIMIT;}public int priority(){return Priority.RULE_MODIFIER;}
    public boolean applies(SkillContext c){return c.actor()==owner && owner.alive() && c.card()!=null && c.card().type()==CardType.BINGLIANG;}
    public int modifyInt(SkillContext c,int value){return Math.max(2,value);}
  };}
}
