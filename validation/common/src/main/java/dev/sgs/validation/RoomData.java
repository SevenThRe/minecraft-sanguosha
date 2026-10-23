package dev.sgs.validation;

import dev.sgs.core.probe.TestRoom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Stored in the overworld data storage; independent of table chunk lifetime. */
public final class RoomData extends SavedData {
    public static final int DATA_VERSION = 1;
    private final Map<UUID, Entry> rooms = new LinkedHashMap<>();
    private RuntimeException readFailure;

    public record Entry(String dimension, BlockPos table, TestRoom room) {}

    public static RoomData get(MinecraftServer server) {
        RoomData data = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(RoomData::new, RoomData::loadOrBlock, null), "sgs_test_rooms");
        if (data.readFailure != null) throw new IllegalStateException("SGS room data cannot be loaded; preserve the original file", data.readFailure);
        return data;
    }

    private static RoomData loadOrBlock(CompoundTag tag, HolderLookup.Provider provider) {
        try { return load(tag, provider); }
        catch (RuntimeException failure) {
            // Return a blocked sentinel so vanilla cannot replace a failed load with fresh data.
            RoomData blocked = new RoomData();
            blocked.readFailure = failure;
            return blocked;
        }
    }

    public Entry find(UUID id) { return rooms.get(id); }

    public Entry at(String dimension, BlockPos pos, UUID owner) {
        for (Entry entry : rooms.values()) {
            if (entry.dimension.equals(dimension) && entry.table.equals(pos)) return entry;
        }
        Entry entry = new Entry(dimension, pos.immutable(), new TestRoom(UUID.randomUUID(), owner, 0));
        rooms.put(entry.room.id(), entry);
        setDirty();
        return entry;
    }

    public static RoomData load(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.getInt("data_version") != DATA_VERSION) throw new IllegalStateException("Unsupported SGS room data version");
        RoomData data = new RoomData();
        for (Tag raw : tag.getList("rooms", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            TestRoom room = new TestRoom(row.getUUID("id"), row.getUUID("owner"), row.getInt("value"));
            Entry entry = new Entry(row.getString("dimension"), BlockPos.of(row.getLong("table")), room);
            if (data.rooms.putIfAbsent(room.id(), entry) != null) throw new IllegalStateException("Duplicate SGS room id");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (readFailure != null) throw new IllegalStateException("Refusing to overwrite unreadable SGS data", readFailure);
        tag.putInt("data_version", DATA_VERSION);
        ListTag list = new ListTag();
        for (Entry entry : rooms.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("id", entry.room.id());
            row.putUUID("owner", entry.room.owner());
            row.putInt("value", entry.room.value());
            row.putString("dimension", entry.dimension);
            row.putLong("table", entry.table.asLong());
            list.add(row);
        }
        tag.put("rooms", list);
        return tag;
    }
}
