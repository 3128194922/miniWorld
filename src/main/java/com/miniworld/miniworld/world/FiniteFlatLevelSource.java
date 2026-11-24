package com.miniworld.miniworld.world;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.StructureManager;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** FlatLevelSource 的有限世界变体：平台外区块只生成虚空。 */
public final class FiniteFlatLevelSource extends FlatLevelSource {
    public FiniteFlatLevelSource(net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings settings) {
        super(settings);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Executor executor,
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk
    ) {
        ChunkPos chunkPos = chunk.getPos();
        if (!MiniWorldIds.isInsidePlatformChunk(chunkPos)) {
            return CompletableFuture.completedFuture(chunk);
        }
        return super.fillFromNoise(executor, blender, randomState, structureManager, chunk);
    }

    @Override
    public int getBaseHeight(
            int x,
            int z,
            net.minecraft.world.level.levelgen.Heightmap.Types heightmap,
            net.minecraft.world.level.LevelHeightAccessor levelHeightAccessor,
            RandomState randomState
    ) {
        if (!MiniWorldIds.isInsidePlatform(new net.minecraft.core.BlockPos(x, 0, z))) {
            return levelHeightAccessor.getMinBuildHeight();
        }
        return super.getBaseHeight(x, z, heightmap, levelHeightAccessor, randomState);
    }

    @Override
    public NoiseColumn getBaseColumn(
            int x,
            int z,
            net.minecraft.world.level.LevelHeightAccessor levelHeightAccessor,
            RandomState randomState
    ) {
        if (MiniWorldIds.isInsidePlatform(new net.minecraft.core.BlockPos(x, 0, z))) {
            return super.getBaseColumn(x, z, levelHeightAccessor, randomState);
        }

        BlockState[] airColumn = new BlockState[levelHeightAccessor.getHeight()];
        Arrays.fill(airColumn, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(levelHeightAccessor.getMinBuildHeight(), airColumn);
    }
}
