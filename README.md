# 鼠标手势 (TweakRoll)

**按住 Shift 用左键扫一排，物品栏和背包就换好了。**

给 **Minecraft 26.4-snapshot-5 (Fabric)** 的客户端模组。只有一件事：把"按住 Shift 逐个点格子"
换成"按住 Shift 扫过去"，扫过哪格就转哪格。

![version](https://img.shields.io/badge/version-1.3.0-blue)
![minecraft](https://img.shields.io/badge/Minecraft-26.4--snapshot--5-3C8527)
![loader](https://img.shields.io/badge/Fabric%20Loader-%E2%89%A5%200.19.5-lightgrey)
![license](https://img.shields.io/badge/license-MIT-blue)

---

## 怎么用

1. 按 `E` 打开背包（或箱子、工作台等任意容器）
2. 按住 **Shift**，再按住**左键**不放
3. 鼠标扫过想移动的格子

- 在**物品栏（快捷栏）**里 → 移到**背包**
- 在**背包**里 → 移到**物品栏**
- 在箱子等**容器界面**里 → 容器 ↔ 玩家背包 互换

按下的第一格由原版处理，之后扫过的新格子由模组补上，所以不会重复转移。
中途松开 Shift 立刻停止。

## 下载安装

**① 下载**

[`鼠标手势 (TweakRoll) 1.3.0`](https://github.com/2006522/TweakRoll/releases/download/v1.3.0/TweakRoll-1.3.0.jar)

> GitHub 的发布文件名不支持非 ASCII 字符，所以实际文件名是 `TweakRoll-1.3.0.jar`，
> 下载页上显示的名字是「鼠标手势 (TweakRoll) 1.3.0」。

**② 前置**：Fabric Loader ≥ 0.19.5 ＋ Fabric API（26.4 版本线，如 `0.164.1+26.4`）＋ Java 25

**③ 安装**：把 jar 丢进 `.minecraft/mods/`，启动游戏。

历史版本见 [Releases](https://github.com/2006522/TweakRoll/releases)。

## 按键

| 按键 | 作用 |
| --- | --- |
| `O` | 打开设置界面（只有一项开关） |
| `Shift + 左键按住` | 扫过转移 |

`O` 可以在原版「按键设置 → 物品栏」里改。

## 配置

```properties
shiftHoverMove=true   # Shift+左键扫过转移
```

文件在 `config/tweakroll.properties`，游戏内按 `O` 也能开关。

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

### 为什么要逐帧轮询

扫过转移没有用 Fabric 的拖拽事件，而是每帧在 `ScreenEvents.afterForeground` 里
读物理按键状态（`InputConstants.isKeyDown` 判断 Shift、`MouseHandler.isLeftPressed()` 判断左键）
和缩放后的鼠标坐标。

原因：原版 `AbstractContainerScreen.mouseDragged` 在把格子加进 `quickCraftSlots` 之后
会**直接 `return true`**，不会调用 `Screen.mouseDragged`，
所以 Fabric 的 `ScreenMouseEvents.allowMouseDrag` 在容器界面里根本收不到。

## 历史

- v1.0.x：初始版本（滚轮整理 + 左键拖拽收集 + 右键拖拽放置）
- v1.1.x：新增 Shift+左键扫过转移
- v1.2.0：移除滚轮整理
- v1.3.0：移除左键拖拽收集 / 右键拖拽放置，只保留 Shift+左键扫过转移

## 已知限制

- 只处理标准容器界面；模组自定义槽位布局如果不用 `slot.x/slot.y`，会回退到原版 `hoveredSlot`。
- 服务端会照常收到快速移动的点击包，所以多人游戏里同样有效（受服务器反作弊/延迟影响）。
