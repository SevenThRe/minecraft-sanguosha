package dev.sgs.validation.client;
import dev.sgs.core.Phase;
import dev.sgs.core.card.CardCategory;
import dev.sgs.core.card.CardType;
import dev.sgs.core.engine.PromptType;
import dev.sgs.core.match.dto.MatchViews.SeatView;
import dev.sgs.validation.ProbeMod;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;

/** Native test driver: legal user actions, real resources and server activation receipts only. */
final class SdkPackSmokeChecks {
  static boolean resources,passed;static long acted=-1;static int frames,captureScale=1;static boolean captured;
  static int second=-1,settleFrames;static boolean ganluSent;
  static String expected(){return "yjcm2011:"+System.getProperty("sgs.sdkHero","wuguotai");}
  static int equipment(SeatView s){return (s.weapon()==null?0:1)+(s.armor()==null?0:1)+(s.horsePlus()==null?0:1)+(s.horseMinus()==null?0:1)+(s.treasure()==null?0:1);}
  static boolean tick(Minecraft client,MatchScreen screen){
    if(!Boolean.getBoolean("sgs.sdkPackSmoke"))return false;
    var v=screen.stateForSmoke();var s=v.snapshot();boolean host=System.getProperty("sgs.matchRole","").equals("host");
    if(settleFrames>0){settleFrames--;return true;}
    if(!resources){
      for(var id:List.of("lingtong","xushu","wuguotai","xusheng")){
        var location=ResourceLocation.parse("yjcm2011:textures/heroes/"+id+".png");
        try(var in=client.getResourceManager().open(location);var image=com.mojang.blaze3d.platform.NativeImage.read(in)){
          if(image.getWidth()!=1060||image.getHeight()!=1484)throw new IllegalStateException("invalid external art "+location);
          ProbeMod.LOG.info("SGS_SDK_RESOURCE_PASS location={} width={} height={}",location,image.getWidth(),image.getHeight());
        }catch(java.io.IOException error){throw new IllegalStateException("external resource missing",error);}
      }resources=true;
    }
    if(s.status()==0)return false;
    var me=s.seats().stream().filter(SeatView::self).findFirst().orElseThrow();
    if(host&&!expected().equals(me.heroId()))throw new IllegalStateException("desired external hero was not in the legal offer: "+me.heroId());
    if(host&&!captured){
      if(frames++==0){client.options.guiScale().set(captureScale);client.resizeDisplay();return true;}
      if(frames<5)return true;
      Screenshot.grab(client.gameDirectory,"sgs-sdk-"+System.getProperty("sgs.sdkHero")+"-gui"+captureScale+".png",client.getMainRenderTarget(),m->{});
      if(++captureScale<=3){frames=0;return true;}captured=true;client.options.guiScale().set(2);client.resizeDisplay();settleFrames=4;return true;
    }
    String required=switch(System.getProperty("sgs.sdkHero","wuguotai")){case "wuguotai"->"ganlu";case "lingtong"->"xuanfeng";case "xushu"->"jujian";default->"pojun";};
    if(host&&!passed&&s.skillActivations().stream().anyMatch(a->a.seat()==s.viewerSeat()&&a.skillId().equals("yjcm2011:"+required))){
      passed=true;ProbeMod.LOG.info("SGS_SDK_NATIVE_SKILL_PASS hero={} skill={} resourcepack=true server_activation=true generic_controls=true",me.heroId(),"yjcm2011:"+required);
      Screenshot.grab(client.gameDirectory,"sgs-sdk-"+System.getProperty("sgs.sdkHero")+"-activated.png",client.getMainRenderTarget(),m->{});
    }
    if(s.status()==2){if(host&&!passed)throw new IllegalStateException("external hero never activated");return false;}
    if(acted==v.revision())return true;
    var prompt=s.prompt();
    if(prompt!=null&&prompt.askerSeat()==s.viewerSeat()&&prompt.skillId().startsWith("yjcm2011:")){
      var type=PromptType.values()[prompt.typeOrdinal()];
      if(type==PromptType.SKILL_INVOKE)screen.clickForSmoke("invoke");
      else if(type==PromptType.EXPANSION_CHOICE||type==PromptType.EXPANSION_OPTION){
        if(!prompt.choices().isEmpty())screen.clickForSmoke("skill-choice:"+prompt.choices().getFirst().instanceId());
        if(!prompt.allowedTargetSeats().isEmpty()){
          int target=second>=0&&prompt.skillId().equals("yjcm2011:ganlu")&&prompt.allowedTargetSeats().contains(second)?second:prompt.allowedTargetSeats().getFirst();
          screen.clickForSmoke("skill-target:"+target);
        }screen.clickForSmoke("skill-choice-confirm");
      }else if(type==PromptType.CARD_ZONE_SELECT){
        if(prompt.hiddenHandAvailable())screen.pressForSmoke("zone-hand:0");
        else if(!prompt.choices().isEmpty())screen.pressForSmoke("skill-choice:"+prompt.choices().getFirst().instanceId());
        else{screen.pressForSmoke("decline");acted=v.revision();return true;}
        screen.pressForSmoke("skill-choice-confirm");
      }else return false;
      acted=v.revision();return true;
    }
    if(!host||passed||prompt!=null||s.counterspellWindow()!=null||s.currentSeat()!=s.viewerSeat()||s.phaseOrdinal()!=Phase.PLAY.ordinal())return false;
    if(me.heroId().equals("yjcm2011:wuguotai")&&!ganluSent){
      for(var first:s.seats())for(var other:s.seats())if(first.alive()&&other.alive()&&first.seat()!=other.seat()
          &&equipment(first)+equipment(other)>0&&Math.abs(equipment(first)-equipment(other))<=me.maxHp()-me.hp()){
        second=other.seat();screen.selectForSmoke(List.of(),first.seat());screen.pressForSmoke("yjcm2011:ganlu");ganluSent=true;acted=v.revision();return true;
      }
    }
    if(me.heroId().equals("yjcm2011:xushu")&&me.hand().stream().anyMatch(c->CardType.values()[c.typeOrdinal()].category()!=CardCategory.BASIC)
        ||me.heroId().equals("yjcm2011:lingtong")&&me.handCount()>=Math.max(0,me.hp())+2){
      screen.pressForSmoke("end");acted=v.revision();return true;
    }
    return false;
  }
}
