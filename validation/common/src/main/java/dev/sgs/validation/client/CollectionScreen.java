package dev.sgs.validation.client;

import static dev.sgs.validation.client.ClientText.tr;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.sgs.validation.collection.CollectionNetwork;
import dev.sgs.validation.client.art.*;
import dev.sgs.validation.client.widget.UnifiedButton;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The approved native collection design. Animation only presents confirmed server draws. */
public final class CollectionScreen extends Screen {
    private enum Page { RECRUIT, MARKET, OWNED, RESULTS }
    private final Screen previous;
    private CollectionNetwork.State state;
    private Page page=Page.RECRUIT;
    private int ox,oy,scroll,filter,ownership;
    private String group="全部武将",query="";
    private Integer smokePointerCell;
    private int renderedCurrencyCell=-1;

    private float scale;
    private boolean skip,wasPlaying;
    private long lastReceipt;
    private final RecruitmentTimeline animation=new RecruitmentTimeline();
    private final RecruitmentFilm film=new RecruitmentFilm();
    private List<dev.sgs.core.collection.CollectionDatabase.Draw> results=List.of();
    private String error="",testHover="";
    private CollectionNetwork.Hero hoverHero;
    private long hoverUntil;
    private int skillScroll,derivedScroll;
    private double pointerX,pointerY;
    private Button drawButton,tenButton;
    private final Map<String,Button> buyButtons=new LinkedHashMap<>();
    private int gridTop(){return page==Page.OWNED?100:56;}
    private int gridBottom(){return page==Page.OWNED?395:432;}
    private int rowHeight(){return page==Page.OWNED?148:192;}
    private int cardWidth(){return page==Page.OWNED?102:174;}
    private int cardHeight(){return page==Page.OWNED?118:156;}
    private int columns(){return page==Page.OWNED?5:3;}
    private List<String> groups(){var result=new ArrayList<String>();result.add("全部武将");if(state!=null)state.heroes().stream().map(h->state.heroGroups().getOrDefault(h.id(),"其他武将")).distinct().forEach(result::add);return result;}
    private long ownedIn(String category){return state==null?0:state.heroes().stream().filter(h->category.equals("全部武将")||state.heroGroups().getOrDefault(h.id(),"其他武将").equals(category)).filter(CollectionNetwork.Hero::owned).count();}
    private long totalIn(String category){return state==null?0:state.heroes().stream().filter(h->category.equals("全部武将")||state.heroGroups().getOrDefault(h.id(),"其他武将").equals(category)).count();}
    private long drawCost(int count){return Math.multiplyExact(count,state==null?1:state.drawTokenCost());}
    private dev.sgs.core.collection.CollectionPolicy.Currency money(int cell){return state==null?(cell==0?dev.sgs.core.collection.CollectionPolicy.DEFAULT_TOKEN:dev.sgs.core.collection.CollectionPolicy.DEFAULT_SOUL):(cell==0?state.tokenCurrency():state.soulCurrency());}

    public CollectionScreen(Screen previous){super(Component.literal(tr("sgs.ui.hero_recruitment_d9f375c0")));this.previous=previous;state=CollectionClient.state();lastReceipt=state==null?-1:state.request();}
    public void update(CollectionNetwork.State next){
        state=next;error="";
        if(next.request()>lastReceipt && next.draws()!=null && !next.draws().isEmpty()){
            lastReceipt=next.request();results=List.copyOf(next.draws());page=Page.RESULTS;
            animation.start(next.request(),results.size(),Util.getMillis(),skip,film.duration()+600);wasPlaying=animation.playing(Util.getMillis());
        }
        rebuildWidgets();
    }
    public void error(String message){error=ClientText.serverMessage(message);rebuildWidgets();}
    public void showOwned(){page=Page.OWNED;hoverHero=null;scroll=0;filter=0;group="全部武将";rebuildWidgets();}
    public void showMarket(){page=Page.MARKET;hoverHero=null;scroll=0;filter=0;rebuildWidgets();}
    private void recruit(){page=Page.RECRUIT;testHover="";rebuildWidgets();}
    private List<CollectionNetwork.Hero> heroes(){
        if(state==null)return List.of();
        String search=query.toLowerCase(java.util.Locale.ROOT);
        return state.heroes().stream()
            .filter(h->page!=Page.MARKET || h.forSale())
            .filter(h->filter==0 || h.kingdom().equals(new String[]{"","WEI","SHU","WU","QUN"}[filter]))
            .filter(h->page!=Page.OWNED || (
                (group.equals("全部武将") || group.equals(state.heroGroups().get(h.id())))
                && (ownership==0 || h.owned()==(ownership==1))
                && (query.isBlank() || h.name().contains(query)
                    || ClientText.heroName(h.id(),h.name()).toLowerCase(java.util.Locale.ROOT).contains(search)
                    || h.id().contains(search))))
            .map(ClientText::collectionHero).toList();
    }
    private int px(int x){return ox+Math.round(x*scale);}private int py(int y){return oy+Math.round(y*scale);}
    private Button button(String text,int x,int y,int w,int h,boolean primary,Runnable action){return addRenderableWidget(new UnifiedButton(px(x),py(y),Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),text,primary,action));}
    private boolean canDraw(int n){return state!=null && state.enabled() && state.tokens()>=drawCost(n) && !CollectionClient.pending();}
    private boolean playing(){return page==Page.RESULTS && animation.playing(Util.getMillis());}
    private void skipAnimation(){animation.skip();wasPlaying=false;rebuildWidgets();}
    private int gridX(int index){return (page==Page.OWNED?133:170)+index%columns()*(page==Page.OWNED?127:201);}
    private int gridY(int index){return gridTop()+index/columns()*rowHeight()-scroll;}
    @Override protected void init(){
        scale=Math.min(width/800f,height/450f);ox=(width-Math.round(800*scale))/2;oy=(height-Math.round(450*scale))/2;buyButtons.clear();
        button("‹",8,7,43,28,false,()->{if(playing())skipAnimation();else if(page==Page.OWNED)onClose();else if(page==Page.RECRUIT)showOwned();else recruit();});
        if(page==Page.RECRUIT){
            drawButton=button(tr("sgs.ui.recruit_once_190bd06e"),193,369,188,48,true,()->CollectionClient.send("draw",""));drawButton.active=canDraw(1);
            tenButton=button(tr("sgs.ui.recruit_10_35c137d5"),402,369,188,48,true,()->CollectionClient.send("draw10",""));tenButton.active=canDraw(10);
            String reason=state==null?tr("sgs.ui.loading_account_95f386d5"):!state.enabled()?tr("sgs.ui.recruitment_is_disabled_on_this_server_342670ca"):CollectionClient.pending()?tr("sgs.ui.waiting_for_server_confirmation_3a9f258b"):ClientText.defaultCurrency(money(0).name())+tr("sgs.ui.insufficient_8533f5f6");
            drawButton.setTooltip(Tooltip.create(Component.literal(drawButton.active?tr("sgs.ui.cost_1969a146")+drawCost(1)+" "+ClientText.defaultCurrency(money(0).name()):reason)));
            tenButton.setTooltip(Tooltip.create(Component.literal(tenButton.active?tr("sgs.ui.cost_1969a146")+drawCost(10)+" "+ClientText.defaultCurrency(money(0).name()):reason)));
            button(skip?tr("sgs.ui.skip_animation_on_e7c19143"):tr("sgs.ui.skip_animation_c22ce40a"),613,405,92,25,false,()->{skip=!skip;rebuildWidgets();});
            var earning=button(tr("sgs.ui.how_to_earn_5425bbc6"),18,337,112,28,false,()->CollectionClient.send("refresh",""));
            earning.setTooltip(Tooltip.create(Component.literal(state==null?tr("sgs.ui.loading_62e51561"):String.join("\n",state.earningHints()))));
            button(tr("sgs.ui.hero_shop_94ed7a98"),698,337,88,58,true,this::showMarket);
            button(tr("sgs.ui.hero_collection_20e4ce48"),18,402,112,28,false,this::showOwned);
        }else if(page==Page.RESULTS){
            button(playing()?tr("sgs.ui.skip_animation_c22ce40a"):tr("sgs.ui.back_to_recruitment_6be10336"),310,403,180,32,true,()->{if(playing())skipAnimation();else recruit();});
        }else{
            if(page==Page.OWNED){
                var tabs=groups();for(int i=0;i<tabs.size();i++){String tab=tabs.get(i);button(ClientText.category(tab),8,54+i*53,105,35,group.equals(tab),()->{group=tab;scroll=0;hoverHero=null;rebuildWidgets();});}
                button(new String[]{tr("sgs.ui.any_ownership_1a4c26d9"),tr("sgs.ui.owned_a2a9f7d9"),tr("sgs.ui.not_owned_6ef9d8a7")}[ownership],133,52,90,24,false,()->{ownership=(ownership+1)%3;scroll=0;hoverHero=null;rebuildWidgets();});
                for(int i=0;i<5;i++){final int n=i;button(ClientText.category(new String[]{"全部","魏","蜀","吴","群"}[i]),232+i*39,52,36,24,filter==i,()->{filter=n;scroll=0;hoverHero=null;rebuildWidgets();});}
                var search=new net.minecraft.client.gui.components.EditBox(font,px(610),py(53),Math.round(174*scale),Math.round(22*scale),Component.literal(tr("sgs.ui.search_heroes_d00097b7")));
                search.setMaxLength(64);search.setValue(query);search.setHint(InkText.component(tr("sgs.ui.search_heroes_d00097b7")));
                search.setResponder(value->{query=value;scroll=0;hoverHero=null;});addRenderableWidget(search);
                button(tr("sgs.ui.recruit_heroes_75480f8c"),653,408,130,32,true,this::recruit);
            }else for(int i=0;i<5;i++){final int n=i;button(ClientText.category(new String[]{"全部","魏","蜀","吴","群"}[i]),18,83+i*63,124,46,filter==i,()->{filter=n;scroll=0;rebuildWidgets();});}
            var list=heroes();scroll=Math.max(0,Math.min(scroll,Math.max(0,((list.size()+columns()-1)/columns())*rowHeight()-(gridBottom()-gridTop()))));
            for(int i=0;i<list.size();i++){
                var hero=list.get(i);int x=gridX(i),y=gridY(i);
                if(y<gridTop() || y+cardHeight()+27>gridBottom())continue;
                if(page==Page.MARKET){
                    var b=button(hero.owned()?tr("sgs.ui.owned_a2a9f7d9"):Long.toString(hero.price()),x,y+cardHeight(), cardWidth(),27,!hero.owned(),()->CollectionClient.send("buy",hero.id()));
                    buyButtons.put(hero.id(),b);b.active=state.enabled() && state.market() && !hero.owned() && state.souls()>=hero.price() && !CollectionClient.pending();
                    String reason=hero.owned()?tr("sgs.ui.you_already_own_this_hero_fb706fa6"):!state.enabled() || !state.market()?tr("sgs.ui.purchases_are_disabled_on_this_server_2dbb489b"):CollectionClient.pending()?tr("sgs.ui.waiting_for_server_confirmation_3a9f258b"):state.souls()<hero.price()?ClientText.defaultCurrency(money(1).name())+tr("sgs.ui.insufficient_8533f5f6"):tr("sgs.ui.redeem_469a2407")+hero.name();
                    b.setTooltip(Tooltip.create(Component.literal(reason)));
                }
            }
            if(page==Page.MARKET)button(tr("sgs.ui.recruit_heroes_75480f8c"),18,402,124,28,false,this::recruit);
        }
    }
    @Override public void tick(){if(wasPlaying && !playing()){wasPlaying=false;rebuildWidgets();}}
    private void title(GuiGraphics g,String text,int x,int y,int w,int h,int color){UnifiedSkin.label(g,font,text,x,y,w,h,color);}
    private void currency(GuiGraphics g,int cell,int x,int y,int size){
        var spec=money(cell);var texture=spec.texture().isEmpty()?null:ResourceLocation.tryParse(spec.texture());
        if(texture!=null && minecraft.getResourceManager().getResource(texture).isPresent()){UnifiedSkin.art(g,texture,x,y,size,size);return;}
        var item=spec.item().isEmpty()?null:ResourceLocation.tryParse(spec.item());
        if(item!=null){var found=net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(item);if(found.isPresent() && found.get()!=net.minecraft.world.item.Items.AIR){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(size/16f,size/16f,1);g.renderItem(found.get().getDefaultInstance(),0,0);g.pose().popPose();return;}}

        if(cell==0)UnifiedSkin.art(g,UnifiedSkin.TALLY,x,y,size,size);
        else g.blit(dev.sgs.validation.ProbeMod.id("textures/gui/collection/currency_icons.png"),x,y,size,size,724,0,724,724,2172,724);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        if(Boolean.getBoolean("sgs.collectionGuiSmoke") && smokePointerCell!=null){mx=smokePointerCell<0?px(6):px(smokePointerCell==0?650:747);my=smokePointerCell<0?py(44):py(20);}
        renderedCurrencyCell=-1;
        g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();g.setColor(1,1,1,1);
        g.fill(0,0,width,height,0xff100e0b);g.pose().pushPose();g.pose().translate(ox,oy,0);g.pose().scale(scale,scale,1);
        if(page==Page.OWNED){
            g.fillGradient(0,0,800,450,0xff76502e,0xffe5c58d);
            g.fillGradient(0,42,116,450,0xe43a281b,0x99452e1a);
            g.fill(117,43,118,450,0x60edcc8f);
        }else UnifiedSkin.art(g,page==Page.RECRUIT || page==Page.RESULTS?UnifiedSkin.RECRUIT:UnifiedSkin.SHOP,0,0,800,450);
        if(page==Page.RECRUIT){
            long time=Util.getMillis();float bob=(float)Math.sin(time/750.0)*5;
            particles(g,time/1000f,.42f);
            g.pose().pushPose();g.pose().translate(400,207+bob,0);g.pose().mulPose(Axis.ZP.rotationDegrees(-9));
            UnifiedSkin.art(g,UnifiedSkin.TALLY,-48,-82,96,164);g.pose().popPose();
        }else if(page==Page.RESULTS)renderResults(g);else renderGrid(g);
        UnifiedSkin.header(g,font,page==Page.MARKET?tr("sgs.ui.hero_shop_94ed7a98"):page==Page.OWNED?tr("sgs.ui.heroes_6fb3f900"):page==Page.RESULTS?tr("sgs.ui.recruitment_results_0c188e09"):tr("sgs.ui.hero_recruitment_d9f375c0"),800,-1,0);
        if(state!=null){currency(g,0,616,9,23);title(g,Long.toString(state.tokens()),640,12,52,18,UnifiedSkin.CREAM);currency(g,1,702,9,23);title(g,Long.toString(state.souls()),729,12,62,18,UnifiedSkin.CREAM);}
        if(!error.isEmpty()){UnifiedSkin.panel(g,UnifiedSkin.DARK,175,337,450,30);title(g,error,185,342,430,20,0xffffc0a0);}
        else if(CollectionClient.pending()){UnifiedSkin.panel(g,UnifiedSkin.DARK,280,333,240,28);title(g,tr("sgs.ui.waiting_for_server_f9a51d77"),285,337,230,20,UnifiedSkin.CREAM);}
        g.flush();g.pose().popPose();super.render(g,mx,my,partial);
        g.pose().pushPose();g.pose().translate(ox,oy,100);g.pose().scale(scale,scale,1);
        if(page==Page.RECRUIT){
            // Costs are part of the large action buttons, below their native labels.
            currency(g,0,267,418,17);title(g,Long.toString(drawCost(1)),286,419,45,15,UnifiedSkin.CREAM);
            currency(g,0,472,418,17);title(g,Long.toString(drawCost(10)),491,419,60,15,UnifiedSkin.CREAM);
        }
        if(page==Page.MARKET){var list=heroes();for(int i=0;i<list.size();i++){int y=gridY(i);if(y>=gridTop() && y+cardHeight()+27<=gridBottom() && !list.get(i).owned())currency(g,1,gridX(i)+20,y+cardHeight()+5,17);}}
        g.pose().popPose();
        if(page==Page.MARKET || page==Page.OWNED){
            pointerX=(mx-ox)/scale;pointerY=(my-oy)/scale;
            CollectionNetwork.Hero hovered=null;
            if(hoverHero!=null && insideDetails(pointerX,pointerY))hovered=hoverHero;
            else {
                var list=heroes();for(int i=0;i<list.size();i++){
                    int x=gridX(i),y=gridY(i);
                    if((pointerY>=gridTop() && pointerY<gridBottom() && pointerX>=x && pointerX<x+cardWidth() && pointerY>=y && pointerY<y+cardHeight())||list.get(i).id().equals(testHover)){hovered=list.get(i);break;}
                }
            }
            if(hovered!=null){
                if(hoverHero==null || !hovered.id().equals(hoverHero.id())){skillScroll=0;derivedScroll=0;}
                hoverHero=hovered;hoverUntil=Util.getMillis()+180;
            }else if(Util.getMillis()>hoverUntil)hoverHero=null;
            if(hoverHero!=null){g.pose().pushPose();g.pose().translate(ox,oy,400);g.pose().scale(scale,scale,1);details(g,hoverHero);g.flush();g.pose().popPose();}
        }
        if(state!=null){double lx=(mx-ox)/scale,ly=(my-oy)/scale;int cell=ly>=5&&ly<=38?(lx>=610&&lx<697?0:lx>=699&&lx<795?1:-1):-1;if(cell>=0)currencyTooltip(g,cell);}
    }

    private void currencyTooltip(GuiGraphics g,int cell){
        renderedCurrencyCell=cell;
        var spec=money(cell);int x=cell==0?530:550,y=44,w=240;
        var lines=font.split(InkText.component(ClientText.defaultCurrency(spec.description())),w-24);int h=48+lines.size()*12;
        g.pose().pushPose();g.pose().translate(ox,oy,600);g.pose().scale(scale,scale,1);
        g.fill(x,y,x+w,y+h,0xf52e231b);g.renderOutline(x,y,w,h,0xffa58a57);
        title(g,ClientText.defaultCurrency(spec.name()),x+10,y+6,w-20,19,0xffffdc76);
        int line=y+31;for(var text:lines){g.drawString(font,text,x+12,line,UnifiedSkin.CREAM,false);line+=12;}
        title(g,tr("sgs.ui.balance_66eb44e1")+(cell==0?state.tokens():state.souls()),x+12,line+3,w-24,12,UnifiedSkin.CREAM);
        g.pose().popPose();
    }
    private int faction(String k){try{return FactionStyle.of(dev.sgs.core.Kingdom.valueOf(k).ordinal()).main();}catch(IllegalArgumentException ex){return FactionStyle.of(-1).main();}}
    private String factionName(String k){return switch(k){case "WEI"->ClientText.category("魏");case "SHU"->ClientText.category("蜀");case "WU"->ClientText.category("吴");default->ClientText.category("群");};}
    private ResourceLocation portrait(CollectionNetwork.Hero h){var custom=h.portrait().isEmpty()?null:ResourceLocation.tryParse(h.portrait());var standard=HeroRenderer.classic(h.id());return custom!=null?custom:standard!=null?standard:HeroRenderer.classic("liubei");}
    private void card(GuiGraphics g,CollectionNetwork.Hero h,int x,int y,int w,int ht){
        UnifiedSkin.panel(g,UnifiedSkin.PAPER,x-3,y-3,w+6,ht+6);
        HeroRenderer.portraitCrop(g,page==Page.OWNED && state.enabled() && !h.owned()?HeroRenderer.grayscale(portrait(h)):portrait(h),x+3,y+3,w-6,ht-6);
        int strip=Math.max(22,w/6);UnifiedSkin.panel(g,UnifiedSkin.PAPER,x+3,y+3,strip,ht-6);
        g.fill(x+7,y+8,x+strip-1,y+35,faction(h.kingdom()));title(g,factionName(h.kingdom()),x+7,y+12,strip-8,20,UnifiedSkin.CREAM);
        int line=Math.min(20,Math.max(8,(ht-48)/Math.max(1,h.name().length())));
        if(ClientText.english()){g.fill(x+3,y+ht-24,x+w-3,y+ht-3,0xdd30251b);title(g,h.name(),x+5,y+ht-22,w-10,18,UnifiedSkin.CREAM);}
        else for(int i=0;i<h.name().length();i++)title(g,h.name().substring(i,i+1),x+6,y+42+i*line,strip-6,line,UnifiedSkin.INK);
    }
    private void renderGrid(GuiGraphics g){
        g.enableScissor(px(page==Page.OWNED?123:157),py(gridTop()-3),px(787),py(gridBottom()));
        var list=heroes();for(int i=0;i<list.size();i++){int x=gridX(i),y=gridY(i);if(y+cardHeight()+27<gridTop() || y>gridBottom())continue;var h=list.get(i);card(g,h,x,y,cardWidth(),cardHeight());
            if(page==Page.OWNED){UnifiedSkin.panel(g,UnifiedSkin.DARK,x,y+cardHeight(),cardWidth(),27);title(g,state.enabled()?(h.owned()?tr("sgs.ui.owned_a2a9f7d9"):tr("sgs.ui.not_owned_6ef9d8a7")):tr("sgs.ui.free_selection_6b42047c"),x,y+cardHeight()+5,cardWidth(),17,UnifiedSkin.CREAM);}}
        g.disableScissor();
        if(page==Page.OWNED){
            title(g,ClientText.category(group)+"  "+ownedIn(group)+"/"+totalIn(group),280,80,290,17,UnifiedSkin.CREAM);
            var tabs=groups();for(int i=0;i<tabs.size();i++)title(g,ownedIn(tabs.get(i))+"/"+totalIn(tabs.get(i)),8,90+i*53,105,13,UnifiedSkin.GOLD);
        }
        if(list.isEmpty())title(g,tr("sgs.ui.no_heroes_64d9cd74"),300,206,350,24,UnifiedSkin.CREAM);
        if(list.size()>columns()*2){int total=((list.size()+columns()-1)/columns())*rowHeight(),available=gridBottom()-gridTop();int thumb=Math.max(24,available*available/total);int y=gridTop()+scroll*(available-thumb)/Math.max(1,total-available);g.fill(785,gridTop(),787,gridBottom(),0x80543820);g.fill(784,y,788,y+thumb,UnifiedSkin.GOLD);}
    }
    private CollectionNetwork.Hero resultHero(int i){return state==null?null:state.heroes().stream().filter(h->h.id().equals(results.get(i).hero())).map(ClientText::collectionHero).findFirst().orElse(null);}
    private void particles(GuiGraphics g,float time,float strength){
        for(int i=0;i<38;i++){
            double a=i*2.399+time*.7;float life=(time*.55f+i*.137f)%1f;
            int x=400+(int)(Math.cos(a)*(45+life*125)),y=285-(int)(life*190),size=i%4==0?4:2;
            int alpha=(int)((1-life)*180*strength);g.fill(x,y,x+size,y+size,(alpha<<24)|0xffce70);
        }
    }
    private void renderResults(GuiGraphics g){
        long now=Util.getMillis(),ms=animation.elapsed(now);boolean moving=animation.playing(now);
        if(results.isEmpty())return;
        long intro=film.duration();
        if(moving && ms<intro+600){
            if(film.available())film.render(g,Math.min(ms,intro),0,0,800,450);
            if(ms>=intro){
                float t=(ms-intro)/600f,flip=Math.max(.025f,Math.abs((float)Math.cos(t*Math.PI)));
                g.pose().pushPose();g.pose().translate(400,213,0);g.pose().scale(flip,1,1);
                if(t<.5)CardBackRenderer.draw(g,-71,-110,142,220);else{var h=resultHero(0);if(h!=null)card(g,h,-71,-110,142,220);}
                g.pose().popPose();
            }
            return;
        }
        g.fill(140,57,780,392,0x61150e08);
        int count=results.size(),cols=count==1?1:5,w=count==1?172:112,h=count==1?248:139;
        int start=count==1?314:91;int revealed=moving?animation.revealed(now):count;
        for(int i=0;i<count;i++){
            int x=start+i%cols*125,y=count==1?93:66+i/cols*161;
            if(i>=revealed){CardBackRenderer.draw(g,x,y,w,h);continue;}
            var hero=resultHero(i);if(hero==null)continue;card(g,hero,x,y,w,h);
            var draw=results.get(i);UnifiedSkin.panel(g,UnifiedSkin.DARK,x,y+h,w,20);
            title(g,draw.duplicate()?ClientText.defaultCurrency(money(1).name())+" +"+draw.soulsAward():tr("sgs.ui.new_hero_d81e92dd"),x,y+h+3,w,14,draw.duplicate()?UnifiedSkin.CREAM:0xffffd57b);
        }
    }
    private boolean insideDetails(double x,double y){
        if(hoverHero==null)return false;
        int left=hoverHero.derivedSkills().isEmpty()?116:28;
        if(y<84)return false;
        if(x>=left && x<left+170 && y<398)return true;
        int mainWidth=hoverHero.derivedSkills().isEmpty()?418:340;
        int mh=Math.min(314,Math.max(154,83+skillHeight(hoverHero.skills(),mainWidth)));
        if(x>=left+170 && x<left+170+mainWidth && y<84+mh)return true;
        if(!hoverHero.derivedSkills().isEmpty()){
            int dh=Math.min(314,Math.max(130,67+skillHeight(hoverHero.derivedSkills(),240)));
            return x>=546 && x<786 && y<84+dh;
        }
        return false;
    }
    private void detailPanel(GuiGraphics g,int x,int y,int w,int h){
        g.flush();g.setColor(1,1,1,.97f);
        BoardSkin.nine(g,dev.sgs.validation.ProbeMod.id("textures/gui/unified/detail_panel.png"),x,y,w,h,1024,1024,20);
        g.setColor(1,1,1,1);
    }
    private String kind(dev.sgs.core.addon.SkillPresentation skill){
        if(skill.lordOnly())return tr("sgs.ui.lord_70368eaf");
        if(java.util.Set.of("zaoxian","zhiji","ruoyu","hunzi").contains(skill.id()) || skill.description().startsWith("觉醒技"))return tr("sgs.ui.awakening_8760bc1c");
        return switch(skill.kind()){case ACTIVE,TOGGLE->tr("sgs.ui.active_3ba4d7bd");case TRIGGERED->tr("sgs.ui.triggered_27eccf97");case PASSIVE->tr("sgs.ui.locked_0b707d6d");case LIMITED->tr("sgs.ui.limited_70c31580");};
    }
    private int skillHeight(List<dev.sgs.core.addon.SkillPresentation> skills,int width){
        int height=0;for(var skill:skills)height+=29+font.split(InkText.component(skill.description()),width-24).size()*14;
        return height;
    }
    private void skillColumn(GuiGraphics g,List<dev.sgs.core.addon.SkillPresentation> skills,int x,int y,int w,int h,boolean derived){
        int content=skillHeight(skills,w),offset=derived?derivedScroll:skillScroll;
        offset=Math.clamp(offset,0,Math.max(0,content-h));if(derived)derivedScroll=offset;else skillScroll=offset;
        g.enableScissor(px(x+5),py(y),px(x+w-5),py(y+h));int line=y-offset;
        for(var skill:skills){
            int tag=ClientText.english()?Math.min(150,w-90):54;
            g.fill(x+12,line,x+14+tag,line+19,0xff4f5b38);title(g,skill.name(),x+13,line+2,tag,15,0xfff5e4b1);
            title(g,kind(skill),x+22+tag,line+3,ClientText.english()?60:40,13,0xffd8aa6c);line+=24;
            for(var part:font.split(InkText.component(skill.description()),w-24)){g.drawString(font,part,x+12,line,UnifiedSkin.CREAM,false);line+=14;}
            line+=5;
        }
        g.flush();g.disableScissor();
        if(content>h){int thumb=Math.max(18,h*h/content),top=y+offset*(h-thumb)/Math.max(1,content-h);g.fill(x+w-5,top,x+w-3,top+thumb,UnifiedSkin.GOLD);}
    }
    private void details(GuiGraphics g,CollectionNetwork.Hero hero){
        boolean derived=!hero.derivedSkills().isEmpty();int left=derived?28:116,top=84,portraitWidth=170,mainWidth=derived?340:418,height=314;
        int mainHeight=Math.min(height,Math.max(154,83+skillHeight(hero.skills(),mainWidth)));
        detailPanel(g,left,top,portraitWidth,height);
        HeroRenderer.portraitCrop(g,portrait(hero),left+4,top+4,portraitWidth-4,height-8);
        int x=left+portraitWidth;
        detailPanel(g,x,top,mainWidth,mainHeight);
        title(g,hero.name()+" · "+factionName(hero.kingdom()),x+12,top+13,mainWidth-24,24,UnifiedSkin.CREAM);
        title(g,hero.maxHp()+tr("sgs.ui.hp_4785e78d"),x+12,top+43,mainWidth-24,15,UnifiedSkin.GOLD);
        skillColumn(g,hero.skills(),x,top+69,mainWidth,mainHeight-83,false);
        if(derived){int dx=left+portraitWidth+mainWidth+8;int dh=Math.min(height,Math.max(130,67+skillHeight(hero.derivedSkills(),240)));detailPanel(g,dx,top,240,dh);title(g,tr("sgs.ui.derived_skills_2d70460d"),dx+12,top+14,216,23,UnifiedSkin.GOLD);skillColumn(g,hero.derivedSkills(),dx,top+53,240,dh-67,true);}
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if((page==Page.MARKET || page==Page.OWNED) && insideDetails((mx-ox)/scale,(my-oy)/scale))return true;
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){
        if(page==Page.MARKET || page==Page.OWNED){
            double lx=(mx-ox)/scale,ly=(my-oy)/scale;
            if(insideDetails(lx,ly)){
                int delta=Math.round((float)dy*36);
                if(!hoverHero.derivedSkills().isEmpty() && lx>=546)derivedScroll=Math.max(0,derivedScroll-delta);else skillScroll=Math.max(0,skillScroll-delta);
                return true;
            }
            hoverHero=null;scroll-=Math.round((float)dy*rowHeight());testHover="";rebuildWidgets();return true;
        }
        return super.mouseScrolled(mx,my,dx,dy);
    }
    @Override public void onClose(){if(playing()){skipAnimation();return;}minecraft.setScreen(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    void smokeOwned(){showOwned();smokeCurrency(-1);}
    void smokeCurrency(int cell){if(Boolean.getBoolean("sgs.collectionGuiSmoke")){smokePointerCell=cell;smokeClearHover();}}
    boolean smokeCurrencyVisible(int cell){return renderedCurrencyCell==cell;}
    void smokeDraw(){if(!drawButton.active)throw new IllegalStateException("draw unavailable");drawButton.onPress();}
    void smokeTen(){recruit();if(!tenButton.active)throw new IllegalStateException("ten unavailable");tenButton.onPress();}
    void smokeRecruit(){recruit();}void smokeMarket(){showMarket();}
    void smokeLocalizationHover(){testHover=heroes().getFirst().id();}
    void smokeHover(){if(Boolean.getBoolean("sgs.collectionGuiSmoke")){hoverHero=null;testHover=heroes().get(0).id();}}
    void smokeDerivedHover(){if(Boolean.getBoolean("sgs.collectionGuiSmoke")){hoverHero=null;testHover=heroes().stream().filter(h->h.skills().stream().anyMatch(k->k.id().equals("hunzi"))).findFirst().orElseThrow().id();}}
    boolean smokeHasDerived(){return hoverHero!=null && !hoverHero.derivedSkills().isEmpty();}
    void smokeClearHover(){testHover="";hoverHero=null;hoverUntil=0;}void smokeScroll(){hoverHero=null;scroll=rowHeight();rebuildWidgets();}
    boolean smokeAnimating(){return playing();}
    long smokeElapsed(){return animation.elapsed(Util.getMillis());}
    long smokeFilmDuration(){return film.duration();}
    boolean smokeFilmAvailable(){return film.available();}
    void smokeSkip(){skipAnimation();}
    String smokeBuy(){for(var e:buyButtons.entrySet())if(e.getValue().active){e.getValue().onPress();return e.getKey();}throw new IllegalStateException("no market button");}
    String smokeBuyCandidate(){return buyButtons.entrySet().stream().filter(e->e.getValue().active).map(Map.Entry::getKey).findFirst().orElseThrow();}
}
