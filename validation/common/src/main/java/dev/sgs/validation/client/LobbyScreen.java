package dev.sgs.validation.client;

import static dev.sgs.validation.client.ClientText.tr;
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
    private enum Modal { NONE, SETTINGS, BANS, PASSWORD, INVITE, CHAT, PACKS }
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
    private java.util.List<LobbyNetwork.CardPackView> renderedPacks=java.util.List.of();
    private long packRenderedFrames;
    long packRenderedFramesForSmoke(){return modal==Modal.PACKS && view!=null && renderedPacks.equals(view.cardPacks())?packRenderedFrames:0;}
    private final Map<String,Button> smokeButtons=new HashMap<>();
    private final Map<String,EditBox> fields=new HashMap<>();
    private String localReason="",lastAction="",passwordAction="join";
    private boolean classicHover;
    private static final class Draft {
        String name=tr("sgs.ui.battle_of_heroes_f2911e0b"),password="";
        int turn=15,response=15,selection=30;
        boolean chat=true,spectators=false,doubleRenegade=false,fixed=false,clearPassword=false;
        LinkedHashSet<String> bans=new LinkedHashSet<>();
    }
    public LobbyScreen(){super(Component.literal(tr("sgs.ui.sanguosha_lobby_291a91c3")));}
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
        actions.add(b);if(id!=null)smokeButtons.put(id,b);
        if(view!=null && id!=null) {
            if(id.startsWith("mode:") && !enabledMode(id.substring(5)))blocked(b,tr("sgs.ui.this_mode_is_disabled_on_the_server_a3ed6b4b"));
            if((id.equals("create") || id.equals("queue")) && !enabledMode(mode))blocked(b,tr("sgs.ui.this_mode_is_disabled_on_the_server_a3ed6b4b"));
            if(id.equals("queue") && !view.matchmaking())blocked(b,tr("sgs.ui.matchmaking_is_disabled_on_the_server_38fcb146"));
        }
        return b;
    }
    private Button button(String id,String label,int x,int y,int w,Runnable run){return button(id,label,x,y,w,22,run);}
    private void blocked(Button b,String reason){b.active=false;b.setTooltip(Tooltip.create(Component.literal(reason)));}
    private boolean enabledMode(String id){return view!=null && view.enabledModes()!=null && view.enabledModes().contains(id);}
    private EditBox edit(String value,String hint,int x,int y,int w,java.util.function.Consumer<String> change){
        var e=new EditBox(font,px(x),py(y),dim(w),dim(20),Component.literal(hint));e.setMaxLength(80);e.setValue(value);e.setHint(Component.literal(hint));e.setResponder(change);addRenderableWidget(e);
        String key=hint.equals(tr("sgs.ui.room_name_2a29de50"))?"name":hint.equals(tr("sgs.ui.room_id_name_3df2d4be"))?"search":hint.equals(tr("sgs.ui.search_heroes_251a60f0"))?"heroSearch":hint.equals(tr("sgs.ui.enter_room_password_59b13f0b"))?"joinPassword":hint.equals(tr("sgs.ui.enter_a_room_message_ffa4ee4e"))?"chat":"password";
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
    private void submitSettings(){if(draft.name.isBlank()){localReason=tr("sgs.ui.enter_a_room_name_2a0577a9");return;}send(editing?"update":"create",0,settingsJson());}
    private void selectMode(String value){mode=value;roomPage=0;go(Page.ROOMS);}
    private void navigateBack(){if(modal==Modal.BANS || modal==Modal.PACKS){modal=Modal.SETTINGS;rebuildWidgets();}else if(modal!=Modal.NONE){modal=Modal.NONE;rebuildWidgets();}else if(page==Page.ROOM){var room=own();if(room!=null && room.playing())onClose();else send("leave",0);}else if(page==Page.ROOMS)go(Page.MODES);else if(page==Page.MODES)go(Page.HOME);else onClose();}
    @Override protected void init(){
        scale=Math.min(width/800f,height/450f);ox=(width-dim(800))/2;oy=(height-dim(450))/2;
        actions.clear();smokeButtons.clear();fields.clear();
        if(modal!=Modal.NONE){buildModal();if(pending!=0)actions.forEach(b->b.active=false);return;}
        button("back","‹",8,7,43,28,this::navigateBack);
        if(page==Page.HOME)buildHome();else if(page==Page.MODES)buildModes();else if(page==Page.ROOMS)buildRooms();else buildRoom();
        if(view!=null && view.inviteRoom()>=0 && view.ownRoom()<0){button("accept",tr("sgs.ui.accept_invite_fac1d8da")+view.inviteRoom(),455,12,145,()->send("accept",view.inviteRoom()));button("decline",tr("sgs.ui.decline_03e210a6"),607,12,58,()->send("decline",view.inviteRoom()));}
        if(pending!=0)actions.forEach(b->b.active=false);
    }
    private void buildHome(){
        button("open_rooms",tr("sgs.ui.play_a6d8a93d"),192,347,202,48,()->go(Page.MODES));
        button("browse_home",tr("sgs.ui.browse_rooms_6062a5ba"),413,347,202,48,()->go(Page.ROOMS));
        button("collection_owned",tr("sgs.ui.heroes_6fb3f900"),192,412,202,28,CollectionClient::openOwned);
        button("collection_recruit",tr("sgs.ui.recruit_heroes_75480f8c"),413,412,202,28,CollectionClient::open);
    }
    private void buildModes(){
        int y=83,w=170,h=248;
        modeCard("two_v_two",tr("sgs.ui.two_vs_two_27542de5"),tr("sgs.ui.2v2_casual_156ea95d"),"sunquan",111,y,w,h);
        modeCard("eight",tr("sgs.ui.identity_mode_dd42243a"),tr("sgs.ui.8_player_6_player_896c1323"),"caocao",315,y,w,h);
        var unavailable=button("mode:guozhan",tr("sgs.ui.kingdoms_unavailable_b13574ad"),519,y,w,h,()->{});blocked(unavailable,tr("sgs.ui.kingdoms_mode_is_unavailable_dual_hero_and_kingd_d9a5b2b9"));
        button("create",tr("sgs.ui.create_room_1688ee1b"),112,370,150,30,()->openSettings(false));
        button("mode:six",tr("sgs.ui.6_player_room_883a54fe"),315,370,170,30,()->selectMode("six"));
        button("browse",tr("sgs.ui.browse_rooms_6062a5ba"),539,370,150,30,()->go(Page.ROOMS));
        // Existing smoke entry points retain all supported modes.

    }
    private void modeCard(String id,String title,String subtitle,String hero,int x,int y,int w,int h){
        var b=button("mode:"+id,title,x,y,w,h,()->selectMode(id));if(b.active)b.setTooltip(Tooltip.create(Component.literal(subtitle)));
    }
    private List<LobbyNetwork.RoomView> rooms(){return view==null?List.of():view.rooms().stream().filter(r->r.mode().equals(mode)).filter(r->appliedSearch.isBlank() || r.name().toLowerCase(Locale.ROOT).contains(appliedSearch.toLowerCase(Locale.ROOT)) || Long.toString(r.id()).contains(appliedSearch)).filter(r->!hideFull || r.members()+r.ai()<r.capacity()).toList();}
    private void buildRooms(){
        button("mode:six",mode.equals("six")?tr("sgs.ui.6_player_25b8fe65"):tr("sgs.ui.6_player_95b65e2d"),40,54,90,()->{mode="six";roomPage=0;rebuildWidgets();});
        button("mode:eight",mode.equals("eight")?tr("sgs.ui.8_player_4bb2b602"):tr("sgs.ui.8_player_ec52ce5b"),138,54,90,()->{mode="eight";roomPage=0;rebuildWidgets();});
        button("mode:two_v_two",mode.equals("two_v_two")?"● 2v2":"2v2",236,54,90,()->{mode="two_v_two";roomPage=0;rebuildWidgets();});
        edit(search,tr("sgs.ui.room_id_name_3df2d4be"),410,54,180,v->search=v);
        button("search",tr("sgs.ui.search_f0409080"),600,54,58,()->{appliedSearch=search;roomPage=0;rebuildWidgets();});button("hide_full",hideFull?tr("sgs.ui.hide_full_42b2fde6"):tr("sgs.ui.hide_full_a8c8d8b2"),665,54,100,()->{hideFull=!hideFull;roomPage=0;rebuildWidgets();});
        var list=rooms();int rows=7;roomPage=Math.min(roomPage,Math.max(0,(list.size()-1)/rows));
        for(int i=roomPage*rows;i<Math.min(list.size(),(roomPage+1)*rows);i++){
            var r=list.get(i);int y=112+(i%rows)*35;
            var join=button("join:"+r.id(),r.settings().locked()?tr("sgs.ui.enter_password_e2f58b68"):tr("sgs.ui.join_b7db4426"),591,y,72,()->{if(r.settings().locked()){targetRoom=r.id();passwordAction="join";passwordInput="";modal=Modal.PASSWORD;rebuildWidgets();}else send("join",r.id());});
            if(r.playing() || r.members()+r.ai()>=r.capacity() || view==null || !view.queued().isEmpty())blocked(join,r.playing()?tr("sgs.ui.the_match_has_already_started_71ada14a"):tr("sgs.ui.the_room_is_full_or_you_are_in_a_queue_7b715063"));
            if(!enabledMode(r.mode()))blocked(join,tr("sgs.ui.this_mode_is_disabled_on_the_server_a3ed6b4b"));
            var watch=button("spectate:"+r.id(),tr("sgs.ui.spectate_c90a24d7"),674,y,70,()->{if(r.settings().locked()){targetRoom=r.id();passwordAction="spectate";passwordInput="";modal=Modal.PASSWORD;rebuildWidgets();}else send("spectate",r.id());});if(!r.settings().allowSpectators() || !r.playing())blocked(watch,tr("sgs.ui.the_match_must_be_running_with_spectators_allowe_768b3e7c"));
        }
        button("create",tr("sgs.ui.create_room_1688ee1b"),40,385,132,30,()->openSettings(false));
        if(view!=null && !view.queued().isEmpty())button("cancel",tr("sgs.ui.leave_queue_d6c98541"),187,385,132,30,()->send("cancel",0));else button("queue",tr("sgs.ui.quick_match_aad6ba4e"),187,385,132,30,()->send("queue",0));
        button("refresh",tr("sgs.ui.refresh_38108eaa"),333,385,80,30,()->send("refresh",0));
        var prev=button("prev","‹",598,385,40,30,()->{roomPage--;rebuildWidgets();});prev.active=roomPage>0;
        var next=button("next","›",706,385,40,30,()->{roomPage++;rebuildWidgets();});next.active=(roomPage+1)*rows<list.size();
    }
    private void buildRoom(){
        var room=own();if(room==null)return;
        for(var seat:room.seats()){
            int x=265+seat.slot()%4*126,y=87+seat.slot()/4*139;
            if(seatMenu==seat.slot() && seat.kind()==0){
                button("sit:"+seat.slot(),tr("sgs.ui.take_seat_9719e30c"),x+5,y+6,76,23,()->send("sit",seat.slot()));
                var ai=button("ai_add:"+seat.slot(),tr("sgs.ui.add_ai_2395bd51"),x+5,y+35,76,23,()->send("ai_add",seat.slot()));if(!room.owner() || room.playing())blocked(ai,tr("sgs.ui.only_the_owner_can_add_ai_while_waiting_96afebd2"));
                button("seat_close",tr("sgs.ui.close_6c14bd7f"),x+5,y+64,76,23,()->{seatMenu=-1;rebuildWidgets();});
                var invite=button("invite",tr("sgs.ui.invite_3d96750f"),x+5,y+93,76,23,()->{modal=Modal.INVITE;rebuildWidgets();});if(!room.owner())blocked(invite,tr("sgs.ui.only_the_owner_can_invite_players_192e97b2"));
            }else{
                var card=button("seat:"+seat.slot(),"",x,y,86,123,()->{if(seat.kind()==0){seatMenu=seat.slot();rebuildWidgets();}else if(seat.kind()==2 && room.owner())send("ai_remove",seat.slot());else if(seat.kind()==1 && !seat.owner() && room.owner()){targetRoom=seat.slot();modal=Modal.INVITE;rebuildWidgets();}});
                if(room.playing() || seat.kind()==1 && (seat.owner() || !room.owner()) || seat.kind()==2 && !room.owner())card.active=false;
                if(seat.kind()==2)smokeButtons.put("ai_remove:"+seat.slot(),card);
                else if(seat.kind()==0)smokeButtons.put("ai_add:"+seat.slot(),card);
                card.setTooltip(Tooltip.create(Component.literal(seat.kind()==0?tr("sgs.ui.open_seat_menu_584d44c0"):seat.kind()==2?tr("sgs.ui.ai_owner_can_click_to_remove_19a35ab1"):seat.name()+" · "+(seat.ready()?tr("sgs.ui.ready_39b7a65a"):tr("sgs.ui.not_ready_7959f080")))));
            }
        }
        button("chat_open",tr("sgs.ui.chat_5358b2dd"),39,382,65,26,()->{modal=Modal.CHAT;rebuildWidgets();});
        var settings=button("settings",tr("sgs.ui.settings_7debf9cb"),112,382,65,26,()->openSettings(true));if(!room.owner() || room.playing())blocked(settings,tr("sgs.ui.only_the_owner_can_change_settings_while_waiting_c82af2dc"));
        var clear=button("ai_clear",tr("sgs.ui.remove_all_ai_3c1d454d"),347,382,125,30,()->send("ai_clear",0));if(!room.owner() || room.ai()==0 || room.playing())blocked(clear,tr("sgs.ui.remove_all_ai_while_keeping_human_players_baaaed3a"));
        var fill=button("ai_fill",tr("sgs.ui.fill_with_ai_b219399a"),482,382,125,30,()->send("ai_fill",0));if(!room.owner() || room.playing() || room.members()+room.ai()>=room.capacity())blocked(fill,tr("sgs.ui.fill_empty_seats_with_ai_6d24d222"));
        var start=button(room.playing()?"resume":room.owner()?"start":view.ownReady()?"unready":"ready",room.playing()?tr("sgs.ui.return_to_match_d631bb84"):room.owner()?tr("sgs.ui.start_79cc871c"):view.ownReady()?tr("sgs.ui.unready_cc601652"):tr("sgs.ui.ready_cc3ce6df"),622,382,122,30,()->{if(room.playing())MatchClient.resume();else send(room.owner()?"start":view.ownReady()?"unready":"ready",0);});
        if(!room.playing() && !enabledMode(room.mode()))blocked(start,tr("sgs.ui.this_mode_is_disabled_on_the_server_a3ed6b4b"));
        if(!room.playing() && room.owner() && (room.members()+room.ai()!=room.capacity() || room.ready()!=room.members()))blocked(start,tr("sgs.ui.fill_all_seats_and_wait_for_players_to_ready_up_0df8bbfa"));
        // Keep explicit ready and leave actions for regression hooks and player control.
        String readyAction=view.ownReady()?"unready":"ready";
        if(room.owner() && !room.playing())button(readyAction,view.ownReady()?tr("sgs.ui.unready_cc601652"):tr("sgs.ui.ready_cc3ce6df"),39,343,138,22,()->send(readyAction,0));
        smokeButtons.put("leave",smokeButtons.get("back"));
        if(room.mode().equals("two_v_two")){String action=room.teammateSharing()?"share_off":"share_on";var share=button(action,tr("sgs.ui.ally_s_hand_702d74e7")+(room.teammateSharing()?tr("sgs.ui.on_b4217093"):tr("sgs.ui.off_a3332f1c")),39,306,138,22,()->send(action,0));if(!room.owner())blocked(share,tr("sgs.ui.the_owner_controls_ally_hand_sharing_459ff3e1"));}
    }
    private void buildModal(){
        button("modal_close","×",666,52,30,this::navigateBack);
        if(modal==Modal.SETTINGS)buildSettings();else if(modal==Modal.BANS)buildBans();else if(modal==Modal.PASSWORD){edit(passwordInput,tr("sgs.ui.enter_room_password_59b13f0b"),245,170,310,v->passwordInput=v);button("password_join",passwordAction.equals("spectate")?tr("sgs.ui.spectate_a5171790"):tr("sgs.ui.join_room_1884a60e"),320,250,160,()->send(passwordAction,targetRoom,passwordInput));}
        else if(modal==Modal.INVITE)buildInvites();else if(modal==Modal.CHAT)buildChat();
        else if(modal==Modal.PACKS)button("pack-back",tr("sgs.ui.back_to_settings_d0684b9e"),320,342,160,30,this::navigateBack);
    }
    private void buildSettings(){
        edit(draft.name,tr("sgs.ui.room_name_2a29de50"),128,100,168,v->draft.name=v);
        edit(draft.password,editing?tr("sgs.ui.leave_blank_to_keep_password_c5f3b7a6"):tr("sgs.ui.no_password_by_default_45152703"),405,100,175,v->draft.password=v);
        button("clear_password",draft.clearPassword?tr("sgs.ui.clear_password_f687af9b"):tr("sgs.ui.clear_password_bcf73ac3"),589,100,85,()->{draft.clearPassword=!draft.clearPassword;rebuildWidgets();});
        button("setting_mode",modeName(mode),127,147,160,()->{if(editing){localReason=tr("sgs.ui.create_a_new_room_to_change_the_mode_cf981783");return;}mode=mode.equals("eight")?"six":mode.equals("six")?"two_v_two":"eight";if(!mode.equals("eight"))draft.doubleRenegade=false;rebuildWidgets();});
        if(editing)blocked(smokeButtons.get("setting_mode"),tr("sgs.ui.the_mode_of_an_existing_room_cannot_be_changed_5c90c3c7"));
        button("turn",tr("sgs.ui.turn_09147041")+draft.turn+tr("sgs.ui.sec_d9ad5055"),312,147,158,()->{draft.turn=cycle(draft.turn,new int[]{10,15,20,30,60,300});rebuildWidgets();});
        button("fixed",draft.fixed?tr("sgs.ui.fixed_seats_d2abac46"):tr("sgs.ui.random_seats_95dc0fb7"),495,147,176,()->{draft.fixed=!draft.fixed;rebuildWidgets();})
            .setTooltip(Tooltip.create(Component.literal(tr("sgs.ui.fixed_preserve_relative_lobby_seating_rotate_the_f897fdf0"))));
        button("response",tr("sgs.ui.response_cc3fc09e")+draft.response+tr("sgs.ui.sec_d9ad5055"),312,181,158,()->{draft.response=cycle(draft.response,new int[]{10,15,20,30,300});rebuildWidgets();});
        button("selection",tr("sgs.ui.hero_pick_745a20d0")+draft.selection+tr("sgs.ui.sec_d9ad5055"),495,181,176,()->{draft.selection=cycle(draft.selection,new int[]{15,30,45,60,300});rebuildWidgets();});
        button("allow_chat",(draft.chat?"☑":"□")+tr("sgs.ui.allow_chat_b66c72f8"),127,231,140,()->{draft.chat=!draft.chat;rebuildWidgets();});
        button("allow_spectators",(draft.spectators?"☑":"□")+tr("sgs.ui.allow_spectators_6f5528da"),282,231,140,()->{draft.spectators=!draft.spectators;rebuildWidgets();});
        var dual=button("double_renegade",(draft.doubleRenegade?"☑":"□")+tr("sgs.ui.two_renegades_ac9d823f"),437,231,114,()->{draft.doubleRenegade=!draft.doubleRenegade;rebuildWidgets();});if(!mode.equals("eight"))blocked(dual,tr("sgs.ui.two_renegades_requires_8_player_identity_mode_ae92f3ce"));
        button("pack-info",tr("sgs.ui.card_packs_f4744398"),562,231,109,()->{modal=Modal.PACKS;rebuildWidgets();})
            .setTooltip(Tooltip.create(Component.literal(tr("sgs.ui.view_card_packs_enabled_by_the_server_3d0dfced"))));
        for(int i=0;i<6;i++){int slot=i;var ids=new ArrayList<>(draft.bans);button("ban_slot:"+i,i<ids.size()?heroName(ids.get(i)):"＋",205+i*72,278,62,42,()->{heroPage=0;modal=Modal.BANS;rebuildWidgets();});}
        button("confirm_settings",editing?tr("sgs.ui.save_settings_8367fa1a"):tr("sgs.ui.create_room_1688ee1b"),320,342,160,30,this::submitSettings);
    }
    private static int cycle(int value,int[] options){for(int i=0;i<options.length;i++)if(options[i]==value)return options[(i+1)%options.length];return options[0];}
    private String heroName(String id){return view==null?id:view.heroes().stream().filter(h->h.id().equals(id)).map(h->ClientText.heroName(h.id(),h.name())).findFirst().orElse(id);}
    private List<LobbyNetwork.HeroView> heroes(){if(view==null)return List.of();return view.heroes().stream().filter(h->faction.equals("全部") || faction.equals(h.kingdom())).filter(h->appliedHeroSearch.isBlank() || (h.name().contains(appliedHeroSearch)||ClientText.heroName(h.id(),h.name()).toLowerCase(Locale.ROOT).contains(appliedHeroSearch.toLowerCase(Locale.ROOT))) || h.id().contains(appliedHeroSearch.toLowerCase(Locale.ROOT))).toList();}
    private void buildBans(){
        int x=122;for(String f:List.of("全部","魏","蜀","吴","群","神")){String value=f;button("faction:"+f,(f.equals(faction)?"● ":"")+ClientText.category(f),x,87,55,()->{faction=value;heroPage=0;rebuildWidgets();});x+=59;}
        edit(heroSearch,tr("sgs.ui.search_heroes_251a60f0"),482,87,135,v->heroSearch=v);button("hero_search",tr("sgs.ui.search_f0409080"),624,87,53,()->{appliedHeroSearch=heroSearch;heroPage=0;rebuildWidgets();});
        var list=heroes();heroPage=Math.min(heroPage,Math.max(0,(list.size()-1)/10));
        for(int i=heroPage*10;i<Math.min(list.size(),(heroPage+1)*10);i++){var h=list.get(i);int n=i%10;var b=button("ban:"+h.id(),(draft.bans.contains(h.id())?"✓ ":"")+ClientText.heroName(h.id(),h.name()),133+n%5*112,128+n/5*86,99,74,()->{if(!draft.bans.remove(h.id())){if(draft.bans.size()>=6){localReason=tr("sgs.ui.you_can_ban_up_to_six_heroes_27ac8823");return;}draft.bans.add(h.id());}rebuildWidgets();});String skills=h.skills().stream().map(s->ClientText.skillName(s.id(),s.name())+": "+ClientText.skillDescription(s.id(),s.description())).collect(java.util.stream.Collectors.joining("\n"));b.setTooltip(Tooltip.create(Component.literal(ClientText.heroName(h.id(),h.name())+" · "+ClientText.category(h.kingdom())+"\n"+ClientText.category(h.description())+"\n"+skills)));}
        var prev=button("hero_prev","‹",122,310,35,()->{heroPage--;rebuildWidgets();});prev.active=heroPage>0;
        var next=button("hero_next","›",639,310,35,()->{heroPage++;rebuildWidgets();});next.active=(heroPage+1)*10<list.size();
        button("clear_bans",tr("sgs.ui.clear_bans_ca598f09"),191,310,90,()->{draft.bans.clear();rebuildWidgets();});
        button("confirm_bans",tr("sgs.ui.confirm_bans_cfd09051")+draft.bans.size()+"/6)",416,346,170,28,()->{modal=Modal.SETTINGS;rebuildWidgets();});
    }
    private List<LobbyNetwork.PlayerView> inviteCandidates(){
        if(view==null)return List.of();
        var occupied=view.rooms().stream().flatMap(r->r.seats().stream()).filter(s->s.kind()==1).map(s->s.uuid()).collect(java.util.stream.Collectors.toSet());
        String self=minecraft.player==null?"":minecraft.player.getUUID().toString();
        return view.players().stream().filter(p->!p.uuid().equals(self) && !occupied.contains(p.uuid())).toList();
    }
    private void buildInvites(){
        if(view==null)return;var players=inviteCandidates();invitePage=Math.min(invitePage,Math.max(0,(players.size()-1)/8));
        for(int i=invitePage*8;i<Math.min(players.size(),(invitePage+1)*8);i++){var p=players.get(i);int y=105+(i%8)*27;button("invite:"+p.uuid(),tr("sgs.ui.invite_7e80a0ab")+p.name(),150,y,240,()->send("invite",0,p.uuid()));}
        var prev=button("invite_prev","‹",150,335,42,()->{invitePage--;rebuildWidgets();});prev.active=invitePage>0;
        var next=button("invite_next","›",348,335,42,()->{invitePage++;rebuildWidgets();});next.active=(invitePage+1)*8<players.size();
        var room=own();if(room!=null && room.owner()){int row2=0;for(var seat:room.seats())if(seat.kind()==1 && !seat.owner()){var s=seat;button("kick:"+s.uuid(),tr("sgs.ui.remove_c5e8370d")+s.name(),422,105+row2++*29,230,()->send("kick",0,s.uuid()));}}
    }
    private void buildChat(){
        var room=own();boolean enabled=room!=null && room.settings().allowChat();edit(chatInput,tr("sgs.ui.enter_a_room_message_ffa4ee4e"),141,322,430,v->chatInput=v);
        var send=button("chat_send",tr("sgs.ui.send_1214d633"),582,322,74,()->{if(!chatInput.isBlank()){send("chat",0,chatInput);rebuildWidgets();}});if(!enabled)blocked(send,tr("sgs.ui.room_chat_is_disabled_by_the_owner_e7e43828"));
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
        UnifiedSkin.header(g,font,page==Page.HOME?tr("sgs.ui.sanguosha_lobby_943345c6"):page==Page.ROOM?tr("sgs.ui.waiting_room_225ed5e6"):page==Page.ROOMS?tr("sgs.ui.room_list_c8ee183f"):tr("sgs.ui.choose_mode_0a1c9313"),800,account==null?-1:account.tokens(),account==null?0:account.souls());
        RenderSystem.enableDepthTest();
        if(page==Page.HOME)drawHome(g);else if(page==Page.MODES)drawModes(g);else if(page==Page.ROOMS)drawRooms(g);else drawRoom(g);
        // Font batches from the background must finish before the opaque modal paper.
        g.flush();
        if(modal!=Modal.NONE){g.pose().translate(0,0,MODAL_PAPER_Z);drawModal(g);}
        String reason=pending!=0?tr("sgs.ui.waiting_for_server_f9a51d77"):!localReason.isEmpty()?localReason:view==null?tr("sgs.ui.loading_rooms_9c134d54"):!view.success()?view.reason():"";
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
        if(modal==Modal.PACKS && view!=null){if(!renderedPacks.equals(view.cardPacks()))packRenderedFrames=0;renderedPacks=List.copyOf(view.cardPacks());packRenderedFrames++;}else packRenderedFrames=0;
    }
    private void drawHome(GuiGraphics g){
        UnifiedSkin.art(g,UnifiedSkin.TITLE,50,67,294,98);
        UnifiedSkin.label(g,font,tr("sgs.ui.play_with_others_on_this_server_262e9b84"),66,168,264,19,UnifiedSkin.CREAM);
        UnifiedSkin.panel(g,UnifiedSkin.WOOD,0,404,800,46);
    }
    private void drawModes(GuiGraphics g){
        text(g,tr("sgs.ui.choose_a_mode_and_create_a_room_1c7e21d7"),280,28,0xffdfd3b5);
        text(g,tr("sgs.ui.available_6_player_8_player_2v2_9c41ae6c"),112,348,GOLD);
    }
    private void drawModeFronts(GuiGraphics g){
        String[] titles={tr("sgs.ui.two_vs_two_27542de5"),tr("sgs.ui.identity_mode_dd42243a"),tr("sgs.ui.kingdoms_mode_2c4c2c97")};String[] subtitles={tr("sgs.ui.2v2_casual_c9162e18"),tr("sgs.ui.8_player_6_player_0e5034a3"),tr("sgs.ui.rules_not_implemented_yet_927b0fd9")};String[] portraits={"sunquan","caocao","liubei"};
        for(int i=0;i<3;i++){int x=111+i*204;HeroRenderer.portrait(g,font,portraits[i],x+8,91,154,192,GOLD,true);g.fill(x+8,252,x+162,283,0xda352618);centered(g,titles[i],x+85,264,GOLD);centered(g,subtitles[i],x+85,304,i==2?0xff65523e:INK);if(i==2)g.fill(x+8,91,x+162,283,0x88595042);highlight(g,"mode:"+new String[]{"two_v_two","eight","guozhan"}[i],x,83,170,248);}
    }
    private void drawRooms(GuiGraphics g){
        paper(g,31,88,738,285);
        text(g,tr("sgs.ui.room_id_8e5a99c4"),47,96,INK);text(g,tr("sgs.ui.room_name_2a29de50"),123,96,INK);text(g,tr("sgs.ui.players_ai_32336d5b"),371,96,INK);text(g,tr("sgs.ui.status_62e951a6"),485,96,INK);
        var list=rooms();for(int i=roomPage*7;i<Math.min(list.size(),(roomPage+1)*7);i++){var r=list.get(i);int y=119+i%7*35;g.hLine(43,755,y+24,0x30734d2a);text(g,"#"+r.id(),47,y,INK);text(g,font.plainSubstrByWidth(r.name(),232)+(r.settings().locked()?tr("sgs.ui.locked_bcc45e7d"):""),123,y,INK);text(g,(r.members()+r.ai())+"/"+r.capacity()+"  AI "+r.ai(),371,y,INK);text(g,r.playing()?tr("sgs.ui.playing_5b5f6ace"):tr("sgs.ui.waiting_bd3488d0"),485,y,INK);}
        if(list.isEmpty())centered(g,tr("sgs.ui.no_matching_rooms_create_one_or_refresh_36e3b0f2"),400,218,INK);
        centered(g,(roomPage+1)+" / "+Math.max(1,(list.size()+6)/7),673,396,GOLD);
    }
    private void drawRoom(GuiGraphics g){
        var r=own();paper(g,24,56,750,316);if(r==null){centered(g,tr("sgs.ui.loading_room_b022dfd4"),400,190,INK);return;}
        text(g,font.plainSubstrByWidth(r.name(),170),42,81,INK);text(g,tr("sgs.ui.room_id_22c8d114")+r.id(),42,108,INK);text(g,tr("sgs.ui.turn_limit_fdf93677")+r.settings().turnSeconds()+"S",42,128,INK);
        text(g,modeName(r.mode()),42,151,INK);text(g,tr("sgs.ui.response_cc3fc09e")+r.settings().responseSeconds()+tr("sgs.ui.s_hero_pick_4122971a")+r.settings().selectionSeconds()+"S",42,174,INK);
        text(g,r.settings().fixedSeats()?tr("sgs.ui.seats_fixed_order_97853e91"):tr("sgs.ui.identities_random_each_match_79565c96"),42,197,INK);text(g,tr("sgs.ui.banned_heroes_cf0d7ea2")+r.settings().bannedHeroes().size()+" / 6",42,219,INK);
        text(g,r.settings().allowChat()?tr("sgs.ui.chat_allowed_6519cc05"):tr("sgs.ui.chat_disabled_08cd3190"),42,242,INK);text(g,r.settings().allowSpectators()?tr("sgs.ui.spectators_allowed_ad64f3c3"):tr("sgs.ui.spectators_disabled_f89bc148"),42,263,INK);
        text(g,tr("sgs.ui.players_a1bf60e9")+r.members()+" · AI "+r.ai(),42,284,INK);
        if(r.owner())text(g,tr("sgs.ui.the_owner_can_edit_settings_and_manage_seats_350517e4"),490,65,INK);
    }
    private void drawSeatFronts(GuiGraphics g){var room=own();if(room==null)return;
        for(var seat:room.seats()){if(seatMenu==seat.slot() && seat.kind()==0)continue;int x=265+seat.slot()%4*126,y=87+seat.slot()/4*139;
            if(seat.kind()==0){dev.sgs.validation.client.art.CardBackRenderer.draw(g,x+3,y+5,80,112);g.fill(x+8,y+44,x+78,y+73,0xb02a2118);centered(g,tr("sgs.ui.empty_a3eef8ee"),x+43,y+55,GOLD);}
            else {g.fill(x+3,y+3,x+83,y+120,0xff534130);g.renderOutline(x+3,y+3,80,117,GOLD);
                if(seat.kind()==1)dev.sgs.validation.client.art.PlayerSkinRenderer.portrait(g,font,seat.uuid(),x+5,y+3,76,85);
                else dev.sgs.validation.client.art.CardBackRenderer.draw(g,x+14,y+7,58,81);
                g.fill(x+3,y+89,x+83,y+120,0xe72b231c);centered(g,font.plainSubstrByWidth(seat.name(),76),x+43,y+94,GOLD);centered(g,seat.kind()==2?tr("sgs.ui.ai_ready_a1be2643"):tr("sgs.ui.player_e7c31937"),x+43,y+108,0xffdcc4a0);
                if(seat.owner()){g.fill(x+61,y+1,x+90,y+15,0xff54758d);text(g,tr("sgs.ui.owner_28f5aa96"),x+65,y+4,0xffffffff);}else if(seat.ready()){g.fill(x+61,y+1,x+90,y+15,0xff6e8c4b);text(g,tr("sgs.ui.ready_b796f2d4"),x+65,y+4,0xffeef5d4);}}
            text(g,Integer.toString(seat.slot()+1),x+6,y+7,0xffeacb92);highlight(g,"seat:"+seat.slot(),x,y,86,123);
        }
    }
    private void drawModal(GuiGraphics g){
        g.fill(0,0,800,429,0xa5090b0c);paper(g,108,48,592,335);g.fill(108,48,700,80,0xff74472b);
        String title=switch(modal){case SETTINGS->editing?tr("sgs.ui.room_settings_4752ab0b"):tr("sgs.ui.create_room_1688ee1b");case BANS->tr("sgs.ui.ban_heroes_55998fce");case PASSWORD->tr("sgs.ui.password_required_7dfc87cd");case INVITE->tr("sgs.ui.invite_manage_players_b3440c60");case CHAT->tr("sgs.ui.room_chat_3b0f1562");case PACKS->tr("sgs.ui.server_card_packs_4730e14c");default->"";};centered(g,title,400,60,GOLD);
        if(modal==Modal.SETTINGS){text(g,tr("sgs.ui.room_name_2a29de50"),128,87,INK);text(g,tr("sgs.ui.password_d71dec76"),405,87,INK);text(g,tr("sgs.ui.mode_bd5e7651"),128,134,INK);text(g,tr("sgs.ui.turn_time_c19a1278"),312,134,INK);text(g,tr("sgs.ui.seat_order_c3aca5cc"),495,134,INK);text(g,tr("sgs.ui.level_quit_restrictions_eb5f1e49"),128,186,INK);text(g,tr("sgs.ui.no_restrictions_in_this_version_827f08c8"),128,202,0xff7b6246);text(g,tr("sgs.ui.ban_heroes_55998fce"),128,286,INK);text(g,draft.bans.size()+"/6",147,305,INK);text(g,tr("sgs.ui.settings_apply_after_server_confirmation_16ce03f6"),286,327,0xff7b6246);}
        else if(modal==Modal.BANS){if(heroes().isEmpty())centered(g,tr("sgs.ui.no_matching_implemented_heroes_6136c04f"),400,200,INK);centered(g,tr("sgs.ui.selected_9c560584")+draft.bans.stream().map(this::heroName).collect(java.util.stream.Collectors.joining("、")),400,294,INK);text(g,tr("sgs.ui.implemented_heroes_only_up_to_six_bans_ea42a578"),122,368,0xff7b6246);}
        else if(modal==Modal.PASSWORD)centered(g,tr("sgs.ui.room_a2a9f213")+targetRoom+tr("sgs.ui.password_checked_by_server_a0354dce"),400,130,INK);
        else if(modal==Modal.INVITE){text(g,tr("sgs.ui.online_players_5798d37a"),150,87,INK);text(g,tr("sgs.ui.players_in_room_owner_controls_40d24b79"),422,87,INK);centered(g,(invitePage+1)+" / "+Math.max(1,(inviteCandidates().size()+7)/8),270,343,INK);if(view!=null && inviteCandidates().isEmpty())text(g,tr("sgs.ui.no_players_available_to_invite_48aafac9"),150,110,INK);}
        else if(modal==Modal.CHAT){if(view!=null){var lines=view.chat();int first=Math.max(0,lines.size()-15);for(int i=first;i<lines.size();i++)text(g,font.plainSubstrByWidth(lines.get(i),520),141,96+(i-first)*14,INK);}text(g,tr("sgs.ui.messages_are_sent_to_this_room_only_b9258ec5"),141,356,0xff7b6246);}
        else if(modal==Modal.PACKS){
            var packs=view==null?List.<LobbyNetwork.CardPackView>of():view.cardPacks();
            if(packs.isEmpty())centered(g,tr("sgs.ui.no_card_pack_information_received_644785f9"),400,180,INK);
            for(int i=0;i<packs.size();i++){
                var p=packs.get(i);int y=104+i*48;text(g,ClientText.named("sgs.pack."+p.id(),p.name()),145,y,INK);
                text(g,!p.available()?tr("sgs.ui.unavailable_58aabf2c"):p.enabled()?tr("sgs.ui.enabled_25d28431"):tr("sgs.ui.disabled_f628761b"),510,y,p.enabled()?0xff426035:0xff7b6246);
                if(!p.reason().isEmpty())text(g,ClientText.named("sgs.pack."+p.id()+".reason",p.reason()),145,y+18,0xff7b6246);
            }
            centered(g,tr("sgs.ui.managed_by_the_server_owner_changes_apply_to_new_cf132db2"),400,312,0xff7b6246);
        }
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
            g.fill(x+3,y+54,x+96,y+71,0xe7292118);centered(g,(draft.bans.contains(h.id())?"✓ ":"")+ClientText.heroName(h.id(),h.name()),x+49,y+59,GOLD);highlight(g,"ban:"+h.id(),x,y,99,74);
        }
    }
    private void highlight(GuiGraphics g,String id,int x,int y,int w,int h){
        var b=smokeButtons.get(id);if(b!=null && b.active && b.isHoveredOrFocused()){g.fill(x+2,y+2,x+w-2,y+h-2,0x22ffdf94);g.renderOutline(x,y,w,h,0xffffdf8c);}
    }
    private void drawPlayerFace(GuiGraphics g,String uuid,int x,int y,int size){
        dev.sgs.validation.client.art.PlayerSkinRenderer.face(g,font,uuid,x,y,size);
    }
    private static String modeName(String id){return switch(id){case "six"->tr("sgs.ui.6_player_identity_7ad3c5e4");case "two_v_two"->tr("sgs.ui.two_vs_two_2v2_a4881ce4");default->tr("sgs.ui.8_player_identity_24fdca9b");};}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==256){navigateBack();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    LobbyNetwork.View smokeView(){return view;} boolean smokePending(){return pending!=0;}
    void smokePress(String action){
        if(action.equals("open_create")){openSettings(false);return;}
        if(action.startsWith("mode:") && !smokeButtons.containsKey(action)){selectMode(action.substring(5));return;}
        if(action.equals("create") && page!=Page.ROOMS && page!=Page.MODES){mode="six";go(Page.ROOMS);}
        if(action.equals("create")){openSettings(false);submitSettings();return;}
        if(action.startsWith("ai_add:")){send("ai_add",Integer.parseInt(action.substring(7)));return;}
        var b=smokeButtons.get(action);if(b==null || !b.active)throw new IllegalStateException("Unavailable UI action "+action);b.onPress();
    }
    /** Test hooks use the exact controls and the same draft as native user input. */
    String smokePage(){return page.name();}
    String smokeModal(){return modal.name();}
    int smokeDraftSeconds(String kind){return switch(kind){case "turn"->draft.turn;case "response"->draft.response;case "selection"->draft.selection;default->throw new IllegalArgumentException(kind);};}
    void smokeSet(String field,String value){
        switch(field){case "name"->draft.name=value;case "password"->draft.password=value;case "joinPassword"->passwordInput=value;case "chat"->chatInput=value;case "search"->search=value;case "heroSearch"->heroSearch=value;default->throw new IllegalArgumentException("Unknown lobby field "+field);}
        rebuildWidgets();
    }
    void smokeAction(String action){
        var b=smokeButtons.get(action);if(b==null || !b.active)throw new IllegalStateException("Unavailable native lobby action "+action);b.onPress();
    }
    void smokeSetField(String field,String value){
        var e=fields.get(field);if(e==null || !e.active)throw new IllegalStateException("Unavailable lobby input "+field);e.setValue(value);
    }
    int[] smokeBounds(String action){var b=smokeButtons.get(action);if(b==null)throw new IllegalArgumentException(action);return new int[]{b.getX(),b.getY(),b.getWidth(),b.getHeight()};}
    void smokeSkinFixture(String uuid,boolean slim){dev.sgs.validation.client.art.PlayerSkinRenderer.installUvFixture(UUID.fromString(uuid),slim);}
    void smokeClearSkinFixture(String uuid){dev.sgs.validation.client.art.PlayerSkinRenderer.clearUvFixture(UUID.fromString(uuid));}
    String smokeSkinDescription(String uuid){return dev.sgs.validation.client.art.PlayerSkinRenderer.describe(uuid);}
    @Override public void removed(){dev.sgs.validation.client.art.PlayerSkinRenderer.clearPreviewCache();super.removed();}
    @Override public void onClose(){LobbyClient.close();minecraft.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}

