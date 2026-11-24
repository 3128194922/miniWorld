package com.miniworld.miniworld.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** 组合玩家小世界记录、运行时维度和传送权限。 */
public final class MiniWorldManager {
    private static final Map<MinecraftServer, MiniWorldManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;

    private MiniWorldManager(MinecraftServer server) {
        this.server = server;
    }

    public static synchronized MiniWorldManager forServer(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, MiniWorldManager::new);
    }

    public MiniWorldRecord ensureRecord(UUID owner) {
        MiniWorldSavedData data = MiniWorldSavedData.get(server.overworld());
        return data.getOrCreate(owner, System.currentTimeMillis());
    }

    public MiniWorldRecord ensureWorld(UUID owner) {
        MiniWorldRecord record = ensureRecord(owner);
        MiniWorldLevelFactory.ensureLevel(server, record);
        return record;
    }

    public boolean unloadIfEmpty(ResourceKey<Level> dimension) {
        return MiniWorldLevelFactory.unloadIfEmpty(server, dimension);
    }

    public void scheduleUnloadIfEmpty(ResourceKey<Level> dimension) {
        server.execute(() -> unloadIfEmpty(dimension));
    }

    public boolean enter(ServerPlayer player, UUID owner, boolean allowNonOwner) {
        if (!allowNonOwner && !player.getUUID().equals(owner)) {
            return false;
        }

        MiniWorldRecord record = ensureWorld(owner);
        ServerLevel level = server.getLevel(record.dimension());
        if (level == null) {
            return false;
        }

        player.teleportTo(
                level,
                record.spawn().getX() + 0.5D,
                record.spawn().getY(),
                record.spawn().getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
        syncWorldBorder(player);
        return true;
    }

    public void syncWorldBorder(ServerPlayer player) {
        player.connection.send(new ClientboundInitializeBorderPacket(player.level().getWorldBorder()));
    }

    public void keepInsidePersonalSpace(ServerPlayer player) {
        if (!MiniWorldIds.isPersonalDimension(player.level().dimension())
                || MiniWorldIds.isInsidePlatform(player.blockPosition())) {
            return;
        }

        MiniWorldRecord record = getRecord(player.getUUID());
        if (record == null || !record.dimension().equals(player.level().dimension())) {
            return;
        }

        player.teleportTo(
                player.server.getLevel(record.dimension()),
                record.spawn().getX() + 0.5D,
                record.spawn().getY(),
                record.spawn().getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
        syncWorldBorder(player);
    }

    public boolean returnToOverworld(ServerPlayer player) {
        ServerLevel overworld = server.overworld();
        var spawn = overworld.getSharedSpawnPos();
        player.teleportTo(
                overworld,
                spawn.getX() + 0.5D,
                spawn.getY(),
                spawn.getZ() + 0.5D,
                overworld.getSharedSpawnAngle(),
                player.getXRot()
        );
        syncWorldBorder(player);
        return true;
    }

    public MiniWorldRecord getRecord(UUID owner) {
        return MiniWorldSavedData.get(server.overworld()).get(owner);
    }

    public boolean hasWorld(UUID owner) {
        return getRecord(owner) != null;
    }
}
