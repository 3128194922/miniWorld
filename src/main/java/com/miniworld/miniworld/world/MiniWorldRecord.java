package com.miniworld.miniworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

public final class MiniWorldRecord {
    private final UUID owner;
    private final ResourceKey<Level> dimension;
    private final BlockPos spawn;
    private final long createdAt;

    public MiniWorldRecord(UUID owner, ResourceKey<Level> dimension, BlockPos spawn, long createdAt) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.spawn = Objects.requireNonNull(spawn, "spawn");
        this.createdAt = createdAt;
    }

    public UUID owner() {
        return owner;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos spawn() {
        return spawn;
    }

    public long createdAt() {
        return createdAt;
    }
}
