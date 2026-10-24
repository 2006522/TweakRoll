# 鼠标手势 (TweakRoll)

**按住 Shift 用左键扫一排，物品栏和背包就换好了。**

给 **Minecraft 26.4-snapshot-5 (Fabric)** 的客户端模组。

![version](https://img.shields.io/badge/version-1.8.1-blue)
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
中途松开 Shift 立刻停止。扫过期间不会弹物品提示（tooltip），不会一格一格地闪名字。

### R 键整理

按 `R` 整理：合并同种物品堆叠 → 按物品 ID 排序 → 排满前面的格子。

| 界面 | 整理范围 |
| --- | --- |
| 背包 | 背包的 27 格（不碰快捷栏、盔甲、合成格） |
| 箱子 / 大箱子 / 木桶 / 潜影盒 | 容器那 27 / 54 格 |
| 发射器（投掷器）/ 漏斗 | 容器那 9 / 5 格 |
| 熔炉、工作台等 | 不整理（有结果格，避免误操作） |

界面上还会有一个「**整理**」按钮（默认在面板右上角）：
**轻点**它和按 `R` 等价，**按住拖动**可以把它挪到顺手的位置，松手自动记住。
整理途中再按一次 `R` 取消。
**单人游戏是零点击的**：直接把服务端数据改好再同步，瞬间整齐；
多人服务器上退回发包模拟点击，满背包约 0.6 秒。

## 下载安装

**① 下载**

[`鼠标手势 (TweakRoll) 1.8.1`](https://github.com/2006522/TweakRoll/releases/download/v1.8.1/TweakRoll-1.8.1.jar)

> GitHub 的发布文件名不支持非 ASCII 字符，所以实际文件名是 `TweakRoll-1.8.1.jar`，
> 下载页上显示的名字是「鼠标手势 (TweakRoll) 1.8.1」。

**② 前置**：Fabric Loader ≥ 0.19.5 ＋ Fabric API（26.4 版本线，如 `0.164.1+26.4`）＋ Java 25

**③ 安装**：把 jar 丢进 `.minecraft/mods/`，启动游戏。

历史版本见 [Releases](https://github.com/2006522/TweakRoll/releases)。

## 按键

| 按键 | 作用 |
| --- | --- |
| `O` | 打开设置界面 |
| `Shift + 左键按住` | 扫过转移 |
| `R` / 界面右上角「整理」按钮 | 一键整理（背包 / 箱子等容器） |

`O` 可以在原版「按键设置 → 物品栏」里改。

## 配置

```properties
shiftHoverMove=true   # Shift+左键扫过转移
sortInventory=true    # R 键一键整理（背包 + 容器）
instantSort=true      # 单人游戏走服务端直改（零点击）
sortButton=true        # 显示「整理」按钮
sortButtonDx=-40       # 按钮位置（相对面板右边缘，可拖动改）
sortButtonDy=-18       # 按钮位置（相对面板顶边）
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

它会自动：构建 → 同步 README/release 目录里的版本号 → `git add/commit/push`
→ 建 Release（标题「鼠标手势 (TweakRoll) <版本>」）→ 上传 `TweakRoll-<版本>.jar`
→ 把附件显示名设为中文全名 → **把新 jar 部署到游戏 mods 目录**。

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

## 实现要点

### 为什么不用 Gradle / Loom

26.4-snapshot-5 这个快照：Mojang 没发 `client_mappings`，Fabric 也没发 Yarn，
但**客户端 jar 本身没有混淆**（`net/minecraft/client/Minecraft.class` 直接可读）。
所以直接对官方 jar 编译即可，产物命名空间就是 `official`，不需要 remap。

受保护成员 —— `AbstractContainerScreen` 的 `leftPos` / `topPos` / `hoveredSlot` / `slotClicked`
—— 都用反射拿，类加载时缓存一次（`slotClicked` 用 `Method.invoke` 走虚分派，
所以创造模式背包的重写版本也生效）。

### 唯一的一处 mixin

`ContainerScreenMixin` 注入 `AbstractContainerScreen.extractTooltip` 的 HEAD，
扫过期间直接 cancel，避免物品名一格一格地闪。平时悬停看物品名不受影响。

配置里 `defaultRequire` 是 `0`：万一以后原版方法改名导致注入失败，
模组只会退回"提示照旧显示"，不会让游戏崩溃。

### 三个必须知道的平台细节

**一、拖拽事件要在 MouseHandler 层拦。**
原版 `AbstractContainerScreen.mouseDragged` 在把格子加进 `quickCraftSlots` 之后会直接 `return true`，
不会调到 `Screen.mouseDragged`。Fabric 的 `ScreenMouseEvents` 是在 `MouseHandler` 里用
`@WrapOperation` 拦调用点的，所以事件照样收得到 —— 模组挂在这里，每个鼠标移动事件都回调。
另外用 20Hz 的客户端 tick 轮询兜底，两条路共用状态去重。

**二、键盘要读 SDL 扫描码。**
`InputConstants.isKeyDown(int)` 内部是 `SDLKeyboard.SDL_GetKeyboardState()` 拿数组后
**用这个 int 直接当下标**，所以它要的是 **SDL 扫描码**：左 Shift = `225`、右 Shift = `229`、O = `18`。
传 GLFW 的 `340/344` 会读越界、永远返回 false。代码里统一用 `InputConstants.KEY_*` 常量。

**三、鼠标按键是 1 起编号。**
`MouseButtonInfo.isLeftMouse()` 的判断是 `button == 1`（SDL 风格：左=1、中=2、右=3），
所以不能写 `event.button() == 0`，要用 `event.isLeftMouse()`。

## 已知限制

- 只处理标准容器界面；模组自定义槽位布局如果不用 `slot.x/slot.y`，会回退到原版 `hoveredSlot`。
- 服务端会照常收到快速移动的点击包，所以多人游戏里同样有效（受服务器反作弊/延迟影响）。
- 整理背包是模拟原版单击完成的（约 80 次/秒，连续几个 tick 内发完），
  延迟很高的服务器上可能看到物品成批跳动。
