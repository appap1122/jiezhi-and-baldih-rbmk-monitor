
# 📟 RBMK-Monitor (RBMK 显示屏扩展)

[![License: MPL 2.0](https://img.shields.io/badge/License-MPL%202.0-brightgreen.svg)](https://opensource.org/licenses/MPL-2.0)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2-blue.svg)]()
[![Forge](https://img.shields.io/badge/Forge-14.23.5.2859+-orange.svg)]()

为 HBM 的 RBMK 反应堆提供极其硬核的多方块监控屏、声光告警、红石停堆，以及拥有黑客攻防与跨维度中控能力的 **11.0+ 终端命令行系统**。

## ✨ 核心特性

*   🖥️ **多方块中控矩阵**：支持屏幕无缝拼接，渲染巨型 ASCII 雷达图，打造科幻级中控室。
*   💻 **终端命令行系统**：右键屏幕即可打开黑底绿字终端，支持 `status`（状态）、`scram`（紧急停堆）、`history`（黑匣子）、`graph`（曲线图）等硬核指令。
*   🌋 **切尔诺贝利级特效**：反应堆熔毁时，触发蒸汽爆炸、实体掀盖子抛飞、延迟核爆，以及**物理级屏幕震动与碎片冲击波**。
*   🌐 **跨维度网络监控**：通过路由器方块，直接在主世界监控下界/末地的反应堆。
*   🕵️‍♂️ **服务器网络安全**：支持跨维度黑客指令（`hack <玩家ID>`），可篡改他人屏幕数据；同时配备 OP 审计日志与全局反黑客开关。
*   🛡️ **工业级安全联动**：支持 Auto-SCRAM 自动插棒，绝不让反应堆轻易失控。

## 📦 安装与前置要求

1.  **游戏版本**：Minecraft 1.12.2
2.  **加载器**：Forge 14.23.5.2859+
3.  **前置模组**：**无强制前置**。本模组可独立运行。
    *   *可选*：HBM's Nuclear Tech Mod (NTM-CE)。如果不装，屏幕只显示 `[数据源未绑定]`，不会崩溃。
4.  将下载的 `.jar` 文件放入 `.minecraft/mods` 文件夹即可。

## 🎮 快速上手

1. 放置 **RBMK 监视器**（多方块结构）并右键开机。
2. 使用 **RBMK 遥控器** 绑定反应堆控制台。
3. 右键屏幕打开终端，输入 `help` 查看所有可用指令。
4. 警告：在终端输入 `scram off` 可以关闭自动停堆保护，请谨慎使用，否则你的基地可能会化为灰烬。

## ⚖️ 开源与授权说明

*   本项目遵循 **[MPL-2.0](LICENSE)** 开源协议。
*   本项目基于 **[树上的节枝 (Jiezhi)](https://gitee.com/jiezhixisichen/DFC-Map-Creation-Tool)** 的源码进行二创，已保留全部授权声明（详见 [`AUTHORIZATION.md`](AUTHORIZATION.md)）。

## 💬 交流与反馈

遇到 Bug 或有新功能建议？欢迎加入交流群：**[你的QQ群号]** 或在 Gitee 提交 Issue。