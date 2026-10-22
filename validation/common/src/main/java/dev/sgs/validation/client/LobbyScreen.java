package dev.sgs.validation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.google.gson.Gson;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.client.art.CardRenderer;
import dev.sgs.validation.client.art.HeroRenderer;
import dev.sgs.validation.client.widget.UnifiedButton;
import dev.sgs.validation.client.art.UnifiedSkin;
import dev.sgs.validation.lobby.LobbyNetwork;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native lobby flow. All room state and all gameplay actions are server authoritative. */
public final class LobbyScreen extends Screen {
    private enum Page { HOME, MODES, ROOMS, ROOM }
    private enum Modal { NONE, SETTINGS, BANS, PASSWORD, INVITE, CHAT }
    private static final Gson JSON=new Gson();
    private static final int GOLD=0xffe9c584, INK=0xff51371e;
    private static final int MODAL_PAPER_Z=300, MODAL_WIDGET_Z=320, MODAL_FRONT_Z=330;
    private LobbyNetwork.View view;
    private Page page=Page.HOME;
    private Modal modal=Modal.NONE;
    private String mode="eight",search="",heroSearch="",appliedSearch="",appliedHeroSearch="",faction="全部",chatInput="",passwordInput="";
    private int roomPage,heroPage,invitePage,seatMenu=-1;
    private long pending,targetRoom;
    private boolean editing,hideFull;
    private float scale=1;
    private int ox,oy;
    private Draft draft=new Draft();
    private final ArrayList<Button> actions=new ArrayList<>();
    private final Map<String,Button> buttonsById=new HashMap<>();
    private final Map<String,EditBox> fields=new HashMap<>();
    private String localReason="",lastAction="",passwordAction="join";
    private boolean classicHover;
    private static final class Draft {
        String name="军争演武",password="";
        int turn=15,response=15,selection=30;
        boolean chat=true,spectators=false,doubleRenegade=false,fixed=false,clearPassword=false;
        LinkedHashSet<String> bans=new LinkedHashSet<>();
    }
    public LobbyScreen(){super(Component.literal("三国杀 · 大厅"));}
    public void update(LobbyNetwork.View next){
        if(view!=null && next.revision()<view.revision())return;
        long before=view==null?-1:view.ownRoom();
        boolean receipt=pending!=0 && next.request()==pending;
        boolean roomChanged=before!=next.ownRoom();
        String focusedField=null;int cursor=0;
        for(var entry:fields.entrySet())if(entry.getValue().isFocused()){focusedField=entry.getKey();cursor=entry.getValue().getCursorPosition();break;}
        view=next;
        if(receipt){pending=0;if(next.success()){localReason="";if(lastAction.equals("chat"))chatInput="";if(modal==Modal.SETTINGS || modal==Modal.PASSWORD)modal=Modal.NONE;}}
        if(next.ownRoom()>=0){page=Page.ROOM;if(roomChanged){seatMenu=-1;modal=Modal.NONE;}}
        else if(before>=0){page=Page.ROOMS;modal=Modal.NONE;seatMenu=-1;}
        // Unsolicited room broadcasts must not dismiss or steal focus from a local draft.
        if(!receipt && !roomChanged && (modal==Modal.SETTINGS || modal==Modal.BANS || modal==Modal.PASSWORD))return;
        rebuildWidgets();
        if(focusedField!=null){var field=fields.get(focusedField);if(field!=null){setFocused(field);field.setFocused(true);field.moveCursorTo(Math.min(cursor,field.getValue().length()),false);}}
    }
    private int px(int n){return ox+Math.round(n*scale);} private int py(int n){return oy+Math.round(n*scale);}
    private int dim(int n){return Math.max(1,Math.round(n*scale));}
    private Button button(String id,String label,int x,int y,int w,int h,Runnable run){
        var b=addRenderableWidget(new UnifiedButton(px(x),py(y),dim(w),dim(h),label, !"browse_home".equals(id) && !"back".equals(id) && (id==null || !id.startsWith("collection")),run));
        actions.add(b);if(id!=null)buttonsById.put(id,b);
        if(view!=null && id!=null) {
            if(id.startsWith("mode:") && !enabledMode(id.substring(5)))blocked(b,"服务器已关闭此玩法");
            if((id.equals("create") || id.equals("queue")) && !enabledMode(mode))blocked(b,"服务器已关闭此玩法");
            if(id.equals("queue") && !view.matchmaking())blocked(b,"服务器已关闭快速匹配");
        }
        return b;
    }
    private Button button(String id,String label,int x,int y,int w,Runnable run){return button(id,label,x,y,w,22,run);}
    private void blocked(Button b,String reason){b.active=false;b.setTooltip(Tooltip.create(Component.literal(reason)));}
    private boolean enabledMode(String id){return view!=null && view.enabledModes()!=null && view.enabledModes().contains(id);}
    private EditBox edit(String value,String hint,int x,int y,int w,java.util.function.Consumer<String> change){
        var e=new EditBox(font,px(x),py(y),dim(w),dim(20),Component.literal(hint));e.setMaxLength(80);e.setValue(value);e.setHint(Component.literal(hint));e.setResponder(change);addRenderableWidget(e);
        String key=hint.equals("房间名称")?"name":hint.equals("房间号 / 房间名称")?"search":hint.equals("请输入武将名")?"heroSearch":hint.equals("输入房间密码")?"joinPassword":hint.equals("输入房间消息")?"chat":"password";
        if(key.equals("password") || key.equals("joinPassword"))e.setFormatter((text,position)->net.minecraft.util.FormattedCharSequence.forward("*".repeat(text.length()),net.minecraft.network.chat.Style.EMPTY));
        fields.put(key,e);return e;
    }
    private void send(String action,long room){send(action,room,"");}
    private void send(String action,long room,String data){if(view==null || pending!=0)return;if(action.equals("spectate"))MatchClient.prepareSpectating();localReason="";lastAction=action;pending=LobbyClient.request(view.revision(),action,mode,room,data);actions.forEach(b->b.active=false);}
    private void go(Page next){page=next;modal=Modal.NONE;seatMenu=-1;rebuildWidgets();}
    private LobbyNetwork.RoomView own(){return view==null?null:view.rooms().stream().filter(r->r.id()==view.ownRoom()).findFirst().orElse(null);}
    private void openSettings(boolean edit){editing=edit;draft=new Draft();var r=own();
        if(edit && r!=null){mode=r.mode();draft.name=r.name();var s=r.settings();draft.turn=s.turnSeconds();draft.response=s.responseSeconds();draft.selection=s.selectionSeconds();draft.chat=s.allowChat();draft.spectators=s.allowSpectators();draft.doubleRenegade=s.doubleRenegade();draft.fixed=s.fixedSeats();draft.bans.addAll(s.bannedHeroes());}
        modal=Modal.SETTINGS;rebuildWidgets();
    }
    private String settingsJson(){var data=new LinkedHashMap<String,Object>();data.put("name",draft.name);data.put("turnSeconds",draft.turn);data.put("responseSeconds",draft.response);data.put("selectionSeconds",draft.selection);data.put("allowChat",draft.chat);data.put("allowSpectators",draft.spectators);data.put("doubleRenegade",draft.doubleRenegade);data.put("fixedSeats",draft.fixed);data.put("bannedHeroes",draft.bans);
        if(!editing || !draft.password.isEmpty())data.put("password",draft.password);data.put("clearPassword",draft.clearPassword);return JSON.toJson(data);}
    private void submitSettings(){if(draft.name.isBlank()){localReason="请输入房间名称";return;}send(editing?"update":"create",0,settingsJson());}
    private void selectMode(String value){mode=value;roomPage=0;go(Page.ROOMS);}
    private void navigateBack(){if(modal==Modal.BANS){modal=Modal.SETTINGS;rebuildWidgets();}else if(modal!=Modal.NONE){modal=Modal.NONE;rebuildWidgets();}else if(page==Page.ROOM){var room=own();if(room!=null && room.playing())onClose();else send("leave",0);}else if(page==Page.ROOMS)go(Page.MODES);else if(page==Page.MODES)go(Page.HOME);else onClose();}
    @Override protected void init(){
        scale=Math.min(width/800f,height/450f);ox=(width-dim(800))/2;oy=(height-dim(450))/2;
        actions.clear();buttonsById.clear();fields.clear();
        if(modal!=Modal.NONE){buildModal();if(pending!=0)actions.forEach(b->b.active=false);return;}
        button("back","‹",8,7,43,28,this::navigateBack);
        if(page==Page.HOME)buildHome();else if(page==Page.MODES)buildModes();else if(page==Page.ROOMS)buildRooms();else buildRoom();
        if(view!=null && view.inviteRoom()>=0 && view.ownRoom()<0){button("accept","接受邀请 #"+view.inviteRoom(),455,12,145,()->send("accept",view.inviteRoom()));button("decline","拒绝",607,12,58,()->send("decline",view.inviteRoom()));}
        if(pending!=0)actions.forEach(b->b.active=false);
    }
    private void buildHome(){
        button("open_rooms","开始牌局",192,347,202,48,()->go(Page.MODES));
        button("browse_home","浏览房间",413,347,202,48,()->go(Page.ROOMS));
        button("collection_owned","武将",108,412,150,28,CollectionClient::openOwned);
        button("collection_recruit","招募",325,412,150,28,CollectionClient::open);
        button("collection_market","将符商店",542,412,150,28,CollectionClient::openMarket);
    }
    private void buildModes(){
        int y=83,w=170,h=248;
        modeCard("two_v_two","欢乐成双","2V2 · 欢乐休闲场","sunquan",111,y,w,h);
        modeCard("eight","身份模式","八人军争 · 六人军争","caocao",315,y,w,h);
        var unavailable=button("mode:guozhan","国战模式 · V1未开放",519,y,w,h,()->{});blocked(unavailable,"V1 尚无国战双将与势力胜负规则，暂未开放");
        button("create","创建房间",112,370,150,30,()->openSettings(false));
        button("mode:six","军六身份房",315,370,170,30,()->selectMode("six"));
        button("browse","浏览房间",539,370,150,30,()->go(Page.ROOMS));

    }
    private void modeCard(String id,String title,String subtitle,String hero,int x,int y,int w,int h){
        var b=button("mode:"+id,title,x,y,w,h,()->selectMode(id));if(b.active)b.setTooltip(Tooltip.create(Component.literal(subtitle)));
    }
    private List<LobbyNetwork.RoomView> rooms(){return view==null?List.of():view.rooms().stream().filter(r->r.mode().equals(mode)).filter(r->appliedSearch.isBlank() || r.name().toLowerCase(Locale.ROOT).contains(appliedSearch.toLowerCase(Locale.ROOT)) || Long.toString(r.id()).contains(appliedSearch)).filter(r->!hideFull || r.members()+r.ai()<r.capacity()).toList();}
    private void buildRooms(){
        button("mode:six",mode.equals("six")?"● 军六":"军六",40,54,90,()->{mode="six";roomPage=0;rebuildWidgets();});
        button("mode:eight",mode.equals("eight")?"● 军八":"军八",138,54,90,()->{mode="eight";roomPage=0;rebuildWidgets();});
        button("mode:two_v_two",mode.equals("two_v_two")?"● 2v2":"2v2",236,54,90,()->{mode="two_v_two";roomPage=0;rebuildWidgets();});
        edit(search,"房间号 / 房间名称",410,54,180,v->search=v);
        button("search","搜索",600,54,58,()->{appliedSearch=search;roomPage=0;rebuildWidgets();});button("hide_full",hideFull?"☑ 隐藏已满":"□ 隐藏已满",665,54,100,()->{hideFull=!hideFull;roomPage=0;rebuildWidgets();});
        var list=rooms();int rows=7;roomPage=Math.min(roomPage,Math.max(0,(list.size()-1)/rows));
        for(int i=roomPage*rows;i<Math.min(list.size(),(roomPage+1)*rows);i++){
            var r=list.get(i);int y=112+(i%rows)*35;
            var join=button("join:"+r.id(),r.settings().locked()?"密码加入":"加入",591,y,72,()->{if(r.settings().locked()){targetRoom=r.id();passwordAction="join";passwordInput="";modal=Modal.PASSWORD;rebuildWidgets();}else send("join",r.id());});
            if(r.playing() || r.members()+r.ai()>=r.capacity() || view==null || !view.queued().isEmpty())blocked(join,r.playing()?"对局已经开始":"房间已满或你正在匹配");
            if(!enabledMode(r.mode()))blocked(join,"服务器已关闭此玩法");
            var watch=button("spectate:"+r.id(),"旁观",674,y,70,()->{if(r.settings().locked()){targetRoom=r.id();passwordAction="spectate";passwordInput="";modal=Modal.PASSWORD;rebuildWidgets();}else send("spectate",r.id());});if(!r.settings().allowSpectators() || !r.playing())blocked(watch,"房间需允许旁观且已开始对局");
        }
        button("create","创建房间",40,385,132,30,()->openSettings(false));
        if(view!=null && !view.queued().isEmpty())button("cancel","取消匹配",187,385,132,30,()->send("cancel",0));else button("queue","快速匹配",187,385,132,30,()->send("queue",0));
        button("refresh","刷新",333,385,80,30,()->send("refresh",0));
        var prev=button("prev","‹",598,385,40,30,()->{roomPage--;rebuildWidgets();});prev.active=roomPage>0;
        var next=button("next","›",706,385,40,30,()->{roomPage++;rebuildWidgets();});next.active=(roomPage+1)*rows<list.size();
    }
    private void buildRoom(){
        var room=own();if(room==null)return;
        for(var seat:room.seats()){
            int x=265+seat.slot()%4*126,y=87+seat.slot()/4*139;
            if(seatMenu==seat.slot() && seat.kind()==0){
                button("sit:"+seat.slot(),"坐下",x+5,y+6,76,23,()->send("sit",seat.slot()));
                var ai=button("ai_add:"+seat.slot(),"添加 AI",x+5,y+35,76,23,()->send("ai_add",seat.slot()));if(!room.owner() || room.playing())blocked(ai,"只有房主可在等待中添加 AI");
                button("seat_close","关闭",x+5,y+64,76,23,()->{seatMenu=-1;rebuildWidgets();});
                var invite=button("invite","邀请",x+5,y+93,76,23,()->{modal=Modal.INVITE;rebuildWidgets();});if(!room.owner())blocked(invite,"只有房主可发出房间邀请");
            }else{
                var card=button("seat:"+seat.slot(),"",x,y,86,123,()->{if(seat.kind()==0){seatMenu=seat.slot();rebuildWidgets();}else if(seat.kind()==2 && room.owner())send("ai_remove",seat.slot());else if(seat.kind()==1 && !seat.owner() && room.owner()){targetRoom=seat.slot();modal=Modal.INVITE;rebuildWidgets();}});
                if(room.playing() || seat.kind()==1 && (seat.owner() || !room.owner()) || seat.kind()==2 && !room.owner())card.active=false;
                if(seat.kind()==2)buttonsById.put("ai_remove:"+seat.slot(),card);
                else if(seat.kind()==0)buttonsById.put("ai_add:"+seat.slot(),card);
                card.setTooltip(Tooltip.create(Component.literal(seat.kind()==0?"打开座位菜单":seat.kind()==2?"AI · 房主点击移除":seat.name()+" · "+(seat.ready()?"已准备":"未准备"))));
            }
        }
        button("chat_open","聊天",39,382,65,26,()->{modal=Modal.CHAT;rebuildWidgets();});
        var settings=button("settings","设置",112,382,65,26,()->openSettings(true));if(!room.owner() || room.playing())blocked(settings,"仅房主可在等待中修改设置");
        var clear=button("ai_clear","一键踢出 AI",347,382,125,30,()->send("ai_clear",0));if(!room.owner() || room.ai()==0 || room.playing())blocked(clear,"房主可移除所有 AI，不影响真人");
        var fill=button("ai_fill","一键添加 AI",482,382,125,30,()->send("ai_fill",0));if(!room.owner() || room.playing() || room.members()+room.ai()>=room.capacity())blocked(fill,"仅填充空位，不替换真人");
        var start=button(room.playing()?"resume":room.owner()?"start":view.ownReady()?"unready":"ready",room.playing()?"返回对局":room.owner()?"开始":view.ownReady()?"取消准备":"准备",622,382,122,30,()->{if(room.playing())MatchClient.resume();else send(room.owner()?"start":view.ownReady()?"unready":"ready",0);});
        if(!room.playing() && !enabledMode(room.mode()))blocked(start,"服务器已关闭此玩法");
        if(!room.playing() && room.owner() && (room.members()+room.ai()!=room.capacity() || room.ready()!=room.members()))blocked(start,"填满座位并等待其他真人准备后开始");
        // Keep explicit ready and leave actions for regression hooks and player control.
        String readyAction=view.ownReady()?"unready":"ready";
        if(room.owner() && !room.playing())button(readyAction,view.ownReady()?"取消准备":"准备",39,343,138,22,()->send(readyAction,0));
        buttonsById.put("leave",buttonsById.get("back"));
        if(room.mode().equals("two_v_two")){String action=room.teammateSharing()?"share_off":"share_on";var share=button(action,"队友手牌："+(room.teammateSharing()?"开":"关"),39,306,138,22,()->send(action,0));if(!room.owner())blocked(share,"由房主设置队友手牌共享");}
    }
    private void buildModal(){
        button("modal_close","×",666,52,30,this::navigateBack);
        if(modal==Modal.SETTINGS)buildSettings();else if(modal==Modal.BANS)buildBans();else if(modal==Modal.PASSWORD){edit(passwordInput,"输入房间密码",245,170,310,v->passwordInput=v);button("password_join",passwordAction.equals("spectate")?"进入旁观":"加入房间",320,250,160,()->send(passwordAction,targetRoom,passwordInput));}
        else if(modal==Modal.INVITE)buildInvites();else if(modal==Modal.CHAT)buildChat();
    }
    private void buildSettings(){
        edit(draft.name,"房间名称",128,100,168,v->draft.name=v);
        edit(draft.password,editing?"留空保留原密码":"默认无密码",405,100,175,v->draft.password=v);
        button("clear_password",draft.clearPassword?"☑ 清密码":"清除密码",589,100,85,()->{draft.clearPassword=!draft.clearPassword;rebuildWidgets();});
        button("setting_mode",modeName(mode),127,147,160,()->{if(editing){localReason="修改模式请重新创建房间";return;}mode=mode.equals("eight")?"six":mode.equals("six")?"two_v_two":"eight";if(!mode.equals("eight"))draft.doubleRenegade=false;rebuildWidgets();});
        if(editing)blocked(buttonsById.get("setting_mode"),"已有房间的模式不可修改");
        button("turn","出手 "+draft.turn+" 秒",312,147,158,()->{draft.turn=cycle(draft.turn,new int[]{10,15,20,30,60,300});rebuildWidgets();});
        button("fixed",draft.fixed?"固定座次":"随机座次",495,147,176,()->{draft.fixed=!draft.fixed;rebuildWidgets();})
            .setTooltip(Tooltip.create(Component.literal("固定：保留大厅相对座次，主公旋转至首位；身份仍由服务器随机分配")));
        button("response","响应 "+draft.response+" 秒",312,181,158,()->{draft.response=cycle(draft.response,new int[]{10,15,20,30,300});rebuildWidgets();});
        button("selection","选将 "+draft.selection+" 秒",495,181,176,()->{draft.selection=cycle(draft.selection,new int[]{15,30,45,60,300});rebuildWidgets();});
        button("allow_chat",(draft.chat?"☑":"□")+" 允许聊天",127,231,140,()->{draft.chat=!draft.chat;rebuildWidgets();});
        button("allow_spectators",(draft.spectators?"☑":"□")+" 允许旁观",282,231,140,()->{draft.spectators=!draft.spectators;rebuildWidgets();});
        var dual=button("double_renegade",(draft.doubleRenegade?"☑":"□")+" 双内奸",437,231,114,()->{draft.doubleRenegade=!draft.doubleRenegade;rebuildWidgets();});if(!mode.equals("eight"))blocked(dual,"双内奸仅八人身份模式支持");
        var expansion=button("expansion","逐鹿天下包",562,231,109,()->{});blocked(expansion,"V1 未实现该卡包，不能启用");
        for(int i=0;i<6;i++){int slot=i;var ids=new ArrayList<>(draft.bans);button("ban_slot:"+i,i<ids.size()?heroName(ids.get(i)):"＋",205+i*72,278,62,42,()->{heroPage=0;modal=Modal.BANS;rebuildWidgets();});}
        button("confirm_settings",editing?"保存房间设置":"创建房间",320,342,160,30,this::submitSettings);
    }
    private static int cycle(int value,int[] options){for(int i=0;i<options.length;i++)if(options[i]==value)return options[(i+1)%options.length];return options[0];}
    private String heroName(String id){return view==null?id:view.heroes().stream().filter(h->h.id().equals(id)).map(h->h.name()).findFirst().orElse(id);}
    private List<LobbyNetwork.HeroView> heroes(){if(view==null)return List.of();return view.heroes().stream().filter(h->faction.equals("全部") || faction.equals(h.kingdom())).filter(h->appliedHeroSearch.isBlank() || h.name().contains(appliedHeroSearch) || h.id().contains(appliedHeroSearch.toLowerCase(Locale.ROOT))).toList();}
    private void buildBans(){
        int x=122;for(String f:List.of("全部","魏","蜀","吴","群","神")){String value=f;button("faction:"+f,(f.equals(faction)?"● ":"")+f,x,87,55,()->{faction=value;heroPage=0;rebuildWidgets();});x+=59;}
        edit(heroSearch,"请输入武将名",482,87,135,v->heroSearch=v);button("hero_search","搜索",624,87,53,()->{appliedHeroSearch=heroSearch;heroPage=0;rebuildWidgets();});
        var list=heroes();heroPage=Math.min(heroPage,Math.max(0,(list.size()-1)/10));
        for(int i=heroPage*10;i<Math.min(list.size(),(heroPage+1)*10);i++){var h=list.get(i);int n=i%10;var b=button("ban:"+h.id(),(draft.bans.contains(h.id())?"✓ ":"")+h.name(),133+n%5*112,128+n/5*86,99,74,()->{if(!draft.bans.remove(h.id())){if(draft.bans.size()>=6){localReason="最多禁用六名武将";return;}draft.bans.add(h.id());}rebuildWidgets();});String skills=h.skills().stream().map(s->s.name()+"："+s.description()).collect(java.util.stream.Collectors.joining("\n"));b.setTooltip(Tooltip.create(Component.literal(h.name()+" · "+h.kingdom()+"\n"+h.description()+"\n"+skills)));}
        var prev=button("hero_prev","‹",122,310,35,()->{heroPage--;rebuildWidgets();});prev.active=heroPage>0;
        var next=button("hero_next","›",639,310,35,()->{heroPage++;rebuildWidgets();});next.active=(heroPage+1)*10<list.size();
        button("clear_bans","清空禁将",191,310,90,()->{draft.bans.clear();rebuildWidgets();});
        button("confirm_bans","确定禁用 ("+draft.bans.size()+"/6)",416,346,170,28,()->{modal=Modal.SETTINGS;rebuildWidgets();});
    }
    private List<LobbyNetwork.PlayerView> inviteCandidates(){
        if(view==null)return List.of();
        var occupied=view.rooms().stream().flatMap(r->r.seats().stream()).filter(s->s.kind()==1).map(s->s.uuid()).collect(java.util.stream.Collectors.toSet());
        String self=minecraft.player==null?"":minecraft.player.getUUID().toString();
        return view.players().stream().filter(p->!p.uuid().equals(self) && !occupied.contains(p.uuid())).toList();
    }
    private void buildInvites(){
        if(view==null)return;var players=inviteCandidates();invitePage=Math.min(invitePage,Math.max(0,(players.size()-1)/8));
        for(int i=invitePage*8;i<Math.min(players.size(),(invitePage+1)*8);i++){var p=players.get(i);int y=105+(i%8)*27;button("invite:"+p.uuid(),"邀请 "+p.name(),150,y,240,()->send("invite",0,p.uuid()));}
        var prev=button("invite_prev","‹",150,335,42,()->{invitePage--;rebuildWidgets();});prev.active=invitePage>0;
        var next=button("invite_next","›",348,335,42,()->{invitePage++;rebuildWidgets();});next.active=(invitePage+1)*8<players.size();
        var room=own();if(room!=null && room.owner()){int row2=0;for(var seat:room.seats())if(seat.kind()==1 && !seat.owner()){var s=seat;button("kick:"+s.uuid(),"移出 "+s.name(),422,105+row2++*29,230,()->send("kick",0,s.uuid()));}}
    }
    private void buildChat(){
        var room=own();boolean enabled=room!=null && room.settings().allowChat();edit(chatInput,"输入房间消息",141,322,430,v->chatInput=v);
        var send=button("chat_send","发送",582,322,74,()->{if(!chatInput.isBlank()){send("chat",0,chatInput);rebuildWidgets();}});if(!enabled)blocked(send,"房主已关闭房间聊天");
    }
    private void text(GuiGraphics g,String text,int x,int y,int color){InkText.draw(g,font,text,x,y,color);}
    private void centered(GuiGraphics g,String text,int x,int y,int color){InkText.drawCentered(g,font,text,x,y,color);}
    private void paper(GuiGraphics g,int x,int y,int w,int h){UnifiedSkin.panel(g,UnifiedSkin.PAPER,x,y,w,h);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        // Blits draw immediately while GUI fills/fonts use buffers. Finish inherited work
        // before painting this opaque screen, and don't inherit the world's depth state.
        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1,1,1,1);
        g.fill(0,0,width,height,0xff100e0b);
        g.pose().pushPose();g.pose().translate(ox,oy,0);g.pose().scale(scale,scale,1);
        UnifiedSkin.art(g,page==Page.HOME?ProbeMod.id("textures/gui/lobby/lobby_home_minecraft_v1.png"):UnifiedSkin.SHOP,0,0,800,450);
        var account=CollectionClient.state();
        UnifiedSkin.header(g,font,page==Page.HOME?"牌局大厅":page==Page.ROOM?"等待房间":page==Page.ROOMS?"房间列表":"选择玩法",800,account==null?-1:account.tokens(),account==null?0:account.souls());
        RenderSystem.enableDepthTest();
        if(page==Page.HOME)drawHome(g);else if(page==Page.MODES)drawModes(g);else if(page==Page.ROOMS)drawRooms(g);else drawRoom(g);
        // Font batches from the background must finish before the opaque modal paper.
        g.flush();
        if(modal!=Modal.NONE){g.pose().translate(0,0,MODAL_PAPER_Z);drawModal(g);}
        String reason=pending!=0?"等待服务器确认…":!localReason.isEmpty()?localReason:view==null?"正在读取服务器房间…":!view.success()?view.reason():"";
        if(!reason.isBlank()){UnifiedSkin.panel(g,UnifiedSkin.DARK,160,page==Page.HOME?303:429,480,21);text(g,font.plainSubstrByWidth(reason,460),170,page==Page.HOME?309:435,view!=null && !view.success()?0xffffa18a:GOLD);}
        g.flush();g.pose().popPose();
        g.pose().pushPose();if(modal!=Modal.NONE)g.pose().translate(0,0,MODAL_WIDGET_Z);
        super.render(g,mx,my,partial);g.flush();g.pose().popPose();
        // Card fronts paint over their hit boxes, keeping native button input and narration.
        g.pose().pushPose();g.pose().translate(ox,oy,modal==Modal.NONE?0:MODAL_FRONT_Z);g.pose().scale(scale,scale,1);
        if(modal==Modal.NONE && page==Page.ROOM)drawSeatFronts(g);
        if(modal==Modal.NONE && page==Page.MODES)drawModeFronts(g);
        if(modal==Modal.BANS)drawHeroFronts(g);
        g.flush();g.pose().popPose();
    }
    private void drawHome(GuiGraphics g){
        UnifiedSkin.art(g,UnifiedSkin.TITLE,50,67,294,98);
        UnifiedSkin.label(g,font,"与同服玩家共赴牌局",66,168,264,19,UnifiedSkin.CREAM);
        UnifiedSkin.panel(g,UnifiedSkin.WOOD,0,404,800,46);
    }
    private void drawModes(GuiGraphics g){
        text(g,"选择玩法 · 与同服玩家创建真实房间",280,28,0xffdfd3b5);
        text(g,"V1 已开放军六 / 军八 / 2v2",112,348,GOLD);
    }
    private void drawModeFronts(GuiGraphics g){
        String[] titles={"欢乐成双","身份模式","国战模式"};String[] subtitles={"2V2 欢乐休闲场","八人 / 六人军争场","V1 规则尚未实现"};String[] portraits={"sunquan","caocao","liubei"};
        for(int i=0;i<3;i++){int x=111+i*204;HeroRenderer.portrait(g,font,portraits[i],x+8,91,154,192,GOLD,true);g.fill(x+8,252,x+162,283,0xda352618);centered(g,titles[i],x+85,264,GOLD);centered(g,subtitles[i],x+85,304,i==2?0xff65523e:INK);if(i==2)g.fill(x+8,91,x+162,283,0x88595042);highlight(g,"mode:"+new String[]{"two_v_two","eight","guozhan"}[i],x,83,170,248);}
    }
    private void drawRooms(GuiGraphics g){
        paper(g,31,88,738,285);
        text(g,"房间号",47,96,INK);text(g,"房间名称",123,96,INK);text(g,"人数 / AI",371,96,INK);text(g,"状态",485,96,INK);
        var list=rooms();for(int i=roomPage*7;i<Math.min(list.size(),(roomPage+1)*7);i++){var r=list.get(i);int y=119+i%7*35;g.hLine(43,755,y+24,0x30734d2a);text(g,"#"+r.id(),47,y,INK);text(g,font.plainSubstrByWidth(r.name(),232)+(r.settings().locked()?" [锁]":""),123,y,INK);text(g,(r.members()+r.ai())+"/"+r.capacity()+"  AI "+r.ai(),371,y,INK);text(g,r.playing()?"对局中":"等待中",485,y,INK);}
        if(list.isEmpty())centered(g,"没有符合条件的房间，可以创建房间或刷新",400,218,INK);
        centered(g,(roomPage+1)+" / "+Math.max(1,(list.size()+6)/7),673,396,GOLD);
    }
    private void drawRoom(GuiGraphics g){
        var r=own();paper(g,24,56,750,316);if(r==null){centered(g,"正在读取房间…",400,190,INK);return;}
        text(g,font.plainSubstrByWidth(r.name(),170),42,81,INK);text(g,"房间号： "+r.id(),42,108,INK);text(g,"出牌限制： "+r.settings().turnSeconds()+"S",42,128,INK);
        text(g,modeName(r.mode()),42,151,INK);text(g,"响应 "+r.settings().responseSeconds()+"S · 选将 "+r.settings().selectionSeconds()+"S",42,174,INK);
        text(g,r.settings().fixedSeats()?"身份：固定座位顺序":"身份：每局随机",42,197,INK);text(g,"禁用武将： "+r.settings().bannedHeroes().size()+" / 6",42,219,INK);
        text(g,r.settings().allowChat()?"聊天：允许":"聊天：关闭",42,242,INK);text(g,r.settings().allowSpectators()?"旁观：允许":"旁观：关闭",42,263,INK);
        text(g,"真人 "+r.members()+" · AI "+r.ai(),42,284,INK);
        if(r.owner())text(g,"房主可设置房间及管理座位",490,65,INK);
    }
    private void drawSeatFronts(GuiGraphics g){var room=own();if(room==null)return;
        for(var seat:room.seats()){if(seatMenu==seat.slot() && seat.kind()==0)continue;int x=265+seat.slot()%4*126,y=87+seat.slot()/4*139;
            if(seat.kind()==0){dev.sgs.validation.client.art.CardBackRenderer.draw(g,x+3,y+5,80,112);g.fill(x+8,y+44,x+78,y+73,0xb02a2118);centered(g,"空位",x+43,y+55,GOLD);}
            else {g.fill(x+3,y+3,x+83,y+120,0xff534130);g.renderOutline(x+3,y+3,80,117,GOLD);
                if(seat.kind()==1)dev.sgs.validation.client.art.PlayerSkinRenderer.portrait(g,font,seat.uuid(),x+5,y+3,76,85);
                else dev.sgs.validation.client.art.CardBackRenderer.draw(g,x+14,y+7,58,81);
                g.fill(x+3,y+89,x+83,y+120,0xe72b231c);centered(g,font.plainSubstrByWidth(seat.name(),76),x+43,y+94,GOLD);centered(g,seat.kind()==2?"AI · 自动就绪":"玩家",x+43,y+108,0xffdcc4a0);
                if(seat.owner()){g.fill(x+61,y+1,x+90,y+15,0xff54758d);text(g,"房主",x+65,y+4,0xffffffff);}else if(seat.ready()){g.fill(x+61,y+1,x+90,y+15,0xff6e8c4b);text(g,"就绪",x+65,y+4,0xffeef5d4);}}
            text(g,Integer.toString(seat.slot()+1),x+6,y+7,0xffeacb92);highlight(g,"seat:"+seat.slot(),x,y,86,123);
        }
    }
    private void drawModal(GuiGraphics g){
        g.fill(0,0,800,429,0xa5090b0c);paper(g,108,48,592,335);g.fill(108,48,700,80,0xff74472b);
        String title=switch(modal){case SETTINGS->editing?"房间设置":"创建房间";case BANS->"禁用武将";case PASSWORD->"密码房间";case INVITE->"邀请玩家 / 管理真人";case CHAT->"房间聊天";default->"";};centered(g,title,400,60,GOLD);
        if(modal==Modal.SETTINGS){text(g,"房间名称",128,87,INK);text(g,"房间密码",405,87,INK);text(g,"房间模式",128,134,INK);text(g,"出手时间",312,134,INK);text(g,"座位身份",495,134,INK);text(g,"进入等级 / 逃跑限制：",128,186,INK);text(g,"无真实资料，V1 不设限制",128,202,0xff7b6246);text(g,"禁用武将",128,286,INK);text(g,draft.bans.size()+"/6",147,305,INK);text(g,"房间设置在服务器确认后生效",286,327,0xff7b6246);}
        else if(modal==Modal.BANS){if(heroes().isEmpty())centered(g,"没有符合条件的已实现武将",400,200,INK);centered(g,"已选择："+draft.bans.stream().map(this::heroName).collect(java.util.stream.Collectors.joining("、")),400,294,INK);text(g,"仅显示服务器已实现武将；最多禁用六名",122,368,0xff7b6246);}
        else if(modal==Modal.PASSWORD)centered(g,"房间 #"+targetRoom+" · 密码由服务器核验",400,130,INK);
        else if(modal==Modal.INVITE){text(g,"在线玩家",150,87,INK);text(g,"房内真人 · 房主管理",422,87,INK);centered(g,(invitePage+1)+" / "+Math.max(1,(inviteCandidates().size()+7)/8),270,343,INK);if(view!=null && inviteCandidates().isEmpty())text(g,"暂无可邀请的在线玩家",150,110,INK);}
        else if(modal==Modal.CHAT){if(view!=null){var lines=view.chat();int first=Math.max(0,lines.size()-15);for(int i=first;i<lines.size();i++)text(g,font.plainSubstrByWidth(lines.get(i),520),141,96+(i-first)*14,INK);}text(g,"消息仅发送至当前房间",141,356,0xff7b6246);}
    }
    private void drawHeroFronts(GuiGraphics g){
        var list=heroes();
        for(int i=heroPage*10;i<Math.min(list.size(),(heroPage+1)*10);i++){
            var h=list.get(i);int n=i%10,x=133+n%5*112,y=128+n/5*86;
            var classic=HeroRenderer.classic(h.id());
            if(!h.portrait().isEmpty()){
                var texture=net.minecraft.resources.ResourceLocation.tryParse(h.portrait());
                if(texture!=null && minecraft.getResourceManager().getResource(texture).isPresent())classic=texture;
            }
            HeroRenderer.portrait(g,font,h.id(),x+3,y+3,93,67,draft.bans.contains(h.id())?0xffffd273:GOLD,true,classic);
            g.fill(x+3,y+54,x+96,y+71,0xe7292118);centered(g,(draft.bans.contains(h.id())?"✓ ":"")+h.name(),x+49,y+59,GOLD);highlight(g,"ban:"+h.id(),x,y,99,74);
        }
    }
    private void highlight(GuiGraphics g,String id,int x,int y,int w,int h){
        var b=buttonsById.get(id);if(b!=null && b.active && b.isHoveredOrFocused()){g.fill(x+2,y+2,x+w-2,y+h-2,0x22ffdf94);g.renderOutline(x,y,w,h,0xffffdf8c);}
    }
    private void drawPlayerFace(GuiGraphics g,String uuid,int x,int y,int size){
        dev.sgs.validation.client.art.PlayerSkinRenderer.face(g,font,uuid,x,y,size);
    }
    private static String modeName(String id){return switch(id){case "six"->"军六 · 身份休闲";case "two_v_two"->"欢乐成双 · 2v2";default->"军八 · 身份休闲";};}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256){navigateBack();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}


    /** Test hooks use the exact controls and the same draft as native user input. */










    @Override public void removed(){dev.sgs.validation.client.art.PlayerSkinRenderer.clearPreviewCache();super.removed();}
    @Override public void onClose(){LobbyClient.close();minecraft.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}

