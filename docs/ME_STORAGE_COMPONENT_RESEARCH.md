# GTNH-AE（AE2U）存储组件与存储外壳配方研究

> 供 ECO 模组设计"ECO ME 存储组件（27 种）与存储外壳（9 种）"中间材料的参考。
> 数据来源：GTNH 中文维基配方数据库（灰机 wiki，数据库版本 GTNH/2.9.0-beta-1）CustomSearch 实证渲染 + AE2U rv3-beta-998-GTNH 源码（MaterialType.java 枚举）+ GT5 源码（gt5-src）。
> 核心结论：GTNH-AE 的存储组件走 **电路组装机 + 超净间**（单步直出）与 **工作台有序合成**（逐级升级）两条路线；材料递进 = 处理器核心 + 电路板 + GT 电路基板 + 72L 焊锡合金 + 编程电路1，电压每档 ×4（LV 30 → UV 491,520 EU/t）。

---

## 1. 物品编号映射（AE2U MaterialType 枚举实证）

| 物品 | ItemMultiMaterial meta | 英文枚举 |
|---|---|---|
| 1k-ME存储组件 | 35 | Cell1kPart |
| 4k-ME存储组件 | 36 | Cell4kPart |
| 16k-ME存储组件 | 37 | Cell16kPart |
| 64k-ME存储组件 | 38 | Cell64kPart |
| 256k-ME存储组件 | 57 | Cell256kPart |
| 1024k-ME存储组件 | 58 | Cell1024kPart |
| 4096k-ME存储组件 | 59 | Cell4096kPart |
| 16384k-ME存储组件 | 60 | Cell16384kPart |
| ME存储外壳（普通） | 39 | EmptyStorageCell |
| ME高级存储外壳 | 61 | EmptyAdvancedStorageCell |

流体组件（ae2fc mod，meta 不确定）：1k~16384k 共 8 档 + 多流体 8 档；源质组件（thaumicenergistics，storage.component）：1k~16384k 共 8 档。

---

## 2. 物品存储组件——电路组装机配方（GTNH 覆写，实证）

统一特征：**需要超净间**（256k 起全部标注"需要超净间"；1k/4k 未标）、**72L 焊锡合金**、**编程电路1 (NC)**、**10 秒**。

| 组件 | 电路输入（2 种电路） | 处理器核心 | GT 电路基板 | 电压 | 功率 EU/t | 总 EU |
|---|---|---|---|---|---|---|
| 1k (35) | ULV电路 + 充能赛特斯石英粉 + 金核心逻辑处理器 + gt.metaitem.01:32710（光学贴片二极管） | 金核心逻辑处理器 | —（无基板，ULV 路线） | LV | 30 | 6,000 |
| 4k (36) | LV电路 + ULV电路 | 金核心逻辑处理器 (LogicProcessorItemGoldCore) | gt.metaitem.03:32100 | LV | 30 | 6,000 |
| 16k (37) | MV电路 + LV电路 | 钻石核心工程处理器 (EngineeringProcessorItemDiamondCore) | gt.metaitem.03:32101 | MV | 120 | 24,000 |
| 64k (38) | HV电路 + MV电路 | 钻石核心工程处理器 | gt.metaitem.03:32102 | HV | 480 | 96,000 |
| 256k (57) | EV电路 + HV电路 | 绿宝石核心工程处理器 (EngineeringProcessorItemEmeraldCore) | gt.metaitem.03:32103 | EV | 1,920 | 384,000 |
| 1024k (58) | IV电路 + EV电路 | 绿宝石核心工程处理器 | gt.metaitem.03:32104 | IV | 7,680 | 1,536,000 |
| 4096k (59) | LuV电路 + IV电路 | 高级绿宝石核心工程处理器 (EngineeringProcessorItemAdvEmeraldCore) | gt.metaitem.03:32105 | LuV | 30,720 | 6,144,000 |
| 16384k (60) | UV电路 + LuV电路 | 高级绿宝石核心工程处理器 | gt.metaitem.03:32107 | UV | 491,520 | 98,304,000 |

**1k 特殊**：1k 为起点配方，输入 = ULV电路 + 充能赛特斯石英粉 + 金核心逻辑处理器 + gt.metaitem.01:32710（光学贴片二极管），无电路基板，LV 30 EU/t。

**材料递进规律（电路组装机路线）**：
- 电路板：ULV → LV → MV → HV → EV → IV → LuV → UV（每档用"本级电路 + 上一级电路"两条）
- 处理器核心：金核心（1k/4k）→ 钻石核心（16k/64k）→ 绿宝石核心（256k/1024k）→ 高级绿宝石核心（4096k/16384k）
- GT 电路基板：32100（电路基板）→ 32101（进阶）→ 32102（高级）→ 32103（精制）→ 32104（精英）→ 32105（超级湿件维生）→ 32107（超生物突变）—— 每档 +1（16384k 直接跳 32107）
- 流体固定 72L 焊锡合金，编程电路 1 固定，耗时恒 10 秒
- 电压每档 ×4：30 → 120 → 480 → 1,920 → 7,680 → 30,720 → 491,520 EU/t

---

## 3. 物品存储组件——工作台有序合成（逐级升级路线）

| 组件 | 四角 ×4 | 四边 ×4 | 中心 ×1 | 输出 |
|---|---|---|---|---|
| 1k (35) | gt.metaitem.03:32075（覆膜电路基板） | 充能赛特斯石英粉 | 金核心逻辑处理器 | 1k |
| 4k (36) | gt.metaitem.01:32701（光学兼容存储器） | 1k组件(35) | 金核心逻辑处理器 | 4k |
| 16k (37) | MV电路 | 4k组件(36) | 钻石核心工程处理器 | 16k |
| 64k (38) | IC2 高级电路板 (itemPartCircuitAdv) | 16k组件(37) | 钻石核心工程处理器 | 64k |
| 256k (57) | EV电路 | 64k组件(38) | 绿宝石核心工程处理器 | 256k |
| 1024k (58) | IV电路 | 256k组件(57) | 绿宝石核心工程处理器 | 1024k |
| 4096k (59) | LuV电路 | 1024k组件(58) | 高级绿宝石核心工程处理器 | 4096k |
| 16384k (60) | UV电路 | 4096k组件(59) | 高级绿宝石核心工程处理器 | 16384k |

**规律**：工作台路线 = 4×上级组件 + 4×本级电路板 + 1×处理器核心。1k 为起点（覆膜电路基板+充能赛特斯石英粉）。

---

## 4. 存储外壳配方（实证）

### 4.1 ME存储外壳（EmptyStorageCell, meta 39）
- **有序合成**：气锤（HV）/螺丝刀（HV）工具 + gt.metaitem.01:17516 + 27516 + 17306×2 + 17019 + TConstruct 玻璃板（GlassPane）→ ME存储外壳。布局约 3×3：`17516 27516 17306 / 玻璃板 17306 27516 / 17019 工具 工具`。
- **组装机**：玻璃板（Minecraft 染色玻璃板 32767 或 TConstruct GlassPaneClearStained）+ 17516 + 17019 + 17306 + 编程电路 → ME存储外壳。**LV 15 EU/t，5 秒，总计 1,500 EU**。
- 用途：可组装 1k~64k 物品存储元件。

### 4.2 ME高级存储外壳（EmptyAdvancedStorageCell, meta 61）
- **有序合成**：气锤（LV）/螺丝刀（LV）+ 17516 + 27516 + 17030×3 + 玻璃板 → ME高级存储外壳。
- **组装机**：玻璃板 + 17516 + 17030 + 27516 + 编程电路3 → ME高级存储外壳。**LV 15 EU/t，5 秒，总计 1,500 EU**。
- 用途：可组装 256k~16384k 物品存储元件。

> 注：gt.metaitem.01:17516 点击实证为赛特斯石英系材料物品（跳转"赛特斯石英"材料页）；27516/17019/17306/17030 为 GT 材料物品（玻璃板+GT 金属/石英系材料组合）。材质主料可概括为 **GT 材料物品 + 玻璃板 + 锤/螺丝刀工具（有序）或 LV 组装机（机器）**。

---

## 5. 流体与源质组件（1k 实证）

### 5.1 1k-ME流体存储组件（ae2fc）
- **有序合成**：gt.metaitem.01:32700（湿件水晶芯片）×4 角 + 充能赛特斯石英粉×4 边 + 流体钻石核心工程处理器 (EngineeringProcessorFluidDiamondCore) 中心 → 1k 流体组件。
- **电路组装机**：ULV电路 + 充能赛特斯石英粉 + gt.metaitem.01:32610 + 流体钻石核心工程处理器 + gt.metaitem.01:32710 + 编程电路1 + 72L → 1k 流体组件。**LV 30 EU/t，10 秒，总计 6,000 EU**（无超净间标注）。

### 5.2 1k-ME源质存储组件（thaumicenergistics）
- **有序奥术合成**：gt.metaitem.01:32700（湿件水晶芯片）×4 角 + 源质脉动核心工程处理器 (EngineeringProcessorEssentiaPulsatingCore) 中心 + 要素（Ignis/Ordo/Aqua 各 1）→ 源质组件。
- **电路组装机**：ULV电路 + 源质脉动核心工程处理器 + gt.metaitem.01:32710 + 编程电路1 + 72L → 源质组件。**LV 30 EU/t，10 秒**。
- 另有"杖端替换/杖柄替换"相关配方（奥术工作台），源质组件亦作为法杖部件材料。

---

## 6. GT 材料编号速查（本地源码实证）

| key | 物品 | 来源 |
|---|---|---|
| gt.metaitem.03:32100 | 电路基板（Basic） | GT 基板页 100电路基板.png |
| gt.metaitem.03:32101 | 进阶电路基板 | 101进阶电路基板.png |
| gt.metaitem.03:32102 | 高级电路基板 | 102高级电路基板.png |
| gt.metaitem.03:32103 | 精制电路基板 | 103精制电路基板.png |
| gt.metaitem.03:32104 | 精英电路基板 | 104精英电路基板.png |
| gt.metaitem.03:32105 | 超级湿件维生电路基板 | 105超级湿件维生电路基板.png |
| gt.metaitem.03:32107 | 超生物突变电路基板 | 107超生物突变电路基板.png |
| gt.metaitem.01:32710 | 光学贴片二极管 (OpticalSMDDiodes) | CircuitWraps.java:76 |
| gt.metaitem.01:32700 | 湿件水晶芯片 (LivingCrystalChips) | CircuitWraps.java:86 |
| gt.metaitem.01:32701 | 光学兼容存储器 (OpticallyCompatibleMemories) | CircuitWraps.java:85 |
| gt.metaitem.01:32610 | 泵盖系物品（成就名 pumpcover） | GTAchievements.java:537 |
| gt.metaitem.01:17516 | 赛特斯石英系材料物品 | CustomSearch 图标点击实证 |
| TConstruct GlassPane | 玻璃板 | 配方实证 |

---

## 7. 对 ECO 设计的映射建议

1. **组件结构**：ECO 27 种盘（k级 256k/1024k/4096k、M级 16M/64M/256M、大M级 1024M/4096M/16384M × 物品/流体/源质）可完全复刻"外壳+组件"两段式：组件 = 处理器核心 + 电路/电路板 + 材料，外壳 = GT 材料 + 玻璃板 + LV 组装机。
2. **档位映射**（队长给定）：k级=EV 钛、16M..256M=ZPM 铱、1024M+=UHV 中子素 —— 与 AE2U 电路组装机电压递进（EV→LuV→UV）对齐即可，材料换成 ECO 专属（钛/铱/中子素）而非 AE2U 的电路基板。
3. **机器**：电路组装机（需要超净间）是 AE2U 权威路线；ECO 可沿用组装机/电路组装机，配方结构"材料×n + 编程电路 + 72L 流体 + 10s"。
4. **外壳**：AE2U 外壳 LV 15 EU/t 5s 极便宜（玻璃+GT材料）；ECO 外壳可提档（L4/L6/L9 对应 EV/ZPM/UHV），把成本大头放在组件而非外壳，维持"外壳便宜、组件贵"的 GTNH-AE 经济学。

---

## 8. 参考链接

- GTNH 中文维基·存储元件总览：https://gtnh.huijiwiki.com/index.php?title=应用能源2/存储元件&curid=358615
- 配方查询（CustomSearch）：https://gtnh.huijiwiki.com/wiki/CustomSearch
- 基板（电路基板全系列）：https://gtnh.huijiwiki.com/index.php?title=基板&action=raw
- AE2U 源码 MaterialType.java：`.research/ae2u-full/Applied-Energistics-2-Unofficial-rv3-beta-998-GTNH/src/main/java/appeng/items/materials/MaterialType.java`
- AE2U 内置原版组件配方（GTNH 已覆写，仅参考）：`.research/ae2u-full/.../recipes/network/cells/storage-components.recipe`
- mcmod 流体外壳：https://www.mcmod.cn/item/777514.html
