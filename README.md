# RBK-Monitor（RBMK 显示屏）

Minecraft 1.12.2 Forge 模组 —— 为 HBM 核技术（Nuclear Tech Mod）的 RBMK 反应堆提供一整套**监控、报警、审计与中控室**解决方案。当前版本：**18.2**。

## 原作与二次创作声明

> **本作品基于节枝特效工具 JiezhiMod 二次创作**，不是原作者的作品。
>
> - **原作者**：树上的节枝（jiezhi）
> - **原作者主页**：<https://space.bilibili.com/3494363348863838>
> - **原作项目**：节枝特效工具（DFC-Map-Creation-Tool / JiezhiMod），<https://gitee.com/yi-sichen-jiezhi/DFC-Map-Creation-Tool>
>
> 本模组（RBMK 显示屏）是二次创作：在原作的多方块吊装屏、遥控器、开机动画等表现层基础上重写了数据层与 RBMK/HBM 核电监控功能。多方块屏、遥控器、开机动画、线圈与喷气背包等元素继承自原作，RBMK 数据采集、审计、报警灯、巡屏/聚合中控等均为二次创作新增。发布、转载本模组时请保留本声明与原作者署名。

## 构建系统说明

本项目基于 [ForgeGradle 5](https://github.com/anatawa12/ForgeGradle-1.2)（GoFancy 社区 1.12.2 现代化工具链），使用 Gradle 7.5.1 + JDK 8：

```gradle
// build.gradle 关键配置（已写好，无需修改）
classpath group: 'net.minecraftforge.gradle', name: 'ForgeGradle', version: '5.+'
java.toolchain.languageVersion = JavaLanguageVersion.of(8)
minecraft {
    mappings channel: 'stable', version: '39-1.12'
}
dependencies {
    minecraft 'net.minecraftforge:forge:1.12.2-14.23.5.2859'
}
```

```bash
# 自行构建（需要 JDK 8）
./gradlew build
# 产物在 build/libs/RBMK-Monitor-18.2.jar
```

> 若在国内构建时 kotlin-stdlib 等 POM 解析报错：删除 `~/.gradle/caches/modules-2` 与 `forge_gradle` 缓存重跑；仍失败可移除 `build.gradle` 里的 aliyun 镜像仓库行（镜像偶尔吐损坏 POM）。

---

## 多媒体依赖安装说明（可选）

4x3 多媒体屏幕的 MP4（H.264）/ MP3（AAC）解码依赖是可选的 JavaCV / FFmpeg 运行库，**不装也能正常进游戏**，只是屏幕会显示 `[UNSUPPORTED CODEC]`。

> **平台限制**：目前仅提供 Windows 64 位（x86_64）的依赖包。Mac/Linux 玩家暂不支持多媒体播放，但中控、红石、黑客、审计日志等其他功能不受影响。

**1. 依赖包放置路径**

下载 `RBMK-Media-Deps-Windows.zip`，解压后把里面的**全部 `.jar` 文件**放到：

```
.minecraft/mods/
```

（和 `RBMK-Monitor-18.2.jar` 放在同一个文件夹，重启游戏即可生效。）

如果服务端/整合包不允许往 `mods/` 里放非模组 jar，可改用回退目录：

```
.minecraft/rbmk_media/libs/
```

模组会先尝试常规 classpath，找不到时再从这个目录加载。

**2. 媒体文件放置路径**

MP4 / MP3 文件统一放在：

```
.minecraft/rbmk_media/
```

例如 `.minecraft/rbmk_media/demo.mp4`；图片放 `.minecraft/rbmk_media/pictures/`。

**3. 终端指令**

打开终端屏幕后输入：

```
media play demo.mp4
media pause
media resume
media stop
media volume 80
media list
media install
```

`media install` 可随时重新调出多媒体依赖安装引导界面。

---

## 方块一览

| 方块 | 说明 |
|---|---|
| `rbmk_screen` / `_real` | 堆芯总览屏（平均/最高柱温、通量、蒸汽、富集度、氙毒、控制棒） |
| `rbmk_monitor` / `_real` | 热图网格屏（逐柱温度矩阵，熔毁自动红屏） |
| `rbmk_graph` / `_real` | 历史曲线屏（滚轮回放，双击开导出面板） |
| `rbmk_custom` / `_real` | 自定义文本屏 |
| `rbmk_term_screen` / `_real` | 墙挂终端屏 |
| `rbmk_cycle_screen` / `_real` | **v18.0 轮播屏**：总览 → 热图 → 曲线自动轮播（默认 3 秒） |
| `rbmk_terminal` / `_real` | 地面控制台（终端指令入口，见下节） |
| `rbmk_patrol` | 巡屏中控：绑定 4 块屏轮巡 |
| `rbmk_aggregate` | 多堆芯聚合中控：多反应堆功率/温度排行（熔毁置顶） |
| `rbmk_siren` | 警笛 |
| `rbmk_alarm_light` | 报警灯：四级自动染色 / 手动染色 / 六档触发 |
| `rbmk_issuer` | 签发器 |
| `rbmk_router` | 网络路由器（跨维度通讯） |
| `rbmk_media_screen` / `rbmk_ppt_screen` | 多媒体屏 / PPT 汇报屏 |

带 `_real` 后缀的是**真实版**，需要三路供电才能亮（见「供电速查」）。普通版免电，行为与旧版完全一致。

---

## 终端指令（对 `rbmk_terminal` 右键，或手持笔记本）

```
help                    指令列表
status                  堆芯状态总览
rods                    控制棒状态 / 逐棒列表
scram | az5             AZ-5 手动停堆（写审计日志）
bind                    绑定附近反应堆控制台
connect <维度> <X> <Y> <Z>   跨维度绑定
where                   显示绑定状态与距离
siren <on|off|test>     警笛控制
list                    列出周围路由器连接的设备
msg <玩家ID> <内容>      跨维度短消息（≤30 字符）
password / auth         安全码上锁与验证
redstone <X> <Y> <Z> <0-15>  远程红石超控
ppt load|next|prev|goto <页>   PPT 汇报屏控制
pic list|view <文件>|clear     图片查看器
diagnose / ai           AI 诊断（可接大模型 API）
hack / fix              入侵与修复（有痕迹，/rbmktrace 可查）
```

### v18.0 管道过滤

```
status | filter heat > 800      只显示温度大于 800 的行
rods | filter flux < 100        通量小于 100 的行
```

- 支持运算符 `> < >= <= =`
- 字段可用别名：`heat` / `temp` / `温度` / `flux` / `water` / `steam` / `enrichment` / `xenon` / `level`
- `filter` 关键字可省略：`status | heat > 800` 等价
- 多级过滤可串联；回显与错误行永远放行；10 秒无回包自动失效

### v18.0 宏指令

```
macro set emergency scram;scram off     保存宏
macro run emergency                     一键执行
emergency                               裸宏名直接运行
macro list                              列出全部宏
macro del emergency                     删除宏
```

宏为会话内存，重启即清（像真 shell）；嵌套深度 ≤3。

### v18.2 阈值自动化防护链（终端 `watch`）

```
watch heat > 800 do scram        超过 800°C 自动停堆
watch water < 40 do alarm        水量低于 40 拉响附近警报器
watch flux > 2000 do audit       只记审计日志
watch list / del N / clear       规则管理
```

- 指标：`heat` / `peak`（最高柱温）/ `flux` / `water` / `steam` / `enrich` / `xenon`
- 动作：`scram`（插棒+审计+公告）/ `alarm`（告警+附近警报器试鸣）/ `audit`
- 冷却 200 刻；规则落盘 `rbmk_watch.json`，重启不丢
- **开关走审批**：与黑客玩法同一套流程——双击操作员工牌申请「阈值自动化防护链」，全体管理员全票通过才开启，任意一名管理员同意即关闭

### v18.2 其它新功能

- **蒸气爆炸景观**：顶盖飞天 → 喷泉式碎片雨（140 块、分 3 秒喷出）→ 地面弹坑阵（32 坑）+ 余火与烟柱，节奏对齐 HBM 实机效果
- **自定义页布局编辑器**：自定义屏设置界面新增「布局编辑器…」，仪表/曲线/文本块自由摆放缩放（最多 12 个），导出/导入剪贴板
- **设置中心**：任意显示屏蹲下连续右键 2 下打开——亮度 40~100、四种磷光主题（标准/琥珀/绿磷/纯白）、曲线对比模式（四通道叠加+可选对数纵轴）、运行报表（CSV/HTML 一键生成）
- **媒体屏幻灯片**：`media play slides@[3-60]` 轮播 `rbmk_media/slides/` 里的 PNG/JPG（默认 8 秒一张）
- **黑匣子增强**：`rbmk_history/index.txt` 索引、`/rbmkhistory merge <x> <y> <z>` 合并历史到注视屏
- **审计日志轮转**：`rbmk_audit.log` 满 4MB 自动滚到 `.1/.2/.3` 三代
- 网络协议代号 9 → 10（17/18/20 号包格式有变，新旧不可互通）

---

## 常用命令与操作

| 操作 | 方式 |
|---|---|
| 开/关屏 | 右键屏 |
| 告警阈值 / 红石联动设置 | 蹲下 + 空手右键 |
| 导出历史图形 | 蹲下 + 连续右键 2 下 → 选 CSV/PNG → 「导出历史图形」 |
| `/rbmkexport csv\|png [秒]` | 对准屏执行；可带时间段（如 `/rbmkexport png 60`） |
| `/rbmktrace` | 查询附近屏的入侵痕迹 |
| `/rbmkcompat` | 网络兼容探针（版本/协议 10/包注册表） |

### 数据文件位置（运行时自动创建）

```
<游戏目录>/rbmk_export/      CSV/PNG 历史图形导出
<游戏目录>/rbmk_history/     曲线黑匣子（纯客户端）
<游戏目录>/rbmk_audit.log    操作审计日志（服务端在服务端根目录）
```

审计日志格式：

```
[2026-11-23 18:00:00] [PlayerName] Action: scram at (120, 64, -3) dim 0
```

记录的操作：手动 SCRAM（终端 `scram`/`az5`）、自动 SCRAM（记 `SYSTEM`）、电源开关 `togglePower`、入侵开启 `hack`、修改安全码 `setSecurityCode`。写盘走 try-with-resources 开-写-关，绝不占用文件句柄。

---

## 真实版供电速查

| 电源 | 类型 | 接法 |
|---|---|---|
| HE（厂用电，优先） | HBM 能量系统 | 屏/终端**贴着** HBM 能量方块即可，自动拉电 |
| FE/RF（外部备用） | Forge Energy | Mekanism / 热力膨胀电缆直接接 |
| 红石（许可） | — | 屏旁 ≥10 级红石信号 |

- HE 优先扣费（1 FE 当量 = 10 HE），HE 不足自动落 FE（UPS 语义），两路全空才黑屏
- 状态行电源指示：`MAINS`=厂用 HE / `EXT`=外部 FE
- 没装 HBM 也能玩：纯 FE 供电照常工作
- 普通版（不带 `_real`）完全免电

---

## 安装与升级

**安装**：Forge 1.12.2（推荐 14.23.5.2860）+ HBM 核技术 → 把 `RBMK-Monitor-18.2.jar` 放进 `mods/`。

**从旧版升级（≤17.10 → 18.2）**：

1. 备份存档
2. `mods/` 里删光所有旧版 RBK-Monitor jar，**只留 18.2 一份**（残留两份会 `DuplicateModsFoundException` 崩启动）
3. 旧配置文件**不用删**，存档**不用动**，`rbmk_export/`、`rbmk_history/` 跨版本通用
4. 进世界跑 `/rbmkcompat` 确认

> **注意**：18.2 网络协议代号为 **10**，与 17.10 及更早版本**不可互通**——多人游戏两端必须都升 18.2（握手阶段直接标红拒绝，`/rbmkcompat` 给可读原因）。这是防止包串台崩运行时的保护，不是 bug。

---

## 常见问题

- **屏不亮？** 普通屏检查是否被红石超控关机；真实版点击屏看提示（redstone / HE / FE 三路红绿灯），缺哪路补哪路
- **连服务器被踢？** 两端版本必须一致，`/rbmkcompat` 查对端版本
- **曲线重启后清零？** 18.0 起落盘接续；若改过采样间隔，旧文件自动放弃接续属正常
- **审计日志没生成？** 首次发生受审计操作（如 SCRAM）时自动创建
- **多媒体屏显示 `[UNSUPPORTED CODEC]`？** 按上文「多媒体依赖安装说明」装依赖包，或跑 `media install` 调出引导

---

*v18.2 · RBMK 显示屏 · 仅供 Minecraft 游戏娱乐，与任何现实核设施无关。*
