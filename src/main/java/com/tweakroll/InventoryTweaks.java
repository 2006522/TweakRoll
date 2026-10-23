package com.tweakroll;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 鼠标整理逻辑本体：滚轮搬运 + 左键拖拽收集 + 右键拖拽放置 + Shift 扫过快速转移。
 */
final class InventoryTweaks {
    private static final int GLFW_KEY_LEFT_SHIFT = 340;
    private static final int GLFW_KEY_RIGHT_SHIFT = 344;

    private enum Mode {
        NONE,
        COLLECT,
        PLACE
    }

    private static final Map<Screen, Boolean> WHEEL_DISABLED = new WeakHashMap<>();

    private static AbstractContainerScreen<?> dragScreen;
    private static Slot lastDragSlot;
    private static int dragButton = -1;
    private static Mode mode = Mode.NONE;

    // Shift+左键扫过转移的逐帧状态
    private static boolean sweepLeftHeld;
    private static Slot lastSweepSlot;
    private static boolean sweepLogged;

    private InventoryTweaks() {
    }

    // ------------------------------------------------------------------
    // 事件入口
    // ------------------------------------------------------------------

    static boolean onScroll(Screen screen, double mouseX, double mouseY, double horizontal, double vertical) {
        // 普通鼠标只有竖直轴有值；取绝对值大的那个，避免参数顺序差异导致失效
        double amount = Math.abs(vertical) >= Math.abs(horizontal) ? vertical : horizontal;
        if (!(screen instanceof AbstractContainerScreen<?> container) || amount == 0.0D) {
            return true;
        }
        Config config = TweakRoll.config();
        if (!config.wheelTweak || !ScreenBridge.isReady() || isWheelDisabled(container)) {
            return true;
        }
        Slot hovered = slotAt(container, mouseX, mouseY);
        if (hovered == null) {
            return true;
        }

        boolean towardsOtherInventory = amount > 0.0D;
        if (config.scrollDirection == Config.ScrollDirection.INVERTED) {
            towardsOtherInventory = !towardsOtherInventory;
        }
        int steps = config.scaling == Config.Scaling.ALWAYS_ONE
                ? 1
                : Math.max(1, (int) Math.round(Math.abs(amount)));

        AbstractContainerMenu menu = container.getMenu();
        if (!menu.getCarried().isEmpty()) {
            // 光标上有物品：滚轮往悬停格里一个一个放
            for (int i = 0; i < steps; i++) {
                placeOne(container, hovered);
            }
        } else {
            for (int i = 0; i < steps; i++) {
                moveOneAcross(container, hovered, towardsOtherInventory);
            }
        }
        return true;
    }

    static boolean onClick(Screen screen, MouseButtonEvent event) {
        resetDrag();
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return true;
        }
        Config config = TweakRoll.config();
        if (!ScreenBridge.isReady()) {
            return true;
        }
        int button = event.button();
        Slot slot = slotAt(container, event.x(), event.y());
        ItemStack carried = container.getMenu().getCarried();

        // Shift + 左键完全交给逐帧轮询处理：
        // 原版 AbstractContainerScreen.mouseDragged 在加入快捷合成格子后会提前 return，
        // 拖拽事件根本到不了这里，所以不能依赖事件。
        if (button == 0 && shiftDown()) {
            return true;
        }

        if (slot == null) {
            return true;
        }

        if (button == 0 && config.lmbCollect && carried.isEmpty() && !slot.getItem().isEmpty()) {
            // 原版这次点击会把整叠拿起来，之后的拖拽由我们接管
            dragScreen = container;
            dragButton = 0;
            mode = Mode.COLLECT;
            lastDragSlot = slot;
        } else if (button == 1 && config.rmbPlace && !carried.isEmpty()) {
            // 原版这次点击会放下一个，之后的拖拽由我们接管
            dragScreen = container;
            dragButton = 1;
            mode = Mode.PLACE;
            lastDragSlot = slot;
        }
        return true;
    }

    static boolean onDrag(Screen screen, MouseButtonEvent event, double mouseX, double mouseY) {
        if (mode == Mode.NONE || dragScreen != screen || event.button() != dragButton) {
            return true;
        }
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return true;
        }
        Slot slot = slotAt(container, mouseX, mouseY);
        if (slot == null || slot == lastDragSlot) {
            return false;
        }
        lastDragSlot = slot;
        if (mode == Mode.COLLECT) {
            collectInto(container, slot);
        } else {
            placeOne(container, slot);
        }
        // 接管拖拽，避免原版快速合成逻辑同时生效
        return false;
    }

    static boolean onRelease(Screen screen, MouseButtonEvent event) {
        resetDrag();
        return true;
    }

    static void onScreenOpened(AbstractContainerScreen<?> screen) {
        resetDrag();
        resetSweep();
        WHEEL_DISABLED.remove(screen);
    }

    /**
     * 每帧调用（ScreenEvents.afterForeground）。
     *
     * <p>Shift + 左键按住时，鼠标扫过的每个新格子都补一次原版快速移动。
     * 之所以不用拖拽事件：原版 {@code AbstractContainerScreen.mouseDragged} 在把格子加进
     * quickCraftSlots 后会直接 return true，根本走不到 {@code Screen.mouseDragged}，
     * Fabric 的 allowMouseDrag 事件因此收不到。</p>
     */
    static void tickSweep(AbstractContainerScreen<?> screen) {
        Config config = TweakRoll.config();
        if (!config.shiftHoverMove || !ScreenBridge.isReady()) {
            resetSweep();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            resetSweep();
            return;
        }
        if (!client.mouseHandler.isLeftPressed() || !shiftDown()) {
            resetSweep();
            return;
        }
        double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
        double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
        Slot slot = slotAt(screen, mouseX, mouseY);
        if (!sweepLeftHeld) {
            // 按下的第一格已经由原版处理过了，这里只记录、不重复触发
            sweepLeftHeld = true;
            lastSweepSlot = slot;
            return;
        }
        if (slot == null || slot == lastSweepSlot) {
            return;
        }
        lastSweepSlot = slot;
        if (!sweepLogged) {
            sweepLogged = true;
            Log.info("Shift 扫过转移已触发（槽位 " + slot.index + "）");
        }
        quickMove(screen, slot);
    }

    private static void resetSweep() {
        sweepLeftHeld = false;
        lastSweepSlot = null;
        sweepLogged = false;
    }

    static void toggleWheel(Screen screen) {
        if (screen == null) {
            return;
        }
        boolean nowDisabled = !isWheelDisabled(screen);
        WHEEL_DISABLED.put(screen, nowDisabled);
        Log.info(nowDisabled ? "当前容器已关闭滚轮整理" : "当前容器已开启滚轮整理");
    }

    static boolean isWheelDisabled(Screen screen) {
        return Boolean.TRUE.equals(WHEEL_DISABLED.get(screen));
    }

    // ------------------------------------------------------------------
    // 具体操作
    // ------------------------------------------------------------------

    /** 往悬停格放一个光标上的物品。 */
    private static void placeOne(AbstractContainerScreen<?> screen, Slot slot) {
        AbstractContainerMenu menu = screen.getMenu();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || !canAccept(slot, carried)) {
            return;
        }
        ScreenBridge.click(screen, slot, 1);
    }

    /** 走原版 Shift+左键 的快速移动：物品栏 → 背包，背包 → 物品栏（容器界面则与容器互换）。 */
    private static void quickMove(AbstractContainerScreen<?> screen, Slot slot) {
        if (slot == null || slot.getItem().isEmpty()) {
            return;
        }
        ScreenBridge.click(screen, slot, 0, ContainerInput.QUICK_MOVE);
    }

    private static boolean shiftDown() {
        return InputConstants.isKeyDown(GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
    }

    /** 把悬停格与另一侧背包之间搬运一个物品。 */
    private static void moveOneAcross(AbstractContainerScreen<?> screen, Slot hovered, boolean push) {
        ItemStack reference = hovered.getItem();
        if (reference.isEmpty()) {
            return;
        }
        Slot counterpart = findCounterpart(screen, hovered, push);
        if (counterpart == null) {
            return;
        }
        if (push) {
            transferOne(screen, hovered, counterpart);
        } else {
            transferOne(screen, counterpart, hovered);
        }
    }

    /**
     * 精确搬运一个物品：整叠拿起 → 右键放一个 → 剩余放回。
     */
    private static void transferOne(AbstractContainerScreen<?> screen, Slot from, Slot to) {
        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        ItemStack fromStack = from.getItem();
        if (fromStack.isEmpty() || !from.mayPlace(fromStack)) {
            // 输出槽之类的格子放不回去，跳过以免物品卡在光标上
            return;
        }
        if (!canAccept(to, fromStack)) {
            return;
        }
        ScreenBridge.click(screen, from, 0);
        if (menu.getCarried().isEmpty()) {
            return;
        }
        ScreenBridge.click(screen, to, 1);
        ScreenBridge.click(screen, from, 0);
    }

    /** 把格子里的同种物品并到光标上。 */
    private static void collectInto(AbstractContainerScreen<?> screen, Slot slot) {
        AbstractContainerMenu menu = screen.getMenu();
        ItemStack carried = menu.getCarried();
        ItemStack stack = slot.getItem();
        if (carried.isEmpty() || stack.isEmpty()) {
            return;
        }
        if (!ItemStack.isSameItemSameComponents(stack, carried)) {
            return;
        }
        ScreenBridge.click(screen, slot, 0);
    }

    // ------------------------------------------------------------------
    // 查找与判定
    // ------------------------------------------------------------------

    private static Slot findCounterpart(AbstractContainerScreen<?> screen, Slot hovered, boolean push) {
        List<Slot> candidates = otherSideSlots(screen, hovered);
        if (candidates.isEmpty()) {
            return null;
        }
        if (TweakRoll.config().searchOrder == Config.SearchOrder.LAST_TO_FIRST) {
            candidates = new ArrayList<>(candidates);
            Collections.reverse(candidates);
        }
        ItemStack reference = hovered.getItem();
        for (Slot candidate : candidates) {
            if (candidate == hovered || !candidate.isActive()) {
                continue;
            }
            if (push) {
                if (canAccept(candidate, reference)) {
                    return candidate;
                }
            } else {
                ItemStack stack = candidate.getItem();
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, reference)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * 悬停格的“另一侧”：玩家背包 ↔ 容器。
     * 纯玩家背包界面（没有容器侧）时退回为玩家背包内部，避免把物品塞进合成格。
     */
    private static List<Slot> otherSideSlots(AbstractContainerScreen<?> screen, Slot hovered) {
        List<Slot> all = screen.getMenu().slots;
        boolean hoveredIsPlayerSide = isPlayerInventory(hovered);
        boolean hasContainerSide = false;
        for (Slot slot : all) {
            if (!isPlayerInventory(slot)) {
                hasContainerSide = true;
                break;
            }
        }
        List<Slot> result = new ArrayList<>();
        for (Slot slot : all) {
            if (slot == hovered) {
                continue;
            }
            if (!hasContainerSide) {
                // 只有玩家背包时，只允许在背包格之间搬运，避免把物品塞进合成格
                if (isPlayerInventory(slot)) {
                    result.add(slot);
                }
            } else if (isPlayerInventory(slot) != hoveredIsPlayerSide) {
                result.add(slot);
            }
        }
        return result;
    }

    private static boolean isPlayerInventory(Slot slot) {
        return slot.container instanceof Inventory;
    }

    private static boolean canAccept(Slot slot, ItemStack stack) {
        if (stack.isEmpty() || !slot.mayPlace(stack)) {
            return false;
        }
        ItemStack existing = slot.getItem();
        if (existing.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(existing, stack)
                && existing.getCount() < slot.getMaxStackSize(stack);
    }

    private static Slot slotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (!ScreenBridge.isReady()) {
            return null;
        }
        int left = ScreenBridge.leftPos(screen);
        int top = ScreenBridge.topPos(screen);
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) {
                continue;
            }
            double x = left + slot.x;
            double y = top + slot.y;
            if (mouseX >= x && mouseX < x + 16.0D && mouseY >= y && mouseY < y + 16.0D) {
                return slot;
            }
        }
        // 保底用原版自己算出来的悬停格
        return ScreenBridge.hoveredSlot(screen);
    }

    private static void resetDrag() {
        dragScreen = null;
        lastDragSlot = null;
        dragButton = -1;
        mode = Mode.NONE;
    }
}
