package dev.sgs.validation.client;

import dev.sgs.core.Phase;
import dev.sgs.core.engine.PromptType;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.match.MatchNetwork;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Real native mouse handlers, local drafts, one authoritative order submission. Opt-in only. */
final class GuanxingRowsSmokeChecks {
    static boolean enabled(){return Boolean.getBoolean("sgs.guanxingRowsSmoke");}
    private static int stage,frames;private static long prompt=-1;private static List<Long> original=List.of();
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("guanxing rows: "+why);}
    private static Path directory(){return Path.of(System.getProperty("sgs.turnEndEvidence"));}
    private static String role(){return System.getProperty("sgs.matchRole","");}
    private static void capture(Minecraft c,MatchScreen screen,String name){try{
        Files.createDirectories(directory());Files.writeString(directory().resolve(role()+"-"+name+".json"),MatchNetwork.JSON.toJson(screen.stateForSmoke()));
        Screenshot.grab(c.gameDirectory,"sgs-guanxing-"+name+".png",c.getMainRenderTarget(),m->{});
    }catch(Exception e){throw new IllegalStateException(e);}}
    private static void drag(MatchScreen s,long id,int[] destination){
        var from=s.privateDeckCardForSmoke(id);double x=from[0]+from[2]/2d,y=from[1]+from[3]/2d;
        need(s.mouseClicked(x,y,0),"card hit failed");need(s.mouseDragged(destination[0]+destination[2]-4,destination[1]+destination[3]/2d,0,20,20),"drag failed");
        need(s.mouseReleased(destination[0]+destination[2]-4,destination[1]+destination[3]/2d,0),"drop failed");
    }
    static boolean tick(Minecraft c,MatchScreen s){
        if(!enabled())return false;var v=s.stateForSmoke().snapshot();if(v.status()!=1||v.openingHand()!=null)return false;
        var p=v.prompt();boolean host=role().equals("host");
        need(v.seats().stream().filter(seat->!seat.self()).allMatch(seat->seat.hand().isEmpty()),"hand leak");
        if(!host&&p!=null)need(p.choices().isEmpty(),"private order leak");
        if(++frames<4)return true;frames=0;
        if(stage==0){if(p==null||!p.skillId().equals("guanxing"))return true;capture(c,s,"invoke");
            if(host){if(p.typeOrdinal()!=PromptType.SKILL_INVOKE.ordinal())return true;s.clickForSmoke("invoke");}stage=1;return true;}
        if(stage==1){if(p==null||p.typeOrdinal()!=PromptType.GUANXING_ORDER.ordinal())return true;
            if(host){prompt=p.promptId();original=p.choices().stream().map(card->card.instanceId()).toList();
                int alive=(int)v.seats().stream().filter(seat->seat.alive()).count();need(original.size()==Math.min(5,alive),"skill count changed");
                need(s.privateDeckOrderForSmoke().top().equals(original)&&s.privateDeckOrderForSmoke().bottom().isEmpty(),"not all cards initially on top");
                c.options.guiScale().set(1);c.resizeDisplay();stage=2;
            }else{capture(c,s,"private-peer");stage=20;}return true;
        }
        if(host&&stage>=2&&stage<=4){
            need(p!=null&&p.promptId()==prompt,"local resize submitted input");capture(c,s,"default-gui"+(stage-1));
            int next=stage==2?2:stage==3?3:2;c.options.guiScale().set(next);c.resizeDisplay();stage++;return true;
        }
        if(host&&stage==5){
            int nonce=MatchClient.nonceForSmoke();
            s.clickForSmoke("autoplay");
            s.clickForSmoke("report");
            need(MatchClient.nonceForSmoke()==nonce,"background control received modal click");
            for(long id:original)drag(s,id,s.privateDeckLaneForSmoke(1));
            need(s.privateDeckOrderForSmoke().top().isEmpty()&&s.privateDeckOrderForSmoke().bottom().equals(original),"all-bottom arrangement failed");
            for(long id:original)drag(s,id,s.privateDeckLaneForSmoke(0));
            need(s.privateDeckOrderForSmoke().top().equals(original)&&s.privateDeckOrderForSmoke().bottom().isEmpty(),"all-top arrangement failed");
            drag(s,original.getLast(),s.privateDeckLaneForSmoke(1));drag(s,original.getFirst(),s.privateDeckLaneForSmoke(0));
            need(MatchClient.nonceForSmoke()==nonce,"local drag sent input");
            var expected=new ArrayList<>(original.subList(1,original.size()-1));expected.add(original.getFirst());
            need(s.privateDeckOrderForSmoke().top().equals(expected)&&s.privateDeckOrderForSmoke().bottom().equals(List.of(original.getLast())),"drag order differs");
            stage=6;return true;
        }
        if(host&&stage==6){
            capture(c,s,"arranged");int nonce=MatchClient.nonceForSmoke();
            try{Files.writeString(directory().resolve("host-local-order.json"),MatchNetwork.JSON.toJson(s.privateDeckOrderForSmoke()));}catch(Exception e){throw new IllegalStateException(e);}
            s.clickForSmoke("skill-choice-confirm");need(MatchClient.nonceForSmoke()==nonce+1,"confirmation did not submit once");stage=20;return true;
        }
        if(stage==20&&p==null&&v.phaseOrdinal()==Phase.PLAY.ordinal()){
            capture(c,s,"accepted");ProbeMod.LOG.info("SGS_GUANXING_ROWS_PASS role={} actual_atomic_order=true local_drag_no_nonce=true",role());
            CreativeTabSmokeChecks.start();stage=99;
        }return true;
    }
}
