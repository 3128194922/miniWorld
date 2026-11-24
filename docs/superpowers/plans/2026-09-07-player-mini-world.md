# 玩家个人小世界 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Minecraft 1.20.1 Forge 中为每个玩家创建、保存并恢复唯一的 3×3 区块基岩个人维度，首次登录只保存记录，玩家通过命令或 Java/KubeJS API 进入时懒加载运行时维度。

**Architecture:** 使用运行时 `ServerLevel` 管理器按需创建带唯一 `ResourceKey<Level>` 的维度；主世界 `SavedData` 保存 UUID 与维度记录，服务器启动只读取记录，进入或 API 调用时恢复运行时维度，无人使用时卸载但保留存档。管理器对外提供无 KubeJS 依赖的 Java API，命令层负责玩家权限，事件层负责生命周期。

**Tech Stack:** Minecraft 1.20.1、Forge 47.4.6、Java 17、Forge Access Transformer、Forge GameTest、原版 NBT/SavedData、Brigadier。

**Spec:** `miniWorld/docs/superpowers/specs/2026-09-07-player-mini-world-design.md`

## Global Constraints

- 目标平台：Minecraft 1.20.1 Forge 47.4.6。
- 只修改 `miniWorld` 主项目，不新增第三方依赖。
- 包名和模组 ID 使用 `com.miniworld.miniworld` 与 `miniworld`，源码路径不得保留 `example` 包。
- 不依赖 KubeJS 编译或运行；KubeJS 只通过 `Java.loadClass('com.miniworld.api.MiniWorldApi')` 调用公开 API。
- 无人使用时只卸载运行时 `ServerLevel`，不删除世界目录、区块数据或 SavedData 记录。
- 默认只有维度所有者可以进入；管理员和 API 的例外访问必须显式授权。
- 每个维度边界为 48×48 方块，对应 3×3 区块，覆盖 `[-16,31]`，出生点为 `(8, -63, 8)`，FlatLevelSource 在 `y=-64` 生成一层基岩。
- 每个测试步骤都必须在实现前先建立失败证据；构建完成后运行完整 Forge 构建和可用的 GameTest/服务端验证。

## 文件结构

- 修改 `miniWorld/gradle.properties`：模组 ID、名称、分组、作者和描述。
- 删除 `miniWorld/src/main/java/com/example/examplemod/Config.java`：移除无关 MDK 配置。
- 修改 `miniWorld/src/main/java/com/example/examplemod/ExampleMod.java` 为 `miniWorld/src/main/java/com/miniworld/miniworld/MiniWorldMod.java`：模组入口、事件总线注册和配置清理。
- 创建 `miniWorld/src/main/resources/META-INF/accesstransformer.cfg`：仅暴露运行时加入 ServerLevel 所需的 MinecraftServer 字段。
- 创建 `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldIds.java`：纯 UUID、ResourceLocation 和坐标规则。
- 创建 `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldRecord.java`：单个玩家记录的数据对象。
- 创建 `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldSavedData.java`：主世界 SavedData 的 NBT 读写和记录管理。
- 创建 `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldLevelFactory.java`：创建、注册、初始化和关闭运行时 ServerLevel。
- 创建 `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldManager.java`：组合 SavedData、ServerLevel 和传送权限。
- 创建 `miniWorld/src/main/java/com/miniworld/api/MiniWorldApi.java`：无 KubeJS 依赖的稳定静态 API。
- 创建 `miniWorld/src/main/java/com/miniworld/miniworld/command/MiniWorldCommands.java`：`enter`、`return`、`info` 和管理员目标参数。
- 创建 `miniWorld/src/main/java/com/miniworld/miniworld/event/MiniWorldEvents.java`：服务器启动和玩家登录处理。
- 创建 `miniWorld/src/test/java/com/miniworld/miniworld/MiniWorldGameTests.java`：Forge GameTest，验证平台、边界和记录恢复。

### Task 1: 清理 MDK 身份和构建入口

**Files:**
- Modify: `miniWorld/gradle.properties`
- Delete: `miniWorld/src/main/java/com/example/examplemod/Config.java`
- Rename and modify: `miniWorld/src/main/java/com/miniworld/miniworld/MiniWorldMod.java`
- Modify: `miniWorld/src/main/resources/META-INF/mods.toml` only if processed properties require a direct text change

**Interfaces:**
- Produces `MiniWorldMod.MODID = "miniworld"` and a Forge mod entry point that compiles without any `com.example` package.

- [ ] **Step 1: Write the identity verification first**

  Add a PowerShell verification script under `miniWorld` only temporarily in the command line, not as a project dependency, that asserts `gradle.properties` contains `mod_id=miniworld`, `mod_group_id=com.miniworld.miniworld`, and that `src/main/java` has no path containing `example`.

- [ ] **Step 2: Run the verification before edits**

  Run:

  ```powershell
  $p = Get-Content -Raw miniWorld\gradle.properties
  if ($p -notmatch '(?m)^mod_id=miniworld$' -or $p -match 'examplemod|com\.example') { throw 'identity check failed as expected' }
  ```

  Expected: the command fails because the MDK still contains `examplemod`.

- [ ] **Step 3: Make the minimal identity change**

  Set these exact properties:

  ```properties
  mod_id=miniworld
  mod_name=Mini World
  mod_group_id=com.miniworld.miniworld
  mod_authors=MiniWorld
  mod_description=为每个玩家提供可持久化的个人基岩小世界。
  ```

  Move the main class to `com.miniworld.miniworld`, set `@Mod(MiniWorldMod.MODID)`, remove client-only example logging, remove the sample block/item/tab/config registration, and register only the common Forge event handlers.

- [ ] **Step 4: Re-run identity and compilation checks**

  Run:

  ```powershell
  $p = Get-Content -Raw miniWorld\gradle.properties
  if ($p -notmatch '(?m)^mod_id=miniworld$' -or $p -notmatch '(?m)^mod_group_id=com\.miniworld\.miniworld$') { throw 'identity check failed' }
  if (rg -n 'examplemod|com\.example|ExampleMod|Config' miniWorld\src\main\java) { throw 'example source remains' }
  .\miniWorld\gradlew.bat -p miniWorld compileJava
  ```

  Expected: identity checks pass and the minimal entry point compiles.

### Task 2: 先建立 ID、记录和 SavedData 的失败测试

**Files:**
- Create: `miniWorld/src/test/java/com/miniworld/miniworld/MiniWorldGameTests.java`
- Create: `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldIds.java`
- Create: `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldRecord.java`
- Create: `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldSavedData.java`

**Interfaces:**
- `MiniWorldIds.dimensionKey(UUID)` returns `ResourceKey<Level>` with location `miniworld:player_<32位小写UUID>`.
- `MiniWorldIds.spawn()` returns `new BlockPos(8, -63, 8)`.
- `MiniWorldIds.platformBounds()` returns the inclusive block range `[-16, 31]` on both X and Z.
- `MiniWorldRecord` exposes `owner()`, `dimension()`, `spawn()`, `createdAt()`.
- `MiniWorldSavedData.get(ServerLevel)` uses storage name `miniworld_players`.
- `MiniWorldSavedData.getOrCreate(UUID, long)` creates one record and calls `setDirty()` only when a new record is inserted.

- [ ] **Step 1: Write a failing GameTest against the wished-for pure API**

  The test must assert the exact stable ID and platform bounds without constructing a server:

  ```java
  @GameTest(template = "empty", timeoutTicks = 1)
  public static void idAndBoundsAreStable(GameTestHelper helper) {
      UUID owner = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
      ResourceKey<Level> key = MiniWorldIds.dimensionKey(owner);
      helper.assertTrue(key.location().toString().equals(
          "miniworld:player_123e4567e89b12d3a456426614174000"), key.location().toString());
      helper.assertTrue(MiniWorldIds.platformBounds().minX() == -64, "minX");
      helper.assertTrue(MiniWorldIds.platformBounds().maxX() == 79, "maxX");
      helper.assertTrue(MiniWorldIds.platformBounds().minZ() == -64, "minZ");
      helper.assertTrue(MiniWorldIds.platformBounds().maxZ() == 79, "maxZ");
      helper.succeed();
  }
  ```

- [ ] **Step 2: Run the test before implementing the types**

  Run:

  ```powershell
  .\miniWorld\gradlew.bat -p miniWorld runGameTestServer
  ```

  Expected: compilation fails because `MiniWorldIds` and its bounds type do not exist.

- [ ] **Step 3: Implement the minimal pure types**

  Use a small immutable bounds value object inside `MiniWorldIds`, remove UUID hyphens with `uuid.toString().replace("-", "")`, and construct the key with:

  ```java
  ResourceKey.create(Registries.DIMENSION,
      new ResourceLocation(MiniWorldMod.MODID, "player_" + uuidText));
  ```

  Implement `MiniWorldRecord` as an immutable class and `MiniWorldSavedData` with `SavedData.Factory`, `CompoundTag`, `ListTag`, `NbtUtils`, and the exact storage name above. Reject duplicate owners by returning the existing record.

- [ ] **Step 4: Run the pure GameTest and a serialization check**

  Run:

  ```powershell
  .\miniWorld\gradlew.bat -p miniWorld runGameTestServer
  ```

  Expected: the ID/bounds GameTest passes. Add a second test that writes two records to a `CompoundTag`, loads them into a new `MiniWorldSavedData`, and asserts both UUIDs and dimension keys match.

### Task 3: 运行时 ServerLevel、平台和持久化恢复

**Files:**
- Create: `miniWorld/src/main/resources/META-INF/accesstransformer.cfg`
- Create: `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldLevelFactory.java`
- Modify: `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldSavedData.java`

**Interfaces:**
- `MiniWorldLevelFactory.ensureLevel(MinecraftServer, MiniWorldRecord)` returns an existing or newly created `ServerLevel`.
- `MiniWorldLevelFactory.initializePlatform(ServerLevel)` sets a 48-block border centered at `(8,8)` and the spawn point to `(8,-63,8)`; the `FlatLevelSource` generates one bedrock layer at `y=-64`.
- `MiniWorldLevelFactory.unloadIfEmpty(MinecraftServer, ResourceKey<Level>)` saves and unloads an empty runtime level without deleting its files or SavedData record.

- [ ] **Step 1: Add a failing integration GameTest for the platform**

  Add a test that obtains a test server player, calls `MiniWorldApi.ensureWorld(server, player.getUUID())`, obtains the returned `ServerLevel`, and asserts:

  ```java
  helper.assertTrue(level.getBlockState(new BlockPos(8, 0, 8)).is(Blocks.BEDROCK), "platform");
  helper.assertTrue(level.getWorldBorder().getSize() == 48.0D, "border size");
  helper.assertTrue(level.getWorldBorder().getCenterX() == 8.0D, "border x");
  helper.assertTrue(level.getWorldBorder().getCenterZ() == 8.0D, "border z");
  ```

  Keep the test’s first call behind `Class.forName("com.miniworld.api.MiniWorldApi")` while the API is not yet created, so the test can run and fail for the missing feature rather than silently passing.

- [ ] **Step 2: Run the failing integration test**

  Run:

  ```powershell
  .\miniWorld\gradlew.bat -p miniWorld runGameTestServer
  ```

  Expected: the test fails because the API/factory cannot yet create a personal `ServerLevel`.

- [ ] **Step 3: Implement the runtime level factory**

  Build the `LevelStem` from the registered overworld stem’s registered dimension type and a `FlatLevelSource` configured with a single bedrock layer and plains biome. Construct `DerivedLevelData` from the server world data and overworld level data. Construct `ServerLevel` with the server executor, storage source, `ChunkProgressListener` implementation used by the Forge 1.20.1 constructor, the unique dimension key, and no custom spawners.

  Add the new level to the Access-Transformer-exposed `MinecraftServer.levels` map and post `new LevelEvent.Load(level)`. Configure the personal 48-block world border and spawn point; do not manually call `setBlock` or force-load the 3×3 chunks because `FlatLevelSource` generates the single bedrock layer on demand. Mark SavedData dirty only after a newly created runtime level.

  The access transformer must expose only the fields actually used by the compiled code, including the server level map, executor, and storage source, with Forge 1.20.1 field names confirmed by the compiler. Do not use reflection or a new library.

- [ ] **Step 4: Implement restart recovery**

  Do not restore every runtime level at server startup. `ensureRecord` loads or creates only the SavedData record; `ensureLevel` creates the runtime level only for an explicit enter/API call. Mark SavedData dirty only when inserting a new record or creating a runtime level.

- [ ] **Step 5: Run platform, border, and restart checks**

  Run:

  ```powershell
  .\miniWorld\gradlew.bat -p miniWorld runGameTestServer
  .\miniWorld\gradlew.bat -p miniWorld build
  ```

  Expected: platform and border tests pass, and the Forge build exits with code 0. Start the generated dedicated server once, stop it cleanly, start it again with the same world folder, and confirm the saved dimension key is restored instead of creating a second key.

### Task 4: 管理器、API、事件和命令

**Files:**
- Create: `miniWorld/src/main/java/com/miniworld/miniworld/world/MiniWorldManager.java`
- Create: `miniWorld/src/main/java/com/miniworld/api/MiniWorldApi.java`
- Create: `miniWorld/src/main/java/com/miniworld/miniworld/event/MiniWorldEvents.java`
- Create: `miniWorld/src/main/java/com/miniworld/miniworld/command/MiniWorldCommands.java`
- Modify: `miniWorld/src/main/java/com/miniworld/miniworld/MiniWorldMod.java`

**Interfaces:**
- `MiniWorldManager.forServer(MinecraftServer)` returns the manager for that server.
- `MiniWorldManager.ensureWorld(UUID)` returns `MiniWorldRecord` and ensures its `ServerLevel` exists.
- `MiniWorldManager.enter(ServerPlayer, UUID, boolean)` returns `boolean`; it rejects non-owner access unless the flag is true, then teleports to the saved spawn `(8,-63,8)`.
- `MiniWorldManager.returnToOverworld(ServerPlayer)` returns `boolean` and teleports to the overworld spawn.
- `MiniWorldApi.ensureWorld(MinecraftServer, UUID)` returns `ResourceKey<Level>`.
- `MiniWorldApi.enter(ServerPlayer)` returns `boolean` for the owner.
- `MiniWorldApi.enter(ServerPlayer, UUID, boolean)` returns `boolean` for explicit exception access.
- `MiniWorldApi.returnToOverworld(ServerPlayer)` returns `boolean`.
- `MiniWorldApi.getDimensionId(MinecraftServer, UUID)` returns `String` or `null` when no record exists.
- `MiniWorldApi.hasWorld(MinecraftServer, UUID)` returns `boolean`.

- [ ] **Step 1: Write failing command/API behavior tests**

  Add GameTests for these exact cases: first login creates one record without changing the player dimension; owner entry succeeds; a different player entry with `allowNonOwner=false` returns false and leaves their level unchanged; the same call with `allowNonOwner=true` succeeds; return sends the player to `Level.OVERWORLD`.

- [ ] **Step 2: Run the behavior tests before implementation**

  Run:

  ```powershell
  .\miniWorld\gradlew.bat -p miniWorld runGameTestServer
  ```

  Expected: the tests fail because lifecycle, manager, and API classes are absent.

- [ ] **Step 3: Implement lifecycle handlers**

  Register `PlayerEvent.PlayerLoggedInEvent` to call `ensureRecord(player.getUUID())` without creating a runtime level. On dimension change and logout, unload the old/current mini dimension when its player list is empty. Do not register client-only classes in common code.

- [ ] **Step 4: Implement the stable KubeJS-compatible API**

  Keep every API method public and static, use only Minecraft/Forge/Java types, and return booleans or nullable IDs instead of KubeJS event objects. A KubeJS script can call:

  ```javascript
  const MiniWorldApi = Java.loadClass('com.miniworld.api.MiniWorldApi')
  MiniWorldApi.ensureWorld(event.server, event.player.getUuid())
  MiniWorldApi.enter(event.player)
  ```

- [ ] **Step 5: Implement commands and permissions**

  Register `/miniworld enter`, `/miniworld return`, and `/miniworld info` for players. Add `/miniworld enter <player>` and `/miniworld info <player>` with permission level 2. Owner entry passes `allowNonOwner=false`; the operator target command passes `allowNonOwner=true`. Return clear Chinese feedback for success, missing records, denied access, and non-player command sources.

- [ ] **Step 6: Run command/API and KubeJS-surface checks**

  Run:

  ```powershell
  .\miniWorld\gradlew.bat -p miniWorld runGameTestServer
  .\miniWorld\gradlew.bat -p miniWorld build
  ```

  Expected: all registered GameTests pass, the jar builds, and `jar tf miniWorld\build\libs\*.jar` contains `com/miniworld` and `miniworld` resources but no `com/example` or `examplemod` entries.

### Task 5: 完整验证和交付检查

**Files:**
- Modify only files created by Tasks 1–4 if a verification failure identifies a concrete defect.

**Interfaces:**
- Produces a buildable Forge mod jar with stable dimension and API behavior.

- [ ] **Step 1: Run the full clean build**

  Run:

  ```powershell
  .\miniWorld\gradlew.bat -p miniWorld clean build
  ```

  Expected: exit code 0 and a jar in `miniWorld/build/libs`.

- [ ] **Step 2: Run GameTests from a clean run directory**

  Run:

  ```powershell
  .\miniWorld\gradlew.bat -p miniWorld runGameTestServer
  ```

  Expected: required tests report success and the process exits with code 0.

- [ ] **Step 3: Verify names and dependencies**

  Run:

  ```powershell
  if (rg -n 'examplemod|com\.example|ExampleMod' miniWorld\src miniWorld\gradle.properties) { throw 'example identifier remains' }
  $deps = Get-Content -Raw miniWorld\build.gradle
  if ($deps -match 'implementation|api|compileOnly|runtimeOnly' -and $deps -match 'mezz|latvian|architectury|rhino|kubejs') { throw 'unexpected third-party dependency' }
  ```

  Expected: no forbidden identity remains and the project still depends only on Forge/Minecraft.

- [ ] **Step 4: Inspect the final diff and report evidence**

  Confirm that only `miniWorld` changed, that no dimension deletion code exists, and report the exact build/GameTest results plus the generated jar path. Do not claim completion if a command exits non-zero.
