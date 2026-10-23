package dev.sgs.validation.survival;

import dev.sgs.core.config.ServerPolicy;
import dev.sgs.validation.ProbeMod;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Opt-in real-server registration/merchant/reload test; requires a disposable run directory. */
public final class SurvivalSmokeChecks {
    public static void run(MinecraftServer server) {
        var file=server.getServerDirectory().resolve("config/sgs/"+ServerPolicy.FILE_NAME);
        var packsFile=server.getServerDirectory().resolve("config/sgs/"+dev.sgs.core.config.PackPolicy.FILE_NAME);
        String original=null,originalPacks=null;
        try {
            original=Files.readString(file);
            originalPacks=Files.readString(packsFile);
            if(!original.equals(ServerPolicy.TEMPLATE))throw new IllegalStateException("Smoke requires a fresh default configuration");
            require(ProbeMod.deck!=null && ProbeMod.table!=null,"survival item registrations");
            var player=new net.minecraft.server.level.ServerPlayer(server,server.overworld(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SurvivalProbe"),net.minecraft.server.level.ClientInformation.createDefault());
            var pos=new net.minecraft.core.BlockPos(0,100,0);
            player.setPos(0.5,100.5,0.5);
            require(SurvivalService.policy(server).virtualTableEnabled(),"virtual tables default enabled");
            require(SurvivalService.accessDenied(player,false)==null,"virtual guest without physical table");
            require("房主需要携带三国杀卡组包".equals(SurvivalService.accessDenied(player,true)),"independent host deck switch");
            player.getInventory().add(new net.minecraft.world.item.ItemStack(ProbeMod.deck));
            require(SurvivalService.accessDenied(player,true)==null,"virtual host without physical table");
            Files.writeString(file,original.replace("tables.virtual.enabled=true","tables.virtual.enabled=false"));
            SurvivalService.reload(server);
            require(!SurvivalService.policy(server).virtualTableEnabled(),"disable virtual tables through reload");
            require(SurvivalService.accessDenied(player,true)!=null,"direct lobby entry denied without table");
            require(SurvivalService.accessDenied(player,false)!=null,"virtual guest entry denied when disabled");
            player.getInventory().clearContent();
            server.overworld().setBlockAndUpdate(pos,ProbeMod.table.defaultBlockState());
            SurvivalService.opened(player,pos);
            require("房主需要携带三国杀卡组包".equals(SurvivalService.accessDenied(player,true)),"deck gate");
            require(SurvivalService.accessDenied(player,false)==null,"guest shares host deck");
            player.getInventory().add(new net.minecraft.world.item.ItemStack(ProbeMod.deck));
            require(SurvivalService.accessDenied(player,true)==null,"table and host deck admitted");
            player.setPos(100.5,100.5,0.5);
            require(SurvivalService.accessDenied(player,true)!=null,"remote admission denied");
            player.setPos(0.5,100.5,0.5);
            server.overworld().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            require(SurvivalService.accessDenied(player,true)!=null,"destroyed table denied");
            Files.writeString(file,original);SurvivalService.reload(server);
            var commands=server.getCommands().getDispatcher();var source=server.createCommandSourceStack();
            var oldRules=SurvivalService.policy(server).packs().apply(dev.sgs.core.match.MatchConfig.standard());
            Files.writeString(packsFile,originalPacks.replace("cards.junzheng.enabled=true","cards.junzheng.enabled=false")
                .replace("heroes.wind.enabled=true","heroes.wind.enabled=false"));
            require(commands.execute("sgs reload",source)==1,"pack reload command");
            require(commands.execute("sgs packs",source)==1,"pack status command");
            var newRules=SurvivalService.policy(server).packs().apply(dev.sgs.core.match.MatchConfig.standard());
            require(newRules.contentPool().size()==108 && oldRules.contentPool().size()==160,"deck policy snapshots");
            require(newRules.heroPool().stream().noneMatch(h->h.id().equals("huangzhong")) && oldRules.heroPool().stream().anyMatch(h->h.id().equals("huangzhong")),"hero policy snapshots");
            var accepted=SurvivalService.policy(server);
            Files.writeString(packsFile,"cards.zhulu.enabled=true\n");
            require(commands.execute("sgs reload",source)==0 && SurvivalService.policy(server)==accepted,"failed pack reload retains all policy");
            try{commands.execute("sgs reload",source.withPermission(0));throw new AssertionError("unprivileged reload accepted");}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
            try{commands.execute("sgs packs",source.withPermission(0));throw new AssertionError("unprivileged packs accepted");}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){}
            Files.writeString(packsFile,originalPacks);require(commands.execute("sgs reload",source)==1,"restore pack defaults");
            ProbeMod.LOG.info("SGS_PACK_CONFIG_SMOKE_PASS reload=true status=true permissions=true old_snapshot_preserved=true invalid_preserved=true");
            player.setPos(100.5,100.5,0.5);
            require(SurvivalService.accessDenied(player,true)==null,"re-enable virtual admission despite stale removed distant anchor");
            Villager v=new Villager(EntityType.VILLAGER,server.overworld());
            v.setUUID(new UUID(0,100));v.setAge(0);
            v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
            SurvivalService.merchant(v);
            var offers=v.getOffers();int count=offers.size();
            require(offers.stream().filter(o->o.getResult().is(ProbeMod.deck)).count()==1,"one deck offer");
            require(offers.stream().filter(o->o.getResult().is(ProbeMod.table.asItem())).count()==1,"one table offer");
            var nitwit=new Villager(EntityType.VILLAGER,server.overworld());
            nitwit.setUUID(new UUID(0,200));nitwit.setAge(0);
            nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
            SurvivalService.merchant(nitwit);
            require(!nitwit.getTags().contains("sgs_card_merchant"),"non-trading nitwit must not be advertised as merchant");
            var tableOffer=offers.stream().filter(o->o.getResult().is(ProbeMod.table.asItem())).findFirst().orElseThrow();
            var payment=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,8);
            require(tableOffer.take(payment,net.minecraft.world.item.ItemStack.EMPTY) && payment.isEmpty(),"vanilla transaction takes eight emeralds");
            SurvivalService.merchant(v);require(v.getOffers().size()==count,"no duplicate offers");
            Files.writeString(file,original.replace("merchants.enabled=true","merchants.enabled=false").replace("rooms.six.enabled=true","rooms.six.enabled=false"));
            SurvivalService.reload(server);SurvivalService.merchant(v);
            require(!SurvivalService.policy(server).six(),"mode reload");
            require(v.getOffers().size()==count-2,"disable removes only SGS offers");
            var before=SurvivalService.policy(server);
            Files.writeString(file,"ranked.enabled=true\n");
            try {SurvivalService.reload(server);throw new AssertionError("invalid ranked accepted");}
            catch(IllegalArgumentException expected){require(SurvivalService.policy(server)==before,"atomic failed reload");}
            Files.writeString(file,original.replace("rooms.max=64","rooms.max=11"));
            SurvivalService.reload(server);SurvivalService.merchant(v);
            require(SurvivalService.policy(server).maxRooms()==11,"room limit reload");
            Files.writeString(file,original);SurvivalService.reload(server);
            ProbeMod.LOG.info("SGS_SURVIVAL_SMOKE_PASS registration=true merchant=true access=true reload=true invalid_preserved=true virtual_default=true physical_only_reload=true virtual_reenabled=true");
        } catch(Exception | AssertionError e) {ProbeMod.LOG.error("SGS_SURVIVAL_SMOKE_FAIL",e);}
        finally {
            if(original!=null)try{Files.writeString(file,original);}catch(Exception e){ProbeMod.LOG.error("Smoke config restore failed",e);}
            if(originalPacks!=null)try{Files.writeString(packsFile,originalPacks);}catch(Exception e){ProbeMod.LOG.error("Smoke pack config restore failed",e);}
            server.halt(false);
        }
    }
    private static void require(boolean passed,String message){if(!passed)throw new IllegalStateException(message);}
}
