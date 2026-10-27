# PVP Balance — Create Compatibility

PVP Balance 的 Create 兼容附属模组，为核心模组的 PVP 区域提供 Contraption 防复制保护。

- 服务端模组，客户端不需要安装。
- 依赖核心模组 `pvpbalance` 1.9.0+。
- 依赖 Create 6.0.8+。
- 不添加指令、方块、物品或配置；区域数据全部来自核心模组。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.92.6+1.20.1 |
| Java | 17+ |
| PVP Balance Core | 1.9.0+ |
| Create | 6.0.8+ |
| WorldEdit | 核心模组要求，7.2.15+ |

## 安装

1. 安装核心模组 `pvpbalance-1.9.0.jar`。
2. 安装本附属模组 `pvpbalance-create-1.0.0.jar`。
3. 服务端安装 Create 6.0.8+。
4. 启动服务器。

核心模组仍然负责区域创建、快照、伤害/恢复和原版活塞保护；本模组只在创建
Contraption 时读取核心模组的区域数据。

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
PvpBalanceProtected
PvpBalanceMultiZone
PvpBalanceZone
PvpBalanceLocalBounds
```

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
- `/pvb reset` 是恢复手段，不是防复制手段。

## 构建

先构建核心模组：

```bash
cd ../pvpbalance-core
./gradlew build
```

再构建本附属模组：

```bash
cd ../pvpbalance-create
./gradlew build
```

产物：

```text
build/libs/pvpbalance-create-1.0.0.jar
```

本附属模组的编译依赖：

- 核心模组的开发 jar：
  `../pvpbalance-core/build/devlibs/pvpbalance-1.9.0-dev.jar`
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
- 核心模组单独安装时不加载 Create Mixin；两个模组同时安装时正常启动。
