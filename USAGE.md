# PVP Balance Create Compatibility — 使用与实现说明

## 定位

本目录是 `pvpbalance` 的独立附属模组，只负责 Create Contraption 防复制。
核心区域的创建、快照、伤害/恢复、耐久保护和原版活塞保护仍在核心模组中。

分家后的依赖关系：

```text
pvpbalance-create
  -> pvpbalance (core)
  -> create
  -> fabric-api / fabric-loader
```

核心模组不依赖 Create；未安装本附属模组时，核心模组不会加载任何 Create Mixin。

## 安装

服务端需要：

- `pvpbalance-1.9.0.jar`
- `pvpbalance-create-1.0.0.jar`
- Create 6.0.8+
- 核心模组要求的 Fabric API、Fabric Loader、WorldEdit

客户端不需要安装两个 PVP Balance 模组；Create 客户端是否安装取决于服务器/整合包配置，
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
| `PvpBalanceProtected` | 是否受保护 |
| `PvpBalanceMultiZone` | 是否与多个区域相交 |
| `PvpBalanceZone` | 单区域时的区域 ID |
| `PvpBalanceLocalBounds` | 组装时局部 AABB，6 个 double |

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

区域边界使用核心模组的 `PvpZone.fullyContainsBlockBounds`，区域最大坐标按包含方块处理，
因此最大边会加 1 个方块单位。

### 3. 清除

`ProtectedContraption` 接口由 `ContraptionMixin` 实现，提供：

```java
void pvpbalance$clearContents();
```

清除包括：

- `getBlocks().clear()`
- `capturedMultiblocks.clear()`
- 遍历 `getStorage().getAllItemStorages()`，将每个槽位设为 `ItemStack.EMPTY`
- 调用方设置 `disassembled = true`

清除后 `addBlocksToWorld` 被取消，不会放置方块，也不会生成方块掉落物。

### 4. 实体销毁保护

`AbstractContraptionEntityMixin` 注入 `Entity.remove(RemovalReason)`：

- 跳过区块卸载、玩家卸载、换维度；
- 正常拆卸已设置 `disassembled`，跳过；
- 受保护 Contraption 在区域外被直接销毁时，清空内容。

## 限制

- 只处理 Create 的 Contraption 体系，不处理其他模组的方块搬运系统。
- 使用局部 AABB，旋转/镜像后可能偏保守。
- 旧版 Contraption 物品没有保护字段，按未保护处理。
- 本模组不提供独立指令和配置，所有区域信息来自核心模组。
- `/pvb reset` 不负责阻止复制。

## 构建顺序

核心模组必须先构建，生成供本模组编译使用的开发 jar：

```bash
cd ../pvpbalance-core
./gradlew build
```

然后：

```bash
cd ../pvpbalance-create
./gradlew build
```

`build.gradle` 使用：

```groovy
compileOnly files("../pvpbalance-core/build/devlibs/pvpbalance-${project.core_version}-dev.jar")
modCompileOnly "com.simibubi.create:create-fabric:6.0.8.1+local-mc1.20.1"
```

## 已验证场景

- 区域内组装 -> NBT 序列化 -> 反序列化，保护标记保留。
- 区域外拆卸：整体清除，不放置方块。
- 区域内拆卸：正常放置。
- 多区域相交：无条件清除。
- 区域外实体销毁：清空 Contraption。
- 核心与附属模组同时安装时服务端正常启动。
