package com.tweakroll;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 桥接 {@link AbstractContainerScreen} 里的受保护成员。
 *
 * <p>26.x 的客户端 jar 没有混淆，所以这里直接用真实字段名反射即可；
 * 反射只用一次（类加载时缓存），鼠标事件里没有额外开销。</p>
 */
final class ScreenBridge {
    private static Field leftPosField;
    private static Field topPosField;
    private static Field hoveredSlotField;
    private static Method slotClickedMethod;
    private static boolean ready;

    private ScreenBridge() {
    }

    static synchronized void init() {
        if (ready) {
            return;
        }
        try {
            leftPosField = AbstractContainerScreen.class.getDeclaredField("leftPos");
            topPosField = AbstractContainerScreen.class.getDeclaredField("topPos");
            hoveredSlotField = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
            slotClickedMethod = AbstractContainerScreen.class.getDeclaredMethod(
                    "slotClicked", Slot.class, int.class, int.class, ContainerInput.class);
            for (Field field : new Field[]{leftPosField, topPosField, hoveredSlotField}) {
                field.setAccessible(true);
            }
            slotClickedMethod.setAccessible(true);
            ready = true;
        } catch (Throwable throwable) {
            Log.error("无法访问容器界面成员，整理功能将被禁用", throwable);
            ready = false;
        }
    }

    static boolean isReady() {
        return ready;
    }

    static int leftPos(AbstractContainerScreen<?> screen) {
        try {
            return leftPosField.getInt(screen);
        } catch (Throwable throwable) {
            return 0;
        }
    }

    static int topPos(AbstractContainerScreen<?> screen) {
        try {
            return topPosField.getInt(screen);
        } catch (Throwable throwable) {
            return 0;
        }
    }

    static Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        try {
            return (Slot) hoveredSlotField.get(screen);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * 走原版 {@code slotClicked}，这样创造模式背包等重写过的界面也能正确响应。
     */
    static void click(AbstractContainerScreen<?> screen, Slot slot, int button) {
        click(screen, slot, button, ContainerInput.PICKUP);
    }

    static void click(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput input) {
        if (slot == null) {
            return;
        }
        try {
            // Method.invoke 会走虚分派，因此 CreativeModeInventoryScreen 的重写依然生效
            slotClickedMethod.invoke(screen, slot, slot.index, button, input);
        } catch (Throwable throwable) {
            Log.error("模拟点击失败", throwable);
        }
    }
}
