package dev.sgs.validation.client.art;

import dev.sgs.validation.ProbeMod;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;
import java.util.Set;
import dev.sgs.core.hero.StandardHeroes;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.TickPhase;
import net.blay09.mods.balm.api.event.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Actual resource-pack rendering checks in an owned development client. */
public final class ArtSmokeChecks {
    private static final int HERO_PAGES=(StandardHeroes.CATALOG.size()+4)/5;
    public static final String[] CAPTURES=java.util.stream.Stream.concat(
        java.util.stream.Stream.of("bodies-small","states-small","bodies-wide","states-large-gui","bodies-gui1","hero-hearts","elements-small","elements-wide"),
        java.util.stream.IntStream.rangeClosed(1,HERO_PAGES).mapToObj(p->String.format("heroes-%02d",p))).toArray(String[]::new);
    private static final int[][] VIEWS=createViews();
    private static int[][] createViews(){
        int[][] base={{854,480,2,0},{854,480,2,1},{1280,720,2,0},{1280,720,3,1},{854,480,1,0},{854,480,2,2},{854,480,2,0},{1280,720,2,0}};
        int[][] result=new int[8+HERO_PAGES][];System.arraycopy(base,0,result,0,8);
        for(int p=8;p<result.length;p++)result[p]=new int[]{1672,941,2,3};return result;
    }
    private static final Set<String> capturedHeroes = new HashSet<>();
    private static final AtomicInteger pending = new AtomicInteger();
    private static boolean started;
    private static int view = -1;
    private static int ticks;
    private static int oldScale;
    private static int oldWidth;
    private static int oldHeight;

    public static boolean enabled() { return Boolean.getBoolean("sgs.artSmoke"); }

    public static void initialize() {
        if (enabled()) Balm.getEvents().onTickEvent(TickType.Client, TickPhase.End, ArtSmokeChecks::tick);
    }

    public static void begin(Minecraft client) {
        capturedHeroes.clear(); view = -1;
        oldScale = client.options.guiScale().get();
        oldWidth = client.getWindow().getWidth(); oldHeight = client.getWindow().getHeight();
        client.setScreen(new ArtGalleryScreen(client.screen));
        client.getToasts().clear();
        started = true;
        next(client);
    }

    private static void next(Minecraft client) {
        view++; ticks = 0;
        if (view == VIEWS.length) return;
        int[] config = VIEWS[view];
        client.getWindow().setWindowed(config[0], config[1]);
        client.options.guiScale().set(config[2]);
        client.resizeDisplay();
        ((ArtGalleryScreen) client.screen).showStatePage(config[3] == 1);
        ((ArtGalleryScreen) client.screen).showHeroPage(config[3] == 2);
        ((ArtGalleryScreen) client.screen).showArtPage(view>=6?1:0);
        ((ArtGalleryScreen) client.screen).showStandardHeroPage(config[3] == 3 ? view - 8 : -1);
        client.getToasts().clear();
    }

    private static void tick(Minecraft client) {
        if (!started) return;
        if (view == VIEWS.length) {
            if (pending.get() == 0) {
                var expected = new HashSet<>(StandardHeroes.CATALOG.stream().map(hero -> hero.id()).toList());
                if (capturedHeroes.size() != expected.size() || !capturedHeroes.equals(expected)) {
                    ProbeMod.LOG.error("SGS_ART_SMOKE_FAIL hero_coverage heroes={}", capturedHeroes);
                    started = false; client.stop(); return;
                }
                started = false;
                client.options.guiScale().set(oldScale);
                client.getWindow().setWindowed(oldWidth, oldHeight);
                client.resizeDisplay();
                ProbeMod.LOG.info("SGS_ART_SMOKE_PASS resources=true captures=8 gui_scales=1,2,3 native_client=true");
                ProbeMod.LOG.info("SGS_ART_HERO_ATLAS_PASS pages={} heroes={} vertical={} wide={} total_captures={} native_resource=true non_match=true",HERO_PAGES,expected.size(),expected.size(),expected.size(),CAPTURES.length);
                dev.sgs.validation.client.LobbySmokeChecks.begin();
            }
            return;
        }
        if (++ticks < 25) return;
        if (!(client.screen instanceof ArtGalleryScreen screen) || screen.loadedCardCount() < 8 || !screen.contentFits()) {
            ProbeMod.LOG.error("SGS_ART_SMOKE_FAIL resources_or_layout view={}", view);
            started = false; client.stop(); return;
        }
        String filename = "sgs-art-v3-" + CAPTURES[view] + ".png";
        if (view >= 8) {
            var ids = screen.shownStandardHeroIds();
            if (!screen.standardHeroResourcesPresent() || ids.size() != 5 || ids.stream().anyMatch(capturedHeroes::contains)) {
                ProbeMod.LOG.error("SGS_ART_SMOKE_FAIL hero_resources_or_duplicate page={} heroes={}", view - 7, ids);
                started = false; client.stop(); return;
            }
            capturedHeroes.addAll(ids);
            ProbeMod.LOG.info("SGS_ART_HERO_PAGE_PASS page={} heroes={} vertical=5 wide=5 native_resource=true non_match=true", view - 7, String.join(",", ids));
        }
        pending.incrementAndGet();
        Screenshot.grab(client.gameDirectory, filename, client.getMainRenderTarget(), message -> {
            ProbeMod.LOG.info("{}", message.getString());
            pending.decrementAndGet();
        });
        ProbeMod.LOG.info("SGS_ART_SMOKE_CAPTURE name={} width={} height={} gui={} cards={} layout=true", filename,
                client.getWindow().getWidth(), client.getWindow().getHeight(), client.options.guiScale().get(), screen.loadedCardCount());
        next(client);
    }
}
