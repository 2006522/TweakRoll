# 鼠标手势(TweakRoll)

给 **Minecraft 26.4-snapshot-5 (Fabric)** 的背包鼠标增强模组：滚轮整理、左键拖拽收集、右键拖拽放置。

游戏内显示名是「鼠标手势(TweakRoll)」；内部 mod id 仍是 `tweakroll`（配置路径、已装实例都依赖它，所以没有跟着改）。

功能对标 [Mouse Tweaks](https://github.com/YaLTeR/MouseTweaks)（`MouseTweaks 2.31` / MC 26.3），
但代码是重写的，未使用其源码。

## 下载

点直接下，不用登录、不用编译：

**[⬇ mousegestures-1.1.0-mc26.4-snapshot-5.jar](https://github.com/2006522/TweakRoll/releases/download/v1.1.0/mousegestures-1.1.0-mc26.4-snapshot-5.jar)**

历史版本见 [Releases](https://github.com/2006522/TweakRoll/releases)。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 26.4-snapshot-5 |
| Fabric Loader | >= 0.19.5（实测 0.20.1 正常） |
| Fabric API | 0.164.1+26.4 或更高（26.4 版本线） |
| Java | 25 |

## 功能

### 滚轮整理
光标**没有**物品时，把鼠标停在格子上滚滚轮：

- 向上滚：把这个格子里的物品**一个一个**搬到另一侧背包（箱子 ↔ 玩家背包），
  自动找同种物品的未满堆叠，没有就找空格。
- 向下滚：反过来，从另一侧背包把同种物品一个个搬进悬停的格子。
- 光标**有**物品时：滚滚轮就往悬停格里一个一个放。

单个物品的搬运是「整叠拿起 → 右键放一个 → 剩余放回」，不会一次搬整叠，
也不会把物品卡在光标上。

### 左键拖拽收集
光标空着时，左键按住一个格子并拖过其它格子，会把沿途**同种物品**全部并到光标上
（类似双击收集，但可以拖着扫一排）。

### 右键拖拽放置
光标上有物品时，按住右键拖过一排格子，每个格子放一个。

### Shift + 左键扫过转移
按住 **Shift** 再按住左键，鼠标扫过哪个格子，那一格的物品就快速转移：

- 在**物品栏（快捷栏）**里的 → 移到**背包**
- 在**背包**里的 → 移到**物品栏**
- 在箱子等**容器界面**里 → 容器 ↔ 玩家背包 互换

中途松开 Shift 会立刻停止，不会误转一片。不想用可以在设置里关掉。

### 容器级开关
在容器界面按 **左 Alt** 临时关掉该界面的滚轮整理（重新打开容器恢复）。

## 按键

| 按键 | 作用 |
| --- | --- |
| `O` | 打开设置界面 |
| `左 Alt` | 当前容器 开/关 滚轮整理 |

都可以在原版「按键设置 → 物品栏」里改。

## 配置

游戏内设置界面（`O`）改完立即写回 `config/tweakroll.properties`：

```properties
wheelTweak=true           # 滚轮整理
lmbCollect=true           # 左键拖拽收集
rmbPlace=true             # 右键拖拽放置
shiftHoverMove=true       # Shift+左键扫过转移
scrollDirection=NORMAL    # NORMAL / INVERTED
searchOrder=FIRST_TO_LAST # FIRST_TO_LAST / LAST_TO_FIRST
scaling=ALWAYS_ONE        # ALWAYS_ONE / PROPORTIONAL
```

## 安装

1. 装好 Fabric Loader（>= 0.19.5）和 Fabric API（26.4 版本线，如 0.164.1+26.4）
2. 从 [Releases](https://github.com/2006522/TweakRoll/releases) 下载 `mousegestures-<版本>.jar`
3. 放进 `.minecraft/mods/`，启动游戏

## 构建

```bash
./fetch-deps.sh   # 一次性下载依赖到 deps/
./build.sh        # 产出 dist/tweakroll-<version>+mc26.4-snapshot-5.jar
```

可选环境变量：`DEPS`（依赖目录）、`JDK`（JDK 25 根目录）。

> 注意：脚本里 `javac` 收到的是绝对路径，如果仓库放在含中文的目录下，
> Windows 上可能因参数编码问题报「找不到文件」，换成纯英文路径即可。

### 为什么不用 Gradle / Loom

26.4-snapshot-5 这个快照：Mojang 没有发布 `client_mappings`，Fabric 也没发 Yarn，
但**客户端 jar 本身已经没有混淆**（`net/minecraft/client/Minecraft.class` 直接可读）。
所以直接对着官方 jar 编译即可，产物命名空间就是 `official`，不需要 remap；
也正因为如此，模组没有写 mixin —— `AbstractContainerScreen` 的
`leftPos` / `topPos` / `hoveredSlot` / `slotClicked` 这几个受保护成员用反射拿，
类加载时缓存一次（`slotClicked` 用 `Method.invoke` 走虚分派，
所以创造模式背包的重写版本也生效）。

## 已知限制

- 光标上有物品时，滚轮只支持「往里放一个」，不支持「从格子里拿一个」（原版点击语义做不出原子的一拿一放）。
- 只在标准容器界面上测过逻辑；模组界面的自定义槽位定位如果不用 `slot.x/slot.y`，会退回使用原版 `hoveredSlot`。
- 未在真实游戏内跑过实机验证（开发环境没有游戏实例），手感可能需要微调。
