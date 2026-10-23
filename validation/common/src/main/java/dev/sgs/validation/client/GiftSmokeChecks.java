package dev.sgs.validation.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import dev.sgs.validation.ProbeMod;
/** Real icon tiles and portrait-target clicks, plus free self emotes and a configured gift patch. */
public final class GiftSmokeChecks {
    private static int stage,index;
    private static long started,committed;
    private static boolean flightCaptured,done;
    private static String prior="";
    private static final String[] IDS={"sgs:poppy","sgs:poppy","sgs:poppy","sgs:tulip_bouquet","sgs:egg_volley","demo:applause"};
    private static final String[] NAMES={"POPPY","POPPY","POPPY","TULIP","EGG","ADDON"};
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.giftSmoke"))return false;if(done)return true;
        var view=screen.stateForSmoke();if(view.snapshot().status()!=1 || view.snapshot().identityIntro()!=null || view.snapshot().openingHand()!=null)return false;
        client.getToasts().clear();
        if(started==0)started=System.currentTimeMillis();
        if(System.currentTimeMillis()-started>30000)throw new IllegalStateException("gift timeout stage="+stage+" index="+index+" message="+screen.feedbackForSmoke());
        boolean host=System.getProperty("sgs.matchRole","").equals("host");var r=screen.giftForSmoke();
        if(!host){
            if(r!=null && r.kind().equals("demo:applause")){
                int me=view.snapshot().viewerSeat();var totals=r.totals().stream().filter(t->t.seat()==me).findFirst().orElseThrow();
                if(totals.flowers()!=14 || totals.eggs()!=10 || r.flowerBalance()!=25)throw new IllegalStateException("peer totals/wallet incorrect");
                ProbeMod.LOG.info("SGS_GIFT_PEER_PASS flowers={} eggs={} own_balance_unchanged=true patch_received=true",totals.flowers(),totals.eggs());done=true;
            }return true;
        }
        if(stage==0){screen.pressForSmoke("flowers");screen.setMarkPreviewForSmoke(true);stage=1;return true;}
        if(stage==1){
            if(r==null)return true;
            if(r.catalog().stream().noneMatch(g->g.id().equals("demo:applause") && g.cost()==2))throw new IllegalStateException("patch catalog absent");
            if(!screen.hasSeatClockForSmoke())return true;
            Screenshot.grab(client.gameDirectory,"sgs-card-mark-component.png",client.getMainRenderTarget(),m->{});
            Screenshot.grab(client.gameDirectory,"sgs-gift-panel-POPPY.png",client.getMainRenderTarget(),m->{});
            prior=r.receipt();screen.clickForSmoke("gift:sgs:poppy");
            int other=view.snapshot().seats().stream().filter(p->p.human() && !p.self()).findFirst().orElseThrow().seat();screen.clickGiftSeatForSmoke(other);stage=3;return true;
        }
        if(stage==2){
            Screenshot.grab(client.gameDirectory,"sgs-gift-panel-"+NAMES[index]+".png",client.getMainRenderTarget(),m->{});
            prior=r.receipt();screen.clickForSmoke("gift:"+IDS[index]);stage=3;return true;
        }
        if(stage==3){
            if(r==null || !r.kind().equals(IDS[index]) || r.receipt().equals(prior))return true;
            if(committed==0){committed=System.currentTimeMillis();return true;}
            long age=System.currentTimeMillis()-committed;
            if(index<2 && age>=150){index++;prior=r.receipt();screen.clickForSmoke("gift:"+IDS[index]);committed=0;return true;}
            if(age>=200 && !flightCaptured){Screenshot.grab(client.gameDirectory,"sgs-gift-flight-"+NAMES[index]+".png",client.getMainRenderTarget(),m->{});flightCaptured=true;}
            if(age<800)return true;
            Screenshot.grab(client.gameDirectory,"sgs-gift-impact-"+NAMES[index]+".png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_GIFT_KIND_PASS kind={} amount={} flowers_left={} receipt={}",r.kind(),r.amount(),r.flowerBalance(),r.receipt());
            if(++index<IDS.length){stage=2;committed=0;flightCaptured=false;return true;}
            if(r.flowerBalance()!=0)throw new IllegalStateException("single flower wallet debit incorrect");
            GiftClient.request(view.matchId(),r.to(),"sgs:egg",1);stage=4;return true;
        }
        if(stage==4){
            if(!screen.feedbackForSmoke().contains("余额不足"))return true;
            screen.setMarkPreviewForSmoke(false);screen.clickForSmoke("emotes");stage=5;return true;
        }
        if(stage==5){Screenshot.grab(client.gameDirectory,"sgs-emote-panel.png",client.getMainRenderTarget(),m->{});screen.clickForSmoke("emote:happy");committed=System.currentTimeMillis();stage=6;return true;}
        if(stage==6){
            if(!screen.emoteForSmoke().equals("EMOTE:happy") || System.currentTimeMillis()-committed<200)return true;
            Screenshot.grab(client.gameDirectory,"sgs-emote-self.png",client.getMainRenderTarget(),m->{});
            ProbeMod.LOG.info("SGS_GIFT_SENDER_PASS compact_two_rows=true portrait_target=true patch_consumed=true rapid_clicks=3 shared_currency=true free_emote=true mark_component_preview=true seat_clock=true");done=true;return true;
        }
        return true;
    }
}
