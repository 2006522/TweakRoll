package com.tweakroll;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 界面里的「整理」按钮，可以按住拖动换位置，位置记在配置里。
 *
 * <p>拖动不用"增量累加"，而是记下按下时鼠标相对按钮的偏移（grab offset），
 * 之后每次事件 / 每个 tick 都按"鼠标位置 - 偏移"重算按钮位置。
 * 这样不会因为拖拽事件稀疏而发飘，跟手程度等于帧率。</p>
 */
final class SortButton {
    private static final int WIDTH = 38;
    private static final int HEIGHT = 16;
    /** 移动超过这么多像素就判定为拖动，而不是点击。 */
    private static final int DRAG_THRESHOLD = 2;

    private static AbstractContainerScreen<?> screen;
    private static Button widget;
    private static boolean dragging;
    private static boolean moved;
    private static double grabOffsetX;
    private static double grabOffsetY;
    private static double startX;
    private static double startY;

    private SortButton() {
    }

    /** 每次界面初始化都调一次（窗口缩放会重建控件）。 */
    static void attach(AbstractContainerScreen<?> target) {
        screen = target;
        widget = null;
        dragging = false;
        moved = false;
        if (!InventorySorter.canSort(target)) {
            return;
        }
        widget = Button.builder(Component.translatable("tweakroll.button.sort"),
                        pressed -> InventorySorter.trigger(target))
                .bounds(0, 0, WIDTH, HEIGHT)
                .build();
        int x = ScreenBridge.leftPos(target) + ScreenBridge.imageWidth(target) + TweakRoll.config().sortButtonDx;
        int y = ScreenBridge.topPos(target) + TweakRoll.config().sortButtonDy;
        setPos(x, y);
        ScreenBridge.addWidget(target, widget);
    }

    /** 按下：落在按钮上就记为拖动起点，并吃掉这次点击（避免按钮自己再触发一次）。 */
    static boolean onClick(Screen s, MouseButtonEvent event) {
        if (widget == null || screen != s || !event.isLeftMouse()) {
            return true;
        }
        if (!widget.isMouseOver(event.x(), event.y())) {
            return true;
        }
        dragging = true;
        moved = false;
        grabOffsetX = event.x() - widget.getX();
        grabOffsetY = event.y() - widget.getY();
        startX = event.x();
        startY = event.y();
        return false;
    }

    /** 拖动事件：立刻跟一下鼠标。 */
    static boolean onDrag(Screen s, MouseButtonEvent event, double deltaX, double deltaY) {
        if (!dragging || widget == null || screen != s) {
            return true;
        }
        follow();
        return false;
    }

    /** 松开：没移动就是点击（执行整理），移动过就保存新位置。 */
    static boolean onRelease(Screen s, MouseButtonEvent event) {
        if (!dragging || widget == null || screen != s) {
            return true;
        }
        follow();
        dragging = false;
        if (!moved) {
            InventorySorter.trigger(screen);
            return false;
        }
        Config config = TweakRoll.config();
        config.sortButtonDx = widget.getX() - (ScreenBridge.leftPos(screen) + ScreenBridge.imageWidth(screen));
        config.sortButtonDy = widget.getY() - ScreenBridge.topPos(screen);
        config.save();
        Log.info("整理按钮位置已保存：dx=" + config.sortButtonDx + ", dy=" + config.sortButtonDy);
        return false;
    }

    /** 每个客户端 tick 跟一次，拖拽事件稀疏时也不掉队。 */
    static void tick(Minecraft client) {
        if (dragging && widget != null) {
            follow();
        }
    }

    /** 按当前鼠标位置重新摆放按钮。 */
    private static void follow() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            return;
        }
        double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
        double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
        if (Math.abs(mouseX - startX) > DRAG_THRESHOLD || Math.abs(mouseY - startY) > DRAG_THRESHOLD) {
            moved = true;
        }
        setPos((int) Math.round(mouseX - grabOffsetX), (int) Math.round(mouseY - grabOffsetY));
    }

    /** 设置位置，并夹在窗口可见范围内。 */
    private static void setPos(int x, int y) {
        if (widget == null) {
            return;
        }
        int maxX = Math.max(0, screen.width - WIDTH);
        int maxY = Math.max(0, screen.height - HEIGHT);
        widget.setX(Math.max(0, Math.min(maxX, x)));
        widget.setY(Math.max(0, Math.min(maxY, y)));
    }
}
