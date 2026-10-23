package com.tweakroll;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * 唯一的功能：Shift + 左键按住，鼠标扫过哪个格子，那格物品就快速转移。
 */
final class InventoryTweaks {
    /** 本次按住期间是否已经记录过起始格（起始格由原版处理）。 */
    private static boolean sweepLeftHeld;
    /** 上一次触发过的格子，避免同一格重复转移。 */
    private static Slot lastSweepSlot;
    /** 每次按住只打一次日志，方便排查。 */
    private static boolean sweepLogged;
    /** 诊断用的计数器，每秒最多打一条状态。 */
    private static int diagTicks;

    private InventoryTweaks() {
    }

    static void onScreenOpened(AbstractContainerScreen<?> screen) {
        resetSweep();
        Log.info("容器界面已接入: " + screen.getClass().getSimpleName());
    }

    /**
     * 客户端 tick 调用（20 次/秒），作为兜底。
     * 只要容器界面开着、且按住了 Shift 或左键其一，就每秒打一条状态，方便定位问题。
     */
    static void tickSweep(AbstractContainerScreen<?> screen) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            resetSweep();
            return;
        }
        boolean left = client.mouseHandler.isLeftPressed();
        boolean shift = shiftDown();
        if (!left && !shift) {
            resetSweep();
            return;
        }
        double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
        double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
        Slot slot = slotAt(screen, mouseX, mouseY);
        if (++diagTicks >= 20) {
            diagTicks = 0;
            Log.info("诊断: shift=" + shift + " left=" + left
                    + " slot=" + (slot == null ? "null" : String.valueOf(slot.index))
                    + " 界面=" + screen.getClass().getSimpleName()
                    + " 坐标=" + Math.round(mouseX) + "," + Math.round(mouseY));
        }
        if (!left || !shift) {
            sweepLeftHeld = false;
            lastSweepSlot = null;
            return;
        }
        sweepAt(screen, slot);
    }

    /**
     * Fabric 的拖拽事件（MouseHandler 层拦截，每个鼠标移动事件都会触发），跟手。
     * 拿不到事件时上面的 tick 轮询兜底，两条路共用 {@link #lastSweepSlot} 去重。
     */
    static boolean onSweepDrag(Screen screen, MouseButtonEvent event, double mouseX, double mouseY) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return true;
        }
        if (event.button() != 0 || !TweakRoll.config().shiftHoverMove || !shiftDown()) {
            return true;
        }
        sweepAt(container, slotAt(container, mouseX, mouseY));
        // Shift+左键拖拽在原版里没有任何行为，直接吃掉
        return false;
    }

    /** 扫过转移的实际处理：同一格不重复触发，按下的第一格交给原版。 */
    private static void sweepAt(AbstractContainerScreen<?> screen, Slot slot) {
        if (!TweakRoll.config().shiftHoverMove || !ScreenBridge.isReady()) {
            resetSweep();
            return;
        }
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
        diagTicks = 0;
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
        // 注意：26.x 起输入走 SDL，InputConstants.isKeyDown 收的是 SDL 扫描码
        // （KEY_LSHIFT=225 / KEY_RSHIFT=229），不是 GLFW 的 340/344
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
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
