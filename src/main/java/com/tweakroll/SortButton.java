package com.tweakroll;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * <p><b>自己绘制、自己判定点击</b>，不往界面里加 Button 控件 ——
 * 创造模式切标签页会重建控件列表，加进去的控件会被清掉且不一定补得回来。
 * 绘制挂在 {@code extractRenderState} 末尾，点击挂在三个鼠标方法上。</p>
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
    private static final int KIND_CREATIVE = 2;

    private static final int COL_BG = 0xFF202020;
    private static final int COL_BORDER = 0xFFA0A0A0;
    private static final int COL_TEXT = 0xFFFFFFFF;

    /** 按界面分组：创造模式和生存背包会同时存在，不能共用一个全局列表。 */
    private static final Map<Screen, List<SortButton>> ACTIVE = new WeakHashMap<>();

    private final AbstractContainerScreen<?> screen;
    private final int[] slots;
    private final int kind;
    private final String label;
    private int x;
    private int y;

    private boolean dragging;
    private boolean moved;
    private double grabOffsetX;
    private double grabOffsetY;
    private double startX;
    private double startY;
    private int startXCache;
    private int startYCache;

    private SortButton(AbstractContainerScreen<?> screen, int[] slots, int kind, String label) {
        this.screen = screen;
        this.slots = slots;
        this.kind = kind;
        this.label = label;
    }

    /** 界面初始化时调一次：算出要整理的槽位和按钮位置。 */
    static void attach(AbstractContainerScreen<?> screen) {
        ACTIVE.remove(screen);
        Config config = TweakRoll.config();
        if (!config.sortInventory || !config.sortButton) {
            return;
        }
        int[] containerSlots = InventorySorter.containerSlots(screen.getMenu());
        int[] bagSlots = InventorySorter.bagSlots(screen.getMenu());

        if (screen instanceof CreativeModeInventoryScreen) {
            if (bagSlots.length > 0) {
                add(new SortButton(screen, bagSlots, KIND_CREATIVE, tr("tweakroll.button.sort")));
            }
            return;
        }
        if (screen.getMenu() instanceof InventoryMenu) {
            if (bagSlots.length > 0) {
                add(new SortButton(screen, bagSlots, KIND_PRIMARY, tr("tweakroll.button.sort")));
            }
            return;
        }
        if (containerSlots.length > 0) {
            add(new SortButton(screen, containerSlots, KIND_PRIMARY, tr("tweakroll.button.sort_container")));
        }
        if (bagSlots.length > 0) {
            add(new SortButton(screen, bagSlots, KIND_BAG, tr("tweakroll.button.sort_bag")));
        }
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static void add(SortButton button) {
        ACTIVE.computeIfAbsent(button.screen, key -> new ArrayList<>()).add(button);
        button.place();
    }

    // ---------------------------------------------------------------- 绘制

    /** 由 mixin 在界面渲染末尾调用。 */
    public static void render(Screen screen, GuiGraphicsExtractor g) {
        List<SortButton> buttons = ACTIVE.get(screen);
        if (buttons == null || buttons.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }
        for (SortButton button : buttons) {
            if (!button.isShown()) {
                continue;
            }
            button.placeIfNeeded();
            int bx = button.x;
            int by = button.y;
            g.fill(bx, by, bx + WIDTH, by + HEIGHT, COL_BG);
            g.fill(bx, by, bx + WIDTH, by + 1, COL_BORDER);
            g.fill(bx, by + HEIGHT - 1, bx + WIDTH, by + HEIGHT, COL_BORDER);
            g.fill(bx, by, bx + 1, by + HEIGHT, COL_BORDER);
            g.fill(bx + WIDTH - 1, by, bx + WIDTH, by + HEIGHT, COL_BORDER);
            int textWidth = client.font.width(button.label);
            g.text(client.font, Component.literal(button.label),
                    bx + (WIDTH - textWidth) / 2, by + 4, COL_TEXT);
        }
    }

    /** 每个客户端 tick 调一次：拖动跟随 + 界面尺寸变化时重算位置。 */
    static void tick(Minecraft client) {
        for (List<SortButton> list : ACTIVE.values()) {
            for (SortButton button : list) {
                if (!button.isShown()) {
                    button.dragging = false;
                    continue;
                }
                if (button.dragging) {
                    button.follow();
                }
            }
        }
    }

    /** 创造模式只在「物品栏」那一页显示（原版公开方法 `isInventoryOpen()`）。 */
    private boolean isShown() {
        if (kind != KIND_CREATIVE) {
            return true;
        }
        if (screen instanceof CreativeModeInventoryScreen creative) {
            try {
                return creative.isInventoryOpen();
            } catch (Throwable throwable) {
                return true;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 点击 / 拖动

    /** 按下：落在按钮上就记拖动起点，并吃掉这次点击。 */
    public static boolean onClick(Screen s, MouseButtonEvent event) {
        if (!event.isLeftMouse()) {
            return true;
        }
        for (SortButton button : buttonsOf(s)) {
            if (button.isShown() && button.contains(event.x(), event.y())) {
                button.placeIfNeeded();
                button.startDrag(event.x(), event.y());
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

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT;
    }

    private void startDrag(double mouseX, double mouseY) {
        dragging = true;
        moved = false;
        grabOffsetX = mouseX - x;
        grabOffsetY = mouseY - y;
        startX = mouseX;
        startY = mouseY;
        startXCache = x;
        startYCache = y;
    }

    private void follow() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            return;
        }
        double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
        double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
        x = (int) Math.round(mouseX - grabOffsetX);
        y = (int) Math.round(mouseY - grabOffsetY);
        clamp();
        if (Math.abs(mouseX - startX) > DRAG_THRESHOLD || Math.abs(mouseY - startY) > DRAG_THRESHOLD
                || Math.abs(x - startXCache) > DRAG_THRESHOLD || Math.abs(y - startYCache) > DRAG_THRESHOLD) {
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
        int dx = x - (ScreenBridge.leftPos(screen) + ScreenBridge.imageWidth(screen));
        int dy = y - ScreenBridge.topPos(screen);
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

    // ---------------------------------------------------------------- 位置

    /** 拖动中不重算位置，避免和拖拽打架。 */
    private void placeIfNeeded() {
        if (!dragging) {
            place();
        }
    }

    private void place() {
        Config config = TweakRoll.config();
        int baseX = ScreenBridge.leftPos(screen) + ScreenBridge.imageWidth(screen);
        int baseY = ScreenBridge.topPos(screen);
        if (kind == KIND_BAG && config.sortBagButtonDx == AUTO) {
            x = baseX + 2;
            y = baseY + ScreenBridge.imageHeight(screen) - 20;
            clamp();
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
        x = baseX + dx;
        y = baseY + dy;
        clamp();
    }

    private void clamp() {
        int maxX = Math.max(0, screen.width - WIDTH);
        int maxY = Math.max(0, screen.height - HEIGHT);
        x = Math.max(0, Math.min(maxX, x));
        y = Math.max(0, Math.min(maxY, y));
    }

    private String kindName() {
        return switch (kind) {
            case KIND_BAG -> "背包";
            case KIND_CREATIVE -> "创造";
            default -> "主";
        };
    }
}
