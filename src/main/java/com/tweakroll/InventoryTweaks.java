package com.tweakroll;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * 唯一的功能：Shift + 左键按住，鼠标扫过哪个格子，那格物品就快速转移。
 */
final class InventoryTweaks {
    private static final int GLFW_KEY_LEFT_SHIFT = 340;
    private static final int GLFW_KEY_RIGHT_SHIFT = 344;

    /** 本次按住期间是否已经记录过起始格（起始格由原版处理）。 */
    private static boolean sweepLeftHeld;
    /** 上一次触发过的格子，避免同一格重复转移。 */
    private static Slot lastSweepSlot;
    /** 每次按住只打一次日志，方便排查。 */
    private static boolean sweepLogged;

    private InventoryTweaks() {
    }

    static void onScreenOpened(AbstractContainerScreen<?> screen) {
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
        if (!TweakRoll.config().shiftHoverMove || !ScreenBridge.isReady()) {
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

    /** 走原版 Shift+左键 的快速移动：物品栏 → 背包，背包 → 物品栏（容器界面则与容器互换）。 */
    private static void quickMove(AbstractContainerScreen<?> screen, Slot slot) {
        if (slot == null || slot.getItem().isEmpty()) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        ScreenBridge.click(screen, slot, 0, ContainerInput.QUICK_MOVE);
        // 触发后光标应保持为空，否则说明这次点击没能走通
        if (!menu.getCarried().isEmpty()) {
            Log.warn("快速移动后光标仍有物品，可能未生效");
        }
    }

    private static boolean shiftDown() {
        return InputConstants.isKeyDown(GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
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
}
