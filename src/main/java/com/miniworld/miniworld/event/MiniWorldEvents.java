package com.miniworld.miniworld.event;

import com.miniworld.miniworld.world.MiniWorldManager;
import com.miniworld.miniworld.world.MiniWorldIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.miniworld.miniworld.command.MiniWorldCommands;

/** 服务器生命周期和玩家生命周期处理。 */
public final class MiniWorldEvents {
    private MiniWorldEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MiniWorldManager manager = MiniWorldManager.forServer(player.server);
            manager.ensureRecord(player.getUUID());
            if (MiniWorldIds.isPersonalDimension(player.level().dimension())) {
                manager.syncWorldBorder(player);
                manager.keepInsidePersonalSpace(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MiniWorldManager manager = MiniWorldManager.forServer(player.server);
            manager.syncWorldBorder(player);
            manager.keepInsidePersonalSpace(player);
            manager.unloadIfEmpty(event.getFrom());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            MiniWorldManager.forServer(player.server).keepInsidePersonalSpace(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MiniWorldManager.forServer(player.server)
                    .scheduleUnloadIfEmpty(player.level().dimension());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MiniWorldCommands.register(event.getDispatcher());
    }
}
