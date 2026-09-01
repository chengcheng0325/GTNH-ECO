# E-Calculator 1.12.2 原版资料网络查证报告

> 任务：t2 网络查证 1.12.2 原版 E-Calculator 资料（wiki/CurseForge/视频）
> 执行：browser-agent · 日期：本次调研会话
> 结论先行：1.12.2 原版 E-Calculator 并非独立发布的单一 mod，而是 1.12.2 整合包「新星工程：世界 (Nova Engineering - World)」内 ECO 技术线的一部分，其 AE2 扩展实现为独立开源 mod **「Nova Engineering - ECO AE Extension」**（mod_id `ecoaeextension`，v1.2.0，GPL-3.0），本地参考源码 `.research/NovaEngineering-ECOAEExtension-main` 即其 GitHub 主线仓库副本。

---

## 1. 核心身份确认（与本地源码一一对应）

| 项 | 值 | 来源 |
|---|---|---|
| mod 全名 | Nova Engineering - ECO AE Extension | `gradle.properties`（本地已核对） |
| mod_id | `ecoaeextension` | 同上 |
| 版本 | 1.2.0 | 同上 |
| MC 版本 | 1.12.2（RetroFuturaGradle 1.3.35，stable_39） | `build.gradle`（本地已核对） |
| 作者 | Kasumi_Nova, sddsd2332, WI_8614_ice | `gradle.properties` |
| 许可证 | GPL-3.0 | 仓库 LICENSE（本地已核对） |
| GitHub 主线 | https://github.com/sddsd2332/NovaEngineering-ECOAEExtension | `gradle.properties` mod_url（本地）+ 网络可访问 |
| OpenEye 崩溃上报页 | https://openeye.openmods.info/mod/ecoaeextension/all （证明该 jar 随整合包实际分发使用） | 网络 |

机器本体：**ECO - 可扩展计算子系统主机**（Extensible Calculation Subsystem Host），等级 C4 / C6 / C9（源码中 L4/L6/L9 控制器 BlockECalculatorController，registry `extendable_calculator_subsystem_l4/6/9`），基于 Modular Machinery Community Edition (MMCE) 多方块 + MixinBooter 10.5 mixin 改写 AE2 合成 CPU（MixinCraftingCPUCluster / MixinCraftingGridCache），部件含：线程核心（CM4A/CM6A/CM9A）、超线程核心（CM4B/CM6B/CM9B）、并行核心（CT4/CT6/CT9）、晶阵驱动器（CD）、闪存晶阵（CE4/CE6/CE9）、ME 矩阵通讯接口（CR）、超导晶阵传输总线（CI）、散热总控（C4/C6/C9）。（以上名称与本地 `en_US.lang` 核对一致。）

## 2. 所属整合包（1.12.2 原版语境）

- 包名：**新星工程：世界 (Nova Engineering - World)**，MC 1.12.2 高难科技魔改包。
  - MC百科整合包页：https://www.mcmod.cn/modpack/784.html
  - 包源码组织（作者方）：https://github.com/NovaEngineering-Source
    - NovaEngineering-Core（包核心 mod，官方描述 "Core mod for NovaEngineering: World modpack"）：https://github.com/NovaEngineering-Source/NovaEngineering-Core
  - 第三方转载/下载渠道（非官方）：ZITBBS 帖 https://www.zitbbs.com/thread-7300-1-1.html 、https://www.zitbbs.com/forum.php?mod=viewthread&tid=1903 ；镜像站 https://www.mczwlt.net/resource/z3sju9mq 、https://www.minecraftzw.com/45633.html
- 重要命名历史：该机器在包内实况中被称为**「计算网络中心」**（见视频 EP20/EP21），mod 内正式名为「可扩展计算子系统主机」。移植方案中建议保留「计算子系统/主机」语义。

## 3. 资料站点核查结论

### 3.1 CurseForge
- **1.12.2 原版 ECO AE Extension 未在 CurseForge 发布**（仓库 `publish_to_curseforge=false`，无 project id；未检索到 1.12.2 页面）。
- CurseForge 上存在的是后继版本 **Neo ECO AE Extension**（高版本移植，非 1.12.2）：
  https://www.curseforge.com/minecraft/mc-mods/neo-eco-ae-extension
- Modrinth 同款：https://modrinth.com/mod/neoecoae （可见版本 20.1.0，GPL-3.0-only）
- 后继版 GitHub：https://github.com/DancingSnow0517/NeoECOAEExtension
  → 对移植有价值：后继版把子系统重构为「计算/合成/存储」三条线（F4 可拓展合成子系统主机 https://www.mcmod.cn/item/923663.html 、C6 可拓展计算子系统主机 https://www.mcmod.cn/item/923651.html 、L4 可拓展存储子系统主机 https://www.mcmod.cn/item/923715.html 、计算子系统结构外壳 https://www.mcmod.cn/item/923641.html 、CE6 闪存晶阵 https://www.mcmod.cn/item/923677.html ），可参考其命名与分层。

### 3.2 Wiki / 文字资料
未发现独立官方 wiki 站点；权威文字资料集中在 MC百科（mcmod.cn）：
- 整合包页（含简介/版本历史/依赖列表）：https://www.mcmod.cn/modpack/784.html
- 1.12.2 ECO AE Extension 物品/机器条目（核心证据）：
  - ECO - C9 可扩展计算子系统主机：https://www.mcmod.cn/item/859367.html
  - ECO - 可扩展计算子系统主机：https://www.mcmod.cn/item/859397.html
  - ECO - 可扩展合成子系统主机：https://www.mcmod.cn/item/859395.html
  - ECO - L6 可扩展存储子系统主机：https://www.mcmod.cn/item/859345.html
  - ECO - 并行核心：https://www.mcmod.cn/item/859396.html
  - ECO - 超线程核心：https://www.mcmod.cn/item/859400.html
- 教程类帖子：
  - 「新星的教程和杂谈」https://www.mcmod.cn/post/4466.html
  - 「新星工程 常见问题答疑」https://www.mcmod.cn/post/4496.html
  - 「新星的一些小寄巧」https://www.mcmod.cn/post/4630.html
  - 「在 HMCL 上手动安装新星工程整合包」https://www.mcmod.cn/post/4574.html
- Neo ECO AE Extension 更新日志（mcmod class 24642）：https://www.mcmod.cn/class/version/24642.html

### 3.3 视频资料（B 站，包内实际使用 E-Calculator/计算网络中心）
- 【新星工程:世界 EP20】计算网络中心、高级元件装配室（计算网络中心首次搭建/演示）：
  https://www.bilibili.com/video/BV1HgyMY5Eh8/
- 【新星工程】EP21 3级计算网络中心！算力提升，版本更新：
  https://www.bilibili.com/video/BV1PopjzfE3L/
- 【新星工程:世界 EP7】紫珀炉、工业数据处理计算机（早期计算类机器铺垫）：
  https://www.bilibili.com/video/BV1RJ4m1u7Zu/
- 【模组介绍】Neo ECO AE Extension | 新一代 AE 系统（后继版功能总览，用于理解设计意图）：
  https://www.bilibili.com/video/BV1DUDrBtE53/
- 其余系列实况（合集 1~10 等）：https://www.bilibili.com/video/BV1X6Jnz5ELe/

## 4. 对 GTNH 1.7.10 移植方案有直接参考价值的要点（供 t4 引用）

1. **依赖面**：1.12.2 版依赖 MMCE(ModularMachinery CE)、AE2 Extended Life、AE2 Fluid Crafting Rework、NAE2、MekanismEnergistics、CraftTweaker、TOP、FTB 系列等；GTNH 侧对应物需逐项映射（MM → 无直接等价，需评估 GTNH 自有多方块框架；AE2 EL/NAE2 → GTNH AE2FL 生态）。
2. **技术栈**：mixin（MixinBooter 10.5）改写 AE2 合成 CPU 集群是核心机制；GTNH 1.7.10 同样可用 MixinBooter/Mixin 0.8，mixin 可行性论证是 t4 关键点。
3. **机器语义**：vCPU 虚拟化（线程核心提供 vCPU 数）、并行核心、闪存晶阵字节内存、ME 通讯接口；移植 MVP 需裁剪到 GTNH 中期可落地的最小闭环（建议 C4 等级单主机 + 线程核心 + 晶阵 + ME 接口，先不引入超线程/合成子系统/存储子系统）。
4. **许可证**：GPL-3.0 原版，移植/借鉴需合规处理（同许可或重写；GTNH 生态多 LGPL/MIT，需评审把关）。
5. **命名**：原版 = ECO - 可扩展计算子系统主机 / 计算网络中心；GTNH 移植可沿用 ECO 命名体系，与后继版（计算/合成/存储三线）保持一致便于玩家认知。

## 5. 未证实/风险提示
- 未找到「E-Calculator 独立 mod」：1.12.2 语境下 E-Calculator = 包内机器 + ECO AE Extension 实现，无独立 CurseForge/Modrinth 1.12.2 页面。
- 无官方 wiki 站点：所有百科内容以 mcmod.cn 条目与视频为准，翻译/描述存在社区口径差异（计算网络中心 vs 可扩展计算子系统）。
- 网络检索到的 mcmod 条目为社区维护，条目内容细节（数值、配方）应以本地源码为准。
