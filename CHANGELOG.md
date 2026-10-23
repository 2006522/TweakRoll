# 更新日志

## v1.1.1

修复 Shift + 左键扫过转移在游戏里不生效的问题。

- 原因：原版 `AbstractContainerScreen.mouseDragged` 在把格子加进 `quickCraftSlots` 之后会
  直接 `return true`，不会走到 `Screen.mouseDragged`，所以 Fabric 的
  `ScreenMouseEvents.allowMouseDrag` 事件在容器界面里根本收不到；
  同时 `MouseButtonEvent.hasShiftDown()` 在这里也不可靠。
- 改为**逐帧轮询**（`ScreenEvents.afterForeground`）：直接读物理按键状态
  （`InputConstants.isKeyDown` 判断 Shift、`MouseHandler.isLeftPressed()` 判断左键）
  和缩放后的鼠标坐标，扫到新格子就补一次 `ContainerInput.QUICK_MOVE`。
  按下的第一格仍交给原版处理，不会重复转移。

## v1.1.0

- 新增 **Shift + 左键扫过转移**：按住 Shift 再按住左键，鼠标扫过哪个格子，
  那一格的物品就快速转移 —— 在物品栏（快捷栏）里就移到背包，在背包里就移到物品栏；
  容器界面里则是 容器 ↔ 玩家背包 互换。
  - 中途松开 Shift 会立刻退出该模式，避免误操作。
  - 设置界面（O）里可以关掉这项。
- 设置界面行距压缩，容纳新增开关。

## v1.0.1

- 模组显示名改为「鼠标手势」（`fabric.mod.json` 的 `name`、设置界面标题、按键名称同步更新）。
  内部 mod id 仍为 `tweakroll`，配置路径 `config/tweakroll.properties` 不变。
- 作者改为 `2006522`。

## v1.0.0

首个版本，目标版本 Minecraft 26.4-snapshot-5 / Fabric Loader 0.19.5+ / Java 25。

- 滚轮整理：悬停格与另一侧背包之间逐个搬运物品，支持方向反转、查找顺序、
  每次滚动数量（固定 1 个 / 跟随滚动量）三项配置。
- 左键拖拽收集：光标空着时拖过同种物品格，把它们并到光标上。
- 右键拖拽放置：光标有物品时拖过格子，每格放一个。
- 容器级开关（左 Alt）+ 游戏内设置界面（O）+ `config/tweakroll.properties`。
- 技术上不使用 Gradle/Loom/mixin：直接对未混淆的官方客户端 jar 编译，
  受保护成员通过反射桥接并在类加载时缓存。
