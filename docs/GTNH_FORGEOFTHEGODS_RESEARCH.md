# GTNH「诸神之锻炉」（Forge of the Gods）里程碑/升级树机制调研报告

> 用途：为 ECO（E-Calculator）版里程碑系统设计提供参考。
> 调研对象：GTNH 2.9.0-beta-2 中的 Forge of the Gods（中文社区称「诸神之锻炉」「锻神炉」，Godforge）。
> 证据来源：
> 1. **本地源码（权威）**：`.research/gt5-src/`（GTNH 全家桶开发工作区源码，对应服务端 `gregtech-5.09.54.20.jar`）——服务端 FML 日志确认 `kubatech(KubaTech)` 与 `tectech(TecTech)` 均**从 `gregtech-5.09.54.20.jar` 加载**（GTNH 2.9 已把 TecTech/Kubatech 合并进 GT5U 本体），且日志出现 `Godforge blocks registered.` / `Godforge Glass registered`（服务端 `logs/fml-server-latest.log`）。Godforge 的 MTE 在 `tectech/thing/metaTileEntity/multi/godforge/`，GUI 在 `gregtech/common/gui/modularui/multiblock/godforge/`，配方在 `tectech/loader/recipe/Godforge.java`。
> 2. **网络资料（玩家视角）**：GTNH 灰机中文维基《[诸神之锻炉](https://gtnh.huijiwiki.com/p/62607)》《[诸神之锻炉升级节点](https://gtnh.huijiwiki.com/wiki/诸神之锻炉升级节点)》（抓取被 403 反爬拦截，仅引用条目存在性与搜索摘要）；[namu.wiki EN/KR/JA 长条目](https://en.namu.wiki/w/신들의%20제련소(GTNH))；[GTNH Modpack GitHub issue #19814（Godforge 重载时碎片重复 bug，佐证碎片持久化）](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/19814)；[B站 GTL 实况「这绝对是这个整合包中最壮观的机器——诸神之锻炉」](https://www.snm0516.aisee.tv/video/BV1mskEBTE73/)、[GTL六周目第二十五期（锻天炉/伪神之锻炉材料）](https://www.bilibili.com/video/BV1ozdHBhEs3/)。
> 3. **服务端配置**：`M:\AA科技\GTNH\服务端\config\kubatech\kubatech.cfg` 仅 debug/mobhandler 两节，**无 godforge 专属配置**（机制全硬编码，无配置开关；仅 `ConfigHandler.debug.DEBUG_MODE` 控制调试按钮）。

> 行号证据约定：源码路径统一缩写——`T:` = `.research/gt5-src/tectech/thing/metaTileEntity/multi/godforge/`，`G:` = `.research/gt5-src/gregtech/common/gui/modularui/multiblock/godforge/`，`L:` = `.research/gt5-src/tectech/loader/recipe/Godforge.java`，`C:` = `.research/gt5-src/tectech/loader/ConfigHandler.java`。行号以本次读取为准。

---

## 1. 系统组成总表

| 组成 | 内容 | 源码证据 |
|---|---|---|
| 主机 | `MTEForgeOfGods extends TTMultiblockBase`（T:`MTEForgeOfGods.java:105`），127×29×186 巨型多方块，3 环结构（T1/T2/T3） | 结构定义 T:`MTEForgeOfGods.java:182-216`；tooltip 尺寸 :809 |
| 控制器渲染 | 前方控制器 + 后方 122 格处 `forgeOfGodsRenderBlock` 动画渲染器（`TileEntityForgeOfGods`：环数/星半径/转速/颜色） | T:`MTEForgeOfGods.java:592-670` |
| 模块（小多方块） | `MTEBaseModule`（基类 T:`MTEBaseModule.java:47`）派生的 4 种：**熔炉模块** `MTESmeltingModule`、**熔融核心** `MTEMoltenModule`、**等离子制造器** `MTEPlasmaModule`、**异域化器** `MTEExoticModule`；每模块 7×7 圆盘 + 柱状结构 | 结构 T:`MTEBaseModule.java:357-385`；模块槽 0-8（START）/0-12（CD）/0-16（END）T:`MTEForgeOfGods.java:387-393` |
| 燃料 | 3 种流体燃料：**Residue**（DTR 超重元素残渣）、**Stellar**（原始星质 RawStarMatter）、**MHDCSM**（熔融）；启动/持续使用**恒星燃料**（中子锭方块或 Avaritia 恒星资源） | G:`data/Fuels.java:10-14`；T:`MTEForgeOfGods.java:125-126,272-279` |
| 内部货币 | **引力子碎片（Graviton Shard）**：由 4 个里程碑进度产生，用于购买升级；END 升级后可喷出到 ME 输出总线/注入 | T:`MTEForgeOfGods.java:939-964`；T:`ForgeOfGodsData.java:358-380` |
| 里程碑 | 4 个：**充能 CHARGE**（总耗电）/ **转化 CONVERSION**（总配方数）/ **催化 CATALYST**（总燃料消耗）/ **构成 COMPOSITION**（结构扩展数）；每里程碑 0~7 级，全满触发**反转 Inversion** 继续升级 | G:`data/Milestones.java:13-71`；公式 T:`GodforgeMath.java:342-434` |
| 升级树 | 31 个节点（START→END），碎片成本 0~12，7 个大升级另有**材料成本**（最多 12 种）；支持拆分支（SPLIT）、重设（respec） | T:`ForgeOfGodsUpgrade.java:24-277`；材料 L:`Godforge.java:688-765` |
| GUI | MUI2 多面板：主面板 + 升级树（300×957 滚动）/单个升级/材料投入/里程碑（400×300）/单个里程碑/统计/燃料配置/电池配置/电压配置/异域输入列表/星星外观/一般信息/特别感谢 | G:`MTEForgeOfGodsGui.java:82-390`；面板清单 §5 |
| 能量 | **不收能量舱**（结构里禁止能量舱，T:`MTEForgeOfGods.java:301-308`）；模块耗电走**无线 EU 网络**（玩家 UUID 绑定） | T:`MTEBaseModule.java:96-106` |
| 配置 | 无 godforge 配置项；仅 DEBUG_MODE（`/kubatech debug` 类命令）显示调试按钮 | C:`ConfigHandler.java`（无匹配）；G:`UpgradeTreePanel.java:271-320` |

---

## 2. 投料与运行机制（机器怎么"活着"）

### 2.1 恒星燃料 → 内部电池（启动门槛）

- 输入总线（仅 1 个，且强制 1 个：`checkHatchExact(InputBus,1)`，T:`MTEForgeOfGods.java:309`）每 5 秒吸收**恒星燃料**（`STELLAR_FUEL` = Avaritia 恒星资源 或 中子锭块，:399-419）。
- 电池为空时：`neededStartupFuel = fuelFactor×25×1.2^fuelFactor`（T:`GodforgeMath.java:37-41`），一次性扣除后注入内部电池 `internalBattery`（T:`MTEForgeOfGods.java:421-428`）。**电池为 0 → 所有模块断开、渲染器销毁、机器停转**（:977-990）。
- 电池满后进入"充电模式"开关（`batteryCharging`，GUI 可切）：充电时燃料消耗 ×2 但按 1:1 补电池（:508,527-529）。

### 2.2 流体燃料消耗（催化里程碑计量）

- 3 种燃料每 5 秒从输入舱抽取（T:`MTEForgeOfGods.java:485-534`），消耗公式（T:`GodforgeMath.java:21-35`）：
  - Residue：`factor×300×1.15^factor` mB/5s
  - Stellar：`factor×2×1.08^factor` mB/5s
  - MHDCSM：`factor/25` mB/5s
- `fuelConsumptionFactor`（燃料系数）上限默认 5，可被升级扩展（`calculateMaxFuelFactor`，:43-65；GEM 每买 1 个升级 +1、CFCE ×1.2、TSE 无上限按公式折算 `effectiveFuelFactor = 43+(f-43)^0.4`）。
- **累计消耗计入催化里程碑**（`totalFuelConsumed`，T:`MTEForgeOfGods.java:526`）。

### 2.3 模块运行（产能载体）

- 模块是**独立的小多方块**，结构成型后由主机每 5 秒检查 `allowModuleConnection`（T:`GodforgeMath.java:302-322`）：
  - 熔炉模块：无条件可连（基础）；
  - 熔融模块：需升级 **FDIM**；等离子模块：需 **GPCI**；异域模块（普通模式）：需 **QGPIU**；异域模块（Magmatter 模式）：需 **EE**。
- 连接成功 → 主机计算该模块的**热量（heat）→ 并行（parallel）→ 速度加成 → 能量折扣 → 处理电压**（T:`MTEForgeOfGods.java:447-469`），写入模块字段（T:`MTEBaseModule.java:124-268`）。
- 模块配方表：熔炉=熔炉+鼓风炉配方（可切换 furnaceMode）、熔融=`godforgeMoltenRecipes`（= 全量鼓风炉配方的"物品输出→熔融流体"自动生成版，L:`Godforge.java:846-898`）、等离子=`godforgePlasmaRecipes`、异域=`godforgeExoticMatterRecipes`。
- 模块耗电走**无线 EU 网络**（玩家 UUID），模块自己用 GT `ProcessingLogic` 跑配方并统计 `powerTally`（BigInteger）/`recipeTally` 回传主机（T:`MTEBaseModule.java:96-106,235-257`；T:`MTEForgeOfGods.java:332-340`）。
- **防作弊**：配方实际所需热量 > 模块当前热量 → 模块断开（`factorChangeDuringRecipeAntiCheese`，T:`GodforgeMath.java:324-330`）。

---

## 3. 里程碑机制（碎片从哪来）

### 3.1 四个里程碑与触发公式（T:`GodforgeMath.java:342-434`，常量 T:`ForgeOfGodsData.java:34-50`）

| 里程碑 | 计量对象 | 第 level 级所需（level=0..7） | 反转（≥T7）后 |
|---|---|---|---|
| CHARGE 充能 | 累计耗电（BigInteger，跨模块汇总） | `9^level × 10^15` EU | 每 7 级 ×`9^6`：`POWER_MILESTONE_T7_CONSTANT×(level-5)` |
| CONVERSION 转化 | 累计配方完成数 | `4^level × 10^7` 个配方 | 每 7 级 ×`4^6` |
| CATALYST 催化 | 累计燃料消耗（恒星燃料单位） | `3^level × 10^4` 单位 | 每 7 级 ×`3^6` |
| COMPOSITION 构成 | 结构扩展数 = 环数 + 模块种类数 − 1（反转时熔融/等离子/异域多装按 1/2/3/4/5 加权小数累加） | `level+1` 个扩展 | 每 7 级线性 +7 |

- 进度计算：前 7 级为 `floor( (log(x/C)/logK + 1) )` 的**对数阶梯**（幂律需求：1e15→9e15→81e15… 配方 1e7→4e7→…，燃料 1e4→3e4→…）；构成里程碑是唯一线性项。
- **反转（Inversion）**：4 个里程碑进度全部 ≥7 时自动激活（`checkInversionStatus`，T:`MTEForgeOfGods.java:872-881），进度继续涨到 7+；反转改变公式（T7 常量等比放缩）、UI 进度条反向显示、模块获得 `inversionConfig`（可处理反转配方）。反转后碎片产出继续增长——**机器的"二周目"**。

### 3.2 碎片产出（货币铸造）

- `gravitonShardsAvailable = Σ_里程碑 progress×(progress+1)/2 − 已花费`（T:`MTEForgeOfGods.java:939-948；UI 同式 G:`IndividualMilestonePanel.java:228-240`）。
- 例：单里程碑 7 级 → 28 碎片；4 里程碑全 7 级 → 112 碎片（debug 碎片设置上限即 112，G:`UpgradeTreePanel.java:296`）。
- 碎片在反转后可继续累积；**END 升级**解锁碎片喷出/注入（ME 输出总线喷出 `GravitonShard` 宝石；输入总线改收碎片补充存量，T:`MTEForgeOfGods.java:396-444,950-964`）。

---

## 4. 升级树（碎片往哪花）——解锁内容全清单

### 4.1 树结构与规则（T:`ForgeOfGodsUpgrade.java:24-277`）

- 31 个节点，`prereqs` 默认"任一前置"即可，`requireAllPrereqs` 时需全部（REC/CTCDD/NGMS 等）。
- 碎片成本：0（START）/1（IGCC..SA 段）/2（GPCI..QGPIU 段）/3（SEFCP..DOR 段）/4（TPTP/DOP/NGMS）/5（SEDS）/6（PA）/7（CD）/8（TSE）/9（TBF）/10（EE）/12（END）。
- **拆分升级** `SPLIT_UPGRADES = {SEFCP, TCT, GGEBE}`（:260）：同一时间激活数 < 当前环数（1/2/3），即每条环可选一条分支（QGPIU 后三选一：SEFCP→CNTI→NDPE、TCT→EPEC→POS、GGEBE→IMKG→DOR 最终汇合 NGMS）。
- **解锁校验链**（服务端 `ForgeOfGodsData.unlockUpgrade`，T:`ForgeOfGodsData.java:358-367`）：前置满足 → 拆分上限 → 碎片足够（`checkCost`）→ 激活并扣碎片；材料成本另由 `payCost` 分步支付（T:`UpgradeStorage.java:41-83`，每槽 short 记录已付数量，全部付清才 `costPaid`）。
- **重设（respec）**：右键升级节点 → 退回碎片，但 `checkDependents` 保证不破坏已解锁的下游（T:`ForgeOfGodsData.java:369-380`；T:`UpgradeStorage.java:125-142`）。
- 数据持久化：每个升级 active 位 + 12 槽已付材料 short 数组，NBT `upgrades` 段（T:`UpgradeStorage.java:190-225`）；升级树整体经 `GenericListSyncHandler` 同步客户端（:228-239）。

### 4.2 升级全表（名称/效果；英文名与短名来自 `assets/tectech/lang/en_US.lang` 的 `fog.upgrade.tt.*`/`short.*`/`text.*`，共 223 个 fog 键）

| # | 短名 | 名称 | 碎片 | 前置 | 解锁内容类型 |
|---|---|---|---|---|---|
| 0 | START | Forge of the Gods | 0(+材料) | — | 基础功能：8 模块槽、1 环、熔炉模块、2GV 处理电压、15,000K 热上限 |
| 1 | IGCC | Improved Gravitational Convection Coils | 1 | START | 倍率：速度加成 = 1/Heat^0.01（随热量提速） |
| 2 | STEM | Spacetime Topology Expansion Modulator | 1 | IGCC | 倍率：燃料消耗 ×0.8 |
| 3 | CFCE | Cosmic Fuel Chamber Expansion | 1 | IGCC | 容量：最大燃料系数 ×1.2 |
| 4 | GISS | Graviton-Induced Superconductivity System | 1 | STEM | 容量：处理电压 +燃料系数×10^8 EU/t（2GV 基础上） |
| 5 | FDIM | Fluid Dynamics Integration Module | 1(+材料) | STEM/CFCE | **新方块**：解锁熔融模块（+自动熔融配方线） |
| 6 | SA | Superluminal Amplifier | 1 | CFCE | 倍率：并行 ×(1+燃料系数/15) |
| 7 | GPCI | Gravitational Plasma Containment Inductor | 2(+材料) | FDIM | **新方块+新配方**：解锁等离子模块、元素→等离子（1 步，T3 融合上限） |
| 8 | REC | Relativistic Electron Capacitor | 2 | GISS+GPCI(全) | 容量+交互：电池大小可配置（上限 int）、能量折扣 ≤5% |
| 9 | GEM | Graviton Entanglement Modulator | 2 | GPCI | 容量：最大燃料系数 +1/已购升级数 |
| 10 | CTCDD | Closed Timelike Curve Disruption Device | 2 | GPCI+SA(全) | 倍率：并行 ×2 |
| 11 | QGPIU | Quark-Gluon Plasma Isolation Unit | 2(+材料) | REC/CTCDD | **新方块+新配方**：解锁异域模块、夸克-胶子等离子（初期不受其他加成） |
| 12 | SEFCP | Singularity Exposure Fuel Compression Process | 3 | QGPIU | 倍率：燃料→热量公式强化（log1.12/log1.18） |
| 13 | TCT | Transfinite Construction Techniques | 3 | QGPIU | 倍率：SA 公式强化（÷15→÷5，熔融/熔炉 ×3） |
| 14 | GGEBE | Gravitationally Guided Electron Beam Emitter | 3 | QGPIU | 倍率：OC 2→2.3/4 |
| 15 | TPTP | Temporal Plasma Transformation Process | 4 | GGEBE | 配方：等离子多步处理（Tier 限制仍在） |
| 16 | DoP | Duplicity of Potency | 4 | CNTI | 倍率：熔炉模块享受熔融升级路径收益 |
| 17 | CNTI | Critical Neutrino Tunnelling Integration | 3 | SEFCP | 容量：EBF 热量上限 15,000K→30,000K |
| 18 | EPEC | Extreme Pulsar Exposure Chambers | 3 | TCT | 倍率：并行 ×(1+Heat/15000) |
| 19 | IMKG | Internal Micro-Kugelblitz Generator | 3 | GGEBE | 倍率：EBF 能量折扣 5%→8% + 电池填充度折扣 |
| 20 | NDPE | Neutron Degeneracy Pressure Exposure | 3 | CNTI | 容量：30,000K 以上热加成（30000+(H-30000)^0.85/0.8） |
| 21 | PoS | Parity of Singularity | 3 | EPEC | 倍率：并行 ×(1+升级数/5) |
| 22 | DoR | Disparity of Rarity | 3 | IMKG | 倍率：IGCC 强化（÷Parallel^0.02） |
| 23 | NGMS | Null-Gravity Modulation Sheath | 4 | NDPE+PoS+DoR(全) | 倍率：处理电压 ×4^环数 |
| 24 | SEDS | Synthetic Element Decay Stabilization | 5 | NGMS | 配方：等离子 T5 上限（Tier 1） |
| 25 | PA | Paradoxical Attainment | 6 | SEDS | 倍率：异域模块可受其他升级影响（平方根化） |
| 26 | CD | Cosmically Duplicate | 7(+材料) | PA | **新结构**：第二环 +4 模块槽；两环升级互连（CTCDD 语义） |
| 27 | TSE | Transfinite Stellar Existence | 8 | CD | 容量：燃料消耗无上限（按公式折算有效值） |
| 28 | TBF | The Boundless Flow | 9 | TSE | 容量+交互：处理电压无上限、模块 GUI 可调电压 |
| 29 | EE | Effortless Existence | 10(+材料) | TBF | **新配方**：Magmatter 生产 + 异域等离子（等离子 Tier 2）；异域模块 Magmatter 模式解锁 |
| 30 | END | Orion's Arm Genesis Schema | 12(+材料) | EE | **新结构+新交互**：第三环 +4 模块槽、碎片喷出/注入、全树终点 |

另有**隐藏升级**（Secret Upgrade，START 旁的彩蛋按钮，纯 UI 状态无服务端效果，G:`UpgradeTreePanel.java:225-269`）。

### 4.3 大升级材料成本（L:`Godforge.java:688-765`，EternalSingularity 加载时注册）

- START：UIV 超导框架×64、超导体复合物×32、亚稳态奥格金属齿轮×16、永恒奇点×8、UIV 机械臂×64、UEV 力场发生器×64
- FDIM：超合金高炉×16、Hypogen 线圈×64、谐波声子传输导管×32、永恒奇点×16 等
- GPCI：恒星能量虹吸外壳×8、UV3 聚变计算机×8 等
- QGPIU：紧凑聚变 MK5×2、T4 聚变线圈×64、永恒奇点×32 等
- CD：时空框架×64、UMV 超导框架×64、Hypogen/龙金属框架箱×64、EOH 增强空间外壳×64、ZPM6×2 等
- EE：白矮/黑矮物质框架×64、永恒框架×16、宇宙素框架×2、EOH 无限能源外壳×64、T6 稳定场发生器×48、ZPM6×16 等
- END：MHDCSM 框架×64、永恒×64、MagMatter 框架×64、T8 稳定场发生器×64、夸克胶子等离子模块×64、星阵制造器×4、MagMatter 纳米×1、ZPM6×32、UXV 力场/机械臂×64 等
- 每个大升级的投料清单同步注册为 NEI 假配方（`godforgeFakeUpgradeCostRecipes`，产出对应模块/调制器/流体展示，L:`Godforge.java:784-844`）。

### 4.4 解锁内容类型归纳（验收问答）

1. **新方块/新结构**：4 种模块（FDIM/GPCI/QGPIU/EE 解锁）+ 第二环（CD）+ 第三环（END）；
2. **新配方**：元素→等离子（GPCI）、多步等离子（TPTP）、T5 等离子（SEDS）、异域等离子+Magmatter（EE）、夸克-胶子等离子（QGPIU）、自动熔融配方线（FDIM 的 godforgeMoltenRecipes）；
3. **容量**：燃料系数上限（CFCE/GEM/TSE）、电池大小（REC）、热量上限（CNTI/NDPE）、处理电压（GISS/NGMS/TBF）、模块槽（CD/END）；
4. **倍率**：速度（IGCC/DoR）、并行（SA/TCT/CTCDD/EPEC/PoS/DoP/PA）、能耗折扣（REC/IMKG）、OC 倍率（GGEBE）、燃料效率（STEM/SEFCP）；
5. **新交互**：电池配置（REC）、电压配置（TBF）、碎片喷出/注入（END）、星星外观/颜色（不依赖升级）；
6. **等级上限**：里程碑本身**无硬上限**（反转后 7+ 继续指数增长）；升级树有穷（31 节点 + 3 环 + 隐藏彩蛋）——"毕业"= 全升级 + 三环 + 反转持续推进。

---

## 5. GUI 与交互（MUI2 多面板）

| 面板 | 内容 | 证据 |
|---|---|---|
| 主面板 MAIN | 顶部数据（电池/最大电池/需启动燃料/燃料存量/碎片/环数/充电开关/格式器）+ 按钮列：里程碑 / 燃料配置 / 电池配置 / 星星外观 / 升级树 / 统计 / 碎片喷出 / 一般信息 / 特别感谢 | G:`MTEForgeOfGodsGui.java:70-390` |
| 升级树 UPGRADE_TREE | 300px 宽、957px 滚动；31 个按钮 + 彩色连接线（激活段变实线）+ 隐藏升级按钮；左键=详情/Shift+左键=直接完成（无材料需求时），右键=重设 | G:`UpgradeTreePanel.java:40-188,190-223` |
| 单个升级 INDIVIDUAL_UPGRADE | 名称/正文/传说文本、碎片成本、可用碎片、材料需求按钮、确认/重设按钮（动态大小 250/300） | G:`IndividualUpgradePanel.java:31-256` |
| 材料投入 MANUAL_INSERTION | 12 个材料需求槽（点击查 NEI 配方/用途）+ 已付数量（红/黄/绿）+ 16 格投料暂存窗口 + 「消耗材料」按钮 → `PAY_UPGRADE_COST` | G:`ManualInsertionPanel.java:35-242`；服务端动作 G:`sync/SyncActions.java:43-50` |
| 里程碑 MILESTONE | 400×300；4 个 130×100 里程碑按钮（图标/标题/双进度条：正向+反转） | G:`MilestonePanel.java:26-138` |
| 单个里程碑 | 总进度、等级、本级所需量、碎片收益、反转激活提示、数字格式器切换 | G:`IndividualMilestonePanel.java:35-240` |
| 统计 STATISTICS | 4 模块 × 7 指标（热量/有效热量/并行/速度加成/能量折扣/OC 除数/处理电压）+ 燃料系数预览滑条 | G:`StatisticsPanel.java:58-265`；G:`data/Statistics.java:13-71` |
| 燃料配置 / 电池配置 / 电压配置 | 选燃料种类；电池充电开关与大小（REC 解锁）；模块电压（TBF 解锁） | G:`panel/FuelConfigPanel.java`、`BatteryConfigPanel.java`、`VoltageConfigPanel.java` |
| 异域输入列表 | 异域模块的预期输入（等离子物质映射表）与可能输入 | G:`panel/ExoticInputsListPanel.java:48-50`、`ExoticPossibleInputsListPanel.java` |
| 星星外观 | 星颜色（预设/自定义 RGB/导入导出）、半径、转速、渲染开关（螺丝刀快捷开关） | G:`panel/StarCosmeticsPanel.java`、`CustomStarColorPanel.java`、`StarColorImportPanel.java`；T:`MTEForgeOfGods.java:707-719` |

- 同步架构：`SyncHypervisor` 统一管理各面板的 `SyncValue`（38 个，G:`sync/SyncValues.java`）与 `SyncAction`（8 个服务端/客户端动作，G:`sync/SyncActions.java`），升级树整体走 `GenericListSyncHandler`。
- 信息输出：扫描器数据（环数/激活升级数/模块数，T:`MTEForgeOfGods.java:744-756`）、tooltip（多段结构+模块槽位提示，:804-847）、活动音效循环。

### 5.1 主面板按钮列实现细节（供 ECO 主面板重构 T55 照搬）

> 源码：`G:MTEForgeOfGodsGui.java:38-400`（全部按钮构建）+ 基类 `MTEMultiBlockBaseGui.java:157-278,1129-1161`（MUI2 布局框架）+ `G:sync/Panels.java:28-116`（窗口 id 与绑定）。ECO 侧 MUI1 对应物（已实现 t54 里程碑窗口）：`src/main/java/ecoaegtnh/metatileentity/MTEEcalArray.java:1469-1584`。

#### 5.1.1 主面板布局骨架（198×201）

- 面板尺寸：`getBasePanelWidth()=198`，`getBasePanelHeight()=181+getTextBoxToInventoryGap()=181+20=201`（基类 `MTEMultiBlockBaseGui.java:199-209`；Godforge 覆写 gap=20，:179-181）。**doesBindPlayerInventory(false)**（:176-183）——放弃基类自动玩家物品栏，改用自定义 inventory 行容纳按钮列。
- 主列自上而下（`createMainColumn`，基类 :165-172）：①terminal 行（终端文本框 190×174，含左右下角按钮列）②muffle 静音按钮（可静音时）③**panelGap 行**（高 20px，Godforge 放 3 个横排按钮）④**inventory 行**（高 76px：玩家物品栏 + 右侧按钮列，基类 :1129-1138）。
- 终端文本框：190×174，padding 4，主题 `BACKGROUND_TERMINAL`；右下角列 `rightRel(0,6,0).bottomRel(0,6,0)`（基类 :236-248），左下角列 `leftRel(0,6,0).bottomRel(0,6,0)`（:256-261）。

#### 5.1.2 右侧竖排按钮列（主入口，5 个按钮）

- 容器（G:`MTEForgeOfGodsGui.java:82-93`）：
  ```java
  Flow.column().width(18).leftRel(1, -3, 1).childPadding(3).mainAxisAlignment(MainAxis.END)
      .child(createMilestonePanelButton())     // 里程碑
      .child(createFuelConfigPanelButton())    // 燃料配置
      .child(createBatteryConfigPanelButton()) // 电池配置
      .child(createStarCosmeticsPanelButton()) // 星星外观
      .child(createUpgradeTreePanelButton());  // 升级树
  ```
- 位置：`leftRel(1,-3,1)` = 贴面板右缘、距右 **3px**、自身右对齐（基类默认按钮列是 `leftRel(1,-2,1)` 距右 2px，:1141-1145）；`mainAxisAlignment(END)` = 整体**靠底**；`childPadding(3)` = 按钮间距 **3px**；列宽 **18px**。
- 按钮统一样式（每个按钮，如 :196-213）：`new ButtonWidget<>().size(16)`（**16×16**）→ `overlay(GTGuiTextures.TT_OVERLAY_*)`（16×16 图标覆盖层）→ `background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)`（32×32 主题按钮纹理自动缩放到 16）→ `disableHoverBackground()`（**无悬停背景**，纯图标风格）→ `clickSound(ForgeOfGodsGuiUtil.getButtonSound())`（`tectech:fx_click`）→ `tooltip(...)` 即时显示（`TOOLTIP_DELAY=0`，`BaseTileEntity.java:650`）。
- **纯图标、无文字标签**：文字说明全部走悬停 tooltip（`fog.button.*.tooltip` 键）。
- 图标纹理（`GTGuiTextures`，资源 `gui/overlay_button/<name>`）：里程碑=`flag`、燃料=`heat_on`、电池=`battery_on/battery_off`（动态切换）、星星=`rainbow_spiral`、升级树=`arrow_blue_up`、刷新=`cyclic_blue`、统计=`statistics`、碎片=`eject/eject_disabled`、感谢=`heart`、logo=`gui/picture/gorge_logo`；按钮底=`gui/button/celestial`（canApplyTheme）。
- 特殊按钮行为：
  - 电池按钮（:234-266）：**左键=切换充电开关**（`BATTERY_CHARGING` 同步值，非开窗）；**右键+REC 升级已解锁=开关电池配置窗口**。
  - 碎片喷出按钮（:342-362）：`setEnabledIf($ -> data.isUpgradeActive(END))`（END 升级前禁用），toggle `SHARD_EJECTION` 同步值，overlay 动态 ON/LOCKED。

#### 5.1.3 窗口绑定机制（每个按钮开独立窗口）

- 窗口 id：`Panels` 枚举（G:`sync/Panels.java:28-60`），id 字符串 = `"fog.panel." + name().toLowerCase()`（:64）：
  - 主面板按钮：`fog.panel.milestone`（里程碑 400×300）、`fog.panel.fuel_config`、`fog.panel.battery_config`、`fog.panel.star_cosmetics`、`fog.panel.upgrade_tree`（升级树 300 宽 × 957 高滚动）、`fog.panel.statistics`、`fog.panel.general_info`（右下角 logo）、`fog.panel.special_thanks`（左下角爱心）
  - 子窗口（由上述窗口再打开）：`fog.panel.individual_milestone`、`fog.panel.custom_star_color`、`fog.panel.star_color_import`、`fog.panel.individual_upgrade`、`fog.panel.manual_insertion`（材料投入）；模块侧 `fog.panel.exotic_inputs_list`、`fog.panel.exotic_possible_inputs_list`、`fog.panel.plasma_debug`
- 绑定：`Panels.MILESTONE.getFrom(Panels.MAIN, hypervisor)` → `syncManager.syncedPanel(panelId, true, supplier)`（:86-104，draggable=true，supplier 惰性建面板）——**服务端与客户端同名注册、数据经 PanelSyncManager 同步**。
- 点击行为：`onMousePressed` 内 `isPanelOpen() ? closePanel() : openPanel()`（**toggle**，如 :202-208）。
- 服务端动作仍走 `SyncActions`（COMPLETE_UPGRADE/RESPEC/PAY 等，G:`sync/SyncActions.java:25-50`）。

#### 5.1.4 横排按钮行（panelGap）与角落按钮

- panelGap 行（G:`MTEForgeOfGodsGui.java:95-107`）：`Flow.row().collapseDisabledChild().fullWidth().paddingRight(6).paddingLeft(5).childPadding(2).height(20)`——3 个 16×16 按钮**横排**在终端文本框与物品栏之间：模块刷新（`STRUCTURE_UPDATE` toggle，:307-321）、统计窗口（:323-340）、碎片喷出（:342-362）。
- muffle 按钮（:183-194）：7×7、`top(8).right(8)`、overlay `GODFORGE_SOUND_ON/OFF`。
- 右下角：一般信息按钮 = `PICTURE_GODFORGE_LOGO` 图片按钮（无 size 指定，按图缩放），边距 6px（:364-380）；左下角：特别感谢 16×16 爱心（:382-399）。
- 主面板"顶部数据行"：Godforge 覆写 `createTerminalTextWidget`（:127-162）在终端文本框内渲染 2 行（电池/燃料标签 + 数值，白色 `GTWidgetThemes.DISPLAY_TEXT_WHITE`，居中）——**数据在文本框内，与按钮列互不重叠**。

#### 5.1.5 ECO（MUI1）照搬映射（衔接 t54 现有实现）

| 神锻炉（MUI2） | ECO（MUI1，`MTEEcalArray.addUIWidgets` 已有范式） |
|---|---|
| `syncedPanel("fog.panel.x", true, supplier)` | `buildContext.addSyncedWindow(WINDOW_ID, this::createXxxWindow)`（t54 已实现里程碑窗口，:1469） |
| `IPanelHandler.openPanel()/closePanel()` | `widget.getContext().openSyncedWindow(WINDOW_ID)`（服务端侧执行，:1471-1475）+ `ButtonWidget.closeWindowButton` 返回 |
| 按钮 `size(16)` + 32×32 主题底 + overlay 图标 | `new ButtonWidget().setBackground(() -> new IDrawable[]{ TecTechUITextures.BUTTON_STANDARD_LIGHT_16x16 }).setSize(16,16)`（现有 t54 按钮，:1477-1480） |
| `DynamicDrawable` 状态图标 | `setBackground(Supplier<IDrawable[]>)` + `FakeSyncWidget.BooleanSyncer` 回写（现有电池/碎片同型） |
| 主面板坐标（右缘竖排、底对齐） | ECO 主面板 198×192；**建议照搬参数**：按钮 16×16、间距 3px、贴右缘 3px、靠底（当前单按钮在 (10,110)，文字标签在 (30,110)——可扩展为竖排按钮列，或按神锻炉放右下角参数条右侧） |
| 纯图标 + tooltip | 保持 ECO 风格：图标 + 右侧文字标签（现有 t54 即图标+文字，可继续） |

#### 5.1.6 可照搬参数速查表

| 参数 | 值 | 出处 |
|---|---|---|
| 主面板尺寸 | 198×(181+gap)，gap=20 → 198×201 | `MTEMultiBlockBaseGui.java:199-209`、Godforge :179-181 |
| 按钮列宽 / 距右缘 | 18px / 3px（`leftRel(1,-3,1)`） | `MTEForgeOfGodsGui.java:83-85` |
| 按钮尺寸 / 间距 | 16×16 / childPadding 3px | :86、:198 |
| 按钮对齐 | 靠底（`mainAxisAlignment(END)`）；基类默认另有 `reverseLayout(true)` | :87；`MTEMultiBlockBaseGui.java:1145-1146` |
| gap 行高度 / 内边距 / 间距 | 20px / 左5 右6 / 2px | `MTEForgeOfGodsGui.java:96-107` |
| 终端文本框尺寸 | 190×174（无玩家物品栏时） | `MTEMultiBlockBaseGui.java:263-277` |
| 角落按钮边距 | 右下/左下 6px | :236-261 |
| muffle 按钮 | 7×7，top 8 / right 8 | `MTEForgeOfGodsGui.java:183-194` |
| 悬停背景 / tooltip 延迟 / 点击音 | 禁用 / 0ms / `tectech:fx_click` | :201、`BaseTileEntity.java:650`、`ForgeOfGodsGuiUtil.java:17-31` |
| 窗口 id 前缀 | `fog.panel.<name>`（MUI1 用自定义 WINDOW_ID 常量） | `Panels.java:64` |

### 5.2 升级树 GUI 深扒（ECO 对齐参考，T67）

> 源码：`G:panel/UpgradeTreePanel.java:40-321`（总览窗口）、`G:panel/IndividualUpgradePanel.java:31-256`（详情窗口）、`G:panel/ManualInsertionPanel.java:35-242`（材料投入窗口）、`G:data/UpgradeColor.java:7-70`（连线颜色）、`G:sync/SyncValues.java:126-130`（跨面板选中值）、`G:sync/Panels.java:28-116`（窗口注册）。MUI2 为 modularui2-2.3.79；MUI1 对应 API 见 §5.2.7（已用 javap 核对 modularui-1.3.4 `ModularUIContext`）。

#### 5.2.1 总览窗口（升级树本体）

- **面板尺寸**：300×300（`SIZE=300`，UpgradeTreePanel.java:42）；背景 `GTGuiTextures.BACKGROUND_STAR` = 资源 `gui/background/star`（GTGuiTextures.java `BACKGROUND_STAR`）；`disableHoverBackground()`；右上关闭按钮 `ForgeOfGodsGuiUtil.panelCloseButton()`（`gui/button/transparent_x_10x10`，ForgeOfGodsGuiUtil.java:34-41）。
- **滚动视口**：`ScrollWidget` 292×292 可视（`OFFSET_SIZE=292`）、滚动总量 957px（`SCROLL_SIZE=957`，:43-44,64-66）；`VerticalScrollData.setScrollSize(957)`。
- **节点按钮**：`BUTTON_W×BUTTON_H = 40×15`（:46-47），坐标 = 每个升级枚举的 `treePos(x,y)`（ForgeOfGodsUpgrade.java 各 `.treePos(...)`，起点 START(126,56)，终点 END(126,798)，最深 SEDS→PA→CD→TSE→TBF→EE→END 至 y=888）；按钮顺序 `Arrays.stream(VALUES).sequential()`（:107-110）。
- **按钮底**：未激活 `BUTTON_SPACE_32x32`？实际是 **`gui/button/purple`（BUTTON_SPACE_32x16）** / 激活 `gui/button/purple_pressed`（BUTTON_SPACE_PRESSED_32x16），`DynamicDrawable` 按 `isUpgradeActive` 切换（:139-149）。
- **节点文字渲染**（:146-149）：按钮 `overlay` 叠加两个 drawable——①DynamicDrawable 按钮底 ②`IKey.lang(upgrade.getShortNameKey()).style(EnumChatFormatting.GOLD).scale(0.8f).alignment(Alignment.CENTER)`。即**文字是"按钮 overlay 上的文本 drawable"，金橙色（GOLD）、0.8 倍缩放、水平居中**；无独立 LabelWidget，靠 overlay 多 drawable 叠加实现。文字来源 = lang 键 `fog.upgrade.tt.short.<ordinal>`（短名：START/IGCC/STEM/CFCE/GISS/FDIM/SA/GPCI/REC/GEM/CTCDD/QGPIU/SEFCP/TCT/GGEBE/TPTP/DoP/CNTI/EPEC/IMKG/NDPE/PoS/DoR/NGMS/SEDS/PA/CD/TSE/TBF/EE/END，assets/tectech/lang/en_US.lang）。
- **点击行为**（:150-184）：左键=开详情窗口（`UPGRADE_CLICKED.setValue(upgrade)` + `REFRESH_DYNAMIC` 动作刷新详情 + 打开 `INDIVIDUAL_UPGRADE`）；**Shift+左键=直接完成**（无材料需求时 `COMPLETE_UPGRADE`，有材料需求时开材料窗口 `MANUAL_INSERTION` 并关详情）；右键=重设 `RESPEC_UPGRADE`；tooltip = 升级全名（`getNameKey`），`TOOLTIP_DELAY` 即时。
- **隐藏升级**（:225-269）：START 左侧 (66,56) 的 40×15 彩蛋按钮 + 20×6 `PICTURE_UPGRADE_CONNECTOR_BLUE_OPAQUE` 连接线，`SECRET_UPGRADE` 布尔同步值，无服务端效果。
- 调试区（DEBUG_MODE 时，:271-320）：重置升级/碎片数量输入（0-112）/全部解锁按钮。

#### 5.2.2 连线绘制（激活实线 vs 未激活半透明）

- 33 条连线硬编码（:69-104）：`createConnectorLine(color, from, to, hypervisor)`，颜色按里程碑主题分组——主线 BLUE、双前置节点 RED（GISS→REC、GPCI→REC、SA→CTCDD、GPCI→CTCDD）、分支 ORANGE（TCT 链）/PURPLE（SEFCP 链）/GREEN（GGEBE 链）。
- **实现**（:190-223）：
  - 线宽 **6px**；线长 = 两端点（`treePos` + 按钮半尺寸 20×7.5 的**中心点**）欧氏距离；位置 = 中点；旋转角 = `atan2(dy, dx) - π/2`（RotatedDrawable 旋转弧度，:202-203）。
  - 纹理选择（:213-219）：`from` 与 `to` **都已激活 → `color.getOpaqueConnector()`（不透明实线）**；否则 `color.getConnector()`（半透明纹理，`nonOpaque()` 构建）。`DynamicDrawable` 每帧求值。
- 纹理资源（GTGuiTextures + UpgradeColor.java:11-39）：`gui/picture/connector_{blue,purple,orange,green,red}`（半透明）+ `gui/picture/connector_{color}_opaque`（不透明）；颜色光环 `gui/background/{blue,purple,orange,green,red}_glow`（详情窗口 overlay 用）、符号叠层 `gui/picture/overlay_{color}`（nonOpaque）。

#### 5.2.3 详情窗口（单个升级）

- **尺寸动态切换**：`panel.size(upgrade.getPanelSize())`——`PanelSize.STANDARD(250, 80, 115)` / `LARGE(300, 55, 170)`（面板宽 / body 文本高 / lore 文本高；START 与 END 用 LARGE，其余 STANDARD；ForgeOfGodsUpgrade.java:476-490）；背景 = 升级色 `getBackground()`（`gui/background/{色}_glow`）+ 中央符号 `getSymbol()`（里程碑图标按宽高比 `size/2 × size/2*ratio`，居中）+ overlay 光环 `getOverlay()`（`gui/picture/overlay_{色}`，size/2 居中）（IndividualUpgradePanel.java:47-52, 81-93）。
- **文本布局**（Flow.column，size-16 × size-26，marginTop 15，:95-124）：①标题 `IKey.lang(getNameKey())` GOLD 居中；②正文 `IKey.lang(getBodyKey())` WHITE 居中、固定高 bodySize、marginTop 7；③传说 `IKey.lang(getLoreKey())` ITALIC、颜色 0xFFBBBDBD、固定高 loreSize、marginTop 5。lang 键：`fog.upgrade.tt.<ordinal>`（全名）/`fog.upgrade.text.<ordinal>`（正文）/`fog.upgrade.lore.<ordinal>`（传说）。
- **底部行**（bottomRel 0，:126-254）：左侧"碎片成本"文本（70×15、scale 0.7、BLUE 数字，`gt.blockmachines.multimachine.FOG.shardcost`）；右侧"可用碎片"文本（70×15、scale 0.7、**足够=GREEN / 不够=RED**）；中间按钮行 78×15：
  - **材料需求按钮** 15×15（:172-208）：动态底 `gui/button/boxed_checkmark`（已付清）/ `gui/button/boxed_exclamation_point`（未付清），点击 → **关升级树 + 关当前详情 + 开材料窗口**（:181-190）；`setEnabledIf(upgrade.hasExtraCost())`（无材料成本的升级不显示）。
  - **确认/重设按钮** 40×15（:211-250）：底 `gui/button/transparent_16x16`（未激活，显示 "Confirm"）/ `gui/button/transparent_pressed_16x16`（已激活，显示 "Respec"），文字 scale 0.7 居中；点击 → `COMPLETE_UPGRADE`（激活）或 `RESPEC_UPGRADE`（重设）。
- 同步值（:69-74）：`AVAILABLE_GRAVITON_SHARDS`、动作 `COMPLETE_UPGRADE`/`RESPEC_UPGRADE`；选中节点来自树面板 `SyncValues.UPGRADE_CLICKED.lookupFrom(Panels.UPGRADE_TREE, ...)`（:39-40，**跨面板共享 EnumSyncValue**）。

#### 5.2.4 材料投入窗口

- **尺寸与定位**：190×115，`relative(mainPanel)` + `leftRelOffset(0,4)` + `topRelOffset(0,3)`（**相对主面板左上角 (4,3) 偏移叠加**，ManualInsertionPanel.java:50-53）；背景 `BACKGROUND_STANDARD`；标题 "支付升级材料"（`gt.blockmachines.multimachine.FOG.payUpgradeCosts`，:71-77）。
- **12 个需求槽**（:79-107,148-212）：3 列 × 4 行（每行 36×18 = 18px 槽 + 18px 数量文本）；槽 = `SlotLikeButtonWidget`（**点击查 NEI 配方 GuiCraftingRecipe / 用途 GuiUsageRecipe**，:164-175）；数量文本 = `"x" + (所需 − 已付)`，颜色**红=0 已付 / 黄=部分 / 绿=付清**（:184-205）；付清后显示 `gui/picture/green_checkmark`（GREEN_CHECKMARK_11x9，11×9）；无材料成本的升级显示禁用槽 `gui/button/standard_disabled`（18×18）。
- **16 格暂存窗口**（:230-241）：`SlotGroupWidget` matrix 4×4（"ssss"×4），槽 = `ModularSlot(upgradeWindowHandler, i)` 绑定服务端 16 槽 `ItemStackHandler`（`storedUpgradeWindowItems`，ForgeOfGodsData.java:87-88），slot group 同步名 `"item_inv_manual_insertion"`（:43,145）。
- **「消耗材料」按钮**（:118-136）：180×18 通栏，点击 → `PAY_UPGRADE_COST` 服务端动作（`payCost` 从 16 槽消费匹配材料并记入 `amountsPaid[12]`，UpgradeStorage.java:41-83）。
- **关闭恢复**（onCloseAction，:58-68）：关材料窗口时**自动重开升级树 + 详情窗口**（恢复层级）。

#### 5.2.5 窗口层级机制（无 hide API，纯状态机）

- MUI2 面板经 `syncManager.syncedPanel(panelId, draggable=true, supplier)` 注册（Panels.java:97-103），`IPanelHandler` 提供 `isPanelOpen()/openPanel()/closePanel()/closeIfOpen()`。
- **打开材料窗口的时序**（三处入口统一"关前窗"）：
  1. 详情窗口点材料按钮（IndividualUpgradePanel.java:181-190）：`upgradeTreePanel.closeIfOpen()` → 当前详情 `closeIfOpen()` → `manualInsertionPanel.openPanel()`；
  2. 树窗口 Shift+左键未付材料节点（UpgradeTreePanel.java:153-169）：设 `UPGRADE_CLICKED` → `manualInsertionPanel.openPanel()` → 详情 `closePanel()` → 树 `closeIfOpen()`；
  3. 主面板按钮只 toggle 树/详情（MTEForgeOfGodsGui.java:202-208 等，主面板**始终保持打开**，多窗口同屏叠加）。
- **恢复**：材料窗口 `onCloseAction` 重开树 + 详情（ManualInsertionPanel.java:58-68）；详情窗口右上 `panelCloseButton`（`ButtonWidget.panelCloseButton()` 标准关闭，ForgeOfGodsGuiUtil.java:34-41）直接关自己。
- 即：**没有 windowManager.hide/show 类 API，用"关旧窗 + 开新窗 + 关闭回调重开"的状态机**；窗口之间传值靠共享 `SyncValue`（UPGRADE_CLICKED/MILESTONE_CLICKED 跨面板 lookupFrom）。
- 树/详情为独立（居中）面板，材料窗口相对主面板 (4,3) 定位，可拖动（draggable=true）。

#### 5.2.6 图标/纹理资源清单（资源根 = `assets/gregtech/textures/`，MODID=gregtech）

| 用途 | 路径 | 常量 |
|---|---|---|
| 升级树面板背景 | `gui/background/star` | `BACKGROUND_STAR` |
| 里程碑面板背景 | `gui/background/space` | `BACKGROUND_SPACE` |
| 详情面板光晕（按升级色） | `gui/background/{blue,purple,orange,green,red,white}_glow` | `BACKGROUND_GLOW_*` |
| 详情符号叠层 | `gui/picture/overlay_{blue,purple,orange,green,red}`（nonOpaque） | `PICTURE_OVERLAY_*` |
| 连线（半透明） | `gui/picture/connector_{blue,purple,orange,green,red}`（nonOpaque） | `PICTURE_UPGRADE_CONNECTOR_*` |
| 连线（激活不透明） | `gui/picture/connector_{color}_opaque` | `*_OPAQUE` |
| 节点按钮底/按下 | `gui/button/purple` / `gui/button/purple_pressed` | `BUTTON_SPACE_32x16` / `_PRESSED` |
| 材料需求按钮 | `gui/button/boxed_checkmark` / `gui/button/boxed_exclamation_point` | `BUTTON_BOXED_CHECKMARK_18x18` / `_EXCLAMATION_POINT_18x18` |
| 确认/重设按钮底 | `gui/button/transparent_16x16` / `gui/button/transparent_pressed_16x16` | `BUTTON_OUTLINE_HOLLOW[_PRESSED]` |
| 禁用槽 | `gui/button/standard_disabled`（18×18 adaptable） | `BUTTON_STANDARD_DISABLED` |
| 已付清勾 | `gui/picture/green_checkmark`（11×9） | `GREEN_CHECKMARK_11x9` |
| 关闭按钮 | `gui/button/transparent_x_10x10` | `CLOSE_BUTTON_HOLLOW` |
| 主面板图标 | `gui/overlay_button/{flag,heat_on,battery_on,battery_off,rainbow_spiral,arrow_blue_up,cyclic_blue,statistics,eject,eject_disabled,heart,sound_on,sound_off}` + `gui/button/celestial` + `gui/picture/gorge_logo` | `TT_OVERLAY_*` / `TT_BUTTON_CELESTIAL_32x32` / `PICTURE_GODFORGE_LOGO` |

#### 5.2.7 ECO（MUI1）逐项映射建议表

> MUI1（modularui-1.3.4）已核对 API：`ModularUIContext.openSyncedWindow(int)` / `isWindowOpen(int)` / `closeWindow(int|ModularWindow)` / `closeAllButMain()` / `getMainWindow()` / `openWindow(IWindowCreator)`（javap 实测）；**无 IPanelHandler/isPanelOpen/closeIfOpen**——层级状态机需用 `isWindowOpen` + 显式 close/open 手写。ECO 现有范式：`MTEEcalArray.addUIWidgets` 的 `buildContext.addSyncedWindow(WINDOW_ID, this::createXxxWindow)` + 服务端 `openSyncedWindow` + `ButtonWidget.closeWindowButton(false)`（t54，1469-1584 行）。

| 神锻炉（MUI2）细节 | ECO（MUI1）映射 | 照搬难度 |
|---|---|---|
| 总览窗口 300×300 + 292×957 滚动 | `ModularWindow.builder(300, 300)` + MUI1 `Scrollable`（ECO 已用 `Scrollable` 于主面板文字屏）；滚动内容高度 957 可照搬 | 直接照搬 |
| 节点按钮 40×15 + `treePos` 坐标表 | `ButtonWidget().setPos(x,y).setSize(40,15)`，坐标直接抄 31 节点表（或 MVP 用 3~6 节点子集） | 直接照搬 |
| 节点文字 = overlay 多 drawable（IKey GOLD 0.8f 居中） | MUI1 无按钮 overlay 文字 API → 用 `TextWidget` 叠在按钮同坐标（`.setPos(x+?，y+?)` 偏移居中）或 `ButtonWidget.setOverlay(IconDrawable)` + 文字放按钮下方（ECO 现有 t54 就是按钮+右侧文字标签风格，建议沿用：图标按钮 + 右侧/下方短名文字） | 需 MUI1 适配 |
| 连线 RotatedDrawable 旋转 + 6px + OPAQUE 切换 | MUI1 无 RotatedDrawable → 两个方案：①自绘（Tessellator 斜线/虚线，MVP 性价比低）；②**预先旋转好的静态连线纹理**（每色 45° 整数角几档，或放弃旋转用 L 形折线贴图）；MVP 建议先**无连线**（按钮坐标即表达层级），P2 再加 | 需自绘/简化 |
| 详情窗口动态尺寸 250/300 + glow 背景 | `builder.setBackground()` 可用（MUI1 支持）→ 250×N 固定窗口 + 滚动正文即可；glow 背景用 `TecTechUITextures` 或自绘 | 基本照搬（尺寸固定化） |
| 材料需求按钮 boxed_checkmark/exclamation + NEI 查询 | MUI1 `ButtonWidget.setBackground(Supplier)` 支持动态底；NEI 查询 = MUI1 无 `GuiCraftingRecipe` 直接调用（NEI 1.7.10 API 可用，`GuiCraftingRecipe.openRecipeGui` 在 1.7.10 同样存在，需 compileOnly 依赖 NEI） | 需 MUI1 适配 |
| 16 格暂存 + 12 槽 + 已付红/黄/绿 | MUI1 `SlotWidget` 网格照搬；`amountsPaid` 短数组已付逻辑与颜色规则（红=0/黄=部分/绿=付清）为纯业务，直接照搬 | 直接照搬 |
| 窗口层级（关旧开新 + onCloseAction 恢复） | MUI1 手写：`context.isWindowOpen(ID)` 判断 → `closeWindow` + `openSyncedWindow`；"关闭材料窗自动回树/详情" = 在材料窗口的关闭按钮/返回按钮处理器里显式重开（MUI1 无 onCloseAction，用关闭按钮回调代替） | 需 MUI1 适配（机制等价） |
| 跨面板选中值（UPGRADE_CLICKED lookupFrom） | MUI1 用 `FakeSyncWidget` 回写共享字段（ECO 现有 sync* 字段模式）——打开详情前把 `selectedUpgrade` 字段写入，详情窗口直接读 | 直接照搬（改字段模式） |
| 全部纹理 | ECO 为原创贴图（GPL 合规），`gui/background/star` 类资源需**自绘等效**（深蓝星云/网格背景 + 16×16 图标），参数（尺寸/坐标/颜色）照搬 | 需自绘贴图 |

- **MVP 建议顺序**（供 T67）：①主面板按钮列（§5.1）→ ②总览窗口（300×300 + Scrollable + 节点按钮 40×15 + 文字标签）→ ③详情窗口（250 固定 + 标题/正文/传说 + 确认/重设按钮）→ ④材料窗口（12 槽 + 16 暂存 + 消耗按钮 + 红黄绿）→ ⑤连线（P2 可选）。窗口 id 用 ECO 命名空间（如 `eco.calc.window.upgrade_tree`）。

---

## 6. 与玩家进度的关系（为什么有深度）

1. **单机长期养成**：一个机器吃掉 UIV~UXV 整个毕业段的材料（升级成本表 §4.3），且**不消耗常规 EU**（模块走无线网络，玩家已有电力体系直接供给），把"能量→碎片→升级→更强处理→更多碎片"做成自洽闭环。
2. **双轨进度**：里程碑（用得多 = 碎片多）与升级树（碎片花在哪 = 能力树）分离，天然形成"使用深度"与"选择深度"；反转模式让毕业机器仍有无限成长空间（碎片收益随里程碑等级二次方增长）。
3. **结构性解锁**：环数（1→2→3）既是视觉奇观（巨型渲染动画），又是硬性能力上限（模块槽 + 拆分分支数 + 电压 ×4^环），对应 GTNH 的"阶段推进"哲学。
4. **风险与反作弊**：燃料必须持续供应（电池耗尽停机）、模块配方热量不匹配即断开——机器"活着"需要持续运营，不是一次建成永久收益。

---

## 7. 可移植到 ECO（E-Calculator）的机制要点摘录

### 7.1 值得借鉴的设计

| 机制 | 诸神之锻炉做法 | ECO 移植建议 |
|---|---|---|
| 货币与里程碑分离 | 碎片（产出端）≠ 升级（消费端），4 条平行计量线（电/配方/燃料/结构） | ECO 可用"计算用量"（任务数/字节流转/耗电）作为 4 类里程碑计量，产物 = "算力货币" |
| 幂律里程碑 | 对数阶梯 9^lvl/4^lvl/3^lvl，低阶快、高阶慢，天然形成节奏 | ECO C4/C6/C9 档位可改为"里程碑等级驱动解锁"，而非纯材料合成——用机量解锁高级晶阵/线程 |
| 反转/二周目 | 4 里程碑全满 → 反转，需求 ×9^6 等比重开，UI 双进度条 | ECO 可做"超频模式"：全部里程碑满级后解锁（如 +10% 字节红线放宽、超线程 10% 免额） |
| 升级树 + 重设 | 31 节点 DAG、碎片成本、respec 退币（依赖校验） | ECO 若做分支（如线程优先 vs 并行优先），可借鉴 checkDependents 的退点校验 |
| 材料成本分步支付 | 12 槽 × 已付 short 数组、NEI 假配方展示、16 格暂存窗口 | ECO 大升级（如 L9 主机）用投料暂存+分步支付，避免一次性大额材料交互 |
| 结构与能力联动 | 环数 = 槽位/分支/电压倍率，拆环自动降级 | ECO 扩展段数（1~12）联动线程/并行上限——已有雏形，可加"档位回退" |
| 统计可视化 | 统计面板实时预览 7 项指标 + 燃料系数滑条 | ECO GUI 可加"预测面板"：拖动并行数/字节量预览任务吞吐 |

### 7.2 与 ECO 现有 C4/C6/C9 档位的关系思考

- 现状（参考仓库 1.12.2）：C4/C6/C9 是**材料/等级静态门禁**（B 晶阵需 L6+、C 晶阵需 L9，见 `docs/ECALCULATOR_RESEARCH.md` §1.2/§2.3），档位不随使用增长。
- 可移植的演进：把"档位"从**静态购买**改为**里程碑驱动的渐进解锁**——例如：晶阵等级解锁 = 累计处理字节数/任务数的里程碑等级；L9 主机 = START 级材料 + 里程碑门槛 + 材料成本（借鉴 §4.3 的大升级模式）；超线程 +10% 可作为"反转奖励"。
- 注意 ECO 的定位差异：诸神之锻炉是**单一毕业机器**（碎片仅内部使用），ECO 是**可扩展计算子系统**（面向 GTNH 全阶段）；建议只借鉴"里程碑→货币→升级树"的三层模型，数值体系按 ECO 自身（字节/并行/线程）重设计，避免把碎片等魔法概念生搬硬套（ECO 已定"量子计算机同款 MUI1 + 星蓝主题"，见 `docs/DESIGN.md`）。

### 7.3 不推荐照搬的点

- 3 环巨型结构 + 122 格渲染动画（实现成本高，与 ECO 的 GT 多方块 + TESR 风格不匹配）；
- 无线 EU 网络供电（ECO 沿用 E-Storage 的接口供电范式即可）；
- 31 节点大树的实现复杂度（ECO MVP 建议 3~6 个里程碑驱动节点起步，详见 t4 方案）。

---

## 8. 证据索引（源码 → 机制 → 行号）

| 机制 | 关键文件:行号 |
|---|---|
| 主机 tick / 燃料 / 模块连接 | T:`MTEForgeOfGods.java:375-483` |
| 燃料抽取与消耗 | T:`MTEForgeOfGods.java:485-534`；公式 T:`GodforgeMath.java:21-35` |
| 里程碑判定（4 公式） | T:`GodforgeMath.java:342-434`；常量 T:`ForgeOfGodsData.java:34-50` |
| 反转判定 | T:`MTEForgeOfGods.java:872-881` |
| 碎片产出/喷出/注入 | T:`MTEForgeOfGods.java:939-964`；T:`ForgeOfGodsData.java:358-380` |
| 升级树定义（31 节点/成本/前置/拆分） | T:`ForgeOfGodsUpgrade.java:24-277` |
| 升级存储/校验/重设/NBT | T:`UpgradeStorage.java:28-285` |
| 大升级材料成本 | L:`Godforge.java:688-765`；NEI 假配方 L:`784-844` |
| 模块解锁门禁 | T:`GodforgeMath.java:302-322`；模块基类 T:`MTEBaseModule.java:47-467` |
| 模块配方表 | L:`Godforge.java:846-898`；T:`MTESmeltingModule.java`（furnace/blast）、`MTEMoltenModule.java`、`MTEPlasmaModule.java`、`MTEExoticModule.java` |
| 升级名/正文/传说（31×4 键） | `assets/tectech/lang/en_US.lang`（fog.upgrade.*，223 个 fog 键） |
| 里程碑 UI/阈值/碎片 UI 公式 | G:`data/Milestones.java:13-71`、`panel/IndividualMilestonePanel.java:197-240` |
| 升级树 GUI（含 §5.2 深扒：背景/节点文字/连线/详情/材料/层级） | G:`panel/UpgradeTreePanel.java:40-321`、`IndividualUpgradePanel.java:31-256`、`ManualInsertionPanel.java:35-242`、`data/UpgradeColor.java:7-70`、`sync/Panels.java:28-116` |
| 服务端动作（完成/重设/支付） | G:`sync/SyncActions.java:25-50` |
| 同步值全集（38 个） | G:`sync/SyncValues.java` |
| 服务端环境证据 | `M:\AA科技\GTNH\服务端\logs\fml-server-latest.log`（kubatech/tectech 自 gregtech jar 加载；`Godforge blocks registered.`） |
| 配置 | `M:\AA科技\GTNH\服务端\config\kubatech\kubatech.cfg`（无 godforge 项） |
| 网络资料 | [灰机wiki：诸神之锻炉](https://gtnh.huijiwiki.com/p/62607)、[灰机wiki：升级节点](https://gtnh.huijiwiki.com/wiki/诸神之锻炉升级节点)、[namu.wiki EN](https://en.namu.wiki/w/신들의%20제련소(GTNH))、[GTNH issue #19814](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/19814)、[B站 GTL S2](https://www.snm0516.aisee.tv/video/BV1mskEBTE73/)（直接抓取均被 403 反爬拦截，引用以源码为准） |

*调研完成于阶段 1（t47）。配套：`docs/ECALCULATOR_RESEARCH.md`（t1 参考仓库原理）、`docs/ECALCULATOR_PORT_PLAN.md`（t4 移植方案，可引用本报告 §7 设计要点）。*
