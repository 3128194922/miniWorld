# miniWorld 玩家个人临时维度设计

## 目标

为每个玩家创建一个稳定且唯一的个人维度。玩家首次进入服务器时创建维度，但不自动传送；玩家通过命令或 Java/KubeJS API 进入。维度数据写入存档，服务器重启后恢复，默认只有所有者可以进入，管理员或脚本可以显式允许例外。

## 范围与约束

- 目标平台：Minecraft 1.20.1 Forge 47.4.6。
- 只修改 `miniWorld` 主项目，不新增第三方依赖。
- 包名和模组 ID 不得包含 `example`；使用 `com.miniworld.miniworld` 与 `miniworld`。
- 不依赖 KubeJS 编译或运行；通过公开 Java API 兼容 KubeJS 的 `Java.loadClass`。
- 运行时维度只在玩家进入或 API 明确调用时创建；无人使用时卸载运行时对象，但不删除维度存档或玩家记录。

## 方案

使用运行时动态创建 `ServerLevel`。每个玩家的维度键为 `miniworld:player_<uuid无连字符小写>`，由玩家 UUID 决定，服务器重启后保持不变。

由于动态维度不会自动出现在启动时的静态 LevelStem 注册表中，模组维护主世界 `SavedData` 中的玩家记录。服务器启动只加载记录，不创建运行时 `ServerLevel`；玩家进入或 KubeJS API 明确调用时才把对应维度加入服务器维度表，同时触发 Forge 的维度加载事件。使用 Forge Access Transformer 暴露 MinecraftServer 的必要内部字段，避免反射和第三方库。

每个维度复用已注册的主世界维度类型，并使用 `FlatLevelSource` 配置一层基岩。世界边界固定为 3×3 区块的正方形，覆盖方块范围 `[-16,31]`，中心为 `(8,8)`，边界尺寸为 48 个方块，出生点为 `(8,-63,8)`，基岩层由生成器放在主世界最低高度 `y=-64`。边界内平台为基岩，边界外不可进入；玩家改造平台后的区块数据由原版区块存档保存。

## 数据与生命周期

`MiniWorldSavedData` 保存在主世界维度的数据存储中，记录：

- 数据格式版本；
- 玩家 UUID；
- 维度键字符串；
- 出生点坐标；
- 创建时间或创建序号，用于诊断。

服务器启动时只加载 SavedData。玩家首次登录只新增记录，不创建运行时维度；首次进入或 API 调用时才创建并保存对应维度。玩家离开维度、登出或无人使用时卸载运行时对象，先保存区块，再保留维度目录和 SavedData 记录。只有新增玩家记录或首次创建运行时维度时标记 SavedData 脏数据。

## 权限和命令

- `/miniworld enter`：玩家进入自己的个人维度。
- `/miniworld return`：玩家返回主世界出生点。
- `/miniworld info`：显示自己的维度 ID 和出生点。
- 管理员命令支持指定在线玩家，并可进入其维度；普通玩家不能进入他人的维度。
- 对外 API 的非所有者传送必须显式传入允许例外的参数，默认拒绝。

## KubeJS API

公开 `com.miniworld.api.MiniWorldApi`，只接受服务端对象和 UUID/ServerPlayer 等原版类型，不引用 KubeJS 类：

- `ensureWorld(MinecraftServer, UUID)`：创建或恢复玩家维度，返回维度键。
- `enter(ServerPlayer)`：进入调用者自己的维度。
- `enter(ServerPlayer, UUID, boolean allowNonOwner)`：进入指定玩家维度。
- `returnToOverworld(ServerPlayer)`：返回主世界。
- `getDimensionId(MinecraftServer, UUID)`：查询维度 ID。
- `hasWorld(MinecraftServer, UUID)`：查询是否已有记录。

脚本侧可使用：

```javascript
const MiniWorldApi = Java.loadClass('com.miniworld.api.MiniWorldApi')
MiniWorldApi.enter(event.player)
```

## 异常处理

- 维度键格式非法或记录损坏时跳过该记录并记录错误，不影响其他玩家维度加载。
- 传送目标不在线、个人维度不存在、调用发生在客户端或非服务端线程时返回失败结果或抛出带原因的 `IllegalStateException`。
- 创建维度前检查服务器维度表，避免重复创建。
- 关闭服务器前不主动删除维度目录或 SavedData 记录。

## 验证

优先为 UUID 到维度键、SavedData 序列化、权限判断和平台范围写单元级测试；再通过 Forge GameTest/服务端启动验证动态维度创建、区块平台、重启恢复和传送。最终运行 `gradlew build`，并检查产物中不存在 `example` 包路径或模组 ID。
