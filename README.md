# ECO AE Extension (GTNH)

**ECO AE Extension** is a GTNH (Minecraft 1.7.10) addon that brings the E-Storage Array and
E-Calculator concepts to GT5-Unofficial multiblocks with full Applied Energistics 2 (Unofficial)
integration.

**ECO AE Extension** 是一个 GTNH（MC 1.7.10）附属模组，将 E-Storage Array 与 E-Calculator 的概念
以 GT5-Unofficial 多方块 + AE2U 深度集成的形式带入 GTNH。

| | |
|---|---|
| Mod ID | `ecoaegtnh` |
| Dependencies | GregTech 5-Unofficial (`gregtech`), Applied Energistics 2 Unofficial (`appliedenergistics2`), StructureLib (`structurelib`) |
| Branch `master` | GTNH **2.9.0-beta-2** (AE2U rv3-beta-1000, GT5U 5.09.54.20) |
| Branch `284` | GTNH **2.8.4** (AE2U rv3-beta-695, GT5U 5.09.51.482) |

---

## Features 功能

### 🧮 E-Calculator Array（计算阵列 / ECO 计算）
- **vCPU 体系**：AE 网格中的虚拟合成 CPU——任务不再占用物理合成方块，由计算阵列提供
  "线程槽"（内置槽 + 线程核心驱动器）并动态分配 vCPU 编号。
- **字节池记账**：任务字节实时扣减共享池（cell drive 容量），10% 红线保护、超频模式
  5% 红线；超线程槽的 +10% 虚拟预留不虚增池占用（M2 修复）。
- **任务合并（t116）**：相同输出的重复订单自动合并到正在运行的 vCPU，不占新线程。
- **故障安全**：断网时任务冻结不取消、网络恢复自动续跑；机器未成形（拆结构）时任务
  数据保留、重新成形自动恢复；取消失败的材料进入"孤儿"保护并自动退款（t122 系列）。
- 升级树（upgrade tree）解锁线程核心档位；停服 / 卸载自动退款在途材料（H2）。

### 🗄️ E-Storage Array（存储阵列 / ECO 存储）
- L4 / L6 / L9 三档多方块控制器，驱动器列 1–2 单元可扩展；
  L4 支持 A 级盘、L6 支持 A/B、L9 支持 A/B/C。
- 存储盘：物品 / 流体 / 源质共 27 种（9 档容量 × 3 类型，256k–16384M；
  源质盘需要 ThaumicEnergistics）。
- 控制器 GUI：结构状态、盘位、驱动器列、总容量统计（20t 缓存，高盘位不卡顿）。
- UNIVERSE 盘容量饱和钳制修复（H1），百分比统计饱和（M6）。

---

## Building 构建

Requirements: JDK 8 (compile toolchain) + JDK 21 (Gradle daemon); GTNH Gradle template
(RetroFuturaGradle).

```powershell
# Environment example
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot"   # daemon needs 21+

# Compile + package
.\gradlew.bat build

# Artifact
#   build/libs/ecoaegtnh.jar
```

For the 2.8.4 version check out the `284` branch (its build.gradle pins the 695 dependency set).

## Installation 安装

1. 将 `build/libs/ecoaegtnh.jar` 放入 `mods/`（服务端与客户端一致）。
2. 依赖：GT5U、AE2U、StructureLib（版本见分支说明）。
3. 客户端/服务端必须使用同一版本的 jar（SHA256 一致）。

## License

[GNU Lesser General Public License v3.0](LICENSE) (LGPL-3.0)

---

*Documentation, design notes and the implementation log stay private; this repository publishes
the source code only.*
