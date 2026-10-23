# 鼠标手势 (TweakRoll)

**按住 Shift 用左键扫一排，物品栏和背包就换好了。**

给 **Minecraft 26.4-snapshot-5 (Fabric)** 的客户端模组。只有一件事：把"按住 Shift 逐个点格子"
换成"按住 Shift 扫过去"，扫过哪格就转哪格。

![version](https://img.shields.io/badge/version-1.3.5-blue)
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

[`鼠标手势 (TweakRoll) 1.3.5`](https://github.com/2006522/TweakRoll/releases/download/v1.3.5/TweakRoll-1.3.5.jar)

> GitHub 的发布文件名不支持非 ASCII 字符，所以实际文件名是 `TweakRoll-1.3.5.jar`，
> 下载页上显示的名字是「鼠标手势 (TweakRoll) 1.3.5」。

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

### 发版

改完 `fabric.mod.json` 里的 `version` 后执行：

```bash
./release.sh
```

它会自动：构建 → `git add/commit/push` → 建 Release（标题「鼠标手势 (TweakRoll) <版本>」）
→ 上传 `TweakRoll-<版本>.jar` 并把附件显示名设为中文全名 → **把新 jar 部署到游戏 mods 目录**。

单纯想更新本地游戏里的版本（不发版）：

```bash
./build.sh && ./deploy.sh
```

`deploy.sh` 会把其它版本的同 id jar 移出 `mods/`（挪到 `profiles/<实例>/_mods_backup/`），
再把当前版本复制成 `mods/TweakRoll-<版本>.jar`，避免重复 mod id。

### 提交即部署

仓库自带 `.githooks/pre-commit`：只要这次提交动了 `src/`，就会自动 `build.sh` + `deploy.sh`，
也就是说**改完代码一提交，游戏里的 mod 就换好了**。

新克隆的仓库要先启用一次：

```bash
git config core.hooksPath .githooks
```

（`release.sh` 里已经包含部署，所以发版时不用额外再跑。）

### 为什么不用 Gradle / Loom

26.4-snapshot-5 这个快照：Mojang 没发 `client_mappings`，Fabric 也没发 Yarn，
但**客户端 jar 本身没有混淆**（`net/minecraft/client/Minecraft.class` 直接可读）。
所以直接对官方 jar 编译即可，产物命名空间就是 `official`，不需要 remap。

也正因为如此，模组只有一个极小的 mixin（`ContainerScreenMixin`，用来在扫过期间屏蔽物品提示），
其余受保护成员 —— `AbstractContainerScreen` 的 `leftPos` / `topPos` / `hoveredSlot` / `slotClicked`
—— 都用反射拿，类加载时缓存一次（`slotClicked` 用 `Method.invoke` 走虚分派，
所以创造模式背包的重写版本也生效）。

mixin 配置里 `defaultRequire` 是 `0`：万一哪天原版方法改名导致注入失败，
模组也只是"tooltip 不再隐藏"，不会让游戏崩溃。

### 触发方式（两个坑）

**一、拖拽事件要靠 MouseHandler 层拦。** 原版 `AbstractContainerScreen.mouseDragged`
在把格子加进 `quickCraftSlots` 之后会**直接 `return true`**，不会调到 `Screen.mouseDragged`。
Fabric 的 `ScreenMouseEvents` 是在 `MouseHandler` 里用 `@WrapOperation` 拦 `screen.mouseDragged`
调用点的，所以事件照样能收到 —— 模组就挂在这里（和 Mouse Tweaks 的做法一致），
每个鼠标移动事件都会回调，扫过不漏格。另外用 20Hz 的客户端 tick 轮询兜底，两条路共用状态去重。

**二、按键要读 SDL 扫描码。** 26.x 起输入层从 GLFW 换成了 SDL，
`InputConstants.isKeyDown(int)` 内部是 `SDLKeyboard.SDL_GetKeyboardState()` 拿数组后**用这个 int 直接当下标**，
所以它要的是 **SDL 扫描码**：左 Shift = **225**、右 Shift = **229**、O = **18**。
传 GLFW 的 340/344 会读越界、永远返回 false（这也是 1.3.0 及之前完全不触发的原因）。
代码里统一用 `InputConstants.KEY_LSHIFT` 这类常量，不要再硬编码数字。

## 历史

- v1.0.x：初始版本（滚轮整理 + 左键拖拽收集 + 右键拖拽放置）
- v1.1.x：新增 Shift+左键扫过转移
- v1.2.0：移除滚轮整理
- v1.3.0：移除左键拖拽收集 / 右键拖拽放置，只保留 Shift+左键扫过转移
- v1.3.1：修复 Shift 检测（GLFW 键码 → SDL 扫描码）
- v1.3.2：补上拖拽事件触发路径（跟手），tick 轮询保留兜底

## 已知限制

- 只处理标准容器界面；模组自定义槽位布局如果不用 `slot.x/slot.y`，会回退到原版 `hoveredSlot`。
- 服务端会照常收到快速移动的点击包，所以多人游戏里同样有效（受服务器反作弊/延迟影响）。
