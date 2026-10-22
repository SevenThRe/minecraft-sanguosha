package dev.sgs.validation.client.art;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.sgs.validation.ProbeMod;
import dev.sgs.validation.client.layout.ReferenceBattleLayout.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Approved voxel sprites. Entry is a committed public equipment event, never a card-use cue. */
public final class EquipmentActivationRenderer {
    public static final int DURATION=2200;
    private static final int TEX=1254;
    private static final ResourceLocation STONE=ResourceLocation.withDefaultNamespace("textures/block/stone.png");
    private static final ResourceLocation ICE=ResourceLocation.withDefaultNamespace("textures/block/ice.png");
    private static final ResourceLocation FIRE=ResourceLocation.withDefaultNamespace("textures/block/fire_0.png");
    private EquipmentActivationRenderer(){}
    public static String equipmentId(String id){return switch(id){
        case "baiyin_leave"->"baiyin";
        case "zhuge_nu","cixiong","qinggang","hanbing","qinglong","zhangba","guanshi","fangtian","qilin_gong","guding","zhuque","bagua","renwang","tengjia","baiyin"->id;
        default->"";
    };}
    public static boolean supports(String id){return !equipmentId(id).isEmpty();}
    public static String name(String id){return switch(equipmentId(id)){
        case "zhuge_nu"->"诸葛连弩";case "cixiong"->"雌雄双股剑";case "qinggang"->"青釭剑";case "hanbing"->"寒冰剑";
        case "qinglong"->"青龙偃月刀";case "zhangba"->"丈八蛇矛";case "guanshi"->"贯石斧";case "fangtian"->"方天画戟";
        case "qilin_gong"->"麒麟弓";case "guding"->"古锭刀";case "zhuque"->"朱雀羽扇";case "bagua"->"八卦阵";
        case "renwang"->"仁王盾";case "tengjia"->"藤甲";case "baiyin"->"白银狮子";default->id;
    };}
    private static ResourceLocation texture(String id){return ProbeMod.id("textures/gui/equipment/"+id+".png");}
    private static String spriteId(String id){return id.equals("qilin_gong")?"qilin":id;}
    private static float smooth(float value){float t=Math.clamp(value,0,1);return t*t*(3-2*t);}
    private record Pose(float angle,float x,float y,float sx,float sy){}
    private static Pose pose(String id,long at){
        float a=smooth(at/360f),b=smooth((at-360)/350f),c=smooth((at-1000)/650f);
        return switch(id){
            case "qinglong"->new Pose(-.48f*a+1.28f*b-.80f*c,22*b*(1-c),0,1,1);
            case "guanshi"->new Pose(-.83f*a+1.12f*b-.29f*c,-15*b*(1-c),-35*a+64*b-29*c,1,1);
            case "qilin_gong"->{float shoot=smooth((at-650)/150f);yield new Pose(0,-18*a+28*shoot-10*c,0,1-.045f*a+.045f*shoot,1+.025f*a-.025f*shoot);}
            case "zhuge_nu"->{float recoil=0;for(int start:new int[]{430,650,870}){float t=(at-start)/150f;if(t>0&&t<1)recoil+=(float)Math.sin(t*Math.PI);}yield new Pose(-.045f*recoil,-14*recoil,8*recoil,1,1);}
            case "cixiong"->new Pose(-.18f*a+.40f*b-.22f*c,0,-12*(float)Math.sin(b*Math.PI)*(1-c),1,1);
            case "qinggang"->new Pose(.18f*a-.28f*b+.10f*c,60*b*(1-c),-25*b*(1-c),1,1);
            case "hanbing"->new Pose(-.3f*a+.75f*b-.45f*c,10*b*(1-c),10*b*(1-c),1,1);
            case "zhangba"->new Pose(.08f*a-.08f*b,-18*a+72*b-54*c,18*a-72*b+54*c,1,1);
            case "fangtian"->new Pose(-.5f*a+1.12f*b-.62f*c,10*b*(1-c),0,1,1);
            case "guding"->{float wind=smooth(at/440f),hit=smooth((at-440)/220f);yield new Pose(-.5f*wind+1.05f*hit-.55f*c,12*hit*(1-c),-15*wind+37*hit-22*c,1,1);}
            case "zhuque"->new Pose(-.22f*a+.62f*b-.40f*c,14*b*(1-c),-7*b*(1-c),1,1);
            default->new Pose(0,0,0,1,1);
        };
    }
    private static float pivotX(String id){return switch(id){case "qilin_gong"->.58f;case "cixiong"->.5f;case "zhuque"->.70f;case "zhuge_nu"->.38f;case "guding"->.27f;case "guanshi","qinggang","hanbing"->.28f;default->.29f;};}
    private static float pivotY(String id){return switch(id){case "qilin_gong"->.50f;case "cixiong"->.65f;case "zhuque"->.78f;case "zhuge_nu"->.62f;case "guding"->.73f;case "guanshi","qinggang","hanbing"->.72f;default->.71f;};}
    private static void blit(GuiGraphics g,ResourceLocation resource,int x,int y,int width,int height,float alpha){
        if(alpha<=0)return;g.setColor(1,1,1,Math.clamp(alpha,0,1));g.blit(resource,x,y,width,height,0,0,TEX,TEX,TEX,TEX);g.setColor(1,1,1,1);
    }
    private static void sprite(GuiGraphics g,String id,long at,float alpha){
        Pose p=pose(id,at);float px=pivotX(id)*1000,py=pivotY(id)*1000;
        g.pose().pushPose();g.pose().translate(px+p.x*1000/460f,py+p.y*1000/460f,0);
        g.pose().mulPose(Axis.ZP.rotationDegrees((float)Math.toDegrees(p.angle)));g.pose().scale(p.sx,p.sy,1);
        if(id.equals("qilin_gong")){
            float blend=smooth(at/400f)*(1-smooth((at-650)/100f));
            blit(g,texture("qilin"),Math.round(-px),Math.round(-py),1000,1000,alpha*(1-blend));
            blit(g,texture("qilin_drawn_bow"),Math.round(-px),Math.round(-py),1000,1000,alpha*blend);
        }else blit(g,texture(spriteId(id)),Math.round(-px),Math.round(-py),1000,1000,alpha);
        g.pose().popPose();
    }
    public static void draw(GuiGraphics g,Font font,String eventId,Box box,long elapsed){
        String id=equipmentId(eventId);if(id.isEmpty() || elapsed<0 || elapsed>=DURATION || box.width()<=0 || box.height()<=0)return;
        float alpha=smooth(elapsed/120f)*(1-smooth((elapsed-1700)/500f));
        // Defense keeps its existing card illustration; rejected geometric shield icons are gone.
        if(id.equals("bagua")||id.equals("renwang")||id.equals("tengjia")||id.equals("baiyin")){
            RenderSystem.enableBlend();g.setColor(1,1,1,alpha);int size=Math.round(box.width()*.65f);
            g.blit(ProbeMod.id("textures/gui/cards/"+id+"_v1.png"),box.x()+(box.width()-size)/2,box.y()+(box.height()-size)/2,size,size,0,0,256,256,256,256);
            g.setColor(1,1,1,1);RenderSystem.disableBlend();return;
        }
        int margin=Math.max(2,box.width()/3);
        g.enableScissor(box.x()-margin,box.y()-margin,box.right()+margin,box.bottom()+margin);
        g.pose().pushPose();g.pose().translate(box.x(),box.y(),0);g.pose().scale(box.width()/1000f,box.height()/1000f,1);RenderSystem.enableBlend();
        try{
            if((id.equals("qinglong")||id.equals("fangtian")||id.equals("qinggang")||id.equals("hanbing")) && elapsed>330 && elapsed<1050)
                for(int k=3;k>0;k--)sprite(g,id,Math.max(0,elapsed-k*40),alpha*(.10f-k*.02f));
            if(id.equals("guanshi")||id.equals("guding")||id.equals("hanbing"))fragments(g,elapsed,id.equals("hanbing")?ICE:STONE,alpha);
            sprite(g,id,elapsed,alpha);
            if(id.equals("zhuque"))flames(g,elapsed,alpha);
            if(id.equals("qilin_gong") && elapsed>100 && elapsed<1500)arrow(g,elapsed,alpha);
        }finally{g.setColor(1,1,1,1);RenderSystem.disableBlend();g.pose().popPose();g.disableScissor();}
    }
    private static void arrow(GuiGraphics g,long elapsed,float alpha){
        // The exact same registered PNG is drawn while nocked and while flying.
        // At release freeze scale and angle; subsequent bow recoil cannot reshape the arrow.
        Pose p=pose("qilin_gong",Math.min(elapsed,650));float flight=Math.max(0,elapsed-650)/850f;
        float fade=smooth((elapsed-100)/120f)*(1-smooth((elapsed-1250)/250f));
        g.pose().pushPose();g.pose().translate(580+p.x*1000/460f+flight*1900,500+p.y*1000/460f,0);g.pose().scale(p.sx,p.sy,1);
        blit(g,texture("qilin_arrow"),Math.round(55.7f-580),31-500,934,934,alpha*fade);g.pose().popPose();
    }
    private static void fragments(GuiGraphics g,long elapsed,ResourceLocation image,float alpha){
        float t=(elapsed-640)/850f;if(t<=0||t>=1)return;
        for(int i=0;i<12;i++){
            double angle=i*2.399;int size=Math.round(32*(1-t*.55f));float distance=220*t;
            g.pose().pushPose();g.pose().translate(650+Math.cos(angle)*distance,540+Math.sin(angle)*distance*.45+t*t*120,0);g.pose().mulPose(Axis.ZP.rotationDegrees((float)Math.toDegrees(angle+t*5)));
            g.setColor(1,1,1,alpha*(1-t));g.blit(image,-size/2,-size/2,size,size,0,0,16,16,16,16);g.pose().popPose();
        }g.setColor(1,1,1,1);
    }
    private static void flames(GuiGraphics g,long elapsed,float alpha){
        if(elapsed<420||elapsed>1550)return;
        for(int i=0;i<7;i++){
            float age=(elapsed-420-i*65)/850f;if(age<0||age>1)continue;
            int size=Math.round(100-35*age),x=Math.round(150+i*100+100*age),y=Math.round(370-300*age),frame=(int)(elapsed/55+i)%32;
            g.setColor(1,1,1,alpha*(1-age)*.8f);g.blit(FIRE,x,y,size,size,0,frame*16,16,16,16,512);
        }g.setColor(1,1,1,1);
    }
}
