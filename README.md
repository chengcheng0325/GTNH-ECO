# ECO AE Extension (GTNH)

GTNH 1.7.10 addon：把 NovaEngineering-ECOAEExtension 的 E-Storage Array 概念移植为
GT5-Unofficial 多方块 + AE2U 存储集成。

> 本目录是**独立的 git 仓库**（开源发布用），位于工作区 `GTNH-ECO/ECOGTNH/`；
> 工作区其余目录（参考/素材/模型/其他项目）为本地资料，不随本仓库发布。

- Mod ID：`ecoaegtnh`
- 依赖：GregTech 5-Unofficial（`gregtech`）、Applied Energistics 2 Unofficial（`appliedenergistics2`）、StructureLib（`structurelib`）
- 参考设计文档：`docs/DESIGN.md`（架构）、`docs/t3-implementation-notes.md`（t3 实现记录）

## 内容

- **ECO E-Storage Array（L4 / L6 / L9）**：GT 多方块控制器，驱动列 1–12 单元可扩展；
  L4 可用 A 级盘、L6 可用 A/B 级盘、L9 可用 A/B/C 级盘。
- **部件方块**：存储阵列外壳（casing）、驱动盘位（drive bay）、电容 A/B/C（capacitance）、
  通风口（vent）、ME 总线（ME bus）。
- **存储盘**：物品/流体/源质盘共 27 种（9 尺寸 × 3 类型，256k–16384M；源质盘需 ThaumicEnergistics 加载）。
- **GUI**：控制器右键打开存储统计面板（结构状态、盘位数、驱动列数、总能量）。

## 构建

要求：JDK 8（编译工具链）+ JDK 21（Gradle 守护进程）；GTNH Gradle 模板（RetroFuturaGradle）。

```powershell
# 环境变量（示例）
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot"   # 守护进程用 21+
# 编译 + 打包
.\gradlew.bat build
# 产物
#   build/libs/ecoaegtnh.jar
#   build/libs/ecoaegtnh-dev.jar（开发用，含依赖）
```

> **本地依赖说明**（不入库，clone 后需自行准备）：
> - `libs/Thaumcraft-1.7.10-4.2.3.5.jar`：从 GTNH 整合包 mods 目录复制（`dependencies.gradle` 以 compileOnly 引用，仅用于编译源质盘相关代码）。
> - `local-maven/`：本机构建用的 RFG 插件本地重定向（`settings.gradle` 引用；不存在时 Gradle 会回退到 GTNH Maven，一般可忽略）。

首次构建会通过 GTNH nexus 拉取依赖；如网络受限，可走本地代理
（见 `docs/ENVIRONMENT.md`：`http://127.0.0.1:7890`，`gradle.properties` 已配
`systemProp.http.proxyHost/Port`）。

## 安装到 GTNH

1. 构建出 `build/libs/ecoaegtnh.jar`。
2. 把 jar 放入 GTNH 整合包的 `mods/` 目录（与 `gregtech`、`appliedenergistics2`、
   `structurelib` 等一起）。
3. 启动游戏；主菜单 Mod 列表应出现 "ECO AE Extension (GTNH)"。

## 游戏内测试步骤

1. **获取物品**：创造模式打开 "ECO AE Extension" 标签页（或 /give），取出
   控制器 L4、外壳、驱动盘位、电容 A、通风口、ME 总线、物品盘 16M 各若干。
2. **搭结构**（控制器面朝外，驱动列向西扩展）：
   - 固定主体 2×3×2：控制器在 (0,0,0)，ME 总线在 (1,0,1)，其余 10 格外壳；
   - 驱动列：每单元 x=-n 放 3 个驱动盘位（z=0, y=-1/0/1）、2 个电容（z=1, y=±1）、
     1 个通风口（z=1, y=0）；
   - 列末端整面 6 格外壳封口（ASCII 图见 `docs/DESIGN.md` §2.5）。
3. **成型检查**：控制器 GUI（右键）显示 "Structure: Valid"；NEI 结构预览可用
   （若已实现）。
4. **接 AE 网络**：ME 总线用智能线缆连到 ME 控制器/网络。
5. **放盘**：把存储盘放入驱动盘位（潜行右键或 GUI），打开 ME 终端应能看到盘内内容
   并可存取；能量条随电容充放电变化。
6. **扩展**：再加 1–2 个驱动列单元，结构自动重检（列数显示应更新）。

## 许可说明

本项目（代码与原创贴图）以 **GNU GPL-3.0** 发布，详见根目录 `LICENSE`。

- 设计概念参考 [NovaEngineering-ECOAEExtension](https://github.com/sddsd2332/NovaEngineering-ECOAEExtension)（GPL-3.0）。
- 本仓库所有贴图均为**原创程序化生成**（见 `src/main/resources/assets/ecoaegtnh/textures/README.txt` 与 `tools/gen-textures.ps1`），未复用参考仓库素材。

