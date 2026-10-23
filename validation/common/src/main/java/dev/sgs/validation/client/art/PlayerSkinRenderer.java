package dev.sgs.validation.client.art;

import com.mojang.authlib.GameProfile;
import dev.sgs.validation.ProbeMod;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * UUID-bound vanilla player rendering. Minecraft owns skin acquisition, async loading and caching.
 * Detached vanilla RemotePlayer models avoid borrowing another hero, altering a world player,
 * displaying their armour, or adding entities to the world. All skin outer layers are shown.
 */
public final class PlayerSkinRenderer {
    private static final int LIMIT=128;
    private static final Map<UUID,PreviewPlayer> MODELS=new LinkedHashMap<>();
    private static final Map<UUID,PlayerSkin> FIXTURES=new LinkedHashMap<>();
    private static ClientLevel modelLevel;
    private static final ResourceLocation TEST_UV=ProbeMod.id("textures/gui/lobby/test_skin_uv64.png");
    private PlayerSkinRenderer(){}
    private record Resolved(GameProfile profile,PlayerSkin skin,boolean fixture,String source){}

    private static String modelName(PlayerSkin skin){return skin.model()==PlayerSkin.Model.SLIM?"slim":"wide";}
    private static UUID parse(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException | NullPointerException ignored){return null;}}
    private static Resolved resolve(UUID id){
        if(id==null)return null;
        var mc=Minecraft.getInstance();
        var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(id);
        var entity=mc.level==null?null:mc.level.getPlayerByUUID(id);
        var live=entity instanceof net.minecraft.client.player.AbstractClientPlayer player?player:null;
        GameProfile profile=live!=null?live.getGameProfile():info!=null?info.getProfile():mc.player!=null && id.equals(mc.player.getUUID())?mc.player.getGameProfile():null;
        if(FIXTURES.containsKey(id))return new Resolved(profile==null?new GameProfile(id,"TEST_UV64"):profile,FIXTURES.get(id),true,"TEST_UV64");
        if(profile==null)return null;
        PlayerSkin skin=live!=null?live.getSkin():info!=null?info.getSkin():mc.player.getSkin();
        boolean fallback=DefaultPlayerSkin.get(id).texture().equals(skin.texture());
        return new Resolved(profile,skin,false,fallback?"minecraft-default":live!=null?"minecraft-live-player":"minecraft-profile-cache");
    }
    public static void face(GuiGraphics g,Font font,String uuid,int x,int y,int size){
        var resolved=resolve(parse(uuid));
        g.fill(x-1,y-1,x+size+1,y+size+1,0xffb99358);
        if(resolved==null){g.fill(x,y,x+size,y+size,0xff524b3b);g.drawCenteredString(font,"玩家",x+size/2,y+size/2-4,0xffe9c584);return;}
        PlayerFaceRenderer.draw(g,resolved.skin().texture(),x,y,size);
        if(resolved.fixture())g.drawString(font,"TEST",x,y+size-8,0xffff72dc,false);
    }
    /** Draw a complete vanilla model; coordinates use the caller's GUI pose transform. */
    public static void portrait(GuiGraphics g,Font font,String uuid,int x,int y,int width,int height){
        var mc=Minecraft.getInstance();var id=parse(uuid);var resolved=resolve(id);
        if(resolved==null || mc.level==null){
            g.fill(x,y,x+width,y+height,0xff4a4234);
            g.drawCenteredString(font,resolved==null?"皮肤资料未加载":"人物场景未加载",x+width/2,y+height/2-5,0xffd8c29b);
            return;
        }
        if(modelLevel!=mc.level){MODELS.clear();modelLevel=mc.level;}
        var player=MODELS.get(id);
        if(player==null){
            if(MODELS.size()>=LIMIT)MODELS.remove(MODELS.keySet().iterator().next());
            player=new PreviewPlayer(mc.level,resolved.profile(),resolved.skin());MODELS.put(id,player);
        }
        player.skin=resolved.skin();
        // Vanilla helper supplies the correct UVs, model geometry, lighting, clipping and pose restore.
        // Keeping the model detached also makes previews independent of live sneaking/swimming/equipment.
        int modelScale=Math.max(8,Math.round(height*.47f));
        g.flush();
        InventoryScreen.renderEntityInInventoryFollowsMouse(g,x,y,x+width,y+height,modelScale,0f,
            x+width*.5f-10f,y+height*.5f,player);
        if(resolved.fixture()){
            g.fill(x,y+height-11,x+width,y+height,0xdf351732);
            g.drawCenteredString(font,"TEST UV64 · "+modelName(resolved.skin()),x+width/2,y+height-9,0xffff92e1);
        }else if(resolved.source().equals("minecraft-default")){
            g.fill(x,y+height-11,x+width,y+height,0xc32c251d);
            g.drawCenteredString(font,"MC 默认皮肤",x+width/2,y+height-9,0xffcfb989);
        }
    }
    private static final class PreviewPlayer extends RemotePlayer {
        private PlayerSkin skin;
        PreviewPlayer(ClientLevel level,GameProfile profile,PlayerSkin skin){super(level,profile);this.skin=skin;setSilent(true);}
        @Override public PlayerSkin getSkin(){return skin;}
        @Override public boolean isModelPartShown(PlayerModelPart part){return true;}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean isCreative(){return false;}
    }
    /** Explicit test-only override, never installed automatically or sent to another player. */
    public static void installUvFixture(UUID player,boolean slim){
        if(FIXTURES.size()>=LIMIT)FIXTURES.remove(FIXTURES.keySet().iterator().next());
        FIXTURES.put(player,new PlayerSkin(TEST_UV,"TEST_UV64",null,null,slim?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,false));
    }
    public static void clearUvFixture(UUID player){FIXTURES.remove(player);MODELS.remove(player);}
    public static void clearUvFixtures(){FIXTURES.clear();clearPreviewCache();}
    public static void clearPreviewCache(){MODELS.clear();modelLevel=null;}
    /** Source metadata is not proof of custom-skin runtime acceptance; screenshots remain required. */
    public static String describe(String uuid){
        var r=resolve(parse(uuid));return r==null?"unavailable":r.source()+";model="+modelName(r.skin())+";texture="+r.skin().texture();
    }
}
