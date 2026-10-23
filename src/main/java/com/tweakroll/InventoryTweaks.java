package com.tweakroll;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 鼠标整理逻辑：Shift+左键扫过转移 / 左键拖拽收集 / 右键拖拽放置。
 */
final class InventoryTweaks {
    private static final int GLFW_KEY_LEFT_SHIFT = 340;
    private static final int GLFW_KEY_RIGHT_SHIFT = 344;

    private enum Mode {
        NONE,
        COLLECT,
        PLACE
    }

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

    private static boolean shiftDown() {
        return InputConstants.isKeyDown(GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
    }

    // ------------------------------------------------------------------
    // 查找与判定
    // ------------------------------------------------------------------

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
