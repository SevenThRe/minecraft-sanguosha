package dev.sgs.validation.client;

import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.match.*;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.collection.CollectionNetwork;
import dev.sgs.validation.lobby.LobbyNetwork;
import dev.sgs.validation.match.MatchNetwork;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;

/** Opt-in language reload and native UI fixture. Uses no game/server connection. */
final class EnglishLocalizationChecks {
    private static final List<String> IDS=List.of("liubei","caocao","huangyueying","sunshangxiang","zhangzhaozhanghong","wolong");
    private static final List<dev.sgs.core.addon.HeroPresentation> RAW=HeroCatalog.builtins().present(IDS);
    private static final String[] LANGS={"en_us","zh_cn","en_us"};
    private static int round,stage,frames,total,captures;
    private static boolean done;
    private static CompletableFuture<Void> loading;
    static void initialize(){
        if(Boolean.getBoolean("sgs.englishLocalization"))Balm.getEvents().onTickEvent(TickType.Client,TickPhase.End,EnglishLocalizationChecks::tick);
    }
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static boolean cjk(String value){return value.codePoints().anyMatch(c->c>=0x4e00&&c<=0x9fff);}
    private static void capture(Minecraft client,String page){
        if(ClientText.english())for(var child:client.screen.children())if(child instanceof AbstractWidget widget)
            require(!cjk(widget.getMessage().getString()),"untranslated widget: "+widget.getMessage().getString());
        captures++;
        Screenshot.grab(client.gameDirectory,"localization-"+LANGS[round]+"-gui"+(round==2?3:2)+"-"+page+".png",client.getMainRenderTarget(),m->{});
    }
    private static LobbyScreen lobby(){
        var catalog=HeroCatalog.builtins();
        var heroes=catalog.present(IDS).stream().map(h->new LobbyNetwork.HeroView(h.id(),h.name(),"蜀",h.portrait(),h.description(),h.skills())).toList();
        var screen=new LobbyScreen();Minecraft.getInstance().setScreen(screen);
        screen.update(new LobbyNetwork.View(1,0,true,"",-1,"",0,0,false,List.of(),0,"LanguageQA",List.of(),-1,List.of(),heroes,0,0,List.of("six","eight","two_v_two"),true,List.of()));
        return screen;
    }
    private static void match(Minecraft client){
        var players=new ArrayList<MatchPlayer>();for(int i=0;i<4;i++)players.add(new MatchPlayer(new UUID(9264,i),"Player"+(i+1),i,true));
        var snapshot=ViewFilter.selecting(players,true,0,IDS,System.currentTimeMillis()+300000).withHeroPresentations(HeroCatalog.builtins().present(IDS));
        client.setScreen(new MatchScreen(new MatchNetwork.View(9264,1,snapshot)));
    }
    private static void collection(Minecraft client){
        var catalog=HeroCatalog.builtins();
        var heroes=catalog.present(IDS).stream().map(h->new CollectionNetwork.Hero(h.id(),h.name(),true,100,true,"SHU",4,h.portrait(),h.description(),h.skills())).toList();
        var screen=new CollectionScreen(null);client.setScreen(screen);
        screen.update(new CollectionNetwork.State(0,false,true,true,10,100,10,Set.of(),heroes,"",List.of(),null,null,1,Map.of()));
    }
    private static void verifyCatalog(){
        boolean english=ClientText.english();var catalog=HeroCatalog.builtins();
        for(var original:catalog.present(catalog.heroes().stream().map(h->h.id()).toList())){
            var localized=ClientText.hero(original);
            require(localized.id().equals(original.id()),"translated rule ID");
            require(english?!cjk(localized.name()):localized.name().equals(original.name()),"hero name: "+original.id());
            for(var skill:localized.skills())require(english?!cjk(skill.name()+skill.description()):cjk(skill.description()),"skill: "+skill.id());
        }
        for(var hero:ClientText.heroes(RAW))require(!english||!cjk(hero.name()),"stale cached language");
        for(var card:dev.sgs.validation.client.art.CardArtCatalog.load(Minecraft.getInstance().getResourceManager()))require(!english||!cjk(card.name()),"untranslated card: "+card.id());
        require(ClientText.serverMessage("房间密码错误").equals(english?"Incorrect room password.":"房间密码错误"),"server error localization");
        require(ClientText.serverMessage("custom message").equals("custom message"),"changed custom message");
        require(ClientText.heroName("addon:custom","自定义武将").equals("自定义武将"),"changed unknown addon name");
        var raw="第1回合 · 1号「刘备」使用了【{card:huosha}】♥7";
        require(JournalText.involves(raw,0)&&!JournalText.involves(raw,1),"log seat filtering changed");
        var names=JournalText.render(raw,id->ClientText.tr("sgs.card."+id),id->ClientText.skillName(id,id)).getString();
        require(!english||!cjk(names),"log not localized: "+names);
        if(english)for(String event:List.of(
            "1号「刘备」的回合开始", "1号「刘备」发动了技能【仁德】", "1号「刘备」摸了2张牌",
            "1号「刘备」对2号「曹操」结算【{card:sha}】", "1号「刘备」从2号「曹操」的手牌区获得了2张牌",
            "1号「刘备」将【{card:lebu}】置入2号「曹操」的判定区", "1号「刘备」受到来自2号「曹操」的2点伤害，剩余体力1",
            "1号「刘备」失去1点体力，剩余体力0", "1号「刘备」回复1点体力，当前体力1",
            "1号「刘备」将上述牌当作【{card:shan}】打出", "1号「刘备」因乐不思蜀跳过出牌阶段")){
            String translated=JournalText.render("第1回合 · "+event,id->ClientText.tr("sgs.card."+id),id->ClientText.skillName(id,id)).getString();
            require(!cjk(translated),"untranslated event: "+translated);
        }
        var classic=dev.sgs.validation.client.art.HeroRenderer.classic("liubei");
        for(var skin:dev.sgs.validation.client.art.HeroSkins.available("liubei",classic))require(!english||!cjk(skin.name()),"skin name not localized");
    }
    private static void tick(Minecraft client){
        if(done)return;
        try{
            if(++total>3600)throw new IllegalStateException("localization timeout stage="+stage);
            if(client.getOverlay()!=null||client.screen==null)return;
            if(stage==0){
                client.options.guiScale().set(round==2?3:2);client.resizeDisplay();
                client.getLanguageManager().setSelected(LANGS[round]);client.options.languageCode=LANGS[round];
                loading=client.reloadResourcePacks();stage=1;frames=0;return;
            }
            if(stage==1){if(!loading.isDone())return;loading.join();verifyCatalog();lobby();stage=2;frames=0;return;}
            if(++frames<20)return;frames=0;
            switch(stage){
                case 2->{capture(client,"home");((LobbyScreen)client.screen).smokeAction("open_rooms");stage=3;}
                case 3->{capture(client,"modes");((LobbyScreen)client.screen).smokePress("open_create");stage=4;}
                case 4->{capture(client,"settings");match(client);stage=5;}
                case 5->{capture(client,"heroes");collection(client);stage=6;}
                case 6->{capture(client,"recruit");((CollectionScreen)client.screen).showOwned();stage=7;}
                case 7->{capture(client,"collection");((CollectionScreen)client.screen).smokeLocalizationHover();stage=8;}
                case 8->{capture(client,"skills");ProbeMod.LOG.info("SGS_LOCALIZATION_ROUND_PASS lang={} gui={}",LANGS[round],round==2?3:2);if(++round<LANGS.length){stage=0;return;}done=true;ProbeMod.LOG.info("SGS_LOCALIZATION_PASS captures={} languages=en_us,zh_cn gui=2,3 live_server=false",captures);client.stop();}
                default->throw new IllegalStateException("unknown stage");
            }
        }catch(Exception|AssertionError error){done=true;ProbeMod.LOG.error("SGS_LOCALIZATION_FAIL",error);client.stop();}
    }
}
