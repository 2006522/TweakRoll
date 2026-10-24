package com.tweakroll;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 界面里的「整理」按钮，可以按住拖动来换位置，位置记在配置里。
 *
 * <p>按钮本身由 {@link Button} 负责画，但我们把点击事件截下来了：
 * 按下先记起点，移动超过阈值就算拖动（改位置），没移动就当点击（执行整理）。
 * 这样"拖"和"点"不会互相干扰。</p>
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
    private static double startX;
    private static double startY;
    private static double lastX;
    private static double lastY;

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

    /** 按下：落在按钮上就进入"待拖动"状态，并吃掉这次点击。 */
    static boolean onClick(Screen s, MouseButtonEvent event) {
        if (widget == null || screen != s || !event.isLeftMouse()) {
            return true;
        }
        if (!widget.isMouseOver(event.x(), event.y())) {
            return true;
        }
        dragging = true;
        moved = false;
        startX = lastX = event.x();
        startY = lastY = event.y();
        return false;
    }

    /** 拖动：跟着鼠标走。 */
    static boolean onDrag(Screen s, MouseButtonEvent event, double deltaX, double deltaY) {
        if (!dragging || widget == null || screen != s) {
            return true;
        }
        // 注意：后两个参数是拖拽增量，不是鼠标位置，坐标要从 event 里取
        double mouseX = event.x();
        double mouseY = event.y();
        if (Math.abs(mouseX - startX) > DRAG_THRESHOLD || Math.abs(mouseY - startY) > DRAG_THRESHOLD) {
            moved = true;
        }
        setPos((int) Math.round(widget.getX() + (mouseX - lastX)),
                (int) Math.round(widget.getY() + (mouseY - lastY)));
        lastX = mouseX;
        lastY = mouseY;
        return false;
    }

    /** 松开：没移动就是点击（执行整理），移动过就保存新位置。 */
    static boolean onRelease(Screen s, MouseButtonEvent event) {
        if (!dragging || widget == null || screen != s) {
            return true;
        }
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
