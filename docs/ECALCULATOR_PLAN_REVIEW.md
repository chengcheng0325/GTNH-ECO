# E-Calculator → GTNH 1.7.10 移植方案评审报告

- 评审对象：`docs/ECALCULATOR_PORT_PLAN.md`（v1.0，t4 产出，468 行）
- 评审人：reviewer（t5）· 日期：阶段 1
- 评审方式：只读。逐条复核方案 §6/§7/§8 的技术结论，证据来源：
  - **A998** `.research/ae2u-full/Applied-Energistics-2-Unofficial-rv3-beta-998-GTNH/src/main/java/`（全量源码，行号级核对）
  - **J1000** `%USERPROFILE%\.gradle\caches\...\Applied-Energistics-2-Unofficial-rv3-beta-1000-GTNH-dev.jar`（评审人独立 javap -p 复核）
  - **JREL** `M:\AA科技\GTNH\服务端\mods\appliedenergistics2-rv3-beta-1000-GTNH.jar`（评审人独立 javap -p 复核）
  - **S** `.research/NovaEngineering-ECOAEExtension-main/src/main/java/github/kasuminova/ecoaeextension/`（参考仓库源码，行号级核对）
  - 既有工程 `src/main/java/ecoaegtnh/**`（E-Storage 复用假设核对）

---

## 0. 结论速览（TL;DR）

**verdict：needs_revision（有条件通过）**

移植方案**总体可行、架构映射完整、计算模型成立、任务拆分可执行**，核心 mixin 可行性结论经评审人独立核验全部成立（见 §1）。但存在 **2 个中等** 与 **4 个低** 严重度问题需要修正后进入实现阶段：

| # | 严重度 | 问题 | 修正 |
|---|--------|------|------|
| F1 | 中 | 风险清单未覆盖**第三方 mod 对 AE2U 合成类的 mixin 竞争**（服务端已装 ae2fc-1.5.95-gtnh，其对 AE2U 合成类有 mixin；方案 R11 仅覆盖"与现有 E-Storage mixin 冲突"） | 风险清单新增一条：ae2fc/其他 mod 与 M1/M2 目标类（CraftingCPUCluster/CraftingGridCache）的 mixin 竞争；缓解=UniMixins priority、FML 日志核对、装机含 ae2fc 实测 |
| F2 | 中 | **控制器整体拆解路径未设计**：结构破坏/控制器方块被拆时，待命 vCPU 与已分配 vCPU（含在途任务）如何销毁/取消/通知网格未写明；参考实现 `disassemble()` 仅置空 `virtualCPU` 引用不 destroy（CraftingNotificationManager 静态注册泄漏，A998:237 注册/:318 注销），与 MVP 成功标准 4"无泄漏"冲突 | 阶段 B 明确：拆解/失效时 destroy 待命 vCPU + 逐线程核心 cancel（或 destroy）在途集群 + `postCPUClusterChangeEvent()`；并加入阶段 B 验证清单 |
| F3 | 低 | §5.2 声称"E-Storage 已实现同逻辑（checkControllerShared），直接复用"——**当前代码库无此逻辑**（grep `checkControllerShared` 零命中；DESIGN.md 仅作设计意图记载） | 改为"需新实现（参考 S:EPartController.java:39-51/102-113，正上/下 2 格同类控制器拒绝成型）"；阶段 A 交付物已含该项，工作量估算不变 |
| F4 | 低 | §5.1 头部格数表述：3×3×2=18 格 = 控制器1 + ME 通道1 + 外壳**16**（"其余 17 格外壳"应为 16） | 修正数字（整机 24~90 格结论不受影响） |
| F5 | 低 | §3 依赖表称"AE2FC 已在 GTNH AE2U 内建"表述不准：ae2fc-1.5.95-gtnh 是**独立 jar**（服务端 mods 内已核实存在），与 AE2U 分开发布 | 改为"服务端独立安装 ae2fc-1.5.95-gtnh"；并强化 F1 的风险说明 |
| F6 | 低 | 阶段 C 的 M3 `MixinCraftingCPUStatus` 需要 **server+client 双侧注册**（服务端 `writeToNBT` 写 ecLevel、客户端 `(NBTTagCompound)` 构造读），方案 §4.3 只注明 M4 进 client 组 | 阶段 C 落地时把 M3 与 M4 的分组一并确认（MVP 的 server 组配置不受影响） |

**通过项（评审人独立核验）**：
- ✅ AE2U mixin 可行性：A998 源码行号、J1000/JREL javap 成员名全部与方案 §6 一致（详见 §1）
- ✅ MVP 范围裁剪合理且有据（§2）
- ✅ 架构映射覆盖参考仓库 ecalculator 子系统全部类，与既有工程约定一致（§3）
- ✅ 计算线程模型 1:1 移植成立，MVP 性能预算无风险（§4）
- ✅ 任务拆分每阶段有交付物+验证方式+退出条件，可独立验证（§5）

---

## 1. AE2U mixin 可行性逐条复核（对照 A998 + J1000/JREL）

评审人对方案 §6 的每条"可行"结论独立复核，**全部属实**：

### 1.1 通用结论：MCP 名保留（方案 §0/§6 核心主张）

评审人独立 javap -p **J1000（dev jar）与 JREL（运行时 release jar）**，两 jar 输出一致：

```
CraftingCPUCluster:
  public CraftingCPUCluster(WorldCoord, WorldCoord)          // 构造器一致
  public ICraftingLink submitJob(IGrid, ICraftingJob, BaseActionSource, ICraftingRequester)
  public void updateCraftingLogic(IGrid, IEnergyGrid, CraftingGridCache)
  protected void executeCrafting(IEnergyGrid, CraftingGridCache)
  public void destroy(); public void cancel(); public void markDirty();
  public boolean isActive(); public IGrid getGrid();
  protected TileCraftingTile getCore(); protected World getWorld();
  protected long availableStorage; protected int accelerator; protected int remainingOperations;
  protected final int[] usedOps; protected boolean isDestroyed; protected boolean isComplete;
  protected MECraftingInventory inventory; protected ICraftingLink myLastLink;
  protected MachineSource machineSrc; workableTasks; parallelismProvider;
CraftingGridCache:
  protected final Set<CraftingCPUCluster> craftingCPUClusters;
  protected void updateCPUClusters(); public void addLink(CraftingLink);
  public void onUpdateTick(); + updateCPUClusters(MENetworkCraftingCpuChange) 事件订阅
MECraftingInventory: public boolean isEmpty();  （getItemList 不存在 → 方案 isEmpty() 替代正确）
CraftingCPUStatus: ()/(ICraftingCPU,int)/(NBTTagCompound)/(ByteBuf) 四构造器 + writeToNBT/writeToPacket
ICraftingGrid: getCpus() → ImmutableSet<ICraftingCPU>
CraftingJobV2: supportsCPUCluster(ICraftingCPU)
AENetworkProxy: (IGridProxyable, String, ItemStack, boolean); setFlags; setIdlePowerUsage
```

**结论：方案"合成类成员在 dev 与运行时 jar 均保留 MCP 原名、无 SRG 混淆"的主张成立**；`remap=false` + MCP 名与既有 `MixinGridStorageCache` 同构（该 mixin 已在生产 jar 中工作）。HANDOVER §4 所述 "remap=false 字面 SRG 名" 仅适用于 TileDrive 的 **vanilla 覆写方法**（func_94041_b），与 CraftingCPUCluster 等无 vanilla 覆写的类不冲突——方案表述准确。

### 1.2 A998 源码行号逐条核对（方案 §6/§7 引用）

| 方案引用 | 复核结果 |
|---|---|
| `remainingOperations = accelerator+1-usedOps`（A998:772） | ✅ 属实（:772 `= this.accelerator + 1 - (usedOps[0]+usedOps[1]+usedOps[2])`） |
| `usedOps[0]` = 本 tick 启动操作数（A998:772,785-787） | ✅ 属实（:785-787 移位，`usedOps[0]=started-remainingOperations`）→ R1 §12.2 开放项关闭成立 |
| `updateCraftingLogic` 头部 `getCore().isActive()`（A998:746） | ✅ 属实；`TileCraftingTile.isActive()` INVOKE 存在（S 侧同款 WrapOperation 可照搬） |
| `submitJob` merge 分支 getOutput()（A998:1097）+ 提交分支 getOutput()（A998:1135）+ usedStorage=byteTotal（A998:1140） | ✅ 属实；merge 条件短路（`requestingMachine==null && myLastLink!=null && isStandalone && isBusy`）→ 普通任务/新 vCPU 首调用不触发 → **注入点改 @Inject(RETURN)+守卫 的设计正确**（RETURN 时任务已装载、非 null 返回值即成功，语义等价且无 ordinal 脆弱性） |
| `supportsCPUCluster` 门禁（A998:1110）+ CraftingJobV2 instanceof（A998:239-241） | ✅ 属实（`return cluster instanceof CraftingCPUCluster`）→ vCPU 天然通过，无门禁风险 |
| `craftingAllowMode` 默认 ALLOW_ALL（A998:183） | ✅ 属实 |
| `updateCPUClusters()`（A998:369-386）+ 事件→updateList（:402-404） | ✅ 属实（clear → getMachinesClasses 过滤 TileCraftingStorageTile → add+addLink）；RETURN 注入追加 vCPU 兼容 |
| `CraftingGridCache.onUpdateTick`（A998:163-175）含 `cpu.tryExtractItems(); cpu.updateCraftingLogic(...)` | ✅ 属实；`tryExtractItems()` 对 vCPU 安全（waitingForMissing 空时直接返回，:2053；getGrid/machineSrc 均已重定向，无 tiles 解引用） |
| `destroy()`（A998:312-334）：isDestroyed 守卫 → 置位 → **CraftingNotificationManager.unregister**（:318）→ 遍历 tiles 发事件（vCPU 无 tiles） | ✅ 属实 → 方案 R5"vCPU 原生 destroy 不发网格事件、需控制器补发"成立；且佐证 F2：不 destroy 的 vCPU 会泄漏通知管理器注册 |
| `cancel()` 末尾 storeItems()（:742）+ `inventory.isEmpty()` 存在（MECraftingInventory:250） | ✅ 属实；方案"cancel @RETURN 用 isEmpty() 判空 destroy"成立 |
| `updateCraftingLogic` isComplete+空库存早退（:757-764） | ✅ 属实；M1 HEAD 注入负责"完成即 destroy"（原生不 destroy）——与 1.12.2 差异点方案已识别 |
| getCore() 原生 = `machineSrc.via`（:614-616） | ✅ 属实 → 对 vCPU 若未注入会 ClassCastException/NPE；M1 的 getCore@HEAD→null + isActive/getGrid/getWorld/markDirty 注入**覆盖全部 5 处 tiles 依赖**（:542 markDirty、:618 getGrid、:1349 isActive、:1725 getWorld、:746 isActive 调用点被 WrapOperation 拦截）——**R4 缓解成立**；`done()`(:1603)/`updateStatus`(:305)/`updateCPU`(:598) 对空 tiles 安全 |
| `AENetworkProxy`/`MachineSource extends BaseActionSource`/`MENetworkCraftingCpuChange(IGridNode)`/`IGrid.getMachines`/`getMachinesClasses`/`ICraftingGrid.getCpus` | ✅ 全部存在（源码 + javap 双重确认） |
| M6 `TaskProgress.value`（A998:2114-2116） | ✅ 属实（`protected long value`） |
| M4 `GuiCraftingStatus` 改走 `GuiCraftingCPUTable` widget（A998:client/gui/...GuiCraftingStatus:45-65） | ✅ 属实（extends GuiCraftingCPU implements ICraftingCPUTableHolder, IGuiSub）→ 1.12.2 的 List.get(i) redirect 不可照搬的结论成立 |

### 1.3 方案相对 R1 的新发现（t4 补充）复核

- JREL 证实合成类成员 MCP 名：✅ 评审人 javap 独立复现（§1.1）。
- rv3 submitJob merge 分支 + supportsCPUCluster 门禁：✅（§1.2）。
- CraftingJobV2 instanceof 放行：✅。
- GuiCraftingStatus 走 GuiCraftingCPUTable：✅。

### 1.4 关于 `.research/ae2u-1041`（验收项）

该目录仅 6 个存储 API 文件（IMEInventory/ICellHandler/ICellInventory/IMEInventoryHandler/StorageChannel/IStorageGrid），**不含任何合成类**——方案 §0 已如实披露并以 A998+J1000+JREL 三源替代，此做法正确。评审人以三源复核方案全部 mixin 结论，未发现高估或低估的集成点。

---

## 2. MVP 范围裁剪合理性（验收项 1）

**结论：合理，裁剪有据。**

- 必做集合（L4 控制器 + 6 类部件 + A 晶阵 + 结构 1~12 段 + 2 核心 mixin + MUI1 文本 GUI + L4 配方）构成"提交任务→vCPU 分配→并行推进→回库→完成→补位"的**可验证计算闭环**，与 R2 §4.3 的裁剪建议吻合。
- 后置/不做逐项有理由：超线程（第三套方块/贴图，机制可由普通槽验证）、L6/L9（参数化已完成但配方×3）、状态 GUI 特殊行（纯视觉且 rv3 渲染路径需重写）、CPU 持久化（rv3 NBT 字段不同）、@Overwrite 批量引擎（服务 EFabricator，rv3 语义不同，MVP 用原生 executeCrafting+accelerator 已验证路径）——**均为正确取舍**。
- 传输总线静态化有源码依据：`getSuppliedBytes()`（S:ECalculatorCellDrive.java:45-70）不检查 connectedSide，连接状态仅视觉 → 静态方块不损失容量核算功能。
- 传输总线/线程核心的结构位置与参考 JSON 一致（S:default_machinery/...l4.json：每段 D×2+B×1+T×1+P×2，6 格；头部 ME 通道 (1,0,1)）——方案 §5.1 属实（除 F4 的 16/17 外壳笔误）。
- 免维护/纯 AE 供电/防叠放与 E-Storage 决策一致（除 F3）。

---

## 3. 架构映射完整性与既有工程一致性（验收项 2）

**结论：完整、一致（除 F3）。**

- 类映射总表覆盖参考仓库 ecalculator 子系统全部 18 个类/接口/mixin（控制器、6 部件、2 物品、EPart 框架、ECPUCluster/ECPUStatus、M1-M4 mixin、网络包、事件、TOP、注册），并逐一给出 GTNH 侧对应与裁剪理由；MMCE 4 mixin、EF/EStorage 侧 mixin、NAE2/AE2FC 依赖均明确"不需要"。
- 与既有工程核对（src 实读）：
  - `MTEEcoStorageArray extends TTMultiblockBase implements ISurvivalConstructable`：✅ 方案"TTMultiblockBase + getStructure_EM + checkMachine + checkProcessing_EM + MUI1 addUIWidgets"全部是现有代码已用模式；
  - 免维护三件套 `getDefaultHasMaintenanceChecks/supportsMaintenanceIssueHoverable/onPostTick 清 NO_REPAIR`：✅ 已实现（可复刻）；
  - `GTStructureChannels.STRUCTURE_LENGTH.getValueClamped` + construct/survivalConstruct + size1..size12 程序化 shape：✅ 已实现（MAX_DRIVE_COLUMNS=12）；
  - RegistryMTE 32030-32032 占用、32033-32035 空闲（<32766，5.09.54.20 数组上限）：✅；
  - mixins.ecoaegtnh.json 简单类名 + package + minVersion 0.8.5-GTNH：✅ 与方案 §4.3 约定一致；
  - dependencies.gradle 锁定 AE2U rv3-beta-1000-GTNH / GT5U 5.09.54.20 / StructureLib 1.4.42：✅ 与方案 §0 一致；
  - MUI1 FakeSyncWidget 文本行模式：✅ E-Storage 已用。

---

## 4. 计算线程模型（验收项 4）

**结论：成立，无并发改造需求。**

- 参考实现所有 vCPU 由 `CraftingGridCache.onUpdateTick` 在服务器主线程逐 tick 顺序驱动（A998:171-174），"线程核心"是逻辑容器（`ECalculatorThreadCore` 持 `List<CraftingCPUCluster>`），并行度 = `remainingOperations = accelerator+1-usedOps`——GTNH 同为单线程主循环，**1:1 移植正确**。
- MVP L4 上限：1 线程/核心 × 12 段 = 12 并发任务 + 1 待命 vCPU，等价 12 台原版合成 CPU 的开销，性能预算结论成立（R6 缓解充分；TimeRecorder 每 200 tick 观测手段合理）。
- 控制器核算（recalculateParallelism/recalculateTotalBytes/getAvailableBytes/10% 红线/onVirtualCPUSubmitJob/createVirtualCPU/getClusterList/onClusterChanged）与 S:ECalculatorController.java:125-254 **逐行一致**；float→long 整数化（`usedBytes/10`、`totalBytes/10`）修正了参考实现 `(long)(usedBytes*0.1F)`（S:182）的舍入问题，**方向正确**。

---

## 5. 任务拆分可独立验证性（验收项 6）

**结论：满足。** 阶段 A-D 每阶段有明确交付物、验证方式、退出条件：
- A（结构）：成型/拆解/重建/网格接入/GUI 可开 —— 游戏内可直接验证；
- B（计算闭环）：任务提交→分配→推进→回库→完成→补位日志链、取消/拆线程核心、10% 红线、30 分钟稳定、getCpus 无残留 —— 全部可观测；
- C（GUI/贴图）：数据实时刷新、无 missing 贴图；
- D（配方/打磨）：服务器日志配方计数 skipped=0。
里程碑依赖（A→B→C→D，A 后 D 可并行启动配方骨架）合理。

---

## 6. 风险清单覆盖度（验收项 5）

- ✅ 性能（R6）：覆盖 ECPUCluster 轮询/调度，有预算与观测手段；
- ✅ mixin apply 失败（R1）：J1000+JREL 双 jar 核验 + 最小集先行；
- ✅ AE2U 版本漂移（R2）：锁定 rv3-beta-1000-GTNH + 签名核对脚本化；
- ✅ TTMultiblockBase 限制（R7）：动态长度/免维护/无 EU 由 E-Storage 全套验证支撑（12 shapes 已在生产代码）；
- ⚠️ **缺：第三方 mod 对 AE2U 合成类的 mixin 竞争**（F1，验收明确要求"UniMixins vs 其他 mod"）——服务端已装 ae2fc-1.5.95-gtnh（独立 jar，F5），其与 AE2U 合成体系交互，M1/M2 目标类可能与其 mixin 竞争；方案 R11 只覆盖同 mod 内 E-Storage mixin 冲突。**必须补一条风险与缓解**。
- ⚠️ **缺：控制器整体拆解生命周期**（F2）——成功标准 4 要求"无泄漏"，但方案未设计拆解时在途 vCPU 的处理。

---

## 7. 评审依据文件清单

- `docs/ECALCULATOR_PORT_PLAN.md`（评审对象，未修改）
- `docs/ECALCULATOR_RESEARCH.md`（t1）、`docs/ECALCULATOR_WEB_NOTES.md`（t2）
- `docs/DESIGN.md`、`HANDOVER.md`、`dependencies.gradle`、`src/main/java/ecoaegtnh/**`（既有工程）
- `.research/NovaEngineering-ECOAEExtension-main/**`（参考仓库 S 证据）
- `.research/ae2u-full/Applied-Energistics-2-Unofficial-rv3-beta-998-GTNH/**`（A998 证据）
- J1000/JREL javap 输出（评审人本次独立复核）

---

## 8. 评审结论

方案**可行性、架构映射、计算模型、任务拆分四项核心均通过独立核验**，可进入实现阶段的前提是完成 F1-F6 的修正（全部为方案文档层面的增补/勘误，不改变架构与技术路线）。按质量门约定，verdict = **needs_revision**，交由修复 + 复审循环。

*评审完成时间：阶段 1（t5）。评审对象：`docs/ECALCULATOR_PORT_PLAN.md`。*

---

## 9. 复审（round-2，t7，方案 v1.5）

> 复审对象：`docs/ECALCULATOR_PORT_PLAN.md` **v1.5**（repair round-2 产物，504 行）。复审内容：①t5 六项 findings（F1-F6）落地核查；②队长转达的用户硬约束（等级命名 C4/C6/C9、UI 仿 E-Storage、结构仿 E-Storage+西向列、方块外观仿原版+原创贴图）逐条核查；③v1.5 修订引入的新问题扫描。

### 9.1 t5 findings 落地核查（F1-F6）

| finding | v1.5 落地位置 | 结论 |
|---|---|---|
| F1（中）第三方 mixin 竞争风险 | §10 新增 **R14**（ae2fc 独立安装背景 + UniMixins priority + FML 日志核对 + 装机含 ae2fc 实测） | ✅ 已落地 |
| F2（中）控制器拆解生命周期 | §11 阶段 B 交付物 4（拆解/失效时 destroy 待命 vCPU → 逐线程核心 cancel/destroy → postCPUClusterChangeEvent）+ 验证清单 5（拆控制器后 getCpus 无残留/无泄漏/重启无异常） | ✅ 已落地 |
| F3（低）checkControllerShared 复用不实 | §5.2 改为"**需新实现**（参考 S:EPartController.java:39-51/102-113，E-Storage 未实现不能直接复用）" | ✅ 已落地 |
| F4（低）头部外壳 17→16 | §5.1 修正为"其余 **16** 格外壳（18−1−1）"，24~90 格结论不变 | ✅ 已落地 |
| F5（低）AE2FC"内建"表述 | §3 依赖表 + §2.3 改为"服务端独立安装 ae2fc-1.5.95-gtnh（独立 mod，非 AE2U 内建，已核验服务端 mods 目录存在独立 jar）" | ✅ 已落地 |
| F6（低）M3 双侧注册 | §4.3 mixins.json 说明 + §11 阶段 C 明确"M3 双侧、M4 仅 client；MVP M1/M2 server 组不受影响" | ✅ 已落地 |

### 9.2 用户硬约束逐条核查（队长转达，违反=high）

**约束 1：等级命名必须 C4/C6/C9** ✅ 已满足
- §4.2 硬性规则块（"⚠️ 分级代号…硬性"）：注册名档位后缀一律 `_c4/_c6/_c9`、显示名统一 `ECO xxx (C4)/(C6)/(C9)`、不使用 CT4/CM4A/CE4 型号作显示名、代码 tier 不沿用 L 字面量、与 E-Storage L4/L6/L9 体系明确区分；§12 有"分级代号更正与 R1/R2 无冲突确认"。
- 全文检索：档位引用（MVP 档、配方档、并行/线程核心、晶阵、MTE 常量 `MTE_ID_C4/C6/C9`、§7.4 数值、§7.6 预算、§11 阶段、§13 开放决策 2）已全部 C4/C6/C9；残留的 L4/L6/L9 仅出现在**说明/区分文字**（命名来龙去脉、E-Storage 对照、R1 源码枚举引用），符合"区分说明文字除外"。
- ⚠️ 低危一致性备注（N1/N2，见 §9.4）：个别示例与硬性格式存在出入。

**约束 2：UI 仿 E-Storage MUI1** ✅ 已满足
- §0.1 约束 1 + §8.1 + §4.1 类映射 + §11 阶段 A/C：`useMui2=false`、198×192、screen_blue、Scrollable 文字屏 + 底部参数条 + LED 悬停 + FakeSyncWidget，明确"不照搬 1.12.2 MonitorPanel/CPUStatusPanel/StorageBar 自定义面板，仅取信息内容"，实现参考 `MTEEcoStorageArray.addUIWidgets/drawTexts`（既有代码）。

**约束 3：结构仿 E-Storage 机制 + 西向扩展列** ✅ 已满足
- §0.1 约束 2 + §5.1/§5.2 + §11 阶段 A：`TTMultiblockBase` + StructureLib、头部 3×3×2 + **西向**（控制器局部西向 = 1.12.2 原版 MM local west 语义，世界方向随朝向旋转）1~12 段每段 6 格；shape 程序化生成/12-shape 下降循环/scanStructureVolume/免维护/结构预览（construct/survivalConstruct + GTStructureChannels）机制与 E-Storage 一致；§13 开放决策 1（方向）已关闭，R8 风险同步更新。

**约束 4：方块外观仿 1.12.2 原版 + 原创贴图** ✅ 已满足
- §0.1 约束 3 + §8.2 + §11 阶段 C：由 model-artist 参考 `.research/NovaEngineering-ECOAEExtension-main/src/main/resources` 原版 E-Calculator 贴图产出**原创贴图**（视觉风格参照、不拷贝文件、GPL 规避，与 E-Storage 40 张原创贴图同法）；阶段 C 验证含"外观与 1.12.2 原版风格对标（用户确认）"。

### 9.3 v1.5 修订引入的新问题扫描

- R14 缓解措施合理（UniMixins priority + FML 日志 + ae2fc 实测）；阶段 B 拆解流程（cancel→storeItems 回库→destroy→postCPUClusterChangeEvent）与 MVP"拆方块=取消任务"决策一致；§0.1 与 R1 无冲突确认（§12 增补）成立；技术章节 §6/§7 相对 v1.0 无实质改动（v1.0 已核验）。
- 未发现新的技术性回归。

### 9.4 低危一致性备注（不阻塞实现，建议实现阶段顺手统一）

- **N1（低）**：§4.2 控制器显示名示例"`ECO C4 可扩展计算主机`（建议，最终以用户确认为准）"与同节硬性规则"显示名统一格式 `ECO xxx (C4)/(C6)/(C9)`"不完全一致（C4 内联 vs 括号后缀）。建议实现阶段按硬性规则统一为 `ECO 可扩展计算子系统主机 (C4)`，或在实现前与用户最终确认一种格式并全文统一（§4.2 已注明"最终以用户确认为准"，故不阻塞）。
- **N2（低）**：§1 MVP 成功标准 3"晶阵驱动器放入 **A 级**闪存晶阵后"沿用内部 A/B/C 描述，与硬性规则"玩家可见命名一律 C4/C6/C9（A/B/C 仅内部追溯）"不一致；建议改为"C4 闪存晶阵"。

### 9.5 复审结论

**verdict = pass**。v1.5 已完整落地 t5 六项 findings（F1-F6）并满足用户全部四项硬约束；核心可行性结论维持 round-1 的独立核验结果（§1-§5）。N1/N2 为显示名示例级低危一致性备注，不阻塞进入实现阶段，由实现阶段按硬性规则统一即可。

*复审完成时间：阶段 1（t7，review round-2）。评审对象：`docs/ECALCULATOR_PORT_PLAN.md` v1.5。*

---

## 10. 最终基线命名一致性专项核查（队长基线更新，t7 补充）

> 队长指定最终评审基线为 v1.4（相对 v1.2：①晶阵注册名改档位式 `ecalculator_cell_c4/c6/c9`、尺寸进 lang；②显示名统一 `ECO xxx (C4)/(C6)/(C9)`、删除 CT4/CM4A/CE4 型号混用；③§4.2 新增来龙去脉证据）。**实际文档当前为 v1.5（504 行），是 v1.4 的超集**——v1.4 的三点命名修订全部包含在内（v1.2→v1.3→v1.4 修订链），v1.5 仅在其上追加 t5 findings 修复（R14/阶段 B 拆解/checkControllerShared/16 格外壳/ae2fc 表述/M3-M4 分组，§9.1 已核）。故以 v1.5 复查命名一致性等价于覆盖 v1.4 基线。

### 10.1 命名一致性核查（显示名 / 注册名 / 配方分档 / 阶段交付物）

| 维度 | 核查结果（行号以 v1.5 为准） |
|---|---|
| 注册名 | ✅ 档位后缀一律 `_c4/_c6/_c9`：控制器 `ecoaegtnh.ecalculator.array.c4/c6/c9`（:153）、并行核心 `ecalculator_parallel_proc_c4/c6/c9`（:157）、线程核心 `ecalculator_thread_core_c4/c6/c9`（:158）、晶阵 `ecalculator_cell_c4/c6/c9`（:160，**档位式**，尺寸 64M/1024M/16384M 进 lang/tooltip）；MTE 常量 `MTE_ID_C4/C6/C9`（:153/:428）。无 `_l4/_l6/_l9` 注册名残留（全文检索仅说明文字出现） |
| 显示名 | ✅ 硬性格式 `ECO xxx (C4)/(C6)/(C9)`（:168）；并行核心 (C4)、线程核心 (C4)、闪存晶阵 (C4/C6/C9) 均为括号式（:157/:158/:160/:168）；CT4/CM4A/CM4B/CE4 仅历史参照说明（:166/:168/:487） |
| 配方分档 | ✅ §9.1 "C4 档（MVP，EV 电压）"（:384）、"C6/C9（阶段 D）"（:388）、部件名 并行核心 (C4)/线程核心 (C4)/C4 闪存晶阵（:386） |
| 阶段交付物 | ✅ §11 阶段 A `MTEEcalArray`（C4）+ `ItemEcalCell(C4)` + MTE 32033/`MTE_ID_C4`（:428）；阶段 D C4 全套配方 + C6/C9 档位扩展（:458-459）；§7.4 数值 C4=256/C6=2048/C9=16384、晶阵门控 C4→C4+/C6→C6+/C9→仅 C9（:336-337） |
| 与 E-Storage 区分 | ✅ §4.2 硬性规则 + §12 一致性声明明确与 E-Storage `_l4/_l6/_l9`/`(L4)` 体系区分（:167/:487），"计算"字样区分（:170） |

### 10.2 结论

**命名一致性核查通过**：v1.4 基线三点修订（晶阵档位式注册名、显示名统一格式、来龙去脉证据）全部满足，v1.5 无命名回归。维持 t7 verdict=pass；仅 2 条低危一致性备注（不阻塞，实现阶段按硬性规则统一）：
- **N1（低）**：§4.2 控制器显示名示例 "`ECO C4 可扩展计算主机`"（:153，内联 C4）与硬性格式 `ECO xxx (C4)` 略有出入——示例已标注"（建议，最终以用户确认为准）"，硬性规则（:168/:487）格式明确；
- **N2（低）**：§1 成功标准 3 "放入 **A 级**闪存晶阵"（:44）沿用内部 A/B/C 描述，建议改 "C4 闪存晶阵"（:171 已注明 A/B/C 仅内部追溯）。

*最终基线命名核查完成时间：阶段 1（t7 补充）。评审对象：`docs/ECALCULATOR_PORT_PLAN.md` v1.5（⊇ v1.4 基线）。*
