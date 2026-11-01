# MiniGamePlayground — Create Compatibility

MiniGamePlayground 的 Create 兼容附属模组，为核心模组的游戏区域提供 Contraption 防复制保护和 Create 实体快照支持。

- 服务端模组，客户端不需要安装。
- 依赖核心模组 `minigameplayground` 2.2.6+。
- 依赖 Create 6.0.8+。
- 不添加指令、方块、物品或配置；区域数据全部来自核心模组。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.92.6+1.20.1 |
| Java | 17+ |
| MiniGamePlayground Core | 2.2.6+ |
| Create | 6.0.8+ |
| WorldEdit | 核心模组要求，7.2.15+ |

## 安装

1. 安装核心模组 `minigameplayground-2.2.6.jar`。
2. 安装本附属模组 `minigameplayground-create-1.3.10.jar`。
3. 从旧版升级时，先移除旧的 `pvpbalance-create-*.jar`。
4. 服务端安装 Create 6.0.8+。
5. 启动服务器。

核心模组仍然负责区域创建、方块快照、伤害/恢复和原版活塞保护；本模组在创建
Contraption 时读取核心模组的区域数据，并向核心注册 Create 实体快照扩展。

## 保护规则

### 标记时机

Create 的所有组装路径最终都会调用 `Contraption.removeBlocksFromWorld`。本模组在该方法
开头读取：

- 当前 Contraption 的局部 `bounds`
- `anchor` 世界坐标

把局部 `bounds` 平移 `anchor` 后，与核心模组的区域列表求交。

结果：

- 没有相交区域：不标记；
- 相交一个区域：标记区域 ID 和局部 AABB；
- 相交多个区域：标记为多区域，并保存局部 AABB。

保护字段写入 Contraption NBT：

```text
MiniGamePlaygroundProtected
MiniGamePlaygroundMultiZone
MiniGamePlaygroundZone
MiniGamePlaygroundLocalBounds
```

升级兼容：如果读取到旧版 `PvpBalanceProtected` 等 NBT 字段，会按旧字段恢复保护；
新写入统一使用 `MiniGamePlayground*` 字段。

`MountedContraption.writeNBT/readNBT` 会调用父类，因此矿车 Contraption 被拆成
`MinecartContraptionItem` 后，保护标记会随物品 NBT 保留。

### 拆卸判断

在 `Contraption.addBlocksToWorld` 执行前检查：

- 多区域 Contraption：无条件清除；
- 区域已被删除、维度不一致、保护标记缺失：失败关闭，清除；
- 单区域 Contraption：将保存的局部 AABB 的 8 个角通过当前
  `StructureTransform` 转换，得到最终世界 AABB；
- 最终 AABB 完全位于原区域内：允许正常放置；
- 最终 AABB 有任何部分在区域外：整体清除。

允许 Contraption 临时移出区域再移回；只在拆卸时判断最终位置，不做每 tick 检查。

### 清除内容

清除时：

- 不放置任何方块；
- 不产生方块掉落物；
- 清空 Contraption 的已挂载物品存储（箱子等容器内容物）；
- 清空 `capturedMultiblocks`；
- 将 `Contraption.disassembled` 设为 `true`；
- 不生成带内容物的可用矿车 Contraption 物品。

### 快照重置与 Create 动能状态

Create 的 `KineticBlock.updateIndirectNeighbourShapes` 会在方块放置时调用
`clearKineticInformation()`，清空速度、网络和应力。WorldEdit 粘贴时也会触发这个
钩子，导致 `/mgp reset` 后电机、传动杆、齿轮和传送带速度为 0。

本附属模组在核心模组执行快照恢复期间检查 `RestoreContext.isRestoring()`，
在 `KineticBlockMixin` 中跳过这次破坏性的动能清理，让快照中的
`Speed`、`Source` 和 `Network` NBT 正常写回。恢复后传动方向、转速和应力状态保留。

此外，`KineticBlockEntityMixin` 会防止 Create 的临时 `wasMoved` 标记导致
`KineticBlockEntity.read()` 跳过动能 NBT；恢复结束后，`KineticRestore` 会重新把
方块注册到内存中的 `KineticNetwork`，并刷新应力/容量和客户端数据。这样不会只恢复
单个方块的 `Speed`，而是恢复整条传动网络的运行状态。

`KineticRestore` 不调用 `attachKinetics`/`updateSpeed`；完整网络破坏后再恢复时，
避免在方块尚未全部就位、速度尚未一致时触发 Create 的冲突传播逻辑。如果触发
`RotationPropagator` 的速度冲突检查，Create 会直接 `destroyBlock`，表现为应力组件
在恢复瞬间断裂。

### Create 实体快照

本附属向核心注册 `ZoneSnapshotExtension`（ID：`create`），因此核心不需要在编译期或
运行期直接依赖 Create：

- 核心保存普通实体，并跳过所有 Create 实体；
- 附属保存 `com.simibubi.create.*` 实体，包括 Controlled/Oriented Contraption、
  Minecart Contraption、Super Glue 等；
- 实体 NBT 写入快照目录的 `.entities-create.nbt` 文件；
- 恢复由核心统一调用 `EntityType.loadEntityRecursive` 完成；
- 核心在保存前统一剥离玩家乘客，在重置清空实体前统一让玩家下坐骑，附属不单独处理玩家；
- 没有实体文件的旧快照仍按旧行为只恢复方块。

快照格式与核心实体快照一致：包含版本号和根实体 NBT 列表，乘客 NBT 随根实体保存，
但玩家乘客会被剥离，因此载具保存为空载具。

### 实体销毁

受保护 Contraption 实体如果在区域外被直接销毁（例如矿车 Contraption），
在 `Entity.remove` 阶段也会清空 Contraption 内容。

以下移除原因不会触发清除，以免区块卸载/换维度误删结构：

- `UNLOADED_TO_CHUNK`
- `UNLOADED_WITH_PLAYER`
- `CHANGED_DIMENSION`

正常拆卸会先由 `addBlocksToWorld` 设置 `disassembled = true`，因此不会重复清除。

## 兼容范围

- 覆盖 Create 的 Contraption 体系：轴承、矿车装配、滑轮、机械活塞、龙门架、电梯、列车等。
- 覆盖 Contraption 内的箱子等已挂载物品存储。
- 原版活塞保护仍在核心模组中，不依赖本附属模组。
- 不覆盖其他模组自己的方块搬运/采掘系统。
- 未带保护标记的旧 Contraption 物品按未保护处理，不会误删升级前的结构。
- 使用 AABB 判断，旋转/镜像后的包围盒可能比实际方块范围更保守；靠近区域边缘时可能被判定为越界。
- `/mgp reset` 是恢复手段，不是防复制手段。

## 构建

先构建核心模组：

```bash
cd ../minigameplayground-core
./gradlew build
```

再构建本附属模组：

```bash
cd ../minigameplayground-create
./gradlew build
```

产物：

```text
build/libs/minigameplayground-create-1.3.10.jar
```

本附属模组的编译依赖：

- 核心模组的开发 jar：
  `../minigameplayground-core/build/devlibs/minigameplayground-2.2.6-dev.jar`
- 本机 Maven 仓库中的 Create 开发包：
  `com.simibubi.create:create-fabric:6.0.8.1+local-mc1.20.1`

这两个依赖都只用于编译，不会打包进发布 jar；运行时由核心模组和 Create 提供。

## 已验证场景

使用 Fabric Loader 0.18.4 + Fabric API + Create 6.0.8.1 + WorldEdit 7.2.15 的服务端验证：

- Contraption 在区域内组装后序列化为 NBT、再反序列化，保护标记和局部 AABB 保留。
- 受保护 Contraption 在区域外拆卸时整体清除，不放置方块。
- 受保护 Contraption 在区域内拆卸时正常放置。
- 与多个区域相交的 Contraption，即使最终位置在其中一个区域内，拆卸时仍整体清除。
- 区域外直接销毁受保护 Contraption 实体时，方块被清空。
- 含创造电机、传动杆、齿轮、飞轮的区域快照在 `/mgp reset` 后保持 `Speed=16`、`Source` 和 `Network`；重置后的网络 Size、Capacity 和应力状态正确。
- Redstone Link 接收模式在恢复后会重新加入远程红石网络，不再因为 `LastKnownPosition` 相同而忽略接收信号。
- 可编程齿轮箱恢复后会重新绑定 `Source`、按快照速度和指令状态重新计算运行时序，并重放当前红石输入状态；该逻辑从快照 schematic 读取，旧快照同样生效。
- Redstone Link 在读取 NBT 前会先创建 `LinkBehaviour`，避免 WorldEdit 在行为未初始化时读取导致空指针。
- 物流链接会重新执行全局注册；流体管道会重建临时 `FluidNetwork`。
- **列车不参与快照恢复**：轨道方块可以恢复，但列车、车厢实体、`RailwaySavedData` 中的 Train/TrackGraph 不恢复；旧快照中的车厢实体也会在恢复后被清理。
- 核心模组单独安装时不加载 Create Mixin；两个模组同时安装时正常启动。
