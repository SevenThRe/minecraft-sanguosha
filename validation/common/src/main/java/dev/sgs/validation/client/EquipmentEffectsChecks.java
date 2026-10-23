package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.client.art.*;
import dev.sgs.validation.client.layout.ReferenceBattleLayout.Box;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opt-in presentation fixtures; real renderers, no simulated rule acceptance. */
public final class EquipmentEffectsChecks {
    private static int frames,stage;
    private static boolean started;
    private EquipmentEffectsChecks(){}
    public static boolean showingGallery(){return Boolean.getBoolean("sgs.equipmentEffectsSmoke") && Minecraft.getInstance().screen instanceof Gallery;}
    public static boolean tick(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.equipmentEffectsSmoke") || screen.stateForSmoke().snapshot().status()!=1)return false;
        if(screen.stateForSmoke().snapshot().openingHand()!=null || screen.stateForSmoke().snapshot().identityIntro()!=null)return false;
        client.getToasts().clear();
        if(!started){verifyResources(client);started=true;screen.equipmentFixtureForSmoke("",700);return true;}
        if(++frames<8)return true;frames=0;
        if(stage==0){capture(client,Boolean.getBoolean("sgs.weaponVoxelSmoke")?"match-idle":"match-chain");screen.equipmentFixtureForSmoke("qinglong",650);stage++;}
        else if(stage==1){capture(client,"match-weapon");screen.equipmentFixtureForSmoke("jizhi",650);stage++;}
        else{capture(client,"match-skill");client.setScreen(new Gallery());}
        return true;
    }
    private static void capture(Minecraft c,String id){Screenshot.grab(c.gameDirectory,"sgs-equipment-"+id+".png",c.getMainRenderTarget(),m->{});}
    private static void verifyResources(Minecraft client){
        for(String id:new String[]{"zhuge_nu","cixiong","qinggang","hanbing","qinglong","zhangba","guanshi","fangtian","qilin","guding","zhuque","qilin_drawn_bow","qilin_arrow"}){
            try(var input=client.getResourceManager().getResourceOrThrow(ProbeMod.id("textures/gui/equipment/"+id+".png")).open();var image=com.mojang.blaze3d.platform.NativeImage.read(input)){
                if(image.getWidth()!=1254 || image.getHeight()!=1254)throw new IllegalStateException("unexpected voxel dimensions: "+id);
            }catch(java.io.IOException error){throw new IllegalStateException("missing voxel equipment: "+id,error);}
        }
        ProbeMod.LOG.info("SGS_WEAPON_VOXEL_RESOURCES_PASS sprites=13 weapons=11 arrow_shared=true");
    }
    private static final class Gallery extends Screen {
        private static final String[] IDS={"zhuge_nu","cixiong","qinggang","hanbing","qinglong","zhangba","guanshi","fangtian","qilin_gong","guding","zhuque","bagua","renwang","tengjia","baiyin","jizhi"};
        private static final long[] TIMES={220,640,650,700,1450};
        private int scale=1,phase,wait;
        private final int original=Minecraft.getInstance().options.guiScale().get();
        Gallery(){super(Component.literal("装备动效验图"));Minecraft.getInstance().options.guiScale().set(scale);Minecraft.getInstance().resizeDisplay();}
        @Override public void tick(){
            if(++wait<10)return;wait=0;
            capture(minecraft,"gallery-gui"+scale+"-"+TIMES[phase]);
            ProbeMod.LOG.info("SGS_EQUIPMENT_EFFECT_CAPTURE gui={} elapsed={} weapons=11 armor=4 skill_control=1 fixture=true",scale,TIMES[phase]);
            if(++phase==TIMES.length){phase=0;if(++scale>3){
                minecraft.options.guiScale().set(original);minecraft.resizeDisplay();
                ProbeMod.LOG.info("SGS_EQUIPMENT_EFFECT_PASS native=true fixtures=true scales=3 phases=5 match_captures=3 weapons=11 armor=4 voxel_sprites=true");minecraft.stop();return;
            }minecraft.options.guiScale().set(scale);minecraft.resizeDisplay();}
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff101b25);
            InkText.fitted(g,font,"装备动效 · 原生绘制样本（非规则触发验收）",width/2f,3,width-20,11,0xffeadbb8);
            int cw=(width-16)/4,ch=(height-23)/4;
            for(int i=0;i<IDS.length;i++){
                int x=8+i%4*cw,y=20+i/4*ch;
                g.fill(x+1,y+1,x+cw-2,y+ch-2,0xff293640);
                int size=Math.min(ch-18,cw-8),bx=x+(cw-size)/2,by=y+2;
                if(i==15)SkillActivationRenderer.draw(g,font,"集智",new Box(bx,by,size,size),TIMES[phase],0xffe6ad68);
                else{
                    EquipmentActivationRenderer.draw(g,font,IDS[i],new Box(bx,by,size,size),TIMES[phase]);
                    InkText.fitted(g,font,EquipmentActivationRenderer.name(IDS[i]),x+cw/2f,y+ch-13,cw-8,9,0xffeadbb8);
                }
            }
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
