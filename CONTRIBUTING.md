# 贡献指南（CONTRIBUTING）

感谢你愿意为 RBK-Monitor（RBMK 显示屏）做出贡献！
本仓库是 [树上的节枝（jiezhi）](https://space.bilibili.com/3494363348863838) 的
节枝特效工具 JiezhiMod（DFC-Map-Creation-Tool）的二次创作，
MPL-2.0 授权；提交代码即表示你同意将改动置于本项目相同的许可协议下。

## 一、开发环境

| 组件 | 要求 |
|---|---|
| JDK | **8**（编译固定走 Java 8 toolchain，不要用更高版本） |
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2859 |
| Gradle | 用仓库自带的 `./gradlew`（ForgeGradle 5），无需自装 |
| IDE | IntelliJ IDEA（推荐）或 Eclipse |
| HBM | **无需放入 libs** —— 本模组对 HBM 核科技（Nuclear Tech Mod）全程反射访问，编译期零 HBM 类名，任何环境都能直接编译 |

### 快速开始

```bash
# 克隆仓库
git clone https://gitee.com/baldih/jiezhi-and-baldih-rbmk-monitor.git
cd jiezhi-and-baldih-rbmk-monitor

# 编译（首次会联网拉取 ForgeGradle 依赖，请勿 --offline）
./gradlew build

# 产物在 build/libs/RBMK-Monitor-<版本>.jar
```

IDE 导入：

```bash
./gradlew genIntellijRuns   # IntelliJ IDEA
./gradlew genEclipseRuns    # Eclipse
```

> 注意：本项目使用 ForgeGradle 5（`net.minecraftforge.gradle` 5.+）+
> `stable_39-1.12` 映射，1.12.2 老教程里常见的
> `setupDecompWorkspace` 是 ForgeGradle 2.x 的命令，这里不需要。

## 二、代码规范

- 缩进 **4 空格**，禁止 Tab；源码编码 UTF-8
- 类名 `PascalCase`，方法/变量 `camelCase`，常量 `UPPER_SNAKE_CASE`
- 遵循 Minecraft Forge 1.12.2 通用编码习惯：
  - 客户端专属类放 `client/` 包，不要在服务端逻辑里引用（会 `NoClassDefFoundError`）
  - 网络包一律服务端校验（距离、权限），客户端输入不受信任
  - 数据落盘用「临时文件 + 原子改名」，写完就关句柄
- 注释：新功能说明「为什么」而不只是「做什么」；中文注释为主，
  面向玩家的文案走 `zh_cn.lang` / `en_us.lang` 双语键
- 玩家可见的版本号六处同步：`@Mod`/`VERSION`、`mcmod.info`、
  `build.gradle`、三卷手册固件横幅、终端横幅——改版本时缺一不可

## 三、提交流程

1. Fork 本仓库
2. 创建特性分支（不要直接在 master 上改）：
   - 功能开发：`feature/xxx`
   - Bug 修复：`fix/xxx`
   - 文档更新：`docs/xxx`
3. 提交并推送，创建 Pull Request

### Commit Message 规范

```
类型: 简短描述（一句中文）

- feat: 新功能
- fix: 修复 Bug
- docs: 文档更新
- style: 代码格式（不改逻辑）
- refactor: 重构
- test: 测试相关
- chore: 构建/工具链
```

### PR 要求

- 一个 PR 只做一件事
- 描述清楚改了什么、为什么改
- **必须通过 `./gradlew build` 编译**，不许带着编译错误提 PR
- 涉及显示/网络行为的改动，请附上游戏内截图或说明

## 四、构建与测试

```bash
./gradlew build        # 完整构建（编译 + 重混淆出 release jar）
./gradlew compileJava  # 只编译，日常自查用
./gradlew runClient    # 启动开发客户端实测（需本地 MC 资源缓存）
```

- 没有自动化测试套件；提交前请进游戏实测你改到的功能路径
- 服务端相关改动请同时测单人世界与局域网两端协议一致性
  （改包格式时记得同步升 `ModNetwork.PROTOCOL`）

## 五、报告问题

请优先使用仓库自带的 Issue 模板：

- Bug 反馈：附上 MC/Forge/HBM/本模组版本与崩溃日志
- 功能请求：说明使用场景

Issue 入口：<https://gitee.com/baldih/jiezhi-and-baldih-rbmk-monitor/issues>

## 六、联系与维护者

- 维护者：@baldih
- 原作者：树上的节枝（jiezhi）—— [B 站主页](https://space.bilibili.com/3494363348863838) ·
  原作项目 [DFC-Map-Creation-Tool](https://gitee.com/yi-sichen-jiezhi/DFC-Map-Creation-Tool)

> 本作品与任何现实核设施无关，仅供 Minecraft 游戏娱乐。
