# 鼠标手势 (TweakRoll)

**按住 Shift 扫一排，背包和物品栏就换好了。**

这是给 **Minecraft 26.4-snapshot-5 (Fabric)** 的客户端模组：在背包、箱子、工作台这些容器界面里，
用鼠标扫过去的动作取代一格一格的 Shift+点击。

![version](https://img.shields.io/badge/version-1.2.0-blue)
![minecraft](https://img.shields.io/badge/Minecraft-26.4--snapshot--5-3C8527)
![loader](https://img.shields.io/badge/Fabric%20Loader-%E2%89%A5%200.19.5-lightgrey)
![license](https://img.shields.io/badge/license-MIT-blue)

---

## 一眼看懂

| 操作 | 效果 |
| --- | --- |
| **Shift + 按住左键**扫过 | 扫到哪格，那格物品就快速转移：物品栏 ↔ 背包 |
| **左键**按住拖过同类物品 | 全部并到光标上（拖着版的"双击收集"） |
| **右键**按住拖过 | 每个格子放一个 |

## 下载安装

**① 下载**（点开直下，不用编译）

[`mousegestures-1.2.0-mc26.4-snapshot-5.jar`](https://github.com/2006522/TweakRoll/releases/download/v1.2.0/mousegestures-1.2.0-mc26.4-snapshot-5.jar)

**② 前置**：Fabric Loader ≥ 0.19.5 ＋ Fabric API（26.4 版本线，如 `0.164.1+26.4`）＋ Java 25

**③ 安装**：把 jar 丢进 `.minecraft/mods/`，启动游戏。

历史版本见 [Releases](https://github.com/2006522/TweakRoll/releases)。

## 功能细节

### Shift + 左键扫过转移

按住 **Shift** 再按住左键，鼠标扫过哪个格子，那一格的物品就快速转移：

- **物品栏（快捷栏）** → **背包**
- **背包** → **物品栏**
- 箱子等**容器界面** → 容器 ↔ 玩家背包 互换

按下的第一格由原版处理，之后扫过的新格子由模组补上，所以不会重复转移。
中途松开 Shift 立刻停止，不会误转一片；不想要可以在设置里关掉。

> 实现上是**逐帧轮询**物理按键状态，不走 Fabric 的拖拽事件 ——
> 因为原版 `AbstractContainerScreen.mouseDragged` 会提前 return，拖拽事件在容器界面里根本收不到。

### 左键拖拽收集

光标空着时，左键按住一个格子并拖过其它格子，把沿途**同种物品**全部并到光标上。

### 右键拖拽放置

光标上有物品时，按住右键拖过一排格子，每个格子放一个。

## 按键

| 按键 | 作用 |
| --- | --- |
| `O` | 打开设置界面 |

可以在原版「按键设置 → 物品栏」里改。

## 配置

游戏内设置界面（`O`）改完立即写回 `config/tweakroll.properties`：

```properties
shiftHoverMove=true   # Shift+左键扫过转移
lmbCollect=true       # 左键拖拽收集
rmbPlace=true         # 右键拖拽放置
```

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 26.4-snapshot-5 |
| Fabric Loader | >= 0.19.5（实测 0.20.1 正常） |
| Fabric API | 0.164.1+26.4 或更高（26.4 版本线） |
| Java | 25 |

## 自己构建

```bash
./fetch-deps.sh   # 一次性下载依赖到 deps/
./build.sh        # 产出 dist/tweakroll-<version>+mc26.4-snapshot-5.jar
```

可选环境变量：`DEPS`（依赖目录）、`JDK`（JDK 25 根目录）。

> 脚本里 `javac` 收到的是绝对路径；仓库放在含中文的目录下时，
> Windows 可能因参数编码报「找不到文件」，换纯英文路径即可。

### 为什么不用 Gradle / Loom

26.4-snapshot-5 这个快照：Mojang 没发 `client_mappings`，Fabric 也没发 Yarn，
但**客户端 jar 本身没有混淆**（`net/minecraft/client/Minecraft.class` 直接可读）。
所以直接对官方 jar 编译即可，产物命名空间就是 `official`，不需要 remap。

也正因为如此，模组没有写 mixin —— `AbstractContainerScreen` 的
`leftPos` / `topPos` / `hoveredSlot` / `slotClicked` 这几个受保护成员用反射拿，
类加载时缓存一次（`slotClicked` 用 `Method.invoke` 走虚分派，
所以创造模式背包的重写版本也生效）。

## 和 Mouse Tweaks 的关系

功能思路参考 [Mouse Tweaks](https://github.com/YaLTeR/MouseTweaks)，
但**代码是重写的，没有使用它的源码**，协议是 MIT。

滚轮整理那个功能**已经在 v1.2.0 移除**（作者不需要），所以本模组不再处理滚轮事件。

## 已知限制

- 左键拖拽收集 / 右键拖拽放置依赖 Fabric 的拖拽事件，在部分容器界面可能收不到
  （原版 `mouseDragged` 会提前 return）。Shift 扫过转移已经改成逐帧轮询绕开了这个问题，
  这两项还没改；遇到无效请提 issue。
- 槽位定位优先用 `slot.x/slot.y` 自己算，算不到才回退到原版 `hoveredSlot`；
  自定义槽位布局的模组界面可能不准。
