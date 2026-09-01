# 多人服务器性能对比：ecoaegtnh vs 上游 GTNH-ECO-1.7.10（neoecoae）

> 对比对象：本 mod（`ecoaegtnh`，mixin 移植版）vs 参考/GTNH-ECO-1.7.10（cn.dancingsnow.neoecoae，1.7.10 移植版）。
> 两者同源于 1.12.2 NovaEngineering-ECOAEExtension，但 1.7.10 落地方式完全不同。

## 1. 架构总览

| 维度 | 上游 neoecoae | 我们 ecoaegtnh |
|---|---|---|
| 虚拟 CPU 接入 AE2 | **继承** `CraftingCPUCluster` + **反射**写私有字段 `CraftingGridCache.craftingCPUClusters`（`ECOComputationCpuBridge`，静态缓存 Field 对象） | **mixin** `@Shadow` 私有字段 + `@Inject` 拦截（编译期织入，零反射） |
| 同步/挂载节奏 | 事件驱动：`requestComputationCpuRefresh()` 置 tick 标记 → 下 tick `refresh()`；另有 `%20` 周期刷新 | 结构重检 `%40` 时 `createVirtualCPU()` + 生命周期事件（submitJob/destroy） |
| updateCraftingLogic | 虚拟 CPU **覆写方法**（多态，只影响自己的实例） | `@Inject` HEAD（守卫）+ TAIL（统计）——**对网格所有 CPU 实例生效** |
| 原版 CPU 额外开销 | **零** | 每 tick 2 次字段 null 检查（HEAD 守卫）+ 1 次 `addUsedTime(usedOps[0])`（TAIL，recorder 为 @Unique final 非 null）；每次 getGrid/isActive/markDirty 调用 1-2 次字段检查 |
| 存储实现 | 自定义 `ECOStorageBackend` + **revision 单调缓存**（`ECOAvailableItemsCache`：revision 未变直接返回缓存 IItemList） | AE2U 标准 `CellInventory` 继承（自带 cellStacks 缓存，saveChanges 后重建）+ drive 懒缓存（t115 已改纯事件驱动）+ tooltip 纯 NBT 直读 |
| 合成吞吐优化 | **fastpath**（`ECOFastPathCache` pattern 匹配结果缓存）+ **runtime batch**（批处理协调/事务）+ **cooling**（冷却配方） | 无（走 AE2U 原版合成引擎） |
| mixin 总数 | 4 个（pattern term GUI ×2、GuiScreen accessor、NEI handler）——**无 AE2 核心类 mixin** | 10 个（含 server 侧 CraftingCPUCluster/CraftingGridCache/GridStorageCache/CraftingCPUStatus） |
| 网络包 | `NEPatternUploadNetwork`（pattern 上传专用，3 包） | `C2SNetworkCellTypeSelected` + MUI `FakeSyncWidget` 按需同步 |
| tick 模型 | 普通 TileEntity `updateEntity` 每 tick + 事件驱动/`%20` 节流 | GT 机器 `onPostTick` 每 tick + `%5/%40/%600` 分频 |

## 2. 关键差异分析

### 2.1 虚拟 CPU 接入：mixin vs 继承+反射
- 我们：编译期织入，运行时零反射；但注入点对 AE2U 方法签名敏感（`updateCraftingLogic(IGrid, IEnergyGrid, CraftingGridCache)` 签名变化 → mixin 失败），AE2U 升级需回归验证。
- 上游：`field.get(grid)` 每次 sync 一次反射（静态缓存 Field，频率低，开销可忽略）；继承要求 CraftingCPUCluster 可被继承（构造器/方法可见性），AE2U 升级同样敏感。
- 结论：运行时性能我们略优；兼容性两者等价。

### 2.2 对原版合成 CPU 的全局开销（**唯一实质差异**）
- 我们：**每个**合成 CPU（包括玩家自建的原版 CPU）每 tick 执行 HEAD 守卫（2 次 null 检查）+ TAIL `addUsedTime`；每次 getGrid/isActive/markDirty 调用执行 1-2 次字段检查。单次开销纳秒级，但**随 CPU 数量线性累计**——数百个 CPU 的服务器上每 tick 增加微秒级，可忽略但存在。
- 上游：override 只作用于虚拟 CPU 实例，原版 CPU 零额外开销。
- 若要消除：可给 HEAD/TAIL 注入加"非 ECO 实例直接 return"的更快路径（目前守卫已是最短路径，收益有限）；或未来迁移到继承方案（工程量大，不推荐）。

### 2.3 存储缓存
- 上游 revision 缓存：`getAvailableItems` 在无写入时**零重建**（直接返回缓存 IItemList）。
- 我们：AE2U `CellInventory` 内部同样"写后重建"（saveChanges 触发），等价；我们的 tooltip NBT 直读（t84）优于上游 tooltip 走完整 inventory 链。
- 结论：等价，各有所长。

### 2.4 合成吞吐（上游有、我们没有）
- fastpath：缓存 pattern 匹配结果，避免大请求树重复匹配计算——**玩家大量 ECO 合成时上游更省 CPU**。
- batch：大请求分批协调 + 事务回滚。
- cooling：机制功能（非性能）。
- 我们的 vCPU 走 AE2U 原版合成引擎（AE2U 自身有优化）；功能差异，非缺陷。若未来做"大请求吞吐"优化，fastpath 缓存是最值得借鉴的。

## 3. 多人服务器结论

1. **两者都不是服务器性能瓶颈**：本 mod 的单 tick 成本（分频 + 事件驱动 + 轻守卫）在多人服务器上可忽略。
2. 我们 mixin 的"全局守卫开销"是理论差异：数百 CPU 时每 tick 微秒级；**不值得为此重构**，但 AE2U 升级时需回归 mixin 签名。
3. 上游值得补的：fastpath pattern 缓存（若玩家大量使用 ECO 自动合成）。
4. 我们已优于上游的点：零反射、GUI 按需同步、tooltip NBT 直读、t115 纯事件驱动 drive 缓存。