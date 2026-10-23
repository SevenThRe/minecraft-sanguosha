package dev.sgs.validation.client.art;

import static dev.sgs.validation.client.ClientText.tr;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sgs.core.card.CardType;
import dev.sgs.core.card.CardCategory;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Composes reusable card art with instance metadata and non-color-only states. */
public final class CardRenderer {
    public static final int WIDTH = 80;
    public static final int HEIGHT = 112;
    public enum State { NORMAL, AVAILABLE, SELECTED, DISABLED, TARGET }
    private static final ResourceLocation FRAME = texture("card_frame_v3.png");
    public static final ResourceLocation BACKGROUND = texture("table_background_v3.png");
    private static final java.util.Map<String,CardType> TYPES=java.util.Arrays.stream(CardType.values())
        .collect(java.util.stream.Collectors.toUnmodifiableMap(CardType::id,type->type));

    private static ResourceLocation texture(String file) { return ProbeMod.id("textures/gui/" + file); }

    public static void background(GuiGraphics graphics, int width, int height) {
        graphics.blit(BACKGROUND, 0, 0, width, height, 0, 0, 1024, 576, 1024, 576);
        graphics.fill(0, 0, width, height, 0x18060908);
    }

    public static void draw(GuiGraphics graphics, Font font, CardArtCatalog.Art art,
                            String suit, String rank, int x, int y, float scale, State state, boolean faceDown) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        if(faceDown)CardBackRenderer.draw(graphics,0,0,WIDTH,HEIGHT);
        else graphics.blit(FRAME, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        if (!faceDown) {
            graphics.blit(art.texture(), 6, 36, 68, 68, 0, 0, 256, 256, 256, 256);
            int color = suit.equals("heart") || suit.equals("diamond") ? 0xac3039 : 0x242422;
            graphics.drawCenteredString(font, rank, 10, 5, color);
            graphics.blit(texture("suits/" + suit + "_v1.png"), 6, 17, 0, 0, 8, 9, 8, 9);
            // Long names fit deterministically; rules and art are never rasterized together.
            graphics.pose().popPose();
            dev.sgs.validation.client.InkText.fitted(graphics,font,art.name(),x+WIDTH*scale/2,y+7*scale,51*scale,26*scale,0xff30291b);
            graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(scale,scale,1);
            graphics.fill(6, 36, 16, 58, 0xddefdfbc);
            if(dev.sgs.validation.client.ClientText.english()) {
                var type=TYPES.get(art.id());String mark=type==null?"?":switch(type.category()){case BASIC->"B";case TRICK,DELAYED_TRICK->"T";default->"E";};
                graphics.drawString(font,mark,7,43,0x30291b,false);
            } else {
                graphics.drawString(font, art.category().substring(0,1), 7, 38, 0x30291b, false);
                graphics.drawString(font, art.category().substring(1), 7, 48, 0x30291b, false);
            }
        }
        if (state == State.DISABLED) {
            graphics.fill(2, 2, 78, 110, 0xa02a2a2a);
            for (int i = 0; i < 12; i++) graphics.fill(8+i*5, 30+i*5, 11+i*5, 33+i*5, 0xd0dddddd);
            graphics.fill(7, 72, 73, 86, 0xee292727);
            graphics.drawCenteredString(font, tr("sgs.ui.unavailable_beff4a1c"), 40, 75, 0xf0dddd);
        } else if (state != State.NORMAL) {
            int border = switch (state) {
                case AVAILABLE -> 0xffb9cf9b;
                case SELECTED -> 0xffffd273;
                case TARGET -> 0xff8edcdb;
                default -> 0xffe5d6ad;
            };
            graphics.fill(-2, -2, 82, 0, border); graphics.fill(-2,112,82,114,border);
            graphics.fill(-2,0,0,112,border); graphics.fill(80,0,82,112,border);
            String marker = switch (state) { case AVAILABLE -> tr("sgs.ui.available_e91365cf"); case SELECTED -> tr("sgs.ui.selected_9d53bab6"); case TARGET -> tr("sgs.ui.target_1_6a98f1b7"); default -> ""; };
            graphics.fill(7, 72, 73, 86, 0xee172f2c);
            graphics.drawCenteredString(font, marker, 40, 75, 0xfff0d4);
        }
        // Keep rules metadata legible beneath selection banners, but never disclose a face-down card.
        if(!faceDown)drawEquipmentValue(graphics,font,TYPES.get(art.id()));
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    private static void drawEquipmentValue(GuiGraphics graphics,Font font,CardType type){
        if(type==null)return;
        boolean weapon=type.category()==CardCategory.WEAPON;
        String value=switch(type.category()){
            case WEAPON -> Integer.toString(type.weaponRange());
            case HORSE_PLUS -> "+1";
            case HORSE_MINUS -> "−1";
            default -> "";
        };
        if(value.isEmpty())return;
        graphics.fill(weapon?43:48,87,76,weapon?109:104,0xe8efdfbc);
        graphics.pose().pushPose();
        graphics.pose().translate(weapon?65:62,88,0);
        graphics.pose().scale(1.5f,1.5f,1);
        graphics.drawString(font,value,-font.width(value)/2,0,0xff30291b,false);
        graphics.pose().popPose();
        if(weapon){
            graphics.pose().pushPose();graphics.pose().translate(59,102,0);graphics.pose().scale(.65f,.65f,1);
            graphics.drawString(font,"攻击范围",-font.width("攻击范围")/2,0,0xff30291b,false);
            graphics.pose().popPose();
        }
    }
}
