# 鼠标手势 (TweakRoll)

**按住 Shift 用左键扫一排，物品栏和背包就换好了。**

给 **Minecraft 26.4-snapshot-5 (Fabric)** 的模组，单人存档直接装就能用。
**联机时服务端也要装** —— 1.13.0 起「Shift 搬运的落点顺序」是在服务端改的。

![version](https://img.shields.io/badge/version-1.13.0-blue)
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

界面上还有按钮：

| 界面 | 按钮 |
| --- | --- |
| 背包 | 「整理」——整理背包 27 格 |
| 箱子等容器 | 上方「箱子」——整理容器；下方「背包」——整理自己的背包 |
| 创造模式「物品栏」页 | 「整理」——整理背包 27 格（其他分类页不显示） |

**轻点**＝整理，**按住拖动**＝挪位置


## 下载安装

**① 下载**

[`鼠标手势 (TweakRoll) 1.12.3`](https://github.com/2006522/TweakRoll/releases/download/v1.12.3/TweakRoll-1.12.3.jar)

> 发布文件名不支持非 ASCII 字符，所以实际文件名是 `TweakRoll-1.12.3.jar`，
> 下载页上显示的名字是「鼠标手势 (TweakRoll) 1.12.3」。

**② 前置**：Fabric Loader ≥ 0.19.5 ＋ Fabric API（26.4 版本线，如 `0.164.1+26.4`）＋ Java 25

**③ 安装**：把 jar 丢进 `.minecraft/mods/`，启动游戏。

## 按键

| 按键 | 作用 |
| --- | --- |
| `O` | 打开设置界面 |
| `Shift + 左键按住` | 扫过转移 |
| `R` / 界面上的「整理」按钮 | 一键整理（背包 / 箱子等容器） |

`O` 可以在原版「按键设置 → 物品栏」里改。

## 配置

```properties
shiftHoverMove=true   # Shift+左键扫过转移
sortInventory=true    # R 键一键整理（背包 + 容器）
sortButton=true       # 显示「整理」按钮
sortButtonDx=-40      # 「箱子/整理」按钮位置（相对面板右边缘，可拖动改）
sortButtonDy=-18      # 同上（相对面板顶边）
sortBagButtonDx=-2147483648  # 容器界面里「背包」按钮位置，默认自动贴面板底部
sortBagButtonDy=-2147483648
sortCreativeDx=-42    # 创造模式「物品栏」页按钮位置，默认面板左侧
sortCreativeDy=20
```

文件在 `config/tweakroll.properties`，游戏内按 `O` 也能开关。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 26.4-snapshot-5 |
| Fabric Loader | >= 0.19.5（实测 0.20.1 正常） |
| Fabric API | 0.164.1+26.4 或更高（26.4 版本线） |
| Java | 25 |

## 已知限制

- 只处理标准容器界面；模组自定义槽位布局如果不用 `slot.x/slot.y`，会回退到原版 `hoveredSlot`。
- 创造模式只有「物品栏」页能整理；建筑方块、搜索等分类页放的是「能刷出来的物品」，不整理。
- 连别人的服务器时，整理由服务器逐格搬（约 0.6 秒），不会瞬间跳完。

## 许可证

[MIT](LICENSE)
