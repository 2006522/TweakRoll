package com.tweakroll;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
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

    /** 按界面分组保存按钮：创造模式和生存背包会同时挂载，不能共用一个全局列表。 */
    private static final java.util.Map<Screen, List<SortButton>> ACTIVE = new java.util.WeakHashMap<>();

    private final AbstractContainerScreen<?> screen;
    private final int[] slots;
    /** 0=主（背包界面 / 容器那颗），1=容器界面里的「背包」，2=创造模式。 */
    private final int kind;
    private Button widget;
    private boolean dragging;
    private boolean moved;
    private double grabOffsetX;
    private double grabOffsetY;
    private double startX;
    private double startY;
    private int startWidgetX;
    private int startWidgetY;

    private static final int KIND_PRIMARY = 0;
    private static final int KIND_BAG = 1;
    private static final int KIND_CREATIVE = 2;

    private SortButton(AbstractContainerScreen<?> screen, int[] slots, int kind) {
        this.screen = screen;
        this.slots = slots;
        this.kind = kind;
    }

    /** 每次界面初始化都调一次（窗口缩放会重建控件）。 */
    static void attach(AbstractContainerScreen<?> screen) {
        ACTIVE.remove(screen);
        Config config = TweakRoll.config();
        if (!config.sortInventory || !config.sortButton) {
            return;
        }
        int[] containerSlots = InventorySorter.containerSlots(screen.getMenu());
        int[] bagSlots = InventorySorter.bagSlots(screen.getMenu());

        if (screen instanceof CreativeModeInventoryScreen) {
            // 创造模式背包：只有一颗，位置和箱子界面的「背包」按钮分开存
            if (bagSlots.length > 0) {
                add(new SortButton(screen, bagSlots, KIND_CREATIVE), "tweakroll.button.sort");
            }
            return;
        }
        if (screen.getMenu() instanceof InventoryMenu) {
            if (bagSlots.length > 0) {
                add(new SortButton(screen, bagSlots, KIND_PRIMARY), "tweakroll.button.sort");
            }
            return;
        }
        if (containerSlots.length > 0) {
            add(new SortButton(screen, containerSlots, KIND_PRIMARY), "tweakroll.button.sort_container");
        }
        if (bagSlots.length > 0) {
            add(new SortButton(screen, bagSlots, KIND_BAG), "tweakroll.button.sort_bag");
        }
    }

    private static void add(SortButton button, String labelKey) {
        List<SortButton> list = ACTIVE.computeIfAbsent(button.screen, key -> new ArrayList<>());
        button.widget = Button.builder(Component.translatable(labelKey),
                        pressed -> InventorySorter.trigger(button.screen, button.slots))
                .bounds(0, 0, WIDTH, HEIGHT)
                .build();
        button.place();
        if (button.kind == KIND_CREATIVE) {
            button.widget.setVisible(creativeInventoryOpen(button.screen));
        }
        ScreenBridge.addWidget(button.screen, button.widget);
        list.add(button);
        Log.info("整理按钮已挂载：" + button.screen.getClass().getSimpleName()
                + " pos=" + button.widget.getX() + "," + button.widget.getY());
    }

    /** 按下：落在任意一颗按钮上就记拖动起点，并吃掉这次点击。 */
    public static boolean onClick(Screen s, MouseButtonEvent event) {
        if (!event.isLeftMouse()) {
            return true;
        }
        for (SortButton button : buttonsOf(s)) {
            if (button.widget != null && button.widget.isVisible()
                    && button.widget.isMouseOver(event.x(), event.y())) {
                button.startDrag(event.x(), event.y());
                debugOnce("hit:" + s.getClass().getSimpleName(),
                        "按钮点击命中：" + s.getClass().getSimpleName());
                return false;
            }
        }
        return true;
    }

    /** 拖动：立刻跟随。 */
    public static boolean onDrag(Screen s, MouseButtonEvent event, double deltaX, double deltaY) {
        boolean handled = false;
        for (SortButton button : buttonsOf(s)) {
            if (button.dragging) {
                button.follow();
                handled = true;
            }
        }
        return !handled;
    }

    /** 松开：没移动就是点击（执行整理），移动过就保存新位置。 */
    public static boolean onRelease(Screen s, MouseButtonEvent event) {
        boolean handled = false;
        for (SortButton button : buttonsOf(s)) {
            if (button.dragging) {
                button.finishDrag();
                handled = true;
            }
        }
        return !handled;
    }

    /** 每个客户端 tick 跟一次，拖拽事件稀疏时也不掉队。 */
    static void tick(Minecraft client) {
        for (List<SortButton> list : ACTIVE.values()) {
            for (SortButton button : list) {
                if (button.kind == KIND_CREATIVE && button.widget != null) {
                    boolean show = creativeInventoryOpen(button.screen);
                    if (show && !ScreenBridge.isRegistered(button.screen, button.widget)) {
                        // 切标签页后界面重建了控件列表，把按钮补挂回去
                        ScreenBridge.addWidget(button.screen, button.widget);
                        Log.info("整理按钮被界面清掉，已重新挂载");
                    }
                    if (button.widget.isVisible() != show) {
                        Log.info("整理按钮显示状态 -> " + show);
                    }
                    button.widget.setVisible(show);
                    if (!show) {
                        button.dragging = false;
                        continue;
                    }
                }
                if (button.dragging) {
                    button.follow();
                }
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
        startWidgetX = widget.getX();
        startWidgetY = widget.getY();
    }

    /** 同一条诊断只打一次，避免刷屏。 */
    private static final java.util.Set<String> DEBUG_SEEN = new java.util.HashSet<>();

    private static List<SortButton> buttonsOf(Screen screen) {
        List<SortButton> list = ACTIVE.get(screen);
        return list == null ? List.of() : list;
    }

    private static void debugOnce(String key, String message) {
        if (DEBUG_SEEN.add(key)) {
            Log.info(message);
        }
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
        switch (kind) {
            case KIND_BAG -> {
                config.sortBagButtonDx = dx;
                config.sortBagButtonDy = dy;
            }
            case KIND_CREATIVE -> {
                config.sortCreativeDx = dx;
                config.sortCreativeDy = dy;
            }
            default -> {
                config.sortButtonDx = dx;
                config.sortButtonDy = dy;
            }
        }
        config.save();
        Log.info("整理按钮位置已保存：" + kindName() + " dx=" + dx + ", dy=" + dy);
    }

    /** 创造模式下当前是不是「物品栏」那一页（原版就有公开方法 `isInventoryOpen()`）。 */
    private static boolean creativeInventoryOpen(AbstractContainerScreen<?> screen) {
        if (screen instanceof CreativeModeInventoryScreen creative) {
            try {
                return creative.isInventoryOpen();
            } catch (Throwable throwable) {
                return true;
            }
        }
        return true;
    }

    private String kindName() {
        return switch (kind) {
            case KIND_BAG -> "背包";
            case KIND_CREATIVE -> "创造";
            default -> "主";
        };
    }

    private void follow() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            return;
        }
        double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
        double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
        setPos((int) Math.round(mouseX - grabOffsetX), (int) Math.round(mouseY - grabOffsetY));
        if (Math.abs(mouseX - startX) > DRAG_THRESHOLD
                || Math.abs(mouseY - startY) > DRAG_THRESHOLD
                || Math.abs(widget.getX() - startWidgetX) > DRAG_THRESHOLD
                || Math.abs(widget.getY() - startWidgetY) > DRAG_THRESHOLD) {
            moved = true;
        }
    }

    /** 按配置摆放；背包那颗没被拖过时贴着面板底部放。 */
    private void place() {
        Config config = TweakRoll.config();
        int baseX = ScreenBridge.leftPos(screen) + ScreenBridge.imageWidth(screen);
        int baseY = ScreenBridge.topPos(screen);
        if (kind == KIND_BAG && config.sortBagButtonDx == AUTO) {
            setPos(baseX + 2, baseY + ScreenBridge.imageHeight(screen) - 20);
            return;
        }
        int dx;
        int dy;
        switch (kind) {
            case KIND_BAG -> {
                dx = config.sortBagButtonDx;
                dy = config.sortBagButtonDy;
            }
            case KIND_CREATIVE -> {
                dx = config.sortCreativeDx;
                dy = config.sortCreativeDy;
            }
            default -> {
                dx = config.sortButtonDx;
                dy = config.sortButtonDy;
            }
        }
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
