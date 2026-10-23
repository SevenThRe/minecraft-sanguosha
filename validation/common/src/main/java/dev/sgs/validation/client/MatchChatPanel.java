package dev.sgs.validation.client;

import dev.sgs.validation.client.layout.ReferenceBattleLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** Native non-pausing chat overlay; receives input before the underlying card table. */
final class MatchChatPanel {
    private final Font font;
    private final EditBox input;
    private int x,y,w,h,maxScroll,viewport;
    private record Row(FormattedCharSequence text,int color){}
    private static final String[] QUICK={"wait","nice","help","defend","thanks","again"};
    MatchChatPanel(Font font){
        this.font=font;input=new EditBox(font,0,0,100,16,Component.translatable("sgs.chat.input"));
        input.setMaxLength(240);input.setBordered(false);input.setTextColor(0xffecd9b4);
        input.setResponder(MatchChatClient::edit);input.setHint(Component.translatable("sgs.chat.placeholder"));
    }
    void layout(ReferenceBattleLayout board){
        w=Math.min(board.width()-16,Math.max(175,Math.round(450*board.unit())));
        h=Math.min(board.height()-40,Math.max(150,Math.round(340*board.unit())));
        x=Math.max(8,board.social().x());y=Math.max(22,board.hand().y()-h-8);
        input.setX(x+9);input.setY(y+h-23);input.setWidth(Math.max(40,w-64));
    }
    void open(){MatchChatClient.open=true;input.setFocused(true);}
    void close(){MatchChatClient.open=false;input.setFocused(false);}
    private boolean hit(double mx,double my,int bx,int by,int bw,int bh){return mx>=bx&&mx<bx+bw&&my>=by&&my<by+bh;}
    private static String tr(String key){return Component.translatable("sgs.chat."+key).getString();}
    void render(GuiGraphics g,int mx,int my,float partial,ReferenceBattleLayout board){
        layout(board);MatchChatClient.tick();
        g.pose().pushPose();g.pose().translate(0,0,600);
        if(!MatchChatClient.open){
            int unread=MatchChatClient.unread();if(unread>0){var b=board.socialButton(5);String text=unread>99?"99+":Integer.toString(unread);
                int bw=font.width(text)+6;g.fill(b.right()-bw,b.y()-3,b.right()+2,b.y()+9,0xff973e2a);g.drawString(font,text,b.right()-bw+3,b.y()-2,0xffffffff,false);}
            g.pose().popPose();return;
        }
        if(!input.getValue().equals(MatchChatClient.draft)){int cursor=input.getCursorPosition();input.setValue(MatchChatClient.draft);input.moveCursorTo(Math.min(cursor,input.getValue().length()),false);}
        g.fill(x,y,x+w,y+h,0xeb281d12);g.renderOutline(x,y,w,h,0xff987346);
        g.fill(x+1,y+1,x+w-1,y+23,0xf0362819);
        g.fill(x+1+(MatchChatClient.quick?54:0),y+1,x+(MatchChatClient.quick?110:55),y+22,0xffc29854);
        g.drawString(font,tr("match"),x+15,y+8,MatchChatClient.quick?0xffdec69b:0xff30200f,false);
        g.drawString(font,tr("quick"),x+63,y+8,MatchChatClient.quick?0xff30200f:0xffdec69b,false);
        g.drawString(font,"×",x+w-15,y+7,0xffd9be8b,false);
        viewport=h-76;
        if(MatchChatClient.quick){
            for(int i=0;i<QUICK.length;i++){int bx=x+8+(i%2)*(w-16)/2,by=y+30+(i/2)*24,bw=(w-20)/2;
                g.fill(bx,by,bx+bw,by+20,0xff45321f);g.renderOutline(bx,by,bw,20,0xff80603b);
                g.drawCenteredString(font,tr("quick."+QUICK[i]),bx+bw/2,by+6,0xffead6ae);}
        }else{
            var rows=new ArrayList<Row>();
            for(var message:MatchChatClient.messages.values()){
                String name=(message.seat()+1)+" · "+message.name();
                int color=Minecraft.getInstance().getUser().getProfileId().equals(message.sender())?0xffa9cbc0:0xffd9b36d;
                rows.add(new Row(Component.literal(font.plainSubstrByWidth(name,w-24)).getVisualOrderText(),color));
                for(var line:font.split(Component.literal(message.text()),w-24))rows.add(new Row(line,0xffecdcc2));
                rows.add(new Row(FormattedCharSequence.EMPTY,0));
            }
            maxScroll=Math.max(0,rows.size()*12-viewport);
            MatchChatClient.scroll=MatchChatClient.follow?maxScroll:Math.clamp(MatchChatClient.scroll,0,maxScroll);
            if(MatchChatClient.follow)MatchChatClient.read();
            g.enableScissor(x+5,y+26,x+w-5,y+26+viewport);
            for(int i=0;i<rows.size();i++){var row=rows.get(i);int ry=y+27+i*12-MatchChatClient.scroll;
                if(ry+10>=y+26&&ry<y+26+viewport)g.drawString(font,row.text(),x+9,ry,row.color(),false);}
            if(rows.isEmpty())g.drawCenteredString(font,tr("empty"),x+w/2,y+42,0xff9e8967);
            g.disableScissor();
            if(maxScroll>0){int thumb=Math.max(10,viewport*viewport/(maxScroll+viewport));int top=y+26+(viewport-thumb)*MatchChatClient.scroll/maxScroll;
                g.fill(x+w-5,y+26,x+w-3,y+26+viewport,0xff4d3924);g.fill(x+w-5,top,x+w-3,top+thumb,0xffac8650);}
        }
        String status=MatchChatClient.pending>0?tr("sending"):!MatchChatClient.error.isEmpty()?tr("error."+MatchChatClient.error):!MatchChatClient.enabled?tr("error.DISABLED"):"";
        if(status.isEmpty()&&!MatchChatClient.draft.isEmpty()&& !dev.sgs.core.social.MatchChat.invalid(MatchChatClient.draft).isEmpty())status=tr("error."+dev.sgs.core.social.MatchChat.invalid(MatchChatClient.draft));
        g.drawString(font,font.plainSubstrByWidth(status,w-16),x+8,y+h-43,0xffd5ac78,false);
        if(!MatchChatClient.quick&&!MatchChatClient.follow&&MatchChatClient.unread()>0){String label="↓ "+MatchChatClient.unread()+" "+tr("new");
            g.fill(x+w-100,y+h-58,x+w-7,y+h-44,0xff674a28);g.drawString(font,label,x+w-96,y+h-55,0xfff2d7a4,false);}
        g.fill(x+6,y+h-29,x+w-53,y+h-6,0xb0160f09);g.renderOutline(x+6,y+h-29,w-59,23,0xff715535);
        input.setEditable(MatchChatClient.enabled&&MatchChatClient.pending==0);input.render(g,mx,my,partial);
        g.fill(x+w-48,y+h-29,x+w-6,y+h-6,MatchChatClient.canSend()?0xffd8a75d:0xff725737);
        g.drawCenteredString(font,tr("send"),x+w-27,y+h-21,0xff30200e);
        g.pose().popPose();
    }
    boolean click(double mx,double my,int button){
        if(!MatchChatClient.open)return false;
        if(!hit(mx,my,x,y,w,h)){close();return true;}
        if(button!=0)return true;
        if(hit(mx,my,x+w-23,y,23,23)){close();return true;}
        if(hit(mx,my,x,y,110,23)){MatchChatClient.quick=mx>=x+55;return true;}
        if(MatchChatClient.quick){for(int i=0;i<QUICK.length;i++)if(hit(mx,my,x+8+(i%2)*(w-16)/2,y+30+(i/2)*24,(w-20)/2,20)){
            input.setValue(tr("quick."+QUICK[i]));input.moveCursorToEnd(false);input.setFocused(true);MatchChatClient.quick=false;return true;}}
        if(hit(mx,my,x+w-48,y+h-29,42,23)){MatchChatClient.send();return true;}
        if(!MatchChatClient.follow&&MatchChatClient.unread()>0&&hit(mx,my,x+w-100,y+h-58,93,14)){MatchChatClient.follow=true;return true;}
        input.mouseClicked(mx,my,button);input.setFocused(true);return true;
    }
    boolean scroll(double amount){if(!MatchChatClient.open)return false;MatchChatClient.scroll=Math.clamp(MatchChatClient.scroll-(int)(amount*24),0,maxScroll);MatchChatClient.follow=MatchChatClient.scroll>=maxScroll;return true;}
    boolean key(int key,int scan,int modifiers){
        if(!MatchChatClient.open)return false;
        if(key==256){close();return true;}
        if(key==257||key==335){MatchChatClient.send();return true;}
        input.keyPressed(key,scan,modifiers);return true;
    }
    boolean character(char c,int modifiers){if(!MatchChatClient.open)return false;input.charTyped(c,modifiers);return true;}
    void draftForSmoke(String text){input.setValue(text);}
}
