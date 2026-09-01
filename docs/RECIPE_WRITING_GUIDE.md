# ECO 配方编写指南（中文）

> 本文档教你怎么在 `src/main/java/ecoaegtnh/recipe/Recipes.java` 里自己加/改配方。
> 所有配方都在 `Recipes.register()` 里注册；改完代码后跑
> `gradlew.bat spotlessApply build --offline` 构建，然后部署两端 jar 并重启。

---

## 1. 三种配方类型

### 1.1 组装机配方（带编程电路）

```java
tryAddAssembler(
    "配方唯一名",                          // 字符串：日志/排查用，别重名
    new ItemStack[] { 输入1, 输入2, ... }, // 物品输入
    new FluidStack[] { 流体1, 流体2 },     // 流体输入
    输出物品,                              // ItemStack
    电路编号,                              // int：编程电路编号（如 1、11、30）
    电压,                                 // long：EU/t（用 TierEU.RECIPE_EV 等常量）
    时长);                                // int：tick（用 10 * SECONDS 表示 10 秒）
```

### 1.2 组装机配方（无编程电路）—— 你上次的配方都用这个

```java
tryAddAssemblerNoCircuit(
    "配方唯一名",
    new ItemStack[] { 输入1, 输入2, ... },
    new FluidStack[] { 流体1 },
    输出物品,
    电压,
    时长);
```

> ⚠️ 别用 `tryAddAssembler(..., 0, ...)` 表示"无电路"——GT 的 `circuit(0)` 会硬塞一个
> 0 号集成电路进去。无电路必须用 `tryAddAssemblerNoCircuit`。

### 1.3 装配线配方

```java
tryAddAL(
    "配方唯一名",
    研究物品,                              // 放扫描器里研究的物品（装配线前置）
    new ItemStack[] { 输入1, ... },        // 固体输入：必须 4~16 个！
    new FluidStack[] { 流体1, ... },
    输出物品,
    扫描电压,                              // 研究扫描时的 EU/t
    装配电压,                              // 装配线运行 EU/t
    时长);
```

> ⚠️ **装配线配方必须 ≥ 4 个固体输入**，否则会被静默丢弃（t105 教训）——输入不够就
> 塞同阶 GT 部件（马达/泵/传感器）凑数。

---

## 2. 电压常量（TierEU）

| 常量 | EU/t | 机器等级 |
|---|---|---|
| `TierEU.RECIPE_LV` | 30 | LV |
| `TierEU.RECIPE_MV` | 120 | MV |
| `TierEU.RECIPE_HV` | 480 | HV |
| `TierEU.RECIPE_EV` | 1920 | EV |
| `TierEU.RECIPE_IV` | 7680 | IV |
| `TierEU.RECIPE_LuV` | 30720 | LuV |
| `TierEU.RECIPE_ZPM` | 131072 | ZPM |
| `TierEU.RECIPE_UV` | 524288 | UV |
| `TierEU.RECIPE_UHV` | 2097152 | UHV |

时长用 `SECONDS`：`10 * SECONDS` = 10 秒 = 200 tick；`MINUTES` 同理。

---

## 3. 物品怎么写进配方（重点：矿词典）

### 3.1 GT 材料（矿词自动展开）—— 最常用

```java
Materials.Titanium.getPlates(6)          // 钛板 ×6（矿词 plateTitanium）
Materials.Titanium.getDust(4)            // 钛粉 ×4（dustTitanium）
Materials.Titanium.getIngots(2)          // 钛锭 ×2（ingotTitanium）
Materials.CertusQuartz.getDust(8)        // 赛特斯石英粉 ×8
Materials.Redstone.getDust(8)            // 红石粉 ×8
Materials.SolderingAlloy.getMolten(576)  // 熔融焊锡合金 576mb（流体）
Materials.Lubricant.getFluid(250)        // 润滑油 250mb（流体）
Materials.Iridium.getMolten(2 * INGOTS)  // 熔融铱 2 锭量（流体）
```

> 矿词的规律：`plate钛`、`dustXX`、`ingotXX`、`frameGtXX`……这些方法拿到的 ItemStack
> 是"矿词代表物品"，放进配方后**自动匹配所有注册同一矿词的物品**（NEI 里显示多个物品）。
> 这就是"矿词典怎么写进去"的核心：**用 `Materials.XXX.getPlates(n)` 这类方法就自动带矿词**。

### 3.2 GT 框架（frameGt 前缀）

```java
GTOreDictUnificator.get(OrePrefixes.frameGt, Materials.Titanium, 1)  // 钛框架 ×1
```

### 3.3 任意矿词名（当 Materials 枚举没有时）

```java
GTOreDictUnificator.get("batteryData", 1)  // 矿词 batteryData 的任意物品 ×1
GTOreDictUnificator.get("circuitMaster", 2)
```

> ⚠️ **t114x 教训：电路板这类输入别用 `GTOreDictUnificator.get`！** 它返回的是该矿词的
> **统一后的单个具体物品**（比如 circuitAdvanced 统一到 IC2 高级电路板，NEI 就显示成
> `IC2:itemPartCircuitAdv`），配方被锁成那一个物品。要真正的矿词输入（NEI 显示矿词、
> 接受所有注册物品），用 `OreDictItemStack`：
>
> ```java
> new OreDictItemStack("circuitAdvanced", 4)   // 矿词 circuitAdvanced ×4（任意物品）
> ```
>
> 配方辅助函数 `tryAddAssembler*` / `tryAddAL` / `tryAddSpaceAssembler` 的输入数组
> 写 `new Object[] { ... }` 即可混放具体物品和 `OreDictItemStack`。

### 3.4 GT 部件 / 电路（ItemList 常量）

```java
ItemList.Electric_Motor_EV.get(1)     // 电动马达 EV ×1
ItemList.Electric_Pump_EV.get(1)      // 电动泵 EV
ItemList.Conveyor_Module_EV.get(1)    // 传送带 EV
ItemList.Electric_Piston_EV.get(1)    // 电动活塞 EV
ItemList.Robot_Arm_EV.get(1)          // 机械臂 EV
ItemList.Emitter_EV.get(1)            // 发射器 EV
ItemList.Sensor_EV.get(1)             // 传感器 EV
ItemList.Field_Generator_EV.get(1)    // 力场发生器 EV
ItemList.Circuit_Data.get(1)          // 数据电路
ItemList.Circuit_Elite.get(2)         // 精英电路 ×2
ItemList.Circuit_Master.get(1)        // 大师电路
ItemList.Circuit_Ultimatecrystalcomputer.get(1)
ItemList.Circuit_Biowaresupercomputer.get(1)
```

> 常用电压后缀：`_LV / _MV / _HV / _EV / _IV / _LuV / _ZPM / _UV / _UHV`。
> 你在 NEI 里看到的 `gt.metaitem.01/32603` 就是 `ItemList.Electric_Motor_EV`
> （damage = ID + 32000，字节码实证）。

### 3.5 AE2 物品（处理器/存储组件）

```java
appeng.api.AEApi.instance().definitions().materials().logicProcessor().maybeStack(8).orNull()
appeng.api.AEApi.instance().definitions().materials().calcProcessor().maybeStack(8).orNull()
appeng.api.AEApi.instance().definitions().materials().engProcessor().maybeStack(8).orNull()
appeng.api.AEApi.instance().definitions().materials().cell1kPart().maybeStack(1).orNull()
```

### 3.6 AE2 方块

```java
appeng.api.AEApi.instance().definitions().blocks().drive().maybeStack(1).orNull()         // ME 驱动器
appeng.api.AEApi.instance().definitions().blocks().iOPort().maybeStack(1).orNull()        // IO 端口
appeng.api.AEApi.instance().definitions().blocks().controller().maybeStack(1).orNull()    // ME 控制器
appeng.api.AEApi.instance().definitions().blocks().energyCellDense().maybeStack(1).orNull() // 致密能源元件
```

### 3.7 本 mod 物品

```java
new ItemStack(BlockEcoStorageCasing.INSTANCE, 1)       // 存储阵列外壳
new ItemStack(BlockEcoStorageDrive.INSTANCE, 1)        // 驱动器
new ItemStack(BlockEcoStorageCapacitance.INSTANCE, 1, BlockEcoStorageCapacitance.META_A) // 电容 A
new ItemStack(BlockEcoStorageMEBus.INSTANCE, 1)        // ME 总线
new ItemStack(BlockEcoStorageVent.INSTANCE, 1)         // 通风口
new ItemStack(RegistryEcal.casing, 2)                  // E-Calculator 外壳
new ItemStack(RegistryEcal.parallelDrive)              // 并行驱动器
new ItemStack(RegistryEcal.threadDrive)                // 线程驱动器
new ItemStack(RegistryEcal.PARALLEL_CORES.get(64))     // 64 并行核心
new ItemStack(RegistryEcal.THREAD_CORES_BY_SUFFIX.get("32"))  // 32 线程核心
new ItemStack(RegistryEcal.CELLS_BY_SIZE.get(ecoaegtnh.item.ecalculator.CellSize.M_64)) // 64m 晶阵
RegistryItems.itemCell(ecoaegtnh.item.estorage.CellSize.K_256)  // 256k 存储盘
RegistryMTE.L4.getStackForm(1)                         // E-Storage 控制器（gt.blockmachines/32030）
```

### 3.8 查不到常量的原始 meta 物品

```java
gtMachineBlockStack(2360, 16)   // gt.blockmachines/2360 ×16（运行时 findBlock 解析）
gtMetaItem02Stack(21028, 1)     // gt.metaitem.02/21028 ×1（运行时 findItem 解析）
```

> 这类物品从游戏里复制 `modid:itemid/meta` 时，meta 直接当数字填。

---

## 4. 工作台配方（3×3 有型合成）

```java
cpw.mods.fml.common.registry.GameRegistry.addRecipe(
    new net.minecraftforge.oredict.ShapedOreRecipe(
        输出物品,           // ItemStack
        "CAC",             // 第 1 行（3 字符）
        "FSF",             // 第 2 行
        "CDC",             // 第 3 行
        'C', "circuitMaster",                    // 字符 = 矿词字符串（自动匹配矿词）
        'A', aeController,                       // 字符 = ItemStack（精确匹配）
        'F', ItemList.Field_Generator_EV.get(1),
        'S', new ItemStack(BlockEcoStorageCasing.INSTANCE, 1),
        'D', aeDenseEnergy));
```

> 字符可以配矿词字符串（`"circuitMaster"` 这种）或具体 ItemStack；空格 `' '` 表示空位。
> 想支持无型合成（任意摆放）用 `ShapelessOreRecipe`。

---

## 5. 新增配方时照抄这个模板

在 `Recipes.register()` 调用的某个 `registerXXX()` 方法里加一段：

```java
// 我的新配方：XXX（EV 组装机，10 秒，无电路，焊锡 576mb）。
tryAddAssemblerNoCircuit(
    "estorage.xxx",                              // 唯一名
    new ItemStack[] { 输入1, 输入2, 输入3 },
    new FluidStack[] { Materials.SolderingAlloy.getMolten(576) },
    输出物品,
    TierEU.RECIPE_EV,
    10 * SECONDS);
```

## 6. 注意事项

1. **替换 vs 新增**：同一输出物品已有配方时，改配方 = 找到旧 `tryAddAssembler` 块整体替换
   （NEI 不会显示重复输出）；想并存就保留旧的另加一条（NEI 显示两个配方）。
2. **空值安全**：任一输入/输出为 null（比如 AE2 没加载）时配方自动跳过并打警告
   `Skipping ECO recipe '...'`，不会崩服。
3. **验证配方是否注册成功**：看服务端启动日志里的
   `ECO recipes registered: ... skipped=N`——N > 0 就说明有配方被跳过，再往上翻
   `Skipping ECO recipe` 警告找原因。
4. **装配线固体输入 4~16**：不够就加同阶 GT 部件。
5. **文件编码 UTF-8**：中文注释没问题，别用 GBK 编辑器乱写。
