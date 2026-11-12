<h1 align="center">RBMK 中控系统 · RBMK-Monitor</h1>

<p align="center">
  仓库名 RBMK-Monitor | 构建产物 RBMK-Monitor-13.0.jar | 模组 ID jiezhirbmkmod
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.12.2-black?style=flat-square&logo=minecraft" alt="Minecraft">
  <img src="https://img.shields.io/badge/Forge-14.23.5.2859+-orange?style=flat-square" alt="Forge">
  <img src="https://img.shields.io/badge/Version-13.0-purple?style=flat-square" alt="Version">
  <img src="https://img.shields.io/badge/Java-8-blue?style=flat-square" alt="Java">
  <img src="https://img.shields.io/badge/Env-Client%20%2B%20Server-green?style=flat-square" alt="Env">
  <img src="https://img.shields.io/badge/License-MPL--2.0-brightgreen?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/署名-非商业-red?style=flat-square" alt="署名">
</p>

<p align="center">
  给硬核服主与核电工程师的一套 Minecraft 核电站中控与灾难模拟工具包。<br>
  多方块拼接屏幕、终端命令行、跨维度监控、物理级核爆震屏、黑客攻防与审计日志。
</p>

<p align="center">
  作者：baldih · <a href="https://gitee.com/jiezhixisichen/DFC-Map-Creation-Tool">基于 树上的节枝 (Jiezhi) 开源项目二创</a>
</p>

---

## 目录

- [这是什么](#这是什么)
- [核心特性](#核心特性)
- [终端指令手册](#终端指令手册)
- [安装与前置要求](#安装与前置要求)
- [二创须知与署名要求](#二创须知与署名要求)
- [常见问题](#常见问题)
- [更新日志](#更新日志)

---

### 这是什么

**RBMK-Monitor** 是为 HBM 核科技（NTM-CE）量身打造的**服务器级中控系统**。它将原本简单的监控屏幕，升级为包含终端命令行、跨维度网络、黑客攻防和物理毁灭特效的硬核监控矩阵。

你可以把它当成基地的“心脏监护仪”，也可以用它来实现基地的自动化停堆与灾难预警。

---

### 核心特性

- **🖥️ 中控屏矩阵**：支持相邻屏幕无缝拼接，自动渲染巨型 ASCII 总控雷达图。
- **💻 终端命令行**：黑底绿字终端，支持 `status`、`scram`、`history`、`graph` 等硬核指令。
- **🌋 物理级灾难特效**：熔毁时触发蒸汽爆炸、盖子实体抛飞、延迟核爆，附带**客户端屏幕震动与碎片冲击波**（内置 120 实体硬上限，防止卡死）。
- **🌐 跨维度网络矩阵**：新增 RBMK 路由器，通过 `connect` 指令实现在主世界监控下界/末地的反应堆。
- **🕵️‍♂️ 终极黑客攻防**：支持 `hack <玩家ID>` 跨维度篡改他人屏幕数据，全服 OP 可收到审计日志。
- **🛡️ 工业级安全联动**：内置 Auto-SCRAM 自动紧急停堆，所有网络包均已做严格的主线程隔离。

---

### 终端指令手册

| 指令 | 用法 | 描述 |
| :--- | :--- | :--- |
| `status` | `status` | 查看当前绑定反应堆的温度、通量、控制棒状态。 |
| `scram` | `scram` | **紧急停堆**：瞬间将所有控制棒插到底。 |
| `scram off` | `scram off` | **黑客操作**：临时关闭自动停堆保护（请谨慎使用！）。 |
| `scram on` | `scram on` | 恢复自动停堆保护。 |
| `history` | `history` | 查看黑匣子历史记录。 |
| `graph` | `graph` | 在终端屏幕绘制 ASCII 字符温度曲线图。 |
| `hack` | `hack <玩家ID>` | **网络攻击**：跨维度黑入目标玩家附近的屏幕。 |
| `fix` | `fix` | 解除当前屏幕的黑客篡改状态。 |
| `connect` | `connect <维度ID> <X> <Y> <Z>` | 跨维度绑定目标坐标的反应堆数据源。 |
| `list` | `list` | 扫描周围的路由器，列出已连接的监控矩阵。 |

---

### 安装与前置要求

1. 游戏版本：**Minecraft 1.12.2**
2. 加载器：**Forge 14.23.5.2859+**
3. 前置模组：**无强制前置**（HBM NTM-CE 为可选前置，不装仅显示数据源未绑定）。
4. 将 `.jar` 文件放入 `.minecraft/mods` 即可。

---

### 二创须知与署名要求

- 本项目遵循 **MPL-2.0** 开源协议。
- 本项目基于 **树上的节枝 (Jiezhi)** 的 DFC 地图创作工具源码进行二创，已保留全部授权声明（详见 [`AUTHORIZATION.md`](AUTHORIZATION.md)）。
- **版权声明**：本仓库中由 `baldih` 编写的代码及功能，版权归 `baldih` 所有。

---

### 常见问题

**Q：为什么屏幕显示 `[数据源未绑定]`？**
A：请使用 RBMK 遥控器右键 HBM 反应堆控制台完成绑定。

**Q：屏幕被黑客黑了怎么办？**
A：在终端输入 `fix` 即可恢复真实数据。

**Q：服务器如何关闭黑客玩法？**
A：服主可在 `config/jiezhirbmkmod.cfg` 的 `[security]` 分类中将 `enableHackerAttack` 改为 `false`。

---

### 更新日志

#### 13.0
- 新增跨维度路由器与 `connect` 指令，支持远程监控。
- 新增核爆屏幕震动特效与碎片冲击波（加入 120 实体上限）。
- 修复跨维度空指针、非法坐标导致的服务器卡死等底层问题。

#### 12.0
- 新增跨维度黑客指令 `hack <玩家ID>` 及 OP 审计日志播报。
- 重构底层扫描逻辑，彻底消灭主线程卡顿。

#### 11.9
- 新增服务器安全与权限系统。
- 支持全局关闭黑客玩法，普通玩家白名单配置。

#### 11.8
- 新增中控屏矩阵渲染，支持屏幕拼接显示总控雷达图。
- 新增黑客攻击玩法，可篡改数据并强制红屏警告。

---
###9. 二创须知与开源授权
本项目遵循 MPL-2.0 开源协议。

本项目基于 树上的节枝 (Jiezhi) 的源码进行二创，已保留全部授权声明（详见 AUTHORIZATION.md）。

版权声明：本仓库中由 baldih 编写的代码及功能，版权归 baldih 所有。未经许可，禁止倒卖或用于商业盈利。

###10. 交流与反馈
遇到 Bug 或有新功能建议？

提交 Gitee Issue

加入玩家交流群：[记得换成你的QQ群号]


---
*Made with ❤️ by baldih & DeepSeek Harness.*