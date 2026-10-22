package dev.sgs.validation.client;

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
    private int ox,oy,scroll,filter;
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
    private static final int GRID_TOP=56,GRID_BOTTOM=432,ROW=192,CARD_W=174,CARD_H=156;
    public CollectionScreen(Screen previous){super(Component.literal("将星招募"));this.previous=previous;state=CollectionClient.state();lastReceipt=state==null?-1:state.request();}
    public void update(CollectionNetwork.State next){
        state=next;error="";
        if(next.request()>lastReceipt && next.draws()!=null && !next.draws().isEmpty()){
            lastReceipt=next.request();results=List.copyOf(next.draws());page=Page.RESULTS;
            animation.start(next.request(),results.size(),Util.getMillis(),skip,film.duration()+600);wasPlaying=animation.playing(Util.getMillis());
        }
        rebuildWidgets();
    }
    public void error(String message){error=message;rebuildWidgets();}
    public void showOwned(){page=Page.OWNED;hoverHero=null;scroll=0;filter=0;rebuildWidgets();}
    public void showMarket(){page=Page.MARKET;hoverHero=null;scroll=0;filter=0;rebuildWidgets();}
    private void recruit(){page=Page.RECRUIT;testHover="";rebuildWidgets();}
    private List<CollectionNetwork.Hero> heroes(){return state==null?List.of():state.heroes().stream().filter(h->page!=Page.MARKET || h.forSale()).filter(h->filter==0 || h.kingdom().equals(new String[]{"","WEI","SHU","WU","QUN"}[filter])).toList();}
    private int px(int x){return ox+Math.round(x*scale);}private int py(int y){return oy+Math.round(y*scale);}
    private Button button(String text,int x,int y,int w,int h,boolean primary,Runnable action){return addRenderableWidget(new UnifiedButton(px(x),py(y),Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),text,primary,action));}
    private boolean canDraw(int n){return state!=null && state.enabled() && state.tokens()>=n && !CollectionClient.pending();}
    private boolean playing(){return page==Page.RESULTS && animation.playing(Util.getMillis());}
    private void skipAnimation(){animation.skip();wasPlaying=false;rebuildWidgets();}
    private int gridX(int index){return 170+index%3*201;}
    private int gridY(int index){return GRID_TOP+index/3*ROW-scroll;}
    @Override protected void init(){
        scale=Math.min(width/800f,height/450f);ox=(width-Math.round(800*scale))/2;oy=(height-Math.round(450*scale))/2;buyButtons.clear();
        button("‹",8,7,43,28,false,()->{if(playing())skipAnimation();else if(page==Page.RECRUIT)onClose();else recruit();});
        if(page==Page.RECRUIT){
            drawButton=button("招募一次",193,369,188,48,true,()->CollectionClient.send("draw",""));drawButton.active=canDraw(1);
            tenButton=button("招募十次",402,369,188,48,true,()->CollectionClient.send("draw10",""));tenButton.active=canDraw(10);
            String reason=state==null?"正在读取账户":!state.enabled()?"服务器未启用招募":CollectionClient.pending()?"等待服务器确认":"招募令不足";
            drawButton.setTooltip(Tooltip.create(Component.literal(drawButton.active?"消耗 1 枚招募令":reason)));
            tenButton.setTooltip(Tooltip.create(Component.literal(tenButton.active?"消耗 10 枚招募令":reason)));
            button(skip?"跳过动画：开":"跳过动画",641,388,143,28,false,()->{skip=!skip;rebuildWidgets();});
        }else if(page==Page.RESULTS){
            button(playing()?"跳过动画":"返回招募",310,403,180,32,true,()->{if(playing())skipAnimation();else recruit();});
        }else{
            for(int i=0;i<5;i++){final int n=i;button(new String[]{"全部","魏","蜀","吴","群"}[i],18,83+i*63,124,46,filter==i,()->{filter=n;scroll=0;rebuildWidgets();});}
            var list=heroes();scroll=Math.max(0,Math.min(scroll,Math.max(0,((list.size()+2)/3)*ROW-(GRID_BOTTOM-GRID_TOP))));
            for(int i=0;i<list.size();i++){
                var hero=list.get(i);int x=gridX(i),y=gridY(i);
                if(y<GRID_TOP || y+CARD_H+27>GRID_BOTTOM)continue;
                if(page==Page.MARKET){
                    var b=button(hero.owned()?"已拥有":Long.toString(hero.price()),x,y+CARD_H, CARD_W,27,!hero.owned(),()->CollectionClient.send("buy",hero.id()));
                    buyButtons.put(hero.id(),b);b.active=state.enabled() && state.market() && !hero.owned() && state.souls()>=hero.price() && !CollectionClient.pending();
                    String reason=hero.owned()?"已拥有此武将":!state.enabled() || !state.market()?"服务器未开放购买":CollectionClient.pending()?"等待服务器确认":state.souls()<hero.price()?"将符不足":"兑换 "+hero.name();
                    b.setTooltip(Tooltip.create(Component.literal(reason)));
                }
            }
            button(page==Page.MARKET?"我的武将":"将符商店",18,402,124,28,false,()->{if(page==Page.MARKET)showOwned();else showMarket();});
        }
    }
    @Override public void tick(){if(wasPlaying && !playing()){wasPlaying=false;rebuildWidgets();}}
    private void title(GuiGraphics g,String text,int x,int y,int w,int h,int color){UnifiedSkin.label(g,font,text,x,y,w,h,color);}
    private void currency(GuiGraphics g,int cell,int x,int y,int size){
        if(cell==0)UnifiedSkin.art(g,UnifiedSkin.TALLY,x,y,size,size);
        else g.blit(dev.sgs.validation.ProbeMod.id("textures/gui/collection/currency_icons.png"),x,y,size,size,724,0,724,724,2172,724);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();g.setColor(1,1,1,1);
        g.fill(0,0,width,height,0xff100e0b);g.pose().pushPose();g.pose().translate(ox,oy,0);g.pose().scale(scale,scale,1);
        UnifiedSkin.art(g,page==Page.RECRUIT || page==Page.RESULTS?UnifiedSkin.RECRUIT:UnifiedSkin.SHOP,0,0,800,450);
        if(page==Page.RECRUIT){
            long time=Util.getMillis();float bob=(float)Math.sin(time/750.0)*5;
            particles(g,time/1000f,.42f);
            g.pose().pushPose();g.pose().translate(400,207+bob,0);g.pose().mulPose(Axis.ZP.rotationDegrees(-9));
            UnifiedSkin.art(g,UnifiedSkin.TALLY,-48,-82,96,164);g.pose().popPose();
        }else if(page==Page.RESULTS)renderResults(g);else renderGrid(g);
        UnifiedSkin.header(g,font,page==Page.MARKET?"将符商店":page==Page.OWNED?"我的武将":page==Page.RESULTS?"招募所得":"将星招募",800,state==null?-1:state.tokens(),state==null?0:state.souls());
        if(!error.isEmpty()){UnifiedSkin.panel(g,UnifiedSkin.DARK,175,337,450,30);title(g,error,185,342,430,20,0xffffc0a0);}
        else if(CollectionClient.pending()){UnifiedSkin.panel(g,UnifiedSkin.DARK,280,333,240,28);title(g,"等待服务器确认…",285,337,230,20,UnifiedSkin.CREAM);}
        g.flush();g.pose().popPose();super.render(g,mx,my,partial);
        g.pose().pushPose();g.pose().translate(ox,oy,100);g.pose().scale(scale,scale,1);
        if(page==Page.RECRUIT){
            // Costs are part of the large action buttons, below their native labels.
            currency(g,0,267,418,17);title(g,"1",286,419,18,15,UnifiedSkin.CREAM);
            currency(g,0,472,418,17);title(g,"10",491,419,27,15,UnifiedSkin.CREAM);
        }
        if(page==Page.MARKET){var list=heroes();for(int i=0;i<list.size();i++){int y=gridY(i);if(y>=GRID_TOP && y+CARD_H+27<=GRID_BOTTOM && !list.get(i).owned())currency(g,1,gridX(i)+20,y+CARD_H+5,17);}}
        g.pose().popPose();
        if(page==Page.MARKET || page==Page.OWNED){
            pointerX=(mx-ox)/scale;pointerY=(my-oy)/scale;
            CollectionNetwork.Hero hovered=null;
            if(hoverHero!=null && insideDetails(pointerX,pointerY))hovered=hoverHero;
            else {
                var list=heroes();for(int i=0;i<list.size();i++){
                    int x=gridX(i),y=gridY(i);
                    if((pointerY>=GRID_TOP && pointerY<GRID_BOTTOM && pointerX>=x && pointerX<x+CARD_W && pointerY>=y && pointerY<y+CARD_H)||list.get(i).id().equals(testHover)){hovered=list.get(i);break;}
                }
            }
            if(hovered!=null){
                if(hoverHero==null || !hovered.id().equals(hoverHero.id())){skillScroll=0;derivedScroll=0;}
                hoverHero=hovered;hoverUntil=Util.getMillis()+180;
            }else if(Util.getMillis()>hoverUntil)hoverHero=null;
            if(hoverHero!=null){g.pose().pushPose();g.pose().translate(ox,oy,400);g.pose().scale(scale,scale,1);details(g,hoverHero);g.flush();g.pose().popPose();}
        }
    }

    private int faction(String k){try{return FactionStyle.of(dev.sgs.core.Kingdom.valueOf(k).ordinal()).main();}catch(IllegalArgumentException ex){return FactionStyle.of(-1).main();}}
    private String factionName(String k){return switch(k){case "WEI"->"魏";case "SHU"->"蜀";case "WU"->"吴";default->"群";};}
    private ResourceLocation portrait(CollectionNetwork.Hero h){var custom=h.portrait().isEmpty()?null:ResourceLocation.tryParse(h.portrait());var standard=HeroRenderer.classic(h.id());return custom!=null?custom:standard!=null?standard:HeroRenderer.classic("liubei");}
    private void card(GuiGraphics g,CollectionNetwork.Hero h,int x,int y,int w,int ht){
        UnifiedSkin.panel(g,UnifiedSkin.PAPER,x-3,y-3,w+6,ht+6);
        HeroRenderer.portraitCrop(g,portrait(h),x+3,y+3,w-6,ht-6);
        int strip=Math.max(22,w/6);UnifiedSkin.panel(g,UnifiedSkin.PAPER,x+3,y+3,strip,ht-6);
        g.fill(x+7,y+8,x+strip-1,y+35,faction(h.kingdom()));title(g,factionName(h.kingdom()),x+7,y+12,strip-8,20,UnifiedSkin.CREAM);
        for(int i=0;i<h.name().length();i++)title(g,h.name().substring(i,i+1),x+6,y+44+i*21,strip-6,20,UnifiedSkin.INK);
    }
    private void renderGrid(GuiGraphics g){
        g.enableScissor(px(157),py(GRID_TOP-3),px(787),py(GRID_BOTTOM));
        var list=heroes();for(int i=0;i<list.size();i++){int x=gridX(i),y=gridY(i);if(y+CARD_H+27<GRID_TOP || y>GRID_BOTTOM)continue;var h=list.get(i);card(g,h,x,y,CARD_W,CARD_H);
            if(page==Page.OWNED){UnifiedSkin.panel(g,UnifiedSkin.DARK,x,y+CARD_H,CARD_W,27);title(g,state.enabled()?(h.owned()?"已拥有":"未拥有"):"自由选将",x,y+CARD_H+5,CARD_W,17,UnifiedSkin.CREAM);}}
        g.disableScissor();
        if(list.isEmpty())title(g,"暂无武将",300,206,350,24,UnifiedSkin.CREAM);
        if(list.size()>6){int total=((list.size()+2)/3)*ROW,available=GRID_BOTTOM-GRID_TOP;int thumb=Math.max(24,available*available/total);int y=GRID_TOP+scroll*(available-thumb)/Math.max(1,total-available);g.fill(785,GRID_TOP,787,GRID_BOTTOM,0x80543820);g.fill(784,y,788,y+thumb,UnifiedSkin.GOLD);}
    }
    private CollectionNetwork.Hero resultHero(int i){return state==null?null:state.heroes().stream().filter(h->h.id().equals(results.get(i).hero())).findFirst().orElse(null);}
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
            title(g,draw.duplicate()?"将符 +"+draw.soulsAward():"新武将",x,y+h+3,w,14,draw.duplicate()?UnifiedSkin.CREAM:0xffffd57b);
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
        if(skill.lordOnly())return "主公";
        if(skill.description().startsWith("觉醒技"))return "觉醒";
        return switch(skill.kind()){case ACTIVE,TOGGLE->"主动";case TRIGGERED->"触发";case PASSIVE->"锁定";case LIMITED->"限定";};
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
            g.fill(x+12,line,x+68,line+19,0xff4f5b38);title(g,skill.name(),x+13,line+2,54,15,0xfff5e4b1);
            title(g,kind(skill),x+76,line+3,40,13,0xffd8aa6c);line+=24;
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
        title(g,hero.maxHp()+" 体力",x+12,top+43,mainWidth-24,15,UnifiedSkin.GOLD);
        skillColumn(g,hero.skills(),x,top+69,mainWidth,mainHeight-83,false);
        if(derived){int dx=left+portraitWidth+mainWidth+8;int dh=Math.min(height,Math.max(130,67+skillHeight(hero.derivedSkills(),240)));detailPanel(g,dx,top,240,dh);title(g,"衍生技",dx+12,top+14,216,23,UnifiedSkin.GOLD);skillColumn(g,hero.derivedSkills(),dx,top+53,240,dh-67,true);}
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
            hoverHero=null;scroll-=Math.round((float)dy*ROW);testHover="";rebuildWidgets();return true;
        }
        return super.mouseScrolled(mx,my,dx,dy);
    }
    @Override public void onClose(){if(playing()){skipAnimation();return;}minecraft.setScreen(previous);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}














}
