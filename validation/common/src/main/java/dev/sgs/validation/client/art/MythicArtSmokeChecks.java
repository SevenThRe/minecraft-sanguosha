package dev.sgs.validation.client.art;

import dev.sgs.core.hero.StandardHeroes;
import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Native resource inspection only: never fabricates a match, prompt, or multiplayer acceptance. */
public final class MythicArtSmokeChecks {
  private static boolean started,done;private static int page,scale=1,frames;
  private static final Set<String> seen=new HashSet<>();private static final AtomicInteger pending=new AtomicInteger();
  public static void initialize(){if(Boolean.getBoolean("sgs.mythicArtSmoke"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,MythicArtSmokeChecks::tick);}
  private static void tick(Minecraft client){
    if(done)return;
    if(client.getOverlay()!=null)return;
    try{
      if(!started){if(!(client.screen instanceof TitleScreen))return;started=true;client.getWindow().setWindowed(1672,941);client.options.guiScale().set(scale);client.resizeDisplay();client.setScreen(new ArtGalleryScreen(client.screen));((ArtGalleryScreen)client.screen).showStandardHeroPage(0);client.getToasts().clear();return;}
      if(!(client.screen instanceof ArtGalleryScreen screen))throw new IllegalStateException("native gallery closed unexpectedly");
      if(page==screen.heroPageCount()){
        if(pending.get()!=0)return;
        if(!seen.equals(new HashSet<>(StandardHeroes.CATALOG.stream().map(h->h.id()).toList())))throw new IllegalStateException("native hero coverage mismatch");
        ProbeMod.LOG.info("SGS_MYTHIC_ART_SCALE_PASS gui={} heroes={} pages={} native_resources=true non_match=true",scale,seen.size(),page);
        if(++scale>3){done=true;ProbeMod.LOG.info("SGS_MYTHIC_ART_PASS heroes={} scales=1,2,3 captures={} non_match=true",seen.size(),screen.heroPageCount()*3);client.stop();return;}
        page=0;frames=0;seen.clear();client.options.guiScale().set(scale);client.resizeDisplay();screen.showStandardHeroPage(0);return;
      }
      if(++frames<10)return;
      if(!screen.standardHeroResourcesPresent()||!screen.contentFits())throw new IllegalStateException("native resource/layout missing at page "+page+" scale "+scale+" resources="+screen.standardHeroResourcesPresent()+" fits="+screen.contentFits()+" size="+screen.width+"x"+screen.height);
      for(var id:screen.shownStandardHeroIds())if(!seen.add(id))throw new IllegalStateException("duplicate native hero "+id);
      String name="sgs-mythic-art-gui"+scale+"-page"+(page+1)+".png";pending.incrementAndGet();
      Screenshot.grab(client.gameDirectory,name,client.getMainRenderTarget(),message->pending.decrementAndGet());
      ProbeMod.LOG.info("SGS_MYTHIC_ART_PAGE_PASS gui={} page={} heroes={} file={} native_resources=true non_match=true",scale,page+1,String.join(",",screen.shownStandardHeroIds()),name);
      page++;frames=0;if(page<screen.heroPageCount())screen.showStandardHeroPage(page);
    }catch(RuntimeException failure){done=true;ProbeMod.LOG.error("SGS_MYTHIC_ART_FAIL",failure);client.stop();}
  }
  private MythicArtSmokeChecks(){}
}
