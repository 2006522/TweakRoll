# 更新日志

## v1.7.0

背包和箱子界面右上角多了一个「整理」按钮，点一下就等于按 R。

- 位置：界面面板右上方（`leftPos + imageWidth - 40, topPos - 18`，贴边不挡住槽位）。
- 只会在支持整理的界面出现：背包、箱子/大箱子/木桶/潜影盒、发射器、漏斗。
- 窗口缩放会重建控件，所以每次界面 init 都重新挂一次。
- `addRenderableWidget` 是 protected，用反射调（和 `slotClicked` 一样）。
- 新增开关 `sortButton`（默认开），配置里可以关掉只用 R 键。

## v1.6.0

R 键现在**也能整理箱子**，不再只限背包。

- 支持的界面：箱子 / 大箱子 / 木桶 / 潜影盒 / 发射器（投掷器）/ 漏斗。  
  只整理容器自己那几格，玩家背包区不动。
- 背包界面照旧只整理背包 27 格（不碰快捷栏、盔甲、合成格）。
- 熔炉、工作台这类有结果格的界面**不会**整理，避免动到不该动的地方。
- 槽位识别方式：`Slot#container` 是玩家 `Inventory` 的就是玩家格，其余算容器格，  
  所以单箱 27 格 / 大箱 54 格 / 漏斗 5 格都能正确识别。
- 点击序列算法用 5 / 9 / 27 / 54 格各 200 组随机数据验证：全部排序正确、光标收尾为空。

## v1.5.2

- 整理完成后只留一行日志（`整理完成：改动 N 格`），去掉刷屏的预览和客户端确认。
- 上一版（1.5.1）修好的两点继续生效：同步补发全量状态、按住 R 不会连续触发。

## v1.5.1

- 修复单人整理后**界面看不到变化**：`broadcastChanges()` 之后再补一次 `broadcastFullState()`。
- 按住 R 时按键自动重复会连跑多次整理，加了 250ms 去抖。

## v1.5.0

单人游戏里整理背包变成**零点击**：直接改服务端数据，一次同步完成。

- 客户端 mod 在别人服务器上只能靠发包模拟点击（服务端权威），  
  但单人游戏的集成服务器就在本进程里 —— 检测到 `hasSingleplayerServer()` 时，  
  把排序交给服务端线程直接 `Slot#set` + `broadcastChanges()`，没有任何点击。
- 多人游戏自动退回点击模式（44 次点击、约 0.55 秒），无需手动切换。
- 新增开关 `instantSort`（默认开），设置界面第三行可关掉，关掉后一律走点击模式。

## v1.4.2

整理背包速度提升（满背包从约 3.5 秒缩到 0.6 秒左右）。

- 点击次数：改用**链式归位**（拾起一次后沿环依次放好，长度 k 的环只要 k+1 次点击，  
  原来要 3(k-1) 次）；合并时源堆叠被掏空就不再补第三次点击。平均点击 63 → 44。
- 发放速率：每个客户端 tick 发 4 次点击（20 tick/秒 → 约 80 次/秒），原来是每 tick 1 次。
- 算法用 400 组随机背包离线验证：排序结果全部正确、光标收尾为空。

## v1.4.1

- 创造模式背包有搜索框，正在输入时按 R 不再触发整理（会正常打出字母 r）。
- 发版脚本改为**可重复执行**：Release 已存在时复用、附件已存在时跳过上传，不会重复发布。

## v1.4.0

新增 **R 键一键整理背包**（在背包界面里按 R）。

- 合并同种物品堆叠 → 按物品 ID 排序 → 前面的格子排满，空格留到最后。
- 只动背包的 27 格，**不碰快捷栏、盔甲和合成格**，所以不会打乱手上的快捷栏顺序。
- 做法：先用本地模型算出完整的点击序列（每个动作 2~3 次「拾起 → 放下 → 放回」），  
  再按每 tick 一次（20 次/秒）发给服务端，避免一次性狂点导致不同步。
- 中途再按一次 R 可以取消；关掉背包界面会自动中止。
- 排序键可在「按键设置 → 物品栏」里改，设置界面（O）里有开关 `sortInventory`。

## v1.3.5

扫过转移时不再闪物品名（tooltip）。

- 原因：原版 `AbstractContainerScreen.extractTooltip` 只要悬停格有物品就画提示，  
  扫过时物品被移走、鼠标又落到下一格，名字就一格一格地闪。
- 加了一个极小 mixin（`com.tweakroll.mixin.ContainerScreenMixin`）注入 `extractTooltip` 的 HEAD，  
  扫过进行中直接 cancel，正常悬停时提示照旧。
- `defaultRequire: 0`，注入失败也只退回"提示照旧显示"，不会崩游戏。

## v1.3.4

修复左键判断——**这是"扫过完全没反应"的真正原因**。

- 26.x 的鼠标按键是 **SDL 编号**：左=1、中=2、右=3（`MouseButtonInfo.isLeftMouse()` 的字节码就是  
  `button == 1`）。之前用 `event.button() == 0` 判断左键，**每一次左键拖拽都被当成"不是左键"**，  
  直接 return 了。
- 改为用 `event.isLeftMouse()`。
- 左键按住状态改为由 `allowMouseClick` / `allowMouseRelease` 事件自己维护  
  （`MouseHandler.isLeftPressed()` 在容器界面里实测返回 false，不可靠）。
- 日志补充：拖拽路径首次接管时输出「拖拽事件已接管（左键+Shift）」。

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
