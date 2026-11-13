# RBMK-Monitor 开发者 API 说明

本模组通过反射（Reflection）读取 HBM 1.12.2 的数据，兼容性强，无需强制前置。

## 数据获取
*   使用 `getConsoleType()` 识别 RBMK 控制台。
*   通过 `getNBTForConsole()` 读取 `RBMKColumn.data` 中的 NBT 数据（温度、通量、控制棒状态）。

## 配置文件 (`config/jiezhirbmkmod.cfg`)
*   `[security] enableHackerAttack`：服务器是否允许黑客玩法（true/false）。
*   `[security] allowNormalPlayerHack`：是否允许非 OP 玩家使用黑客指令。
*   `[safety] enableAutoScram`：是否开启自动紧急停堆。
*   `[safety] autoScramTemp`：触发自动停堆的临界温度（默认 1000℃）。

## 终端指令
*   `status`：查看反应堆状态。
*   `scram`：紧急停堆。
*   `hack <玩家ID>`：跨维度黑客攻击。
*   `fix`：解除黑客状态。