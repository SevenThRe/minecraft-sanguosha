package dev.sgs.validation.social;

import com.google.gson.Gson;
import dev.sgs.core.social.ModerationLedger;
import dev.sgs.core.social.SkinSelections;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Overworld data/sgs_social_state.dat. Typed rows avoid a growing single NBT UTF string. */
public final class SocialData extends SavedData {
  private static final Gson JSON=new Gson();
  private static final String DATA_ID="sgs_social_state";
  private ModerationLedger moderation=new ModerationLedger();
  private SkinSelections skins=new SkinSelections();
  private RuntimeException readFailure;
  private void check(){if(readFailure!=null)throw new IllegalStateException("社交数据不可读，保留原文件",readFailure);}
  public ModerationLedger moderation(){check();return moderation;}
  public SkinSelections skins(){check();return skins;}
  public static SocialData get(MinecraftServer server){
    var data=server.overworld().getDataStorage().computeIfAbsent(new Factory<>(()->createIfMissing(server),SocialData::loadOrBlock,null),DATA_ID);
    data.check();return data;
  }
  /** Vanilla also invokes this supplier after a failed compressed-NBT read. Never reset that file. */
  private static SocialData createIfMissing(MinecraftServer server){
    var file=server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATA_ID+".dat");
    var data=new SocialData();
    // notExists is false both for an existing entry (including a broken link) and unknown access.
    if(!Files.notExists(file,LinkOption.NOFOLLOW_LINKS))data.readFailure=new IllegalStateException(
        "Existing or inaccessible SGS ledger could not be read; refusing to create or overwrite: "+file);
    return data;
  }
  private static SocialData loadOrBlock(CompoundTag tag,HolderLookup.Provider provider){
    try{
      if(!tag.contains("data_version",Tag.TAG_INT) || tag.getInt("data_version")!=1)throw new IllegalArgumentException("unsupported social data");
      var data=new SocialData();
      data.moderation=ModerationLedger.restore(new ModerationLedger.Snapshot(1,read(tag,"moderation",ModerationLedger.Entry.class)));
      data.skins=SkinSelections.restore(read(tag,"skins",SkinSelections.Selection.class));return data;
    }catch(RuntimeException failure){var blocked=new SocialData();blocked.readFailure=failure;return blocked;}
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
    check();tag.putInt("data_version",1);
    write(tag,"moderation",moderation.entries());write(tag,"skins",skins.snapshot());return tag;
  }
  public void persist(MinecraftServer server){setDirty();server.overworld().getDataStorage().save();}
}
