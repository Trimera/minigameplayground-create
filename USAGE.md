# MiniGamePlayground Create Compatibility — 使用与实现说明

## 定位

本目录是 `minigameplayground` 的独立附属模组，负责 Create Contraption 防复制和 Create 实体快照。
核心区域的创建、方块快照、伤害/恢复、耐久保护和原版活塞保护仍在核心模组中。

分家后的依赖关系：

```text
minigameplayground-create
  -> minigameplayground (core)
  -> create
  -> fabric-api / fabric-loader
```

核心模组不依赖 Create；未安装本附属模组时，核心模组不会加载任何 Create Mixin。

## 安装

服务端需要：

- `minigameplayground-2.2.1.jar`
- `minigameplayground-create-1.3.2.jar`
- Create 6.0.8+
- 核心模组要求的 Fabric API、Fabric Loader、WorldEdit

客户端不需要安装两个 MiniGamePlayground 模组；Create 客户端是否安装取决于服务器/整合包配置，
但本附属模组是服务端逻辑。

## 实现

### 1. 组装标记

`ContraptionMixin` 注入 `Contraption.removeBlocksFromWorld` 的 HEAD：

```text
worldBounds = bounds.move(anchor)
intersecting = ZoneManager.getZonesIntersecting(world, worldBounds)
```

- `bounds` 是 Create 维护的 Contraption 局部 AABB；
- 加上 `anchor` 后得到组装时的世界 AABB；
- 区域查询调用核心模组公开的 `ZoneManager` API。

标记字段：

| NBT 字段 | 含义 |
| --- | --- |
| `MiniGamePlaygroundProtected` | 是否受保护 |
| `MiniGamePlaygroundMultiZone` | 是否与多个区域相交 |
| `MiniGamePlaygroundZone` | 单区域时的区域 ID |
| `MiniGamePlaygroundLocalBounds` | 组装时局部 AABB，6 个 double |

旧版 `PvpBalanceProtected`、`PvpBalanceMultiZone`、`PvpBalanceZone`、
`PvpBalanceLocalBounds` 会被读取作为兼容回退；新写入使用 `MiniGamePlayground*` 字段。

`writeNBT` 在 RETURN 阶段追加字段；`readNBT` 在 RETURN 阶段恢复字段。
矿车 Contraption 的 `MountedContraption` 调用父类方法，因此自动继承。

### 2. 拆卸检查

`ContraptionMixin` 注入 `Contraption.addBlocksToWorld` 的 HEAD，并 `cancellable = true`。

判断顺序：

1. 未受保护或已经拆卸：直接返回；
2. 多区域：清除；
3. 没有区域 ID、没有局部 AABB、没有 `StructureTransform`：清除；
4. 区域不存在：清除；
5. Contraption 所在维度与区域不一致：清除；
6. 单区域：调用当前 `StructureTransform.apply(Vec3)` 变换局部 AABB 的 8 个角；
7. 最终世界 AABB 不完全在区域方块边界内：清除；
8. 否则允许原版放置流程。

区域边界使用核心模组的 `PlaygroundZone.fullyContainsBlockBounds`，区域最大坐标按包含方块处理，
因此最大边会加 1 个方块单位。

### 3. 清除

`ProtectedContraption` 接口由 `ContraptionMixin` 实现，提供：

```java
void minigameplayground$clearContents();
```

清除包括：

- `getBlocks().clear()`
- `capturedMultiblocks.clear()`
- 遍历 `getStorage().getAllItemStorages()`，将每个槽位设为 `ItemStack.EMPTY`
- 调用方设置 `disassembled = true`

清除后 `addBlocksToWorld` 被取消，不会放置方块，也不会生成方块掉落物。

### 4. 快照恢复时的 Create 动能状态

`KineticBlockMixin` 拦截 `KineticBlock.updateIndirectNeighbourShapes`：

- 平时不改变 Create 行为；
- 核心模组的 `ZoneSnapshots.restore` 在 `RestoreContext` 中标记“正在恢复”；
- 恢复期间跳过 `warnOfMovement` / `clearKineticInformation` 路径；
- 快照中的 `Speed`、`Source`、`Network` 等 BlockEntity NBT 正常写回。

这修复了 `/mgp reset` 后传送带停止、创造电机应力输出不再转动的问题。

恢复期间 `KineticBlockEntityMixin` 还会防止 Create 的临时 `wasMoved` 标记跳过
`Speed`/`Source`/`Network` NBT。`KineticRestore` 在方块恢复完成后重新把每个动能
方块注册进内存中的 `KineticNetwork`，重新计算应力/容量并刷新网络，避免只恢复部分
方块的转速而导致同一传动网络中的组件停止。

它不会调用 `attachKinetics` 或设置 `updateSpeed`。这样在“整条应力机构从源到应用端
全部被破坏再恢复”时，不会在方块尚未全部就位、速度尚未统一时触发 Create 的冲突
传播；Create 的冲突检查会调用 `destroyBlock`，表现为组件在恢复瞬间断裂。

### 5. Create 实体快照

附属在初始化时调用：

```java
ZoneSnapshots.registerExtension(new CreateSnapshotExtension());
```

扩展 ID 为 `create`，由核心写入 `.entities-create.nbt`：

- `handlesEntity` 判断实体类名是否以 `com.simibubi.create.` 开头；
- `saveEntities` 调用核心的 `snapshotEntityNbt`，只保存根实体；
- `clearEntities` 调用核心的 `clearEntities`，由核心先让玩家下坐骑再删除实体；
- 核心跳过所有 Create 实体，因此不会重复保存；
- 恢复使用核心的通用实体 NBT 加载逻辑，不把 Create 类型硬编码进核心；
- 玩家永远不写入快照；载具上的玩家乘客由核心统一剥离，保存为空载具；
- 没有实体文件的旧快照仍按旧行为只恢复方块。

### 6. 实体销毁保护

`AbstractContraptionEntityMixin` 注入 `Entity.remove(RemovalReason)`：

- 跳过区块卸载、玩家卸载、换维度；
- 正常拆卸已设置 `disassembled`，跳过；
- 受保护 Contraption 在区域外被直接销毁时，清空内容。

## 限制

- 只处理 Create 的 Contraption 体系，不处理其他模组的方块搬运系统。
- 使用局部 AABB，旋转/镜像后可能偏保守。
- 旧版 Contraption 物品没有保护字段，按未保护处理。
- 本模组不提供独立指令和配置，所有区域信息来自核心模组。
- Create 实体快照依赖核心 2.2.1+ 的 `ZoneSnapshotExtension` API。
- `/mgp reset` 不负责阻止复制。

## 构建顺序

核心模组必须先构建，生成供本模组编译使用的开发 jar：

```bash
cd ../minigameplayground-core
./gradlew build
```

然后：

```bash
cd ../minigameplayground-create
./gradlew build
```

`build.gradle` 使用：

```groovy
compileOnly files("../minigameplayground-core/build/devlibs/minigameplayground-${project.core_version}-dev.jar")
modCompileOnly "com.simibubi.create:create-fabric:6.0.8.1+local-mc1.20.1"
```

## 已验证场景

- 区域内组装 -> NBT 序列化 -> 反序列化，保护标记保留。
- 区域外拆卸：整体清除，不放置方块。
- 区域内拆卸：正常放置。
- 多区域相交：无条件清除。
- 区域外实体销毁：清空 Contraption。
- 含创造电机、传动杆、齿轮、飞轮的区域快照在 `/mgp reset` 后保持转速 16、Source 和 Network 数据。
- 核心与附属模组同时安装时服务端正常启动。
- 实体快照扩展已注册；普通实体和玩家乘客安全逻辑由核心测试覆盖。
