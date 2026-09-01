# ECO-GTNH 开发环境备忘（队长维护）

本文件记录 GTNH 1.7.10 ECO 模组开发的关键环境事实，供团队成员（researcher / engineer-* / reviewer）直接引用，避免重复探测。

## 工作区

- 项目根：`D:\DeepSeek\GTNH-ECO`
- 参考仓库（1.12.2 ECO AE Extension，MIT 许可）：`D:\DeepSeek\GTNH-ECO\ref\NovaEngineering-ECOAEExtension-main`
  - E-Storage 相关代码：`src/main/java/github/kasuminova/ecoaeextension/common/block/ecotech/estorage/`（以及同包 `tileentity/`、`container/`、`gui/`、`network/`）
  - 贴图：`src/main/resources/assets/ecoaeextension/textures/blocks/storage_array_*.png` 等（MIT 许可，可复制并注明来源）
  - 备用 gradle wrapper jar：`gradle/wrapper/gradle-wrapper.jar`（59KB，可复制，改 properties 指向 8.7）
- 输出文档：`docs/DESIGN.md`（researcher）、`docs/REVIEW.md`（reviewer）、本文件

## 网络代理（重要！用户机器一直开着 ikuuu VPN）

- 局域网代理：`http://127.0.0.1:7890`（用户确认一直开启，允许走它加速所有下载）
- 已配置：
  - `git config --global http.proxy` / `https.proxy` = `http://127.0.0.1:7890` ✅（已测试 git ls-remote 939ms）
  - 项目 `gradle.properties` 已加 `systemProp.http.proxyHost/Port` 与 `systemProp.https.proxyHost/Port`（Gradle 依赖下载走代理）✅
  - Gradle 发行包已本地化：`gradle/wrapper/gradle-wrapper.properties` 的 distributionUrl 指向 `file:///D:/DeepSeek/GTNH-ECO/.gradle-dist/gradle-8.7-bin.zip`（134MB 已下载，wrapper 不再联网下载发行包）✅
- 用 pwsh 做 HTTP 下载时，给 `Invoke-WebRequest` 加 `-Proxy "http://127.0.0.1:7890"` 参数（PowerShell 不会自动读 HTTP_PROXY 环境变量）

## 构建工具链

- **跑 gradlew 用 JDK21**（守护进程 JVM）：`C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot`
  - 原因：spotless/google-java-format 跑在守护进程上，JDK8 守护进程把 GJF 锁死 1.7 无法解析现代语法（P1-1）；GTNH settings 插件的 dynamicSpotlessVersion 机制需要 JDK17+ 守护进程；RFG 也已弃用 JDK<21 运行
  - 用法：`$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot'` 后再跑 gradlew
  - 编译产物仍是 JVM8 字节码（工具链由 `org.gradle.java.installations.paths` 解析到 JDK8/Zulu）
- JDK 8 Temurin（`C:\Program Files\Eclipse Adoptium\jdk-8.0.502.7-hotspot`）：仅作为编译工具链之一；**不要**把它设为 JAVA_HOME 跑 gradlew（会导致 spotless 失败）
- 系统默认 java 是 25（`M:\像素工厂\jdk-25.0.1`），仅用于启动 GTNH 服务器（lwjgl3ify/RFB 支持）
- 系统无全局 gradle，用项目内 `gradlew.bat`（wrapper 8.7：`https://services.gradle.org/distributions/gradle-8.7-bin.zip`，已验证可达 200）
- 网络已验证可达：`services.gradle.org` ✅、`nexus.gtnewhorizons.com` ✅、`raw.githubusercontent.com` ⚠️（时快时慢，clone 曾失败；优先用 nexus 或 codeload tarball）

## 构建体系（GTNH 官方模板，GTplusplus 同款）

- `settings.gradle`：pluginManagement 仓库 `https://nexus.gtnewhorizons.com/repository/public/` + `plugins { id 'com.gtnewhorizons.gtnhsettingsconvention' }`
  - ⚠️ 实际版本坑：settingsconvention **1.0.22 未发布**、1.0.28+ 要求 Gradle 8.8 → 本项目用 **1.0.27**（配合 Gradle 8.7）
  - ⚠️ RFG marker 1.4.1 未发布 → 通过 `local-maven/` 重定向到 1.4.2（详见 settings.gradle 与 build.gradle 注释）
- `build.gradle`：仅 `plugins { id 'com.gtnewhorizons.gtnhconvention' }`
- `gradle.properties` 关键项：`minecraftVersion=1.7.10`、`forgeVersion=10.13.4.1614`、`channel=stable`、`mappingsVersion=12`、`enableModernJavaSyntax=true`（Jabel，需 JDK 17 toolchain 编译到 JVM 8）、`generateGradleTokenClass=ecoaegtnh.Tags`（⚠️ Tags 类由 gtnhconvention 构建期自动生成，**不要**手写源文件，否则重复类报错）、`modVersion=0.0.1`
- 本地 JDK 注册（避免 foojay 探测被 Cloudflare 屏蔽，error 1010；⚠️ 逗号后**不要加空格**，Gradle 不 trim 会解析错路径）：
  `org.gradle.java.installations.paths = C:/Program Files/Eclipse Adoptium/jdk-8.0.502.7-hotspot,C:/Users/30792/.jdks/zulu17.52.17-ca-jdk17.0.12-win_x64,C:/Users/30792/.jdks/zulu8.96.0.205-ca-jdk8.0.504-win_x64`
  - JDK 8 Temurin（`C:\Program Files\Eclipse Adoptium\jdk-8.0.502.7-hotspot`）：仅作编译工具链（MCP 编译要求 8+AZUL，若报错改用 Zulu 8）；跑 gradlew 请用 JDK21（见上）
  - Zulu 17（`.jdks\zulu17.52.17-ca-jdk17.0.12-win_x64`）：Jabel 现代语法编译用（要求 17+AZUL）
  - Zulu 8（`.jdks\zulu8.96.0.205-ca-jdk8.0.504-win_x64`）：MCP 编译 compilePatchedMcJava/compileMcLauncherJava 用
  - 注：Gradle 守护进程用 JDK 21（系统 PATH 自带）最稳；RFG 已提示未来将要求 Java≥21 运行插件
- `dependencies.gradle`（当前对齐服务器 GTNH 2.9.0-beta-2 的 mods 版本）：
  - `api('com.github.GTNewHorizons:GT5-Unofficial:5.09.54.20:dev')`（服务器版本；⚠️ MTE ID 数组仅 32766 槽，MTE ID 必须 < 32766，本项目用 32030-32032（32030-32049 空档，TecTech 止于 32029、GT_Framer 起于 32050）；旧 API `GT_MetaTileEntity_MultiBlockBase` 等在新旧版本均不存在，新 API 见 docs/DESIGN.md §2.1）
  - `api('com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-1000-GTNH:dev')`（服务器版本，artifact 名带连字符）
  - `api('com.github.GTNewHorizons:StructureLib:1.4.42:dev')`（结构检查，服务器同款；GT5U 传递依赖显式声明）

## 命令注意事项（Windows + DSH 沙箱）

- 不要用管道捕获 gradle/java 子进程的 stdout（`stdio:pipe` 会被沙箱拒绝 EPERM）；应把输出重定向到文件（`> build.log 2>&1`）或使用 run_in_background
- `java -version` 之类把输出写到 stderr 的命令会显示为红字错误，但 exit code 0 时属正常
- 首次 `gradlew` 会下载 Forge/MCP/依赖，耗时可能 10-30 分钟，务必后台运行并耐心等待，失败可重试
