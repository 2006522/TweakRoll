# 更新日志

## v1.3.3

只加诊断，不改功能，用来定位"扫过没反应"到底卡在哪一步。

- 打开任意容器界面时输出 `容器界面已接入: <界面类名>`，确认事件挂钩成功。
- 只要容器开着、按住 Shift **或** 左键其一，就每秒输出一条
  `诊断: shift=? left=? slot=? 界面=? 坐标=?` —— 这样能区分
  "Shift 没读到"、"左键没读到"、"鼠标坐标对不上槽位" 三种情况。

## v1.3.2

对照 Mouse Tweaks 2.31 的实现补齐触发方式。

- 参考实现确认：Shift 检测就是 `InputConstants.isKeyDown(225)` / `(229)`（SDL 扫描码），
  与 v1.3.1 的修法一致。
- 新增**拖拽事件**触发路径（`ScreenMouseEvents.allowMouseDrag`）：Fabric 在 `MouseHandler`
  层用 `@WrapOperation` 拦截 `screen.mouseDragged`，每个鼠标移动事件都会回调，
  比 20Hz 的 tick 轮询跟手，扫过去不会漏格。
- 原有的 tick 轮询保留作兜底，两条路径共用同一份"上次处理过的格子"状态去重，
  不会重复转移。

## v1.3.1

修复 Shift + 左键扫过转移**完全不触发**。

- **根因**：26.x 起输入走 SDL，`InputConstants.isKeyDown(int)` 收的是 **SDL 扫描码**，
  不是 GLFW 键码。之前传的是 `340`/`344`（GLFW 的左右 Shift），
  在 SDL 键盘状态数组里读的是越界位置，永远 false —— 所以一直判定"没按 Shift"。
- 改用 `InputConstants.KEY_LSHIFT`（225）/ `KEY_RSHIFT`（229）。
- 顺带修正 `O` 键：原来用 `79`（GLFW），现在用 `InputConstants.KEY_O`（SDL 扫描码 18）。
- 轮询从逐帧渲染事件改为 `ClientTickEvents.END_CLIENT_TICK`（20 次/秒），
  不再依赖可能不派发的 `ScreenEvents.afterForeground`。
- 加了每秒一次的诊断日志（按住 Shift+左键时输出悬停槽位/坐标），方便后续排查。

## v1.3.0

- **只保留 Shift + 左键扫过转移**，删掉「左键拖拽收集」和「右键拖拽放置」：
  移除了 `onClick` / `onDrag` / `onRelease` 三个事件处理器、
  `ScreenMouseEvents` 的点击/拖拽/释放注册、`placeOne` / `collectInto` / `canAccept`。
- 配置只剩 `shiftHoverMove`；设置界面只剩一行开关。
- 发布命名统一为 **「鼠标手势 (TweakRoll)」**（Release 标题与 jar 文件名都用这个名字，
  不再带版本号），以后同样。

## v1.2.0

- **移除滚轮整理功能**（作者不需要）：连同"滚轮方向 / 搜索顺序 / 每次滚动数量"三项配置、
  滚轮整理开关、`左 Alt` 临时关闭当前容器的按键一起删掉。模组不再处理滚轮事件。
- 设置界面只剩三项：Shift+左键扫过转移、左键拖拽收集、右键拖拽放置。
- 配置文件只剩 `shiftHoverMove` / `lmbCollect` / `rmbPlace` 三个键；
  旧的 `wheelTweak`、`scrollDirection`、`searchOrder`、`scaling` 会被忽略并清掉。
- 简介、README、徽章同步到 1.2.0。

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
