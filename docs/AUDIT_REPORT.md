# ECOAEGTNH（290 版）全面代码审计报告

- **审计对象**：`D:\DeepSeek\GTNH-ECO\ECOGTNH`（GTNH 2.9.0-beta-2 / AE2U rv3-beta-1000-GTNH / GT5U 5.09.54.20 / StructureLib 1.4.42 / TE4 1.7.60-GTNH）
- **审计范围**：`src/main/java/ecoaegtnh` 全部 80 个 Java 文件（ae2 / block / client / ecalculator / item / metatileentity / milestone / mixin / network / recipe / registry / tile / upgrade / waila）+ `dependencies.gradle` + `mixins.ecoaegtnh.json` + 构建产物 refmap
- **审计方式**：只读代码分析（未修改任何文件）；逐类对照 AE2U 参考源码（`参考/AE2-Unofficial/src`，1626 文件；注意：该 checkout 为 **rv3-beta-1045-GTNH**，比目标新约 45 个 beta）、TE4 1.7.56-GTNH 参考、GT5U 5.09.54.20 jar；5 个并行子代理按包深挖 + 主审计员逐文件交叉复核关键结论；mixin 专项子代理另以 **Gradle 缓存中的 rv3-beta-1000-GTNH dev jar 字节码**（比 beta-1045 源码更权威）逐一核对全部注入目标，关键结构结论（destroy/cancel/updateCraftingLogic/submitJob/mergeJob 语义、CraftingGridCache 无 teardown 钩子）均已由 beta-1000 字节码证实
- **结论总览**：**未发现可稳定利用的崩溃/复制/提权漏洞**；发现 **4 条高**（含 1 条潜在客户端启动崩溃）、**9 条中**、**26 条低** 问题。最高优先级：UNIVERSE 盘完全不可用（高，已核实）、服务器重启/卸载丢任务材料（高，已核实）、无槽位提交导致任务永久冻结（高，代码路径已核实、触发窗口需实测）、MixinGuiCraftingCPUTable 的 ScreenColor 注入目标包名错误（高，潜在客户端启动崩溃，需客户端实测确认）。

---

## 1. 严重度分布

| 严重度 | 数量 | 说明 |
|---|---|---|
| 致命 | 0 | 无已证实的服务器崩溃型、物品复制型、越权型漏洞 |
| 高 | 4 | H1 旗舰盘报废、H2 重启丢料、H3 任务冻结、H4 ScreenColor 注入目标包名错误（潜在客户端崩溃） |
| 中 | 9 | 内存泄漏、字节池超卖、GUI 热路径、配方缺口、显示溢出、断连冻结、内置槽重复条目、发布占位成本 |
| 低 | 26 | 线程模型、容错、日志、文档、理论性问题 |

---

## 2. 问题清单（按严重度排序）

| 编号 | 位置（文件:行） | 类型 | 严重度 | 触发场景 | 影响 | 修复建议（参考出处） | 核实状态 |
|---|---|---|---|---|---|---|---|
| H1 | `ae2/EcoStorageCellInventory.java:63-67`（getRemainingItemCount）+ `item/estorage/CellSize.java:38`（UNIVERSE=2⁵⁹-1） | 正确性/数值溢出 | **高** | 把**人造宇宙（UNIVERSE）物品/流体盘**放入 E-Storage 驱动器或 ME 箱后插入任意物品/流体 | `getRemainingItemCount() = freeBytes(≈5.76e17) × weight(物品 4096 / 流体 ≥64000)` ≈ 2.4e21~3.7e22 **溢出 long → 负值 → 钳 0** → AE2U `CellInventory.addItems` 既有类型分支（参考 `CellInventory.java:207-215`）与新类型分支（`:239-252`）均因 `<=0` 拒绝 → **宇宙盘完全无法存入任何内容**（"永远已满"），状态灯/工具提示与实际矛盾；t113 旗舰内容报废 | 乘法前饱和钳制（`freeBytes > (Long.MAX-unused)/weight → Long.MAX`），或超大容量盘直接走 `CreativeCellInventory`（同 INF_WATER/ARCANE）。参考同 mod 自身 `MTEEcalArray.recalculateTotalBytes:913` 的饱和求和范式 | 已核实（两个子代理独立算术验证 + 主审复核：2⁵⁹×2¹²=2⁷¹ ≡ −4096 (mod 2⁶⁴)） |
| H2 | `metatileentity/MTEEcalArray.java:998-1018`（newStandbyCluster 纯内存）+ `tile/ecalculator/TileEcalThreadDrive.java:307-309`（"In-flight tasks are NOT persisted"） | 正确性/数据丢失 | **高** | vCPU 在途任务运行中**服务器重启 / 维度卸载 / 区块卸载** | 任务已从网格抽出的原料存在集群内存 `MECraftingInventory` 中，无任何 tile NBT 承载；AE2 `CraftingGridCache` 网格销毁路径**不调用 cancel/destroy**（参考 `CraftingGridCache.java:167-219` 无 teardown 钩子）→ 集群对象被 GC → **原料永久丢失**；请求方（接口）的合成链接永久挂起。对照：香草合成 CPU 通过 `TileCraftingTile` NBT 走 `CraftingCPUCluster.writeToNBT:1557` 持久化任务，重启不丢 | 产品决策：① 在控制器 NBT 落盘集群任务（参考 vanilla `writeToNBT/readFromNBT`，重建恢复 isComplete=false）；② 或注册 `FMLServerStoppingEvent`/世界卸载钩子先 cancel 退款（网格仍可达时）；③ 最低限度在 GUI/文档显著警告 | 已核实（代码路径完整；"不持久化"是注释声明的用户决定，但材料丢失面大于"任务丢失"） |
| H3 | `mixin/MixinCraftingCPUCluster.java:184-200`（RETURN 分配）+ `metatileentity/MTEEcalArray.java:1157-1166`（无槽位时仅 warn）+ `:1179-1192`（getClusterList 不含未分配集群） | 正确性（任务冻结） | **高** | 同一 tick 内多个合成请求提交，槽位在提交间隙被占满 | 提交 B 通过字节预检并成功返回 link，但 `onVirtualCPUSubmitJob` 找不到槽位 → 集群保持"未分配"且 `virtualCPU` 被置 null → 下一 tick 网格重建后从 `craftingCPUClusters` 掉出 → `updateCraftingLogic` 不再被调用 → **任务永久冻结、材料锁死、vCPU 编号可释放但任务不回滚**；终端列表刷新后玩家无法取消（集群已不在网格），仅拆机（disassembleAll→cancel）可退款 | 分配失败时立即 `cluster.cancel()` 退款并 `destroy()`（复用 t118 的 cancel→destroy 顺序），或提交前在 `onVirtualCPUSubmitJob` 复核槽位、无槽则拒绝返回 null | 代码路径已核实；同 tick 双提交窗口需运行时验证频率 |
| H4 | `mixin/MixinGuiCraftingCPUTable.java:55-65`（tintRow 的 @WrapOperation target） | 正确性（潜在崩溃） | **高** | 客户端启动应用 mixin 时 | `@At` owner 写为 `Lappeng/core/localization/ScreenColor;setGuiColor()V`，但该类实际位于 **`appeng/client/gui/ScreenColor`**（beta-1000 jar 字节码 + 参考源码双重证实；`appeng.core.localization.ScreenColor` 不存在）。依赖 Mixin 0.8.5-GTNH 对不可解析 owner 的"imaginary 匹配"（仅按 name+desc）才可能侥幸命中 drawFG 内唯一调用点；若该 fork 严格校验 owner 或未来类路径变化 → InjectionError → `required:true` → **客户端启动崩溃** | target 改为 `Lappeng/client/gui/ScreenColor;setGuiColor()V` | 已核实（目标类不存在、真实 owner 已由字节码证实）；当前运行时走"侥幸命中"还是崩溃分支需客户端实测 |
| M1 | `mixin/MixinCraftingCPUCluster.java:297-326`（injectDestroy 跳过 vanilla 体）+ `MTEEcalArray.java:1071-1079`（destroyStandbyVCPU 只 markDestroyed）+ `tile/ecalculator/TileEcalThreadDrive.java:101`（onCPUDestroyed 内 markDestroyed） | 资源泄漏 | 中 | 每次 standby 补充、每个任务完成/取消/拆机 | 所有 vCPU 集群的 vanilla `destroy()` 体被跳过（isDestroyed 已置位 → `CraftingCPUCluster.destroy():313` 提前 return）→ `CraftingNotificationManager.unregister:50` **永不执行**；构造器 `register:237` 把 `unreadNotifications` Map 永久滞留静态 `ArrayList`（`CraftingNotificationManager.java:21-52`）→ 长期服务器**无界增长**（每集群 1 个 Map，含离线玩家合成通知的 IAEStack 引用） | 在 `onClusterReleased`/`destroyStandbyVCPU`/`onCPUDestroyed` 补 `unregister(cluster.unreadNotifications)`（mixin accessor），或让 `ecoaegtnh$markDestroyed` 调用方先跑 vanilla destroy 注销段 | 已核实 |
| M2 | `MTEEcalArray.java:1123-1156`（hyper 槽 `availableStorage=usedBytes+usedBytes/10`）+ `:929-941`（getAvailableBytes 减 ΣavailableStorage）+ `MixinCraftingCPUCluster.java:160-170,177-181` | 正确性/数值（池超卖） | 中 | 多个超线程任务并发 / 频繁 merge / overclock 模式 | +10% 幻影容量计入"已用"→ **字节池可算成负值**（负池 → `createVirtualCPU:960` 销毁 standby → 后续任务被拒；GUI 显示 used>total）；另 t114g 预检对**每个**任务预留 10%（即使落普通槽）→ 池实际利用率上限 ≈90.9%；merge 路径（`:170`）后 standby 池值不刷新，存在陈旧值窗口；**overclock 模式下预检仍无条件预扣 10%**（`*11/10`），而 merge/分配在 overclock 下 extra=0 → 池余量 ∈ [bytes, 1.1×bytes) 的任务被错误拒绝（保守方向，可用性） | 超线程 extra 从"池外"记账（getAvailableBytes 只减真实任务字节，extra 单列释放时核对）；预检的 10% 预留按 `isOverclocked()` 条件化并改为分配时按实际槽位复核 | 已核实（数值路径）；对玩家体验的实际影响需运行时统计 |
| M3 | `MTEEcoStorageArray.java:1593-1618`（sumStat）+ `:1038-1137`（15 个 FakeSyncWidget suppliers） | 性能 | 中 | 存储阵列 GUI 打开期间 | 每个同步周期（~20Hz）对每颗 cell 调 `EcoStorageCellHandler.getCellInventory`（`EcoStorageCellHandler.java:85`）**全量重建** EcoStorageCellInventory——反序列化 cell NBT 全部条目（大盘 315 类型）→ 12 bay × 12 统计项 × 20Hz ≈ **每秒数百次全量 NBT 反序列化**，大阵列开 GUI 明显掉 tick（t84 已为 tooltip 优化此路径，GUI 同步未做同款优化） | sumStat 复用 `TileEcoStorageDrive.getHandler(type)` 的缓存 handler（`:37-47` 已有缓存、onCellChanged 失效），或每 N tick 缓存统计结果 | 已核实（调用链逐行确认） |
| M4 | `recipe/Recipes.java`（全配方扫描）+ `registry/RegistryItems.java:86-101` | 正确性（内容缺口） | 中 | 生存模式尝试获得 F11 无限水 / E11 魔导源质细胞 | 两个族独占尺寸已注册/进创造页/升级树（F11/E11），但**全配方体系无任何输出** → 生存不可获得，无任何日志提示 | 补配方，或明确标注后续内容 | 已核实（grep 无 infwater/arcane 配方引用） |
| M5 | `recipe/Recipes.java:718-754` | 正确性（悬空物品） | 中 | 创造页/NEI 查询 UNIVERSE 组件 | `COMPONENTS/FLUID_COMPONENTS/ESSENTIA_COMPONENTS` 注册了 UNIVERSE 组件但**无配方产出、不被任何配方消费**（太空配方名"component_*_universe"实际输出 CELL）→ 创造-only 悬空物品 | 删注册或让太空配方以组件为中间产物 | 已核实 |
| M6 | `MTEEcoStorageArray.java:1540`（percentOf `part*100/total`）+ `MTEEcalArray.java:2069`（bytesLedTooltip `used*100`） | 正确性（显示溢出） | 中 | UNIVERSE/奇点盘使用量 > 9.2e16 B（终局可及） | `part*100` long 溢出 → 百分比显示为负值（客户端 tooltip/LED） | 先除后乘或饱和钳制；参考 GT `GTUtility` 饱和数学 | 已核实（算术） |
| M7 | `mixin/MixinCraftingCPUCluster.java:273-291`（isActive redirect → channel proxy）+ vanilla `CraftingCPUCluster.updateCraftingLogic:746` + `storeItems:1081-1084` | 正确性（冻结可恢复） | 中 | 通道断连/网格分裂/区块卸载期间 | 运行中 vCPU 任务**既不推进也不取消**：材料锁在集群库存、线程槽与 vCPU 编号滞留；恢复路径存在（通道翻转 → 事件 → 续跑；拆机 → cancel 退款），但期间该槽不可用，GUI 无"冻结中"状态 | isActive=false 连续 N tick 后自动 cancel+退款（参考 AE2 `CraftingLinkNexus.isDead` 的 requester 断线处理）；至少把冻结状态写入 GUI | 需进一步验证（恢复路径为代码级推断） |
| M8 | `upgrade/CalculatorUpgradeTree.java:243` + `upgrade/StorageUpgradeTree.java:101`（t113c TEST ONLY） | 正确性（发布项） | 中 | 当前构建发布 | 每个非免费节点仅需 **1 个铁锭**即可点亮整棵升级树（含 16384M/宇宙/无限水终结点）→ 升级经济崩坏；注释已标注"装机后调"，属有意占位但**不得随发布版上线** | 上线前替换为正式成本阶梯（docs §4） | 已核实 |
| M9 | `MixinCraftingCPUCluster.java:184-200`（RETURN 注入器）+ `MTEEcalArray.java:1087-1167`（onVirtualCPUSubmitJob 无 contains 检查） | 正确性（重复条目/账目偏差） | 中 | ①任务完成但 `inventory` 非空（存储满/断连→storeItems 失败）→ 集群不销毁、滞留 `builtinThreadClusters` 且 availableStorage 陈旧；②该"空闲但滞留"集群再次被网格选中接新任务 | ②下 `builtinThreadClusters.add(cluster)` 无去重 → **重复条目** → `getBuiltinThreadsUsed()=size` 虚增、任务字节被池账目**双重计入** → 其他任务被错误拒绝（可用性，非复制）；另 merge 分支在 HEAD `setReturnValue` 使 RETURN 注入器不执行 → `mergedJob` 标志滞留，后续该集群的新任务提交会跳过 `onVirtualCPUSubmitJob`（availableStorage 不更新）→ 池账目偏差；两缺陷部分相互掩盖 | `onVirtualCPUSubmitJob` 先 `contains` 再 add（参考 `TileEcalThreadDrive.addCPU` 幂等写法）；RETURN 注入器 merge 分支改为"以集群是否已占槽"判断而非标志位 | 需进一步验证（Mixin 取消语义下 RETURN 不执行概率极高，需运行期确认） |
| L1 | `network/C2SNetworkCellTypeSelectedHandler.java:19-24` | 安全（防御性） | 低 | 任意玩家发消息 | 注册 `Side.SERVER` + `instanceof` 绑定发送者本人容器，无实际危害；补显式 side 断言更佳 | `if (ctx.side != Side.SERVER) return null;`（AE2U `PacketValueConfig` 同款模式） | 已核实 |
| L2 | `network/C2SNetworkCellTypeSelected.java:37-41` | 安全（健壮性） | 低 | 恶意 <4 字节畸形包 | `buf.readInt()` 越界 → 仅断开发送者自己的连接（自伤式 DoS）；ordinal 越界已正确兜底回 ITEM | `readableBytes()<4` 前置检查 | 已核实 |
| L3 | `C2SNetworkCellTypeSelectedHandler.java:22` + `mixin/MixinContainerNetworkStatus.java:35-37,43-45` | 正确性（线程） | 低 | 包到达与 detectAndSendChanges 并行 | netty IO 线程写容器字段、服务器主线程读——正式数据竞争，最坏一次陈旧列表；与 AE2U `PacketNetworkStatusSelected` 同款 | 字段加 volatile 或 `addScheduledTask` 切主线程 | 已核实 |
| L4 | `mixin/MixinSlotRestrictedInput.java:38-56` + `mixin/MixinTileDrive.java:29-34` | 安全（限制绕过面） | 低 | **数字键交换**（`Container.slotClick` mode-2）不经 `isItemValid` | "ECO 盘不可入 ME 驱动器/箱子"限制可被 GUI 数字键绕过（无崩溃/复制/提权）；同款绕过适用于 AE2U 全部槽位限制——上游/vanilla 级问题；自动化路径已被 MixinTileDrive 覆盖 | TileDrive 内部 cell 槽接受点加服务端兜底校验 | 需进一步验证（1.7.10 vanilla mode-2 字节码确认） |
| L5 | `mixin/MixinCraftingCPUCluster.java:262-267`（TAIL 记录 usedOps） | 性能/正确性 | 低 | 常驻 | TAIL 注入对**香草集群**也每 tick 记录 `parallelismRecorder.addUsedTime`（无 owner 守卫），香草集群的 recorder 永不被读取——纯噪音 | 加 `ecoaegtnh$core != null || virtualCPUOwner != null` 守卫 | 已核实 |
| L6 | `ecalculator/EcoTimeRecorder.java:16-27` | 正确性（统计失真） | 低 | 常驻 | javadoc 称"rolling-window"，实现是**累积平均**：count 封顶 100 但 total 永不清零 → average 单调失真、peak 永不衰减；600-tick perf 日志与 GUI 数值失真 | 真滚动窗口（环形数组或 total−=window[slot]） | 已核实 |
| L7 | `upgrade/UpgradeTreeGui.java:419,454,464,503,621`（pack.contains 子串匹配） | 正确性（显示） | 低 | 升级树窗口渲染 | 子串误匹配（"I1"⊂"I10"、"N1"⊂"N11"）：因前置链前缀闭包 + 基节点免费，**当前定义无实际触发**；无安全/解锁影响 | `(","+pack+",").contains(","+id+",")` 或同步 Set | 已核实（理论性） |
| L8 | `ae2/EcoStorageCellInventory.java:104-112`（it/ic 恒用）vs AE2U `FluidCellInventory.java:22-30`（ft/fc） | 正确性 | 低 | 跨通道转换/外部工具链改盘 | 物品盘与流体盘共用 `it`/`ic` 键：流体盘把物品数据当流体解析失败后 saveChanges 覆写 → **原物品数据被擦除**（正常使用自洽不触发） | 流体盘改用独立键 ft/fc（对照 AE2U） | 已核实（键名逐行对照） |
| L9 | `ae2/EcoStorageCellInventory.java:84-87`（315）+ AE2U `CellInventory.java:83-88`（maxTypes 钳 63） | 正确性 | 低 | 盘类型数曾 >63 后减少 | `saveChanges` 清理界取钳制 63 → `#63..#314` 陈旧标签永久残留（NBT 膨胀）；无数据丢失 | 清理界改用 `getTotalItemTypes()`（同 TE4 1.7.60 essentia 修复思路） | 已核实 |
| L10 | `ae2/EcoStorageCellInventory.java:56-77` + AE2U `CellInventory.java:93-94` | 安全/容错 | 低 | 损坏/恶意存档（ic=Long.MIN 等） | 无崩溃（1.7.10 NBT getter 语义 + 构造期自愈）；最坏 `ic=Long.MIN,it=0` → 幻影剩余容量 ≈9.2e18（首次写入自愈）；**与 AE2U 基线同构，非回归** | 可选：loadCellStacks 后校验 storedCount 范围 | 已核实 |
| L11 | `item/estorage/ItemEcoStorageCellEssentia.java:22-25,50-57` + AE2U 63 钳制 | 正确性 | 低 | L6/L9 源质盘实际使用 | 实际类型上限 = 60/63/63（80/100 为死常量），tooltip 与库存一致（min 钳制）但文档不符 | 覆盖 `getTotalItemTypes` 或改常量 | 已核实 |
| L12 | `item/estorage/ItemEcoStorageCell.java:221-247`（tooltip 扫描） | 性能 | 低 | 悬停源质盘 tooltip | 每帧 ≤315 次字符串拼接 + hasKey（essentia 63 次）——hover 渲染热点，量级小 | 缓存或限制扫描次数 | 已核实 |
| L13 | `metatileentity/MTEEcalArray.java:1062-1068,1104-1146,983` + `TileEcalThreadDrive.java:108,277-301` + `TileEcoStorageMEBus.java:68-86` | 性能/日志噪音 | 低 | 高频任务服务器 | 每任务 3-5 条 INFO + onClusterReleased 每次格式化整个 PriorityQueue；MEBus 每 5s 一条 | 降 DEBUG 或按 AELog 式开关门控 | 已核实 |
| L14 | `MTEEcalArray.java:1163-1166` + `mixin/MixinCraftingGridCache.java:52-64` | 正确性（同 tick 竞争） | 低 | 同一 tick 两次自动合成 | 新 standby 要等下一 tick `updateCPUClusters` 才入集合 → 本 tick 第二次请求找不到 CPU → 返回 null → 请求方下 tick 重试（自愈） | 可选：createVirtualCPU 时同步 postCPUClusterChangeEvent | 需进一步验证（重试语义） |
| L15 | `tile/estorage/TileEcoStorageCapacitance.java:121-128`（readFromNBT 无下界/NaN 钳制） | 安全/容错 | 低 | 损坏存档 energyStored=负/NaN | 负能量可超容量存储（自愈）；NaN 永久污染能量值（显示 NaN、无法充放电直至重写 NBT） | 读入钳制 [0, CAPACITY] 且 `Double.isNaN` 归零 | 已核实 |
| L16 | `MTEEcalArray.java:399` / `MTEEcoStorageArray.java:213`（upgradeStaging 16 槽不持久化） | 正确性 | 低 | 升级暂存窗口有物品时重启 | 暂存材料（已放入未提交）重启丢失 | 随机器 NBT 持久化或 GUI 提示 | 已核实 |
| L17 | `mixin/MixinCraftingCPUCluster.java:155-176`（merge 分支缺 `requestingMachine==null`） | 正确性（语义偏差） | 低 | 接口等机器在 vCPU 运行中请求同输出 | 机器请求也会 merge 进运行中任务（香草仅独立请求可 merge）——finalOutput.merge 后双方都收到产出；SA1 已核实该放宽是"确认界面显式合并到忙 vCPU"功能**所需**（有意为之），但机器侧自动请求同样受益/受影响，语义与香草不同 | 文档化该行为差异，或对机器请求保持香草限制 | 已核实（有意，功能所需） |
| L18 | `upgrade/UpgradeTree.java:116-117`（paid merge Integer::sum） | 数值溢出 | 低 | 单材料累计投入 ≥2³¹（天文次数） | 静默回绕 → isCostFulfilled 判定错乱（理论） | long 记账或 Math.addExact | 已核实（理论） |
| L19 | `recipe/Recipes.java:772,930,1005,1043,1061-1171`（`new ItemStack(Map.get(...))`） | 安全（null） | 低 | 注册时序异常/枚举新增尺寸遗漏 | `new ItemStack(null)` 产出 item 为 null 的 Stack；addAssembler 的 null 检查拦不住 → GT builder 可能静默丢弃（当前时序全非 null） | 辅助方法统一加 `output.getItem()==null` 校验 | 需进一步验证（潜伏） |
| L20 | `metatileentity/MTEEcalArray.java:2069`（used*100）与 M6 同型；`MTEEcoStorageArray.java:2068-2069` | 正确性（显示） | 低 | 负池/大池 | GUI"已用"可能大于"总量"（tooltip 已钳制，主行未钳制） | 主行 `Math.max(0,…)`；根本修复见 M2 | 已核实 |
| L21 | `dependencies.gradle` + `MTEEcalArray.java:76-77`（TTMultiblockBase）+ `Recipes.java:741`（ae2fc ItemAndBlockHolder） | 正确性（依赖声明） | 低 | 缺失 TecTech/ae2fc 时启动 | 硬类引用 → NoClassDefFoundError；`dependencies` 仅声明 `required-after:gregtech; after:appliedenergistics2`（GTNH 包内两 mod 恒在，故当前无影响） | 依赖列表补声明或反射隔离 | 已核实 |
| L22 | `milestone/*` 三文件 | 正确性（死代码） | 低 | 无 | 整个 milestone 包 @Deprecated、零外部调用；`MilestoneSystem.getCurrentLine` 空集合 NPE、`MilestoneLine.getPercent` 溢出均只在死代码内 | 删除或随 T61 迁移清理 | 已核实 |
| L23 | `tile/ecalculator/TileEcalThreadDrive.java:158-163`（getStackInSlotOnClosing 未 onCoreChanged） | 正确性 | 低 | 未来接入容器后取核心 | 控制器线程容量计数过期（当前无容器路径） | 对齐 `TileEcoStorageDrive.java:112-120` | 已核实 |
| L24 | `block/` 全部驱动方块 | 性能/功能 | 低 | 比较器贴驱动方块 | 未覆写比较器输出 → 恒 0（AE2U `AEBaseTileBlock` 对 IInventory 提供 calcRedstoneFromInventory） | 按 AE2U 参考补输出 | 已核实 |
| L25 | `upgrade/CalculatorUpgradeTree.java`（H1 节点）+ `ItemEcalThreadCore.java:65-76` | 正确性（设计） | 低 | — | H1（2 超线程）无任何核心物品要求（hyperThreads=2 的核心不存在）→ 可激活但无物品门控的"死节点" | 调整 H1 前置或删除 | 已核实 |
| L26 | `mixin/MixinGuiCraftingCPUTable.java:48-53,119-123` | 性能 | 低 | CPU 表每帧渲染 | `List.get` 全捕获（drawFG 内唯一行循环 get，已核实安全）+ `tierColor` 每帧 new float[3]（小 GC 噪音） | tierColor 改静态常量数组 | 已核实 |
| L27 | `mixin/MixinContainerNetworkStatus.java:39-45`、`MixinGuiNetworkStatus.java:36`、`MixinTileDrive.java:29`（SRG 字面量 + remap=false） | 正确性（可维护性） | 低 | dev 环境运行 | SRG 目标在 MCP 环境不命中 → mixin 静默失效（release jar 正常；注释已声明有意为之）；升级 AE2U 版本时目标名需重新核对 | 保留现状；升级 AE2U 时回归验证；dev 加 apply-state 日志 | 已核实（有意） |
| L28 | `MTEEcalArray.java:1179-1192`（getClusterList 每次对 standby setVirtualCPUOwner） | 性能 | 低 | 网格 CPU 列表重建 | 每次重建重写 machineSrc（幂等）——小浪费 | 无需处理 | 已核实（安全） |
| L29 | `item/ecalculator/CellSize.java:32`（SINGULARITY）+ `MTEEcalArray.java:908-920` | 正确性 | 低 | 多张奇点盘同池 | **已核实安全**：recalculateTotalBytes 为饱和求和（t114b），双奇点不溢出——列此条仅为记录覆盖 | 无需处理 | 已核实（安全） |
| L30 | `mixin/MixinCraftingCPUStatus.java:74-86`（NBT 构造注入读 ecEffStorage） | 正确性 | 低 | 老版本客户端连新服务端 | NBT 缺键有 hasKey 保护 → 默认 -1 → 回退 vanilla；无崩溃 | 无需处理 | 已核实（安全） |

---

## 3. 重点领域逐项结论

### 3.1 Mixin 注入安全性（最高风险项）
**总体：注入点与 @Shadow 签名经 mixin 专项子代理以 rv3-beta-1000-GTNH dev jar 字节码逐一核对一致**（14 个 @Shadow + 10 个 @Inject + 1 个 @WrapOperation 全部精确匹配；含 `CraftingGridCache.craftingCPUClusters/grid/addLink`、`GridStorageCache.activeCellProviders/updateCellsStatusFromRegistry`、`SlotRestrictedInput.isItemValid→func_75214_a`（refmap 已核实）、`ContainerNetworkStatus.func_75142_b`、`AEConfig.selectedCellType()`、`GuiCraftingCPUTable.drawFG` 的 `List.get(I)`（1 处）与 `CraftingCPUStatus.getName()`（2 处）调用点数量与顺序）。**唯一签名错误：H4（ScreenColor owner 包名错写为 `appeng.core.localization`，实际为 `appeng.client.gui`）——必须修复**。其余无 NoSuchFieldError/NoSuchMethodError 风险。

- **香草集群零副作用**：所有注入均有 `ecoaegtnh$core/ecoaegtnh$virtualCPUOwner` 空守卫或"非 ECO 回退 original"，香草 CraftingCPUCluster/Slot/Container 行为不变（`MixinCraftingCPUCluster.java:148,187,226-229,243-246,290,330` 等）。
- **cancellable 逻辑无节流绕过**：`submitJob` HEAD 预检在字节不足时 `setReturnValue(null)`（cancellable CIR 语义 = 提前返回，vanilla 体不执行）；`updateCraftingLogic` HEAD 在 `isComplete && inventory.isEmpty()` 时 destroy+ci.cancel()，与 vanilla 完成语义等价；standby 有专门守卫（fresh standby isComplete=true 不会被首 tick 误销毁）。
- **vCPU 的 NPE 防护完整**：vanilla `updateCraftingLogic` 的 `getCore().isActive()`/`markDirty()`/`getGrid()` 对 vCPU（getCore 注入返回 null）由 `@WrapOperation`/HEAD 注入在解引用前拦截（已对照 `CraftingCPUCluster.java:542,614-628,746` 确认唯一调用点）。
- **@Redirect 覆盖面一致**：`MixinCraftingGridCache` 的 submitJob storage redirect 覆盖方法内 **2 个调用点**（beta-1000 字节码实测 offset 127 合并检查、offset 174 空闲检查），均与 effective 语义一致；排序比较器（源码 :700）位于匿名 Comparator 类的方法内、不在 submitJob 字节码中 → 不被重定向（仅影响候选排序，使用原始字段无碍）；`MixinContainerCraftConfirm` 覆盖 `cpuMatches` 2 处（`:307,309`）与 `onCPUUpdate` 1 处（`:132`）——无"部分重定向导致语义混杂"。
- **发现的问题**：H4（ScreenColor owner 包名错误——唯一签名错误，必须修复）、H3（无槽冻结）、M1（通知管理器泄漏）、M2（池超卖 + overclock 预扣偏差）、M7（断连冻结）、M9（内置槽重复条目 + mergedJob 标志滞留）、L5（香草集群计时噪音）、L17（merge 条件与香草不一致——缺 `requestingMachine==null`，为"确认界面显式合并到忙 vCPU"功能所需，已核实为有意）、L26、L27、L30。
- **AE2U 升级兼容风险（低）**：MCP/SRG 字面量混用 + `remap=false` 依赖"release jar 保留 MCP 名 + vanilla override 保留 SRG 名"这一约定（已通过 refmap 与字节码注释验证成立）；AE2U 升级时每个注入目标名都需回归核对。`mixins.ecoaegtnh.json` 的 client/server 分组与目标类用途一致（GUI 类在 client、集群/网格缓存在 server）。

### 3.2 网络包处理（安全）
- 唯一 C2S 消息 `C2SNetworkCellTypeSelected`：注册 `Side.SERVER`、handler 绑定"发送者本人打开的 ContainerNetworkStatus"、`CellType` 值域在 fromBytes 兜底、无槽位索引/物品操作——**无越权面**（恶意包最大影响 = 给自己多送一份 cell 列表视图）。
- 数据流单向（客户端选择 → 服务端容器字段 → detectAndSendChanges 输出），客户端无法注入 effective storage 或伪造 vCPU 状态（服务端 submitJob 池检查是真正闸门）。
- 残余：L1/L2/L3/L4。

### 3.3 NBT 读写（安全/正确性）
- **键名兼容**：物品盘 `it/ic/#N/@N` 与 AE2U ItemCellInventory 逐字节一致；源质盘 `et/ec/Essentia#N/Cnt` 与 TE4 一致 → 新旧盘互读无损。
- **损坏存档容错**：1.7.10 NBT getter 语义 + 构造期自愈 + tooltip Throwable 兜底 → 无崩溃型路径（数组越界/负长度/无限循环均排除）；残余 L10（幻影容量，与 AE2U 基线同构）、L15（电容 NaN）。
- 315 类型 enforcing 严格（`min(freeBytes/perType, 315−stored)`，第 316 类型被拒）。

### 3.4 数值边界与溢出
- 饱和求和（奇点池）安全；parallelism 上限 24×65536=1.57M int 安全；`remainingOperations` 不取负；vCPU 编号池幂等。
- **高**：H1（UNIVERSE 盘乘法溢出）。
- 中：M2（池超卖）、M6（显示百分比溢出）。

### 3.5 并发/线程安全
- **全部状态变更在服务器 tick 线程串行执行**（无 new Thread/Executor/synchronized；AE2U CRAFTING_POOL 仅跑合成模拟不碰集群状态）；`craftingCPUClusters` 的"clear+重建"由 `updateList` 延迟到下一 tick 且在 for-each 之前 → **无 CME 风险**（结构性化解）；拆机遍历用快照副本。
- 残余：L3（netty 线程写容器字段，与 AE2U 同款）、F3 类竞态见 H3/M2。

### 3.6 性能热路径
- 每 tick 无 O(n²)：onPostTick 仅 %5/%40/%600 周期任务；E-Storage onPostTick 空体（t115 事件驱动缓存修复有效）；网格侧每 vCPU 每 tick 一次 updateCraftingLogic + nanoTime 计时（~100ns 级）。
- **中**：M3（sumStat GUI 热路径全量反序列化，唯一显著热点）。
- 低：L5/L12/L13/L26。

### 3.7 资源泄漏
- **中**：M1（CraftingNotificationManager 无界增长）。
- 无 FML tick/事件处理器泄漏（grep 零命中）、无 cell handler 缓存泄漏（tile 固定 3 槽缓存 + 换盘失效）、网格事件订阅随 proxy 生命周期注销。

### 3.8 正确性（退款/teardown/跨维度）
- **拆机退款链完整**：`disassembleAll` 顺序 = destroyStandby → builtin cancel→destroy（t118）→ 线程核 cancel→destroy → 事件 → proxy.invalidate，全部在网格仍可达时执行 → 退款成功。
- **缺口**：H2（重启/卸载无退款钩子）、H3（无槽冻结）、M7（断连冻结）。
- 跨维度/跨存档：owner-alive 检查带 worldObj 守卫；升级树每机独立实例 + setItemNBT 随掉落保存，无串数据。

---

## 4. 无问题领域（覆盖证明）

1. **方块破坏/掉落**：4 个驱动方块 breakBlock 均"清槽 → 服务端 spawnEntityInWorld（含 NBT）→ super.breakBlock"，`world.isRemote` 守卫防双端双刷；爆炸/活塞路径同走 breakBlock。**无吞物、无复制**。
2. **升级门控无旁路**：未成形时 `isCellSupported` 放行窗口由 `scanStructureVolume` 静态重校验（t62）与插槽门控（t9 双保险）关闭；插入路径（interactWithCell/interactWithCore/setInventorySlotContents/isItemValidForSlot）全链路服务端权威 + 升级树节点门。
3. **配方 null 框架**：`tryAddAssembler/tryAddAL/tryAddSpaceAssembler` 对 inputs/fluids/output 全量 null 检查 + 警告 + skippedRecipes 计数；`findItemStack/findBlock/gtFluid` 安全返回 null；GT adder 参数（TierEU/SECONDS/输入数/ModuleTier）均在合法边界。
4. **描述包大小有界**：盘类型上限 315/63，无 32K 包溢出风险。
5. **能量 double 数学**：单格 2M AE × 24 格 = 48M，远小于 2⁵³ 精确整数域；inject/extract 有防死循环守卫。
6. **无限盘（INF_WATER/ARCANE）**：CreativeCellInventory 路径与 AE2FC 无限水 / TE4 创造源质元件同构（广告 2⁵²−1、inject 恒收、字节统计 0）。
7. **WAILA**：双 provider 全 hasKey 守卫、服务端只发 lang key（t40 本地化）、客户端回落 tile 描述包；无静态可变状态。
8. **静态状态生命周期**：UpgradeCosts.SOURCES/MaterialValue.Table 仅类加载期写入后只读；@SideOnly 图标数组无 `<clinit>` putstatic 残留（t30 修复，dedicated server 无 NoSuchFieldError）。
9. **渲染**：display list 一次性编译、渲染线程单线程读写、无逐帧分配热点（tierColor 除外，L26）。
10. **注册表一致性**：MTE ID 32030/32033 已对照 GT5U 5.09.54.20 jar 核实空闲；物品注册名全唯一；`registerItem` 去 `item.` 前缀逻辑正确；essentia 系物品 TE4 缺失时整体跳过（类加载安全）。
11. **升级树 ↔ 物品门控映射**：N2..N11/P1..P9/T1..T5/H2..H3/I1..I10/F1..F11/E1..E11 与各 Item 的 getRequiredUpgradeNode 同源推导，逐项一致。
12. **milestone 数值链**：全程 long 无 double 精度问题；`MaterialValue` 最大值 5000×2³¹≈1.07e13 无溢出（死代码，见 L22）。

---

## 5. 待进一步验证清单

| 项 | 关联 | 验证方式 |
|---|---|---|
| H4 当前运行时走"imaginary 匹配侥幸命中"还是 InjectionError 崩溃分支 | H4 | 真实客户端启动实测（含最新 Mixin 0.8.5-GTNH 行为） |
| H3 同 tick 双提交窗口的实际频率 | H3 | 起服实测：多接口并发请求 + 槽位满载时序 |
| M2 池超卖对玩家体验的实际影响（负池出现频率） | M2 | 运行统计 |
| M9 滞留集群重复条目与 mergedJob 标志的实际触发 | M9 | 制造 storeItems 失败场景实测 |
| L4 vanilla 1.7.10 `Container.slotClick` mode-2 是否调用 isItemValid | L4 | 1.7.10 字节码/实测 |
| M7 通道断连恢复路径实机行为 | M7 | 断缆/拆缆实测 |
| L14 请求方重试语义 | L14 | 多请求方实测 |
| mixins.ecoaegtnh.json client/server 分组在 SSP（单人游戏）下的应用行为 | 3.1 | SSP 实测（GTNH Mixin 0.8.5 的 side 判定） |
| V1（SA5）TecTech MTE ID 上界 32029 | 注册表 | 需 TecTech jar 核实 |
| L19 运行时非 null 时序假设 | L19 | 异常时序注入测试 |

---

## 6. 修复优先级建议

1. **H4**（ScreenColor owner 包名错误）——一行改包名，消除潜在客户端启动崩溃
2. **H1**（UNIVERSE 盘报废）——一行饱和钳制即可恢复旗舰内容
3. **H2**（重启丢材料）——需产品决策（持久化 / 停机退款 / 显著警告）
4. **H3**（任务冻结）——分配失败即 cancel 退款
5. **M1**（通知管理器泄漏）——补 unregister
6. **M2**（池超卖 + overclock 预扣）——extra 池外记账 + 预扣条件化
7. **M3**（GUI 热路径）——复用 tile handler 缓存
8. **M4/M5**（配方缺口）——补 F11/E11 配方、清理 UNIVERSE 组件
9. **M9**（内置槽重复条目）——contains 去重
10. **M8**（TEST ONLY 成本）——发布前替换
11. 其余低危按成本收益择机处理

---

*报告生成：审计员 audit-290；全部结论基于 290 版源码只读分析，未修改任何文件。参考对照：`参考/AE2-Unofficial/src`（AE2U rv3-beta-1000）、GT5U 5.09.54.20 jar、TE4 1.7.56-GTNH 参考。*
