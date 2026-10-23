package dev.sgs.validation.client.art;

import com.google.gson.JsonParser;
import dev.sgs.validation.ProbeMod;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Presentation data only; card rules and physical-card identities stay in sgs-core. */
public final class CardArtCatalog {
    public record Art(String id, String name, String category, String element, ResourceLocation texture) {
        @Override public String name() { return dev.sgs.validation.client.ClientText.named("sgs.card."+id,name); }
    }

    public static List<Art> load(ResourceManager resources) {
        try (Reader reader = resources.getResourceOrThrow(ProbeMod.id("card_art_catalog.json")).openAsReader()) {
            var object = JsonParser.parseReader(reader).getAsJsonObject();
            if (object.get("schema_version").getAsInt() != 1) throw new IOException("Unsupported card art catalog");
            List<Art> result = new ArrayList<>();
            for (var entry : object.getAsJsonArray("cards")) {
                var card = entry.getAsJsonObject();
                var texture = ResourceLocation.parse(card.get("texture").getAsString());
                resources.getResourceOrThrow(texture);
                result.add(new Art(card.get("id").getAsString(), card.get("name").getAsString(),
                        card.get("category").getAsString(), card.get("element").getAsString(), texture));
            }
            for (String file : List.of("table_background_v3.png", "card_back_fangkuaisha_v4.png", "card_frame_v3.png",
                    "heroes/liubei_v4.png", "suits/spade_v1.png", "suits/heart_v1.png", "suits/club_v1.png", "suits/diamond_v1.png"))
                resources.getResourceOrThrow(ProbeMod.id("textures/gui/" + file));
            ProbeMod.LOG.info("SGS_ART_RESOURCES_LOADED cards={} schema=1", result.size());
            return List.copyOf(result);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("SGS card art resource load failed", error);
        }
    }
}
