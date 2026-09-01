# vCPU 相同配方合成请求合并方案（t116 设计稿）

## 1. 目标

玩家用 vCPU 下单物品 A（占用一个线程槽），**任务进行中**再次下单相同的 A：
- 若池剩余字节够 → 把第二次请求**合并进正在运行的那个 vCPU 线程**（AE2U 原版 mergeJob 机制）
- **不消耗新线程槽**（线程数量不增加）

## 2. 现状与根因

AE2U（GTNH fork）**原版已有合并机制**（`CraftingGridCache.submitJob` 里 busy CPU 优先 + `CraftingCPUCluster.submitJob` 内合并判断）：

```java
// CraftingCPUCluster.submitJob（AE2U rv3-beta-1000）
if (requestingMachine == null && myLastLink != null && myLastLink.isStandalone()
    && isBusy() && finalOutput.get().isSameType(job.getOutput())
    && availableStorage >= usedStorage + job.getByteTotal()) {
    return mergeJob(g, job, src, requestingMachine);   // ← 原版合并
}
```

合并条件：玩家请求（无请求机器）+ 当前任务 standalone + CPU 忙 + **输出同类型** + **可用字节 >= 已用字节 + 新任务字节**。

**根因**：我们的 vCPU 在 `onVirtualCPUSubmitJob` 里把 `availableStorage` 设成了**任务字节**（`setAvailableStorage(usedBytes)`，hyper 为 `usedBytes+extra`）。因此原版合并条件变成 `usedBytes >= usedStorage + newBytes` **恒不成立** → vCPU 永远不会合并相同配方，第二次下单必然再占一个新线程槽。

> 注：vCPU 的 availableStorage 语义 == 任务字节 是 `TileEcalThreadDrive.getUsedStorage()`（Σ cpu.getAvailableStorage()）池记账的基础，**不能改这个语义**（否则池记账崩坏）——所以不能靠"放大 availableStorage 让原版条件成立"。

## 3. 方案：在 submitJob 注入里主动合并（方案 B）

不改 availableStorage 语义、不改池记账。在 `MixinCraftingCPUCluster.submitJob` HEAD 注入里**先于字节预检**判断合并，满足则直接调用原版 `mergeJob`。

### 3.1 合并判断（HEAD 注入，置于 t114g 预检之前）

```java
// 仅在 vCPU（virtualCPUOwner != null）且任务进行中时考虑合并
if (virtualCPUOwner != null && isBusy()
    && myLastLink != null && myLastLink.isStandalone()
    && finalOutput.get().isSameType(job.getOutput())
    && poolCanFit(virtualCPUOwner, job.getByteTotal())) {   // 池剩余 × 1.1(hyper 预留) >= 新任务字节
    mergeJob(g, job, src, requestingMachine);               // @Shadow 原版合并（自带回滚保护）
    // 记账：
    long extra = hyperAssigned && !overclocked ? job.getByteTotal() / 10 : 0;
    usedExtraStorage += extra;                              // hyper 预留累加
    setAvailableStorage(availableStorage + job.getByteTotal() + extra);  // 保持"任务字节"语义 → 池扣自动正确
    ecoaegtnh$mergedFlag = true;                            // 通知 RETURN 注入
    cir.setReturnValue(link);                               // 返回 mergeJob 生成的 CraftingLink
}
```

- `poolCanFit`：`virtualCPUOwner.getAvailableBytes() * 11 / 10 >= job.getByteTotal()`（超线程 10% 预留；超频模式无预留）
- `mergeJob` 原版行为（AE2U 源码核实）：备份 inventory/tasks → `job.startCrafting` + `ci.commit` → `finalOutput.merge` → `usedStorage += byteTotal` → 生成同 craftingID 的新 link；失败时回滚备份 ✓ 安全
- 池记账自动正确：`availableStorage` 保持"任务字节"语义，`getUsedStorage()` 汇总自动 +new，`getAvailableBytes()` 自动减少

### 3.2 RETURN 注入：防重复分配（关键）

`submitJob` RETURN 注入现在无条件调 `onVirtualCPUSubmitJob`（分配 vCPU 编号 + 加入线程槽列表）。合并成功后它**也会执行** → 必须跳过：

```java
// RETURN 注入
if (ecoaegtnh$mergedFlag) { ecoaegtnh$mergedFlag = false; return; }  // 合并：不重复分配线程
```

（等效做法：`onVirtualCPUSubmitJob` 开头检测 `cluster 已在 builtinThreadClusters/hyperClusters/任一 core.cpus 中` → 直接 return。二选一，推荐标志位，简单明确。）

### 3.3 不动的部分

- `availableStorage` 语义、`TileEcalThreadDrive.getUsedStorage()` 池记账：**零改动**
- standby vCPU / 空闲 vCPU：走原版 idle 分支（不合并）✓
- 原版 CPU（core != null）：注入守卫已 return，零影响 ✓
- t114g 字节预检：合并判断在其**之前**，合并请求不经过预检（预检的"×1.1 装得下"由 `poolCanFit` 等价保证）

## 4. 边界与语义

| 场景 | 行为 |
|---|---|
| vCPU 忙 + 同输出 + 池剩余够 | **合并**（不占新线程，输出累计，字节扣减） |
| vCPU 忙 + 同输出 + 池剩余不够 | 不合并 → 走普通路径（占新线程或拒绝） |
| vCPU 忙 + 不同输出 | 不合并（AE2U 原版语义：isSameType 判定） |
| 多个 vCPU 同时满足合并条件 | 竞态窗口内可能超卖一瞬（原版 idle CPU 同样存在，可接受） |
| 合并后任务完成 | 原版 destroy 流程（t114j 已覆盖：释放线程槽 + 归还 vCPU 号） |

超线程 10% 预留：合并字节也按 `newBytes/10` 累加进 `usedExtraStorage`（超频模式 0），与首次提交一致。

## 5. 改动清单

| 文件 | 改动 |
|---|---|
| `MixinCraftingCPUCluster.java` | ① HEAD 注入新增合并分支（置于预检前）：@Shadow `mergeJob`/`finalOutput`/`myLastLink`/`isBusy`；`poolCanFit` 调 owner.getAvailableBytes()；合并记账（extra + availableStorage）；`@Unique` 合并标志位 ② RETURN 注入：标志位跳过 onVirtualCPUSubmitJob |
| `MTEEcalArray.java` | 无需改（`getAvailableBytes()` 已有） |

约 40-60 行代码，集中在 mixin 一个文件。

## 6. 验证

1. 构建部署，重启两端。
2. 游戏内：vCPU 下单 A（观察线程槽占用 1）→ 任务进行中再次下单 A → **线程槽仍为 1**（不新增）；NEI/终端显示输出数量累计。
3. 反向验证：下单 A 后下单 B（不同物品）→ 占新线程槽（不合并）。
4. 边界：池剩余临界（A 合并后池将满）→ 第三次下单应走普通路径/拒绝而非超卖。
5. 完成后任务结束，线程槽释放、vCPU 号归还（t114j 回归）。