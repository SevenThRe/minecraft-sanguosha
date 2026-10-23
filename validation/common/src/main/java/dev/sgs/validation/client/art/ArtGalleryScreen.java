package dev.sgs.validation.client.art;

import java.util.ArrayList;
import java.util.List;
import dev.sgs.core.addon.HeroCatalog;
import dev.sgs.core.addon.HeroPresentation;
import dev.sgs.core.hero.StandardHeroes;
import dev.sgs.validation.ProbeMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import dev.sgs.validation.client.widget.SgsButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit art inspection screen, not a simulated match or fake lobby. */
public final class ArtGalleryScreen extends Screen {
    private List<CardArtCatalog.Art> cards = List.of();
    private final List<Hit> hits = new ArrayList<>();
    private boolean statePage;
    private boolean heroPage;
    private int standardHeroPage = -1;
    private final List<HeroPresentation> standardHeroes = HeroCatalog.builtins().present(
            StandardHeroes.CATALOG.stream().map(hero -> hero.id()).toList());
    private int page;
    private final Screen parent;
    private record Hit(int x, int y, int width, int height, String description) {}

    public ArtGalleryScreen(Screen parent) { super(Component.literal("三国杀 · 素材验收")); this.parent = parent; }

    @Override protected void init() {
        cards = CardArtCatalog.load(minecraft.getResourceManager());
        addRenderableWidget(SgsButton.make(Component.literal("卡牌 / 状态"), button -> { statePage = !statePage; heroPage = false; standardHeroPage = -1; })
                .bounds(Math.max(8, width / 2 - 110), height - 28, 104, 20).build());
        addRenderableWidget(SgsButton.make(Component.literal("武将·体力"), button -> { heroPage = !heroPage; standardHeroPage = -1; })
                .bounds(width / 2 + 6, height - 28, 104, 20).build());
        if (cards.size() > 4) {
            addRenderableWidget(SgsButton.make(Component.literal("<"), button -> { if (standardHeroPage >= 0) standardHeroPage = Math.floorMod(standardHeroPage - 1, heroPageCount()); else page = Math.floorMod(page - 1, (cards.size()+3)/4); })
                    .bounds(8,height-28,24,20).build());
            addRenderableWidget(SgsButton.make(Component.literal(">"), button -> { if (standardHeroPage >= 0) standardHeroPage = (standardHeroPage + 1) % heroPageCount(); else page = (page+1)%((cards.size()+3)/4); })
                    .bounds(width-32,height-28,24,20).build());
        }
        if (width >= 420) addRenderableWidget(SgsButton.make(Component.literal("武将图册"), button -> showStandardHeroPage(0))
                .bounds(Math.max(36, width / 2 - 194), height - 28, 66, 20).build());
        addRenderableWidget(SgsButton.make(Component.literal("返回牌桌"), button -> onClose())
                .bounds(width - 86, 8, 78, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CardRenderer.background(graphics, width, height);
        graphics.fill(10, 8, Math.min(width - 94, 265), 32, 0xe13a291d);
        float chromeScale = minecraft.getWindow().getGuiScale() < 1.5 && width >= 640 ? 2f : 1f;
        graphics.pose().pushPose();
        graphics.pose().translate(16, 12, 0); graphics.pose().scale(chromeScale, chromeScale, 1);
        graphics.drawString(font, title, 0, 0, 0xffe4af, false);
        graphics.pose().popPose();
        if (standardHeroPage >= 0) {
            renderStandardHeroes(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        centered(graphics, heroPage ? "武将资源验收 · 体力示例 3/4；非真实对局" : statePage ? "状态使用文字、图形和描边共同表达" : "墨绘纸牌 · 金苹果桃 / 力量药水酒 · 素材验收", 42, chromeScale, 0xffead0);
        int offset = page * 4;
        int shown = Math.min(4,cards.size()-offset);
        int count = heroPage ? 1 : statePage ? 5 : shown + 1;
        float scale = Math.min(2f, Math.min((width - 40f - (count-1)*8) / (count*80f), (height - 142f) / 112f));
        scale = Math.max(0.35f, scale);
        int cardWidth = Math.round(80 * scale);
        int cardHeight = Math.round(112 * scale);
        int rowWidth = count * cardWidth + (count-1) * 8;
        int startX = (width - rowWidth) / 2;
        int top = 66;
        hits.clear();
        for (int index = 0; index < count; index++) {
            boolean back = !statePage && index == shown;
            var art = cards.get(statePage || back || heroPage ? 0 : offset + index);
            var state = statePage ? CardRenderer.State.values()[index] : CardRenderer.State.NORMAL;
            String suit = index % 2 == 0 ? "spade" : "heart";
            int x = startX + index * (cardWidth + 8);
            if (heroPage) HeroRenderer.draw(graphics,font,x,top,scale,3,4);
            else CardRenderer.draw(graphics, font, art, suit, index % 2 == 0 ? "7" : "A", x, top, scale, state, back);
            String description = heroPage ? "刘备 · 原创武将插画 · 原版红心纹理 · 示例体力3/4" : back ? "统一卡背 · 未公开牌面" : art.name() + " · " + art.category() + " · " + art.element() + " · " + (suit.equals("spade") ? "黑桃7" : "红桃A");
            hits.add(new Hit(x, top, cardWidth, cardHeight, description));
        }
        graphics.fill(14, top + cardHeight + 10, width - 14, height - 36, 0xdd35251c);
        String detail = heroPage ? "红心按数据绘制；满心和空心区分当前体力与上限" : "悬停检查牌名与类别；墨绘主体不包含规则和实例文字";
        for (Hit hit : hits) if (mouseX >= hit.x && mouseX < hit.x+hit.width && mouseY >= hit.y && mouseY < hit.y+hit.height) detail = hit.description;
        int detailY = top + cardHeight + 16;
        centered(graphics, detail, detailY, chromeScale, 0xfff1d3);
        centered(graphics, "独立原图已保存 · 未从概念截图切片 · 尚非完整对局", detailY + Math.round(font.lineHeight * chromeScale) + 3, chromeScale, 0xd2b98d);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void centered(GuiGraphics graphics, String text, int y, float scale, int color) {
        int maxWidth = (int)((width - 32) / scale);
        String visible = font.plainSubstrByWidth(text, maxWidth);
        if (!visible.equals(text)) visible = font.plainSubstrByWidth(text, maxWidth - 8) + "…";
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, y, 0); graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(font, visible, 0, 0, color);
        graphics.pose().popPose();
    }

    private void renderStandardHeroes(GuiGraphics graphics) {
        centered(graphics, standardHeroes.size()+"将资源验收 / 非对局 · 第 " + (standardHeroPage + 1) + "/"+heroPageCount()+" 页 · 全竖图 + 宽裁切", 42, 1f, 0xffead0);
        hits.clear();
        int gap = 12;
        int column = Math.max(20, (width - 40 - 4 * gap) / 5);
        int portraitWidth = Math.min(column - 4, Math.max(10, (height - 174) * 5 / 7));
        int portraitHeight = portraitWidth * 7 / 5;
        int wideHeight = Math.max(16, Math.min(60, (height - 142 - portraitHeight)));
        int start = (width - (5 * column + 4 * gap)) / 2;
        var heroes = shownStandardHeroes();
        for (int index = 0; index < heroes.size(); index++) {
            var hero = heroes.get(index);
            int x = start + index * (column + gap);
            int px = x + (column - portraitWidth) / 2;
            graphics.drawCenteredString(font, hero.name(), x + column / 2, 60, 0xffe6bd);
            HeroRenderer.portrait(graphics, font, hero.id(), px, 76, portraitWidth, portraitHeight, 0xffbc9550, true);
            hits.add(new Hit(px - 2, 74, portraitWidth + 4, portraitHeight + 4, hero.id() + " vertical"));
            int wideY = 76 + portraitHeight + 20;
            HeroRenderer.portrait(graphics, font, hero.id(), x + 2, wideY, column - 4, wideHeight, 0xffbc9550, true);
            hits.add(new Hit(x, wideY - 2, column, wideHeight + 4, hero.id() + " wide"));
        }
        centered(graphics, "同一原生纹理的两种比例取样 · 不含选将、体力或技能结算", height - 46, 1f, 0xd2b98d);
    }

    private List<HeroPresentation> shownStandardHeroes() {
        if (standardHeroPage < 0) return List.of();
        int from = standardHeroPage * 5;
        return standardHeroes.subList(from, Math.min(from + 5, standardHeroes.size()));
    }

    public int heroPageCount(){return (standardHeroes.size()+4)/5;}
    public void showStandardHeroPage(int index) { standardHeroPage = index < 0 ? -1 : Math.clamp(index, 0, heroPageCount()-1); }
    public List<String> shownStandardHeroIds() { return shownStandardHeroes().stream().map(HeroPresentation::id).toList(); }
    public boolean standardHeroResourcesPresent() {
        return standardHeroes.size() == StandardHeroes.CATALOG.size() && !shownStandardHeroes().isEmpty() && shownStandardHeroes().stream().allMatch(hero -> {
            var texture=HeroRenderer.classic(hero.id());
            if(Boolean.getBoolean("sgs.factionArtV2Smoke")&&(texture==null||!texture.getPath().endsWith("_faction_v2.png")))return false;
            return texture!=null&&minecraft.getResourceManager().getResource(texture).isPresent();
        }) && hits.size() == shownStandardHeroes().size()*2;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The pixel-art background is already drawn before the card layers. Vanilla blur
        // here would blur those layers when Screen.render() draws the native widgets.
    }

    public void showStatePage(boolean states) { statePage = states; }
    public void showHeroPage(boolean hero) { heroPage = hero; }
    public void showArtPage(int index) { page = Math.clamp(index,0,(cards.size()-1)/4); }
    public int loadedCardCount() { return cards.size(); }
    public boolean contentFits() {
        return !hits.isEmpty() && hits.stream().allMatch(hit -> hit.x >= 8 && hit.y >= 56
                && hit.x + hit.width <= width - 8 && hit.y + hit.height <= height - 42);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
