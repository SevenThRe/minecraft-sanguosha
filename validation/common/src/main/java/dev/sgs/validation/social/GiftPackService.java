package dev.sgs.validation.social;
import dev.sgs.core.social.*;
import dev.sgs.validation.ProbeMod;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
/** Administrator-selected JSON patches. Textures/sprite sheets ship as ordinary client resource packs. */
public final class GiftPackService {
    public record Pack(int api,String namespace,List<GiftDefinition> gifts) {}
    private static final Map<MinecraftServer,GiftCatalog> CATALOGS=new IdentityHashMap<>();
    public static GiftCatalog catalog(MinecraftServer server){return CATALOGS.computeIfAbsent(server,s->load(s.getWorldPath(LevelResource.ROOT).resolve("sgs-devkit")));}
    public static GiftCatalog load(Path root){
        try{
            var config=root.resolve("gift-packs.properties");var additions=new ArrayList<GiftDefinition>();
            if(Files.exists(config)){
                if(Files.size(config)>8192)throw new IllegalArgumentException("Gift config too large");
                var props=new Properties();try(var r=Files.newBufferedReader(config,StandardCharsets.UTF_8)){props.load(r);}
                if(!props.stringPropertyNames().equals(Set.of("api","enabled")) || !props.getProperty("api").equals("1"))throw new IllegalArgumentException("Invalid gift config");
                var names=props.getProperty("enabled").isBlank()?List.<String>of():Arrays.stream(props.getProperty("enabled").split(",",-1)).map(String::strip).toList();
                if(names.size()>8 || names.stream().distinct().count()!=names.size())throw new IllegalArgumentException("Gift pack count/duplicate");
                var namespaces=new HashSet<String>();
                for(var name:names){
                    if(!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}\\.json"))throw new IllegalArgumentException("Invalid gift pack filename");
                    var file=root.resolve("gift-packs").resolve(name);
                    if(Files.isSymbolicLink(file) || Files.size(file)>128_000)throw new IllegalArgumentException("Invalid gift pack file");
                    var pack=FeedbackNetwork.JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),Pack.class);
                    if(pack==null || pack.api()!=1 || pack.namespace()==null || !pack.namespace().matches("[a-z0-9_.-]{1,32}") || pack.namespace().equals("sgs")
                        || !namespaces.add(pack.namespace()) || pack.gifts()==null || pack.gifts().isEmpty() || pack.gifts().size()>28)throw new IllegalArgumentException("Invalid gift patch");
                    for(var gift:pack.gifts())if(!gift.id().startsWith(pack.namespace()+":"))throw new IllegalArgumentException("Gift namespace mismatch");
                    additions.addAll(pack.gifts());
                }
            }
            var catalog=new GiftCatalog(additions);ProbeMod.LOG.info("SGS_GIFT_CATALOG_LOADED gifts={} extensions={}",catalog.gifts().size(),additions.size());return catalog;
        }catch(Exception e){throw new IllegalStateException("Gift patches rejected: "+root,e);}
    }
    public static void stop(){CATALOGS.clear();}
}
