package dev.sgs.validation.social;

import com.google.gson.Gson;
import dev.sgs.core.social.MatchLedger;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Overworld data/sgs_match_ledger.dat. Typed rows avoid a growing single NBT UTF string. */
public final class FeedbackData extends SavedData {
  private static final Gson JSON=new Gson();
  private static final String DATA_ID="sgs_match_ledger";
  private MatchLedger ledger=new MatchLedger();
  private RuntimeException readFailure;
  public MatchLedger ledger(){if(readFailure!=null)throw new IllegalStateException("preserve unreadable SGS ledger",readFailure);return ledger;}
  public static FeedbackData get(MinecraftServer server){
    var data=server.overworld().getDataStorage().computeIfAbsent(new Factory<>(()->createIfMissing(server),FeedbackData::loadOrBlock,null),DATA_ID);
    data.ledger();return data;
  }
  /** Vanilla also invokes this supplier after a failed compressed-NBT read. Never reset that file. */
  private static FeedbackData createIfMissing(MinecraftServer server){
    var file=server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATA_ID+".dat");
    var data=new FeedbackData();
    // notExists is false both for an existing entry (including a broken link) and unknown access.
    if(!Files.notExists(file,LinkOption.NOFOLLOW_LINKS))data.readFailure=new IllegalStateException(
        "Existing or inaccessible SGS ledger could not be read; refusing to create or overwrite: "+file);
    return data;
  }
  private static FeedbackData loadOrBlock(CompoundTag tag,HolderLookup.Provider provider){
    try{
      if(!tag.contains("data_version",Tag.TAG_INT) || !tag.contains("last_match_id",Tag.TAG_LONG) || !tag.contains("last_report_id",Tag.TAG_LONG))throw new IllegalArgumentException("missing ledger header");
      var snapshot=new MatchLedger.Snapshot(tag.getInt("data_version"),tag.getLong("last_match_id"),tag.getLong("last_report_id"),
          read(tag,"matches",MatchLedger.MatchRecord.class),read(tag,"accounts",MatchLedger.Account.class),
          read(tag,"receipts",MatchLedger.Receipt.class),read(tag,"reports",MatchLedger.Report.class));
      var data=new FeedbackData();data.ledger=MatchLedger.restore(snapshot);return data;
    }catch(RuntimeException failure){var blocked=new FeedbackData();blocked.readFailure=failure;return blocked;}
  }
  private static <T> List<T> read(CompoundTag tag,String key,Class<T> type){
    if(!(tag.get(key) instanceof ListTag storedRows)
        || (storedRows.getElementType()!=Tag.TAG_COMPOUND && storedRows.getElementType()!=Tag.TAG_END))throw new IllegalArgumentException("missing or invalid ledger rows: "+key);
    if(storedRows.size()>1_000_000)throw new IllegalArgumentException("ledger row bound exceeded");
    var result=new ArrayList<T>();for(var entry:storedRows){
      if(!(entry instanceof CompoundTag row) || !row.contains("json",Tag.TAG_STRING))throw new IllegalArgumentException("missing ledger row payload");
      String json=row.getString("json");
      if(json.isBlank() || json.length()>12_000)throw new IllegalArgumentException("empty or oversized ledger row");
      var parsed=JSON.fromJson(json,type);if(parsed==null)throw new IllegalArgumentException("empty ledger row");result.add(parsed);}
    return result;
  }
  private static void write(CompoundTag tag,String key,List<?> values){
    var rows=new ListTag();for(var value:values){var row=new CompoundTag();String json=JSON.toJson(value);
      if(json.length()>12_000)throw new IllegalArgumentException("oversized ledger row");row.putString("json",json);rows.add(row);}tag.put(key,rows);
  }
  @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider){
    var snapshot=ledger().snapshot();tag.putInt("data_version",snapshot.version());tag.putLong("last_match_id",snapshot.lastMatchId());tag.putLong("last_report_id",snapshot.lastReportId());
    write(tag,"matches",snapshot.matches());write(tag,"accounts",snapshot.accounts());write(tag,"receipts",snapshot.receipts());write(tag,"reports",snapshot.reports());return tag;
  }
  public void persist(MinecraftServer server){setDirty();server.overworld().getDataStorage().save();}
}
