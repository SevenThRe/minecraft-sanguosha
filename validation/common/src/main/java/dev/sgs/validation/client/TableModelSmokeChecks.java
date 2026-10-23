package dev.sgs.validation.client;

import dev.sgs.validation.ProbeMod;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Opt-in native baked-model capture. Does not change normal client screens. */
public final class TableModelSmokeChecks {
    private static int ticks;
    private static boolean opened;

    static void initialize() {
        if (Boolean.getBoolean("sgs.tableModelSmoke"))
            Balm.getEvents().onTickEvent(TickType.Client, TickPhase.End, TableModelSmokeChecks::tick);
    }

    private static void tick(Minecraft client) {
        if (!opened && client.screen instanceof TitleScreen && ++ticks > 40) {
            verifyShape();
            opened = true;
            client.getWindow().setWindowed(1672, 941);
            client.options.guiScale().set(2);
            client.resizeDisplay();
            client.setScreen(new Preview());
        }
    }

    private static void verifyShape() {
        var state=ProbeMod.table.defaultBlockState();
        for(var direction:new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH,
                net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST}) {
            var facing=state.setValue(dev.sgs.validation.TableBlock.FACING,direction);
            var bounds=facing.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).bounds();
            boolean acrossX=direction.getAxis()==net.minecraft.core.Direction.Axis.Z;
            double width=acrossX?bounds.getXsize():bounds.getZsize();
            double depth=acrossX?bounds.getZsize():bounds.getXsize();
            if(Math.abs(width-1.75)>1e-6 || Math.abs(depth-1.125)>1e-6)
                throw new IllegalStateException("Table collision does not follow facing: "+direction+" "+bounds);
            var rotated=facing;
            for(int i=0;i<4;i++)rotated=rotated.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
            if(!rotated.equals(facing))throw new IllegalStateException("Table rotation does not round trip");
        }
        ProbeMod.LOG.info("SGS_TABLE_MODEL_SHAPE_PASS facings=4 width=1.75 depth=1.125");
    }

    private static final class Preview extends Screen {
        private int frames;
        private Preview() { super(Component.literal("SGS model preview")); }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff191b20);
            graphics.drawCenteredString(font, "SGS / NATIVE MODEL PREVIEW", width/2, 22, 0xd4ad65);
            item(graphics, new ItemStack(ProbeMod.table), width*.29f, height*.48f, 13);
            item(graphics, new ItemStack(ProbeMod.deck), width*.74f, height*.48f, 12);
            graphics.drawCenteredString(font, Component.translatable("block.sgs.card_table"), (int)(width*.29f), height-65, 0xe4d6ba);
            graphics.drawCenteredString(font, Component.translatable("item.sgs.deck_pack"), (int)(width*.74f), height-65, 0xe4d6ba);
            if (++frames == 35) {
                Screenshot.grab(minecraft.gameDirectory, "sgs-table-models.png", minecraft.getMainRenderTarget(), message -> {});
                ProbeMod.LOG.info("SGS_TABLE_MODEL_CAPTURE native_baked_models=true");
            }
            if (frames == 55) minecraft.stop();
        }

        private void item(GuiGraphics graphics, ItemStack stack, float x, float y, float scale) {
            if (minecraft.getItemRenderer().getModel(stack, null, null, 0) == minecraft.getModelManager().getMissingModel())
                throw new IllegalStateException("Missing baked model: " + stack);
            graphics.pose().pushPose();
            graphics.pose().translate(x-8*scale, y-8*scale, 0);
            graphics.pose().scale(scale, scale, scale);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        }
    }
}
