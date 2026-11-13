# RBMK-Monitor 底层架构说明

## 网络通信 (C2S / S2C)
所有状态修改（黑客、停堆、绑定）均通过 `MessageTerminalCommand` 等网络包发送。服务端处理逻辑严格使用 `MinecraftServer.addScheduledTask()` 调度回主线程，防止并发崩溃。

## 跨维度监控 (v13.0+)
利用 `MinecraftServer.getWorld(dim)` 获取目标维度。在 `ConnectMessage` 中加入了严格的坐标校验（±3000万边界）和区块加载校验（`isBlockLoaded`），防止服务器卡顿。

## 物理灾难与性能保护 (v13.1+)
*   **屏幕震动**：客户端渲染层的震动逻辑全量包裹 `try-catch`，异常时立即清零。
*   **实体碎片上限**：在 `SteamExplosionHandler.spawnDebris()` 中，设定了 **120 个实体硬上限**，单次爆炸限制在 30-50 个，彻底解决连环核爆导致的 TPS 暴跌。