package com.miniworld.miniworld.world;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** 创建玩家专属的运行时 ServerLevel。 */
public final class MiniWorldLevelFactory {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ChunkProgressListener NO_OP_PROGRESS_LISTENER = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos chunkPos) {
        }

        @Override
        public void onStatusChange(ChunkPos chunkPos, ChunkStatus status) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    };

    private MiniWorldLevelFactory() {
    }

    public static ServerLevel ensureLevel(MinecraftServer server, MiniWorldRecord record) {
        ServerLevel existing = server.getLevel(record.dimension());
        if (existing != null) {
            return existing;
        }

        ServerLevel overworld = server.overworld();
        LevelStem overworldStem = server.registryAccess()
                .registryOrThrow(Registries.LEVEL_STEM)
                .get(LevelStem.OVERWORLD);
        if (overworldStem == null) {
            throw new IllegalStateException("Overworld level stem is not registered");
        }

        HolderSet<StructureSet> noStructures = HolderSet.direct(List.of());
        Holder<Biome> personalBiome = server.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(MiniWorldIds.biomeKey());
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                Optional.of(noStructures), personalBiome, List.of()
        );
        settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
        settings.updateLayers();

        LevelStem personalStem = new LevelStem(
                overworldStem.type(),
                new FiniteFlatLevelSource(settings)
        );
        long obfuscatedSeed = BiomeManager.obfuscateSeed(
                server.getWorldData().worldGenOptions().seed()
        );
        RandomSequences randomSequences = overworld.getRandomSequences();
        DerivedLevelData levelData = new DerivedLevelData(
                server.getWorldData(),
                server.getWorldData().overworldData()
        );
        ServerLevel created = new ServerLevel(
                server,
                server.executor,
                server.storageSource,
                levelData,
                record.dimension(),
                personalStem,
                NO_OP_PROGRESS_LISTENER,
                false,
                obfuscatedSeed,
                ImmutableList.of(),
                false,
                randomSequences
        );

        server.levels.put(record.dimension(), created);
        MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(created));
        initializePlatform(created);
        MiniWorldSavedData.get(overworld).setDirty();
        server.markWorldsDirty();
        return created;
    }

    public static void initializePlatform(ServerLevel level) {
        level.getWorldBorder().setCenter(
                MiniWorldIds.spawn().getX(),
                MiniWorldIds.spawn().getZ()
        );
        level.getWorldBorder().setSize(48.0D);
        level.setDefaultSpawnPos(MiniWorldIds.spawn(), 0.0F);
    }

    public static boolean unloadIfEmpty(MinecraftServer server, ResourceKey<Level> dimension) {
        // 仅回收本模组生成的个人空间；末地、下界及其他模组维度必须保留以供返回。
        if (!MiniWorldIds.isPersonalDimension(dimension)) {
            return false;
        }

        ServerLevel level = server.getLevel(dimension);
        if (level == null || !level.players().isEmpty()) {
            return false;
        }

        ServerLevel removed = server.levels.remove(dimension);
        if (removed == null) {
            return false;
        }

        removed.getChunkSource().save(true);
        MinecraftForge.EVENT_BUS.post(new LevelEvent.Unload(removed));
        try {
            removed.close();
        } catch (IOException exception) {
            LOGGER.error("Failed to close MiniWorld dimension {}", dimension.location(), exception);
        }
        server.markWorldsDirty();
        return true;
    }

}
