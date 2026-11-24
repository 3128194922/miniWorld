package com.miniworld.miniworld;

import net.minecraft.gametest.framework.GameTestRegistry;
import com.miniworld.miniworld.event.MiniWorldEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MiniWorldMod.MODID)
public class MiniWorldMod
{
    public static final String MODID = "miniworld";

    public MiniWorldMod(FMLJavaModLoadingContext context)
    {
        GameTestRegistry.register(MiniWorldGameTests.class);
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(MiniWorldEvents.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
    }

}
