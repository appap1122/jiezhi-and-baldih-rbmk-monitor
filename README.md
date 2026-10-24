<div align="center">
  <img src="screenshots/banner.png" width="100%" alt="JiezhiMod 节枝特效工具">
</div>

<h1 align="center">节枝特效工具 · JiezhiMod////baldihmod为2创是RBMK的显示屏，前置为jiezhimod！</h1>

<p align="center">
  <sub>仓库名 <code>DFC-Map-Creation-Tool</code> ｜ 构建产物 <code>jiezhi-1.12.2-dfcmaptools-4.0.jar</code> ｜ 模组 ID <code>jiezhimod</code></sub>
</p>

<p align="center">
  <a href="https://minecraft.net"><img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.12.2-62B47A?style=flat-square"></a>
  <a href="https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html"><img alt="Forge" src="https://img.shields.io/badge/Forge-14.23.5.2864-5A8DEE?style=flat-square"></a>
  <img alt="Version" src="https://img.shields.io/badge/版本-1.0.0--beta-FF6BD6?style=flat-square">
  <img alt="Java" src="https://img.shields.io/badge/Java-8-ED8B00?style=flat-square">
  <img alt="Side" src="https://img.shields.io/badge/端-客户端%20%2B%20服务端-FFC55C?style=flat-square">
  <img alt="License" src="https://img.shields.io/badge/许可证-署名·非商业-94E2BE?style=flat-square">

`

---


## 🚀 安装

1. 安装 **Minecraft 1.12.2** 与 **Forge 14.23.5.2864**；
2. 把 `jiezhi-1.12.2-dfcmaptools-3.9.jar` 放进 `.minecraft/mods/`（**服务端也要放**，特效靠网络包下发）；
3. 启动游戏，日志里出现 `jiezhi` 大字 LOGO 与 `JiezhiMod v4.0 is loading.` 即加载成功；
4. 进世界输入 `/shake 40` 自验。
（baldihmod为2创，前置为jiezhimod）
> **服务端必须装**：指令在服务端注册，画面在客户端渲染。只装服务端能注册指令但看不到特效，只装客户端则连指令都没有。

---

## 🔧 从源码构建

### 环境

| 依赖 | 版本 |
|---|---|
| JDK | **21**（编译目标仍是 Java 8） |
| Gradle | 由 `gradlew` 自带 |
| ProGuard | **7.4.2**（本地构建需要，需自行下载到 `tools/proguard-7.4.2/`） |

```
git clone https://github.com/<你的用户名>/<仓库名>.git
cd <仓库名>
```

### 构建三步（缺一不可）（jzmod）

```bat
:: 1. 编译出基础 jar
gradlew.bat build --offline
::    -> build\libs\jiezhi-1.12.2-dfcmaptools-3.9.1.jar

:: 2. ProGuard 混淆
java -jar tools\proguard-7.4.2\lib\proguard.jar @tools\proguard.pro
::    -> tools\obf.jar

:: 3. 注入完整性封印（内含 ASM StackMapTable 补帧 + SealA/SealB 哈希注入）
python tools\seal_inject.py tools\obf.jar build\libs\jiezhi-1.12.2-dfcmaptools-3.9.1.jar
```

**为什么必须三步全跑？** jar 内置**双重完整性校验**：业务类的聚合哈希在构建期被固化进 Seal，运行时校验不过就直接崩溃。所以改了源码却不重跑三步，进游戏必定 `integrity check failed`。**纯资源改动（贴图 / mcmod.info / 语言文件）也要重跑**。

### 构建后必验

```bat
python tools\check_stackmap.py build\libs\jiezhi-1.12.2-dfcmaptools-3.9.1.jar   :: 期望输出 OK
python tools\selftest\run_selftest.py                                        :: 期望 ALL GREEN
:: 编译并运行 tools\testmain\TestMain.java，期望输出 SEAL OK
```

### 辅助开发工具

| 工具 | 用途 |
|---|---|
| `tools\canvastest\run_dump.bat` | **不进游戏**，离屏渲染三块屏的任意页面为 PNG（README 里的配图就是这么来的） |
| `tools\selftest\` | 离线断言测试（格式化 / 序列化 / 版式哨兵 / 注册检查） |
| `tools\testmain\` | Seal 完整性自检 |
| `tools\gen_readme_assets.py` | 生成 `screenshots/` 下的横幅与物品 / 方块一览图 |

---


```

---

## ⚠️ 二创须知与署名要求

**本仓库以「源码公开」的形式发布，但不是无限制许可。** 简而言之：

- ✅ **可以**：阅读、学习、编译自用、在自己整合包里使用、向本仓库提 PR。
- ✅ **可以**：基于本模组做二次创作（改数值、改贴图、加功能、做附属）。
- ❗ **必须**：**任何二次创作、修改版、转载、整合包分发，都必须显著保留原作者的署名**
  —— **「树上的节枝」**（附带 B 站主页 `https://space.bilibili.com/3494363348863838`），
  并注明本项目链接。署名需出现在**项目说明（README / 页面介绍 / 整合包清单 / 模组列表）**中，不能只写在源码注释里。
- ❌ **不可以**：删除或替换原作者署名后分发；把本模组或其修改版**当作自己的作品**发布。
- ❌ **不可以**：直接**二次分发本模组的 jar 或修改版 jar**（请引导用户回到本仓库 / 原作者发布页下载）。整合包内置需先取得作者同意。
- ❌ **不可以**：**商业用途**（售卖、付费墙、收费整合包等）。








<

## 🙏 致谢

- **MinecraftForge** 团队 —— 这个模组的地基。
- **HBM 的核科技（NTM-CE，尤其是 Cursed Edition / leafia 的 DFC 扩展）** —— DFC 显示器读取的所有数据都来自它。本模组对它是**纯反射的软依赖**，没有引用任何它的类。
- 所有提交 issue / 反馈 bug 的玩家。

---

<p align="center">
  <b>作者：树上的节枝</b> · <a href="https://space.bilibili.com/3494363348863838">B站主页</a><br>
  <sub>本项目以「源码公开 + 署名非商业」形式发布，二次创作请务必保留「树上的节枝」署名。</sub>
</p>
