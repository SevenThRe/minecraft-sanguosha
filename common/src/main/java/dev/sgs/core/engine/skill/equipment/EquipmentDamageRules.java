package dev.sgs.core.engine.skill.equipment;

import dev.sgs.core.card.*;
import dev.sgs.core.engine.DamageNature;
import dev.sgs.core.engine.skill.*;
import dev.sgs.core.player.SgsPlayer;

/** Shared direct-damage timing rules; chain propagation carries the already modified amount. */
public final class EquipmentDamageRules implements SkillListener {
  private final Timing timing;
  public EquipmentDamageRules(Timing timing){this.timing=timing;}
  public String id(){return "equipment_damage_"+timing.name().toLowerCase(java.util.Locale.ROOT);}
  public SgsPlayer owner(){return null;}
  public Timing timing(){return timing;}
  public int priority(){return Priority.EQUIPMENT;}
  public boolean applies(SkillContext ctx){return ctx.target()!=null && ctx.target().alive() && ctx.damage()>0;}
  public static boolean attack(GameCard card){return card!=null && (card.type()==CardType.SHA || card.type()==CardType.HUOSHA || card.type()==CardType.LEISHA);}
  public static boolean ignoresArmor(SkillContext ctx){
    var weapon=ctx.source()==null?null:ctx.source().equipment().weapon();
    return !ctx.chainDamage() && attack(ctx.card()) && weapon!=null && weapon.type()==CardType.QINGGANG;
  }
  public void execute(SkillContext ctx){
    if(timing==Timing.DAMAGE_CAUSE_BEFORE){
      var weapon=ctx.source()==null?null:ctx.source().equipment().weapon();
      if(!ctx.chainDamage() && attack(ctx.card()) && weapon!=null && weapon.type()==CardType.GUDING && ctx.target().handCount()==0){
        ctx.setDamage(ctx.damage()+1);ctx.state().publishSkillActivation(ctx.source().seat(),"guding");ctx.state().log("seat {} guding adds 1 damage",ctx.source().seat());
      }
    }else if(!ignoresArmor(ctx)){
      var armor=ctx.target().equipment().armor();if(armor==null)return;
      if(armor.type()==CardType.TENGJIA && ctx.nature()==DamageNature.FIRE){
        ctx.setDamage(ctx.damage()+1);ctx.state().log("seat {} tengjia adds 1 fire damage",ctx.target().seat());
      }else if(armor.type()==CardType.BAIYIN && ctx.damage()>1){
        ctx.setDamage(1);ctx.state().log("seat {} baiyin caps damage at 1",ctx.target().seat());
      }
    }
  }
}
