package com.miniworld.miniworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MiniWorldSavedData extends SavedData {
    public static final String STORAGE_NAME = "miniworld_players";

    private static final String RECORDS_TAG = "records";
    private static final String OWNER_TAG = "owner";
    private static final String DIMENSION_TAG = "dimension";
    private static final String SPAWN_TAG = "spawn";
    private static final String CREATED_AT_TAG = "createdAt";

    private final Map<UUID, MiniWorldRecord> records = new LinkedHashMap<>();

    public MiniWorldSavedData() {
    }

    public static MiniWorldSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                MiniWorldSavedData::load,
                MiniWorldSavedData::new,
                STORAGE_NAME
        );
    }

    public MiniWorldRecord get(UUID owner) {
        return records.get(owner);
    }

    public Collection<MiniWorldRecord> records() {
        return Collections.unmodifiableCollection(records.values());
    }

    public MiniWorldRecord getOrCreate(UUID owner, long createdAt) {
        MiniWorldRecord existing = records.get(owner);
        if (existing != null) {
            return existing;
        }

        MiniWorldRecord created = new MiniWorldRecord(
                owner,
                MiniWorldIds.dimensionKey(owner),
                MiniWorldIds.spawn(),
                createdAt
        );
        records.put(owner, created);
        setDirty();
        return created;
    }

    public static MiniWorldSavedData load(CompoundTag tag) {
        MiniWorldSavedData data = new MiniWorldSavedData();
        ListTag recordTags = tag.getList(RECORDS_TAG, Tag.TAG_COMPOUND);
        for (Tag rawTag : recordTags) {
            CompoundTag recordTag = (CompoundTag) rawTag;
            if (!recordTag.contains(OWNER_TAG, Tag.TAG_INT_ARRAY)
                    || !recordTag.contains(DIMENSION_TAG, Tag.TAG_STRING)
                    || !recordTag.contains(SPAWN_TAG, Tag.TAG_COMPOUND)
                    || !recordTag.contains(CREATED_AT_TAG, Tag.TAG_LONG)) {
                continue;
            }

            UUID owner = NbtUtils.loadUUID(recordTag.get(OWNER_TAG));
            ResourceLocation location = ResourceLocation.tryParse(recordTag.getString(DIMENSION_TAG));
            if (location == null) {
                continue;
            }

            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, location);
            BlockPos spawn = NbtUtils.readBlockPos(recordTag.getCompound(SPAWN_TAG));
            MiniWorldRecord record = new MiniWorldRecord(
                    owner,
                    dimension,
                    spawn,
                    recordTag.getLong(CREATED_AT_TAG)
            );
            data.records.put(owner, record);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag recordTags = new ListTag();
        for (MiniWorldRecord record : records.values()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.put(OWNER_TAG, NbtUtils.createUUID(record.owner()));
            recordTag.putString(DIMENSION_TAG, record.dimension().location().toString());
            recordTag.put(SPAWN_TAG, NbtUtils.writeBlockPos(record.spawn()));
            recordTag.putLong(CREATED_AT_TAG, record.createdAt());
            recordTags.add(recordTag);
        }
        tag.put(RECORDS_TAG, recordTags);
        return tag;
    }
}
