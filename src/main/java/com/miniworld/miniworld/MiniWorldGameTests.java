package com.miniworld.miniworld;

import com.miniworld.api.MiniWorldApi;
import com.miniworld.miniworld.world.MiniWorldIds;
import com.miniworld.miniworld.world.MiniWorldLevelFactory;
import com.miniworld.miniworld.world.MiniWorldManager;
import com.miniworld.miniworld.world.MiniWorldRecord;
import com.miniworld.miniworld.world.MiniWorldSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

public final class MiniWorldGameTests {
    private MiniWorldGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 1)
    public static void idAndBoundsAreStable(GameTestHelper helper) {
        UUID owner = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        ResourceKey<Level> key = MiniWorldIds.dimensionKey(owner);
        helper.assertTrue(key.location().toString().equals(
                "miniworld:player_123e4567e89b12d3a456426614174000"), key.location().toString());
        helper.assertTrue(MiniWorldIds.spawn().equals(new BlockPos(8, -63, 8)), "spawn");
        helper.assertTrue(MiniWorldIds.platformBounds().minX() == -16, "minX");
        helper.assertTrue(MiniWorldIds.platformBounds().maxX() == 31, "maxX");
        helper.assertTrue(MiniWorldIds.platformBounds().minZ() == -16, "minZ");
        helper.assertTrue(MiniWorldIds.platformBounds().maxZ() == 31, "maxZ");
        helper.assertTrue(MiniWorldIds.isPersonalDimension(key), "personal dimension");
        helper.assertTrue(!MiniWorldIds.isPersonalDimension(Level.OVERWORLD), "overworld is not personal");
        helper.assertTrue(!MiniWorldIds.isPersonalDimension(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation("othermod", "player_123e4567e89b12d3a456426614174000")
        )), "foreign dimension is not personal");
        helper.assertTrue(MiniWorldIds.isInsidePlatform(new BlockPos(31, -63, 31)), "inside edge");
        helper.assertTrue(!MiniWorldIds.isInsidePlatform(new BlockPos(32, -63, 31)), "outside edge");
        helper.assertTrue(MiniWorldIds.isInsidePlatformChunk(new ChunkPos(1, 1)), "inside chunk edge");
        helper.assertTrue(!MiniWorldIds.isInsidePlatformChunk(new ChunkPos(2, 1)), "outside chunk edge");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1)
    public static void recordCreationDoesNotLoadRuntimeLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.fromString("423e4567-e89b-12d3-a456-426614174000");
        MiniWorldManager manager = MiniWorldManager.forServer(server);
        MiniWorldRecord record = manager.ensureRecord(owner);
        helper.assertTrue(record != null, "record");
        helper.assertTrue(server.getLevel(record.dimension()) == null, "runtime level must stay lazy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1)
    public static void recordsRoundTripThroughNbt(GameTestHelper helper) {
        UUID firstOwner = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID secondOwner = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        MiniWorldSavedData source = new MiniWorldSavedData();
        MiniWorldRecord first = source.getOrCreate(firstOwner, 100L);
        helper.assertTrue(source.isDirty(), "first insert must dirty");

        MiniWorldRecord second = source.getOrCreate(secondOwner, 200L);
        source.setDirty(false);
        helper.assertTrue(source.getOrCreate(firstOwner, 999L) == first, "duplicate owner");
        helper.assertTrue(!source.isDirty(), "duplicate must not dirty");

        CompoundTag tag = source.save(new CompoundTag());
        MiniWorldSavedData loaded = MiniWorldSavedData.load(tag);
        MiniWorldRecord loadedFirst = loaded.get(firstOwner);
        MiniWorldRecord loadedSecond = loaded.get(secondOwner);
        helper.assertTrue(loadedFirst != null, "first record");
        helper.assertTrue(loadedSecond != null, "second record");
        helper.assertTrue(loadedFirst.owner().equals(first.owner()), "first owner");
        helper.assertTrue(loadedFirst.dimension().equals(first.dimension()), "first dimension");
        helper.assertTrue(loadedFirst.spawn().equals(first.spawn()), "first spawn");
        helper.assertTrue(loadedFirst.createdAt() == first.createdAt(), "first creation time");
        helper.assertTrue(loadedSecond.owner().equals(second.owner()), "second owner");
        helper.assertTrue(loadedSecond.dimension().equals(second.dimension()), "second dimension");
        helper.assertTrue(loadedSecond.spawn().equals(second.spawn()), "second spawn");
        helper.assertTrue(loadedSecond.createdAt() == second.createdAt(), "second creation time");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dynamicLevelHasPlatform(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.fromString("923e4567-e89b-12d3-a456-426614174000");
        ResourceKey<Level> key = MiniWorldApi.ensureWorld(server, owner);
        ServerLevel level = server.getLevel(key);
        helper.assertTrue(level != null, "personal level");
        level.getChunkSource().getChunk(0, 0, ChunkStatus.FULL, true);
        helper.assertTrue(level.getBlockState(new BlockPos(8, -64, 8)).is(Blocks.BEDROCK), "platform");
        level.getChunkSource().getChunk(2, 0, ChunkStatus.FULL, true);
        helper.assertTrue(level.getBlockState(new BlockPos(32, -64, 8)).isAir(),
                "outside chunks must be void");
        FlatLevelSource generator = (FlatLevelSource) level.getChunkSource().getGenerator();
        helper.assertTrue(generator.settings().getBiome().unwrapKey().isPresent(),
                "personal biome must be registered");
        helper.assertTrue(generator.settings().getBiome().unwrapKey().get().location().toString()
                        .equals("miniworld:personal_space"),
                "personal biome id");
        helper.assertTrue(generator.settings().structureOverrides().isPresent(), "structures disabled explicitly");
        helper.assertTrue(generator.settings().structureOverrides().get().size() == 0, "no structures");
        helper.assertTrue(generator.settings().getBiome().value().getGenerationSettings().features().stream()
                        .allMatch(featureSet -> featureSet.size() == 0),
                "no biome features");
        helper.assertTrue(generator.settings().getBiome().value().getGenerationSettings().getCarvingStages().isEmpty(),
                "no biome carvers");
        helper.assertTrue(level.getWorldBorder().getSize() == 48.0D, "border size");
        helper.assertTrue(level.getWorldBorder().getCenterX() == 8.0D, "border x");
        helper.assertTrue(level.getWorldBorder().getCenterZ() == 8.0D, "border z");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void emptyRuntimeLevelCanUnloadWithoutDeletingRecord(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.fromString("623e4567-e89b-12d3-a456-426614174000");
        ResourceKey<Level> key = MiniWorldApi.ensureWorld(server, owner);
        helper.assertTrue(server.getLevel(key) != null, "runtime level");
        helper.assertTrue(MiniWorldLevelFactory.unloadIfEmpty(server, key), "unload");
        helper.assertTrue(server.getLevel(key) == null, "runtime level removed");
        helper.assertTrue(MiniWorldSavedData.get(server.overworld()).get(owner) != null, "record retained");
        helper.succeed();
    }

    // 回归：离开末地/下界进入个人空间时，自动卸载不得移除原版维度。
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vanillaLevelsMustNeverBeUnloaded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (ResourceKey<Level> key : java.util.List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            ServerLevel original = server.getLevel(key);
            helper.assertTrue(original != null, "vanilla level exists: " + key.location());
            helper.assertTrue(!MiniWorldLevelFactory.unloadIfEmpty(server, key),
                    "must not unload vanilla level: " + key.location());
            helper.assertTrue(server.getLevel(key) == original,
                    "vanilla level retained: " + key.location());
        }
        ResourceKey<Level> foreignDimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation("othermod", "player_123e4567e89b12d3a456426614174000")
        );
        helper.assertTrue(!MiniWorldLevelFactory.unloadIfEmpty(server, foreignDimension),
                "must not unload another mod dimension");
        helper.succeed();
    }
}
