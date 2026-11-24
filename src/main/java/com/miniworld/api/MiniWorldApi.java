package com.miniworld.api;

import com.miniworld.miniworld.world.MiniWorldManager;
import com.miniworld.miniworld.world.MiniWorldRecord;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** 面向 KubeJS 等脚本的稳定、无脚本依赖 API。 */
public final class MiniWorldApi {
    private MiniWorldApi() {
    }

    public static ResourceKey<Level> ensureWorld(MinecraftServer server, UUID owner) {
        return MiniWorldManager.forServer(server).ensureWorld(owner).dimension();
    }

    public static boolean enter(ServerPlayer player) {
        return MiniWorldManager.forServer(player.server).enter(player, player.getUUID(), false);
    }

    public static boolean enter(ServerPlayer player, UUID owner, boolean allowNonOwner) {
        return MiniWorldManager.forServer(player.server).enter(player, owner, allowNonOwner);
    }

    public static boolean returnToOverworld(ServerPlayer player) {
        return MiniWorldManager.forServer(player.server).returnToOverworld(player);
    }

    public static String getDimensionId(MinecraftServer server, UUID owner) {
        MiniWorldRecord record = MiniWorldManager.forServer(server).getRecord(owner);
        return record == null ? null : record.dimension().location().toString();
    }

    public static boolean hasWorld(MinecraftServer server, UUID owner) {
        return MiniWorldManager.forServer(server).hasWorld(owner);
    }
}
