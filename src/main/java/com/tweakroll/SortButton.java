package com.tweakroll;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 界面里的「整理」按钮：背包界面一个（整理背包），箱子等容器界面两个
 * （上面的整理箱子、下面的整理背包）。
 *
 * <p>按住拖动可以换位置，位置按"相对面板"记在配置里。拖动不用增量累加，
 * 而是记下按下时鼠标相对按钮的偏移，之后按"鼠标位置 - 偏移"重算，所以跟手。</p>
 */
public final class SortButton {
    private static final int WIDTH = 38;
    private static final int HEIGHT = 16;
    /** 移动超过这么多像素就判定为拖动，而不是点击。 */
    private static final int DRAG_THRESHOLD = 2;
    /** 第二颗按钮（背包）还没被拖过时的默认垂直位置：面板底边往上 20px。 */
    private static final int AUTO = Integer.MIN_VALUE;

    private static final List<SortButton> ACTIVE = new ArrayList<>();

    private final AbstractContainerScreen<?> screen;
    private final int[] slots;
    /** true 表示这是"背包"那颗按钮（用 sortBagButton* 配置）。 */
    private final boolean bagButton;
    private Button widget;
    private boolean dragging;
    private boolean moved;
    private double grabOffsetX;
    private double grabOffsetY;
    private double startX;
    private double startY;

    private SortButton(AbstractContainerScreen<?> screen, int[] slots, boolean bagButton) {
        this.screen = screen;
        this.slots = slots;
        this.bagButton = bagButton;
    }

    /** 每次界面初始化都调一次（窗口缩放会重建控件）。 */
    static void attach(AbstractContainerScreen<?> screen) {
        ACTIVE.clear();
        Config config = TweakRoll.config();
        if (!config.sortInventory || !config.sortButton) {
            return;
        }
        int[] containerSlots = InventorySorter.containerSlots(screen.getMenu());
        int[] bagSlots = InventorySorter.bagSlots(screen.getMenu());

        if (screen.getMenu() instanceof InventoryMenu) {
            if (bagSlots.length > 0) {
                add(new SortButton(screen, bagSlots, false), "tweakroll.button.sort");
            }
            return;
        }
        if (containerSlots.length > 0) {
            add(new SortButton(screen, containerSlots, false), "tweakroll.button.sort_container");
        }
        if (bagSlots.length > 0) {
            add(new SortButton(screen, bagSlots, true), "tweakroll.button.sort_bag");
        }
    }

    private static void add(SortButton button, String labelKey) {
        button.widget = Button.builder(Component.translatable(labelKey),
                        pressed -> InventorySorter.trigger(button.screen, button.slots))
                .bounds(0, 0, WIDTH, HEIGHT)
                .build();
        button.place();
        ScreenBridge.addWidget(button.screen, button.widget);
        ACTIVE.add(button);
        Log.info("整理按钮已挂载：" + button.screen.getClass().getSimpleName()
                + " pos=" + button.widget.getX() + "," + button.widget.getY());
    }

    /** 按下：落在任意一颗按钮上就记拖动起点，并吃掉这次点击。 */
    public static boolean onClick(Screen s, MouseButtonEvent event) {
        if (!event.isLeftMouse()) {
            return true;
        }
        for (SortButton button : ACTIVE) {
            if (button.screen == s && button.widget != null
                    && button.widget.isMouseOver(event.x(), event.y())) {
                button.startDrag(event.x(), event.y());
                return false;
            }
        }
        return true;
    }

    /** 拖动：立刻跟随。 */
    public static boolean onDrag(Screen s, MouseButtonEvent event, double deltaX, double deltaY) {
        boolean handled = false;
        for (SortButton button : ACTIVE) {
            if (button.dragging && button.screen == s) {
                button.follow();
                handled = true;
            }
        }
        return !handled;
    }

    /** 松开：没移动就是点击（执行整理），移动过就保存新位置。 */
    public static boolean onRelease(Screen s, MouseButtonEvent event) {
        boolean handled = false;
        for (SortButton button : ACTIVE) {
            if (button.dragging && button.screen == s) {
                button.finishDrag();
                handled = true;
            }
        }
        return !handled;
    }

    /** 每个客户端 tick 跟一次，拖拽事件稀疏时也不掉队。 */
    static void tick(Minecraft client) {
        for (SortButton button : ACTIVE) {
            if (button.dragging) {
                button.follow();
            }
        }
    }

    private void startDrag(double mouseX, double mouseY) {
        dragging = true;
        moved = false;
        grabOffsetX = mouseX - widget.getX();
        grabOffsetY = mouseY - widget.getY();
        startX = mouseX;
        startY = mouseY;
    }

    private void finishDrag() {
        follow();
        dragging = false;
        if (!moved) {
            InventorySorter.trigger(screen, slots);
            return;
        }
        Config config = TweakRoll.config();
        int dx = widget.getX() - (ScreenBridge.leftPos(screen) + ScreenBridge.imageWidth(screen));
        int dy = widget.getY() - ScreenBridge.topPos(screen);
        if (bagButton) {
            config.sortBagButtonDx = dx;
            config.sortBagButtonDy = dy;
        } else {
            config.sortButtonDx = dx;
            config.sortButtonDy = dy;
        }
        config.save();
        Log.info("整理按钮位置已保存：" + (bagButton ? "背包" : "主") + " dx=" + dx + ", dy=" + dy);
    }

    private void follow() {
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

    /** 按配置摆放；背包那颗没被拖过时贴着面板底部放。 */
    private void place() {
        Config config = TweakRoll.config();
        int baseX = ScreenBridge.leftPos(screen) + ScreenBridge.imageWidth(screen);
        int baseY = ScreenBridge.topPos(screen);
        if (bagButton && config.sortBagButtonDx == AUTO) {
            setPos(baseX + 2, baseY + ScreenBridge.imageHeight(screen) - 20);
            return;
        }
        int dx = bagButton ? config.sortBagButtonDx : config.sortButtonDx;
        int dy = bagButton ? config.sortBagButtonDy : config.sortButtonDy;
        setPos(baseX + dx, baseY + dy);
    }

    /** 设置位置，并夹在窗口可见范围内。 */
    private void setPos(int x, int y) {
        if (widget == null) {
            return;
        }
        int maxX = Math.max(0, screen.width - WIDTH);
        int maxY = Math.max(0, screen.height - HEIGHT);
        widget.setX(Math.max(0, Math.min(maxX, x)));
        widget.setY(Math.max(0, Math.min(maxY, y)));
    }
}
