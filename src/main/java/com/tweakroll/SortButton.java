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
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 界面里的「整理」按钮：背包界面一个（整理背包），箱子等容器界面两个
 * （上面的整理箱子、下面的整理背包），创造模式只在「物品栏」页显示一个。
 *
 * <p>用原版 {@link Button} 控件，按住拖动可以换位置（位置按"相对面板"记在配置里）。
 * 拖动不用增量累加，而是记下按下时鼠标相对按钮的偏移，之后按"鼠标位置 - 偏移"重算，所以跟手。</p>
 */
public final class SortButton {
    private static final int WIDTH = 38;
    private static final int HEIGHT = 16;
    /** 移动超过这么多像素就判定为拖动，而不是点击。 */
    private static final int DRAG_THRESHOLD = 2;
    /** 「背包」那颗按钮还没被拖过时的哨兵值：自动贴面板底部。 */
    private static final int AUTO = Integer.MIN_VALUE;

    private static final int KIND_PRIMARY = 0;
    private static final int KIND_BAG = 1;

    /** 按界面分组：创造模式和生存背包会同时存在，不能共用一个全局列表。 */
    private static final Map<Screen, List<SortButton>> ACTIVE = new WeakHashMap<>();

    private final AbstractContainerScreen<?> screen;
    private final int[] slots;
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

    private SortButton(AbstractContainerScreen<?> screen, int[] slots, int kind) {
        this.screen = screen;
        this.slots = slots;
        this.kind = kind;
    }

    /** 界面初始化时调一次：算出要整理的槽位并挂上按钮。 */
    static void attach(AbstractContainerScreen<?> screen) {
        ACTIVE.remove(screen);
        Config config = TweakRoll.config();
        if (!config.sortInventory || !config.sortButton) {
            return;
        }
        int[] containerSlots = InventorySorter.containerSlots(screen.getMenu());
        int[] bagSlots = InventorySorter.bagSlots(screen.getMenu());

        // 创造模式物品栏不加按钮（只在生存模式的背包 / 容器界面出现）
        if (screen instanceof CreativeModeInventoryScreen) {
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
        ScreenBridge.addWidget(button.screen, button.widget);
        list.add(button);
        Log.info("整理按钮已挂载：" + button.screen.getClass().getSimpleName()
                + " pos=" + button.widget.getX() + "," + button.widget.getY());
    }

    // ---------------------------------------------------------------- 定时维护

    /** 每个客户端 tick 调一次：拖动跟随、位置重算、被界面清掉就补挂。 */
    static void tick(Minecraft client) {
        for (List<SortButton> list : ACTIVE.values()) {
            for (SortButton button : list) {
                if (button.widget == null) {
                    continue;
                }
                // 被界面重建清掉时补挂回来
                if (!button.dragging && !ScreenBridge.isRegistered(button.screen, button.widget)) {
                    ScreenBridge.addWidget(button.screen, button.widget);
                    Log.info("整理按钮被界面清掉，已重新挂载");
                }
                if (button.dragging) {
                    button.follow();
                } else {
                    button.place();
                }
            }
        }
    }

    // ---------------------------------------------------------------- 点击 / 拖动

    /** 按下：落在按钮上就记拖动起点，并吃掉这次点击。 */
    public static boolean onClick(Screen s, MouseButtonEvent event) {
        if (!event.isLeftMouse()) {
            return true;
        }
        for (SortButton button : buttonsOf(s)) {
            if (button.widget != null && button.widget.isVisible()
                    && button.widget.isMouseOver(event.x(), event.y())) {
                button.startDrag(event.x(), event.y());
                Log.info("按钮按下：" + s.getClass().getSimpleName()
                        + " 位置 " + (int) event.x() + "," + (int) event.y());
                return false;
            }
        }
        return true;
    }

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

    private static List<SortButton> buttonsOf(Screen screen) {
        List<SortButton> list = ACTIVE.get(screen);
        return list == null ? List.of() : list;
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

    private void follow() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            return;
        }
        double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
        double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
        setPos((int) Math.round(mouseX - grabOffsetX), (int) Math.round(mouseY - grabOffsetY));
        if (Math.abs(mouseX - startX) > DRAG_THRESHOLD || Math.abs(mouseY - startY) > DRAG_THRESHOLD
                || Math.abs(widget.getX() - startWidgetX) > DRAG_THRESHOLD
                || Math.abs(widget.getY() - startWidgetY) > DRAG_THRESHOLD) {
            moved = true;
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
            default -> {
                config.sortButtonDx = dx;
                config.sortButtonDy = dy;
            }
        }
        config.save();
        Log.info("整理按钮位置已保存：" + kindName() + " dx=" + dx + ", dy=" + dy);
    }

    // ---------------------------------------------------------------- 位置

    private void place() {
        if (widget == null) {
            return;
        }
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
            default -> {
                dx = config.sortButtonDx;
                dy = config.sortButtonDy;
            }
        }
        setPos(baseX + dx, baseY + dy);
    }

    private void setPos(int x, int y) {
        if (widget == null) {
            return;
        }
        int maxX = Math.max(0, screen.width - WIDTH);
        int maxY = Math.max(0, screen.height - HEIGHT);
        widget.setX(Math.max(0, Math.min(maxX, x)));
        widget.setY(Math.max(0, Math.min(maxY, y)));
    }

    private String kindName() {
        return kind == KIND_BAG ? "背包" : "主";
    }
}
