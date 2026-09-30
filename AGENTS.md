# AGENTS.md — 随机换位（yg-random-swap）开发规范

> 本包是 yunxigames 系列的玩法包之一。系列总览、公共约定与全系列踩坑速查见
> [yunxigames 文档仓库](https://github.com/Yunqingqingxi/yunxigames) 的 AGENTS.md（必读）。
> 本文件是本仓库开发者（人类与 AI）的入口，开工前通读。

## 1. 本包是什么

**随机换位**：玩家受到任何伤害，**必定**与周围随机活体（生物或其他玩家）互换位置，
无概率、无来源判定（2.0.0 起「掉血直接换」是玩法定版口径，别再往回加触发条件）；
换位后公屏播报随机文案轮换（`{player} 与 {target} 互换了人生/位置/空间/命运……`），
自带落点保护（岩浆 / 火跳过、清摔落、排除骑乘 / 盔甲架 / Boss 黑名单）。

- mod id：`yg_swap`，jar：`yg-swap-<版本>.jar`，配置：`config/yg-swap.json`，入口 `YunxiGamesSwap`
- 版本线：1.0.0 概率制 → 1.0.1 必换 → **2.0.0 删全部触发判定（major）→ 2.1.0 公屏随机文案**

### 类地图

| 类 | 职责 |
| --- | --- |
| `SwapConfig` | 本包全部配置项 + `validate()` 钳制（radius 8~256 默认 48） |
| `HurtSwap` | 核心：AFTER_DAMAGE 事件 → 选随机换位对象 → 双向 `teleportTo` + 落点保护 + 公屏播报 |
| `SwapSelfTest` | 本包自检 |

### 三条设计底线 / 向后兼容承诺

1. 只在服务端做判定；2. 一局制、零持久化（LAST_HINT_AT 等全在内存）；3. 物品不凭空消失。
mod id / jar 名 / 配置文件名 / lang key 永不改；配置字段只增不删（2.0.0 删掉的
`hurtSwapChance` / `hurtSwapMobsCanTrigger` 靠 Gson 对残留字段静默忽略）；删字段 / 改默认行为升 major；
语义化版本 + GitHub Release 附 jar。

## 2. 环境（硬性）

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.159.0+26.2 |
| **JDK** | **25**（本机 `D:\Java\jdk-25`，runServer/build 必须显式指定） |

一切 gradle 命令加 `--offline`。

## 3. 常用命令

```bash
./gradlew compileJava --offline            # 开发期每个功能写完就跑
./gradlew test --offline                   # 三层 JUnit 测试
./gradlew smokeTest --offline              # 只跑冒烟
JAVA_HOME='D:\Java\jdk-25' ./gradlew runServer --offline > selftest-<版本>.log 2>&1
JAVA_HOME='D:\Java\jdk-25' ./gradlew build --offline
```

- runServer 工作目录是本仓库自己的 `run/`（首次跑改 `run/eula.txt` 为 `eula=true`）；
- 自检前把 `run/config/yg-swap.json` 的 `selfTestRolls` 改成 `200`，跑完**改回 `0`**；
- 自检完 runServer 不自退，手动结束 java 进程，否则 `run/` 被锁。

## 4. 代码规范

1. 一个功能一个类，类头 javadoc 写「是什么 + 为什么」；
2. 一切数值进本包 `SwapConfig`，带中文注释，每个功能独立开关（「爽但不劝退」）；
3. 新配置项必须在 `validate()` 钳制：`!(x >= lo && x <= hi)` 顺带治 NaN；
4. 中文注释 / 文案 / lang 键值；
5. 26.2 API 不确定：**先查反混淆 jar，别猜**。

## 5. 测试节奏

- 三层 JUnit（Smoke / Unit / Regression）+ runServer 自检；批量开发期只跑 `compileJava`；
- **新增功能必须同步新增自检项**并更新本包 README 的自检表；
- 配置测试基建：`YgConfig.configDirOverride`、`mergeMissingFields`（`raw.has` 判缺项补回——
  **本包是最早改用 JsonObject 存在性检查的**，显式 false 不可偷改）、`orDefaultIfNaN`；
  构造器与 `validate()` 包内可见是测试前提，别改回 private。

## 6. 本包专属坑（全系列公共坑见系列仓库 AGENTS §7）

- **事件**：`ServerLivingEntityEvents.AFTER_DAMAGE`（签名：entity, source, amount, newHealth, blocked），
  mixin 挂 `hurtServer` TAIL——**溺水也触发**（溺水走伤害链路但不是攻击来源）；
- **玩法口径**：玩家掉血就直接换，致死伤不换、骑乘不换、blocked 不换——这些是定版边界，
  别当 bug 修；
- **随机源**：`Level.random` 是 protected，要用 `level.getRandom()`；
- **传送**：`Entity.teleportTo(double,double,double)`（没有 3 参 `moveTo`）；换位后
  `resetFallDistance()`（public double 字段 `fallDistance` 也可直接清零）；
- **公屏播报**：`PlayerList#broadcastSystemMessage(Component, false)`；文案模板占位符
  `{player}` / `{target}`，随机轮换数组放 `HurtSwap` 常量；
- 换位对象不足时限频提示（HINT_INTERVAL_TICKS=100 + Map 记上次提示刻），别每刻刷屏。
