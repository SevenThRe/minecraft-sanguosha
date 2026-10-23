package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Opt-in native UI acceptance: clicks rendered cards, captures actual frames, never changes game state. */
public final class SelectionLiftChecks {
    private static final int[][] VIEWS={{854,480,1},{854,480,2},{1280,720,3}};
    private static final AtomicInteger pending=new AtomicInteger();
    private static int index,stage,ticks,baseY,oldScale,oldWidth,oldHeight,baseHeight;
    private static boolean begun,complete;
    private static boolean windowRestored;
    private static int restoredFrames;
    private static int denseStage,denseIndex,denseTicks,denseCard,expectedPreview=-1;
    public static boolean exercise(Minecraft client,MatchScreen screen){
        if(!Boolean.getBoolean("sgs.uiSmoke") || complete)return true;
        if(!begun){
            begun=true;oldScale=client.options.guiScale().get();oldWidth=client.getWindow().getWidth();oldHeight=client.getWindow().getHeight();
        }
        if(index==VIEWS.length){
            if(!dense(client,screen))return false;
            if(pending.get()!=0)return false;
            if(!windowRestored){
                client.options.guiScale().set(oldScale);client.getWindow().setWindowed(oldWidth,oldHeight);client.resizeDisplay();
                screen.hoverForSmoke(-1,-1);windowRestored=true;return false;
            }
            if(++restoredFrames<5)return false;
            screen.verifySkillGeometryForSmoke();
            Screenshot.grab(client.gameDirectory,"sgs-skill-health-final.png",client.getMainRenderTarget(),message->{});
            complete=true;ProbeMod.LOG.info("SGS_UI_CONTROL_SMOKE_PASS scales=1,2,3 selected_lift=true raised_hitbox=true deselect_restore=true custom_buttons=true");return true;
        }
        if(stage==0){
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(client.getWindow().getWindow());
            int[] config=VIEWS[index];client.options.guiScale().set(config[2]);client.getWindow().setWindowed(config[0],config[1]);client.resizeDisplay();
            client.getToasts().clear();screen.pressForSmoke("cancel");stage=1;ticks=0;return false;
        }
        if(++ticks<5)return false;
        var bounds=screen.firstHandBoundsForSmoke();if(bounds==null)return true; // Wait for an actually playable card, never select a disabled one.
        if(!screen.customControlsForSmoke())throw new IllegalStateException("vanilla button in match UI");
        screen.verifySkillGeometryForSmoke();
        if(stage==1){client.options.guiScale().set(VIEWS[index][2]);client.resizeDisplay();stage=2;ticks=0;return false;}
        if(stage==2){
            if(Math.abs(client.getWindow().getGuiScale()-VIEWS[index][2])>.01)throw new IllegalStateException("requested GUI scale was not applied: "+client.getWindow().getGuiScale());
            baseHeight=screen.height;baseY=bounds[1];capture(client,"normal");
            screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+bounds[3]/2d,0);
            if(screen.selectedCountForSmoke()!=1)throw new IllegalStateException("real card click did not select");
            stage=3;ticks=0;return false;
        }
        if(stage==3){
            if(screen.height!=baseHeight){stage=0;ticks=0;return false;}
            if(bounds[1]>=baseY)throw new IllegalStateException("selected card did not lift");
            capture(client,"selected");
            ProbeMod.LOG.info("SGS_UI_SELECTION_CAPTURE gui={} baseline={} selected={} lift={}",VIEWS[index][2],baseY,bounds[1],baseY-bounds[1]);
            // Click the part above the old top edge: proves the hitbox moved with the artwork.
            screen.mouseClicked(bounds[0]+bounds[2]/2d,bounds[1]+1,0);
            if(screen.selectedCountForSmoke()!=0)throw new IllegalStateException("raised edge did not deselect");
            stage=4;ticks=0;return false;
        }
        if(screen.height!=baseHeight){stage=0;ticks=0;return false;}
        if(bounds[1]!=baseY)throw new IllegalStateException("deselected card did not return gui="+VIEWS[index][2]+" stage="+stage+" expected="+baseY+" actual="+bounds[1]+" selected="+screen.selectedCountForSmoke());
        if(stage==4){screen.focusSkillForSmoke(true);stage=5;ticks=0;return false;}
        capture(client,"skill");screen.focusSkillForSmoke(false);
        index++;stage=0;ticks=0;return false;
    }
    private static boolean dense(Minecraft client,MatchScreen screen){
        if(denseIndex>=VIEWS.length)return true;
        if(denseStage==0){
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(client.getWindow().getWindow());
            int[] config=VIEWS[denseIndex];client.getWindow().setWindowed(config[0],config[1]);client.resizeDisplay();
            denseStage=1;denseTicks=0;return false;
        }
        if(++denseTicks<5)return false;
        if(denseStage==1){
            client.options.guiScale().set(VIEWS[denseIndex][2]);client.resizeDisplay();screen.layoutFixtureForSmoke(10,false);
            denseStage=2;denseTicks=0;return false;
        }
        if(denseStage==2){
            denseCapture(client,"10");screen.layoutFixtureForSmoke(20,false);denseStage=3;denseTicks=0;return false;
        }
        if(denseStage==3){
            denseCapture(client,"20");denseCard=0;denseStage=4;denseTicks=0;return false;
        }
        if(denseStage==4){
            int[] b=screen.handBoundsForSmoke(denseCard);
            if(b==null || !screen.mouseClicked(b[0]+Math.max(1,b[2]/2d),b[1]+2,0))throw new IllegalStateException("dense hand card inaccessible: "+denseCard);
            // Ordinary PLAY replaces the previous choice; multi-cost prompts may retain it.
            // Assert the actual clicked card, not an unrelated assumption about selection count.
            if(!screen.handIndexSelectedForSmoke(denseCard))throw new IllegalStateException("overlap selected wrong card: "+denseCard);
            if(++denseCard==20){denseStage=5;denseTicks=0;}else denseTicks=3;
            return false;
        }
        if(denseStage==5){
            denseCapture(client,"20-selected");screen.layoutFixtureForSmoke(20,false);moveToBadge(client,screen,false);
            denseStage=6;denseTicks=0;return false;
        }
        if(denseStage==6){
            if(screen.previewedSeatForSmoke()!=expectedPreview)throw new IllegalStateException("hand-count hover not reached");
            denseCapture(client,"hand-denied");screen.layoutFixtureForSmoke(20,true);moveToBadge(client,screen,false);
            denseStage=7;denseTicks=0;return false;
        }
        if(screen.previewedSeatForSmoke()!=expectedPreview)throw new IllegalStateException("authorized hand-count hover not reached");
        denseCapture(client,"hand-authorized-fixture");
        ProbeMod.LOG.info("SGS_REFERENCE_LAYOUT_PASS gui={} counts=10,20 every_card_clickable=true selected_card_verified=true hand_badge_hover=true preview=authorized_fixture",VIEWS[denseIndex][2]);
        screen.layoutFixtureForSmoke(0,false);screen.hoverForSmoke(-1,-1);
        denseIndex++;denseStage=0;denseTicks=0;return false;
    }
    private static void moveToBadge(Minecraft client,MatchScreen screen,boolean own){
        var b=screen.badgeBoundsForSmoke(own);expectedPreview=b[4];
        screen.hoverForSmoke(b[0]+b[2]/2,b[1]+b[3]/2);
    }
    private static void denseCapture(Minecraft client,String state){
        pending.incrementAndGet();Screenshot.grab(client.gameDirectory,"sgs-reference-gui"+VIEWS[denseIndex][2]+"-"+state+".png",client.getMainRenderTarget(),message->pending.decrementAndGet());
    }
    private static void capture(Minecraft client,String state){
        pending.incrementAndGet();Screenshot.grab(client.gameDirectory,"sgs-ui-selection-gui"+VIEWS[index][2]+"-"+state+".png",client.getMainRenderTarget(),message->pending.decrementAndGet());
    }
}
