package com.miniworld.miniworld.world;

import com.miniworld.miniworld.MiniWorldMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.UUID;

public final class MiniWorldIds {
    private static final String PERSONAL_DIMENSION_PATH = "player_[0-9a-f]{32}";
    private static final Bounds PLATFORM_BOUNDS = new Bounds(-16, 31, -16, 31);
    private static final BlockPos SPAWN = new BlockPos(8, -63, 8);

    private MiniWorldIds() {
    }

    public static ResourceKey<Level> dimensionKey(UUID owner) {
        String uuidText = owner.toString().replace("-", "");
        return ResourceKey.create(
                Registries.DIMENSION,
                new ResourceLocation(MiniWorldMod.MODID, "player_" + uuidText)
        );
    }

    public static ResourceKey<Biome> biomeKey() {
        return ResourceKey.create(
                Registries.BIOME,
                new ResourceLocation(MiniWorldMod.MODID, "personal_space")
        );
    }

    public static boolean isPersonalDimension(ResourceKey<Level> dimension) {
        ResourceLocation location = dimension.location();
        return MiniWorldMod.MODID.equals(location.getNamespace())
                && location.getPath().matches(PERSONAL_DIMENSION_PATH);
    }

    public static boolean isInsidePlatform(BlockPos position) {
        return position.getX() >= PLATFORM_BOUNDS.minX()
                && position.getX() <= PLATFORM_BOUNDS.maxX()
                && position.getZ() >= PLATFORM_BOUNDS.minZ()
                && position.getZ() <= PLATFORM_BOUNDS.maxZ();
    }

    public static boolean isInsidePlatformChunk(ChunkPos chunkPos) {
        return chunkPos.x >= PLATFORM_BOUNDS.minX() >> 4
                && chunkPos.x <= PLATFORM_BOUNDS.maxX() >> 4
                && chunkPos.z >= PLATFORM_BOUNDS.minZ() >> 4
                && chunkPos.z <= PLATFORM_BOUNDS.maxZ() >> 4;
    }

    public static BlockPos spawn() {
        return SPAWN;
    }

    public static Bounds platformBounds() {
        return PLATFORM_BOUNDS;
    }

    public record Bounds(int minX, int maxX, int minZ, int maxZ) {
    }
}
