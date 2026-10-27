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
    private static final int KIND_CREATIVE = 2;

    /** 按界面分组：创造模式和生存背包会同时存在，不能共用一个全局列表。 */
    private static final Map<Screen, List<SortButton>> ACTIVE = new WeakHashMap<>();

    private final AbstractContainerScreen<?> screen;
    /** 固定要整理的槽位；{@link #dynamic} 为 true 时不用它。 */
    private final int[] slots;
    /** 创造模式的槽位随标签页变化，只能在点击的那一刻现算。 */
    private final boolean dynamic;
    private final int kind;
    private Button widget;

    private boolean dragging;
    private boolean moved;
    /** 已经因为屏幕边界把位置改写回配置了（每次挂载只做一次，避免每 tick 写盘）。 */
    private boolean clampedStored;
    /** 已经打印过一次几何兜底日志，别每 tick 刷。 */
    private boolean geometryLogged;
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
        this.dynamic = false;
    }

    private SortButton(AbstractContainerScreen<?> screen, int kind) {
        this.screen = screen;
        this.slots = new int[0];
        this.kind = kind;
        this.dynamic = true;
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

        // 创造模式：只在「物品栏」页放一颗整理背包的按钮，其他分类页不放。
        // 槽位不能在这里算死——切标签页会重建菜单里的槽位表，只有 INVENTORY 页
        // 才有对应的玩家背包槽，所以改成点击时现算（dynamic）。
        if (screen instanceof CreativeModeInventoryScreen) {
            add(new SortButton(screen, KIND_CREATIVE), "tweakroll.button.sort");
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
        button.widget = Button.builder(Component.translatable(labelKey), pressed -> button.run())
                .bounds(0, 0, WIDTH, HEIGHT)
                .build();
        button.place();
        button.refreshVisible();
        ScreenBridge.addWidget(button.screen, button.widget);
        list.add(button);
        Log.info("整理按钮已挂载：" + button.screen.getClass().getSimpleName()
                + " pos=" + button.widget.getX() + "," + button.widget.getY());
    }

    /** 点按钮（或按键）时真正要整理的槽位。 */
    private void run() {
        int[] target = resolveSlots();
        if (target.length == 0) {
            return;
        }
        InventorySorter.trigger(screen, target);
    }

    private int[] resolveSlots() {
        return dynamic ? InventorySorter.bagSlots(screen.getMenu()) : slots;
    }

    /**
     * 创造模式的「物品栏」页才显示按钮。
     *
     * <p>{@code CreativeModeInventoryScreen.isInventoryOpen()} 是公开方法，
     * 内部比较的就是 {@code selectedTab.getType() == Type.INVENTORY}，比反射拿
     * {@code selectedTab} 再和注册表里的实例做 {@code ==} 稳得多。</p>
     */
    private void refreshVisible() {
        if (widget == null) {
            return;
        }
        widget.setVisible(kind != KIND_CREATIVE || creativeInventoryOpen(screen));
    }

    private static boolean creativeInventoryOpen(Screen screen) {
        return !(screen instanceof CreativeModeInventoryScreen creative) || creative.isInventoryOpen();
    }

    // ---------------------------------------------------------------- 定时维护

    /** 每个客户端 tick 调一次：拖动跟随、位置重算、显隐刷新、被界面清掉就补挂。 */
    static void tick(Minecraft client) {
        Screen current = client.gui == null ? null : client.gui.screen();
        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Screen, List<SortButton>> entry = iterator.next();
            // 界面已经关掉了：拖动中被关掉的话先把位置补存一次，别让这次拖动白做
            if (entry.getKey() != current) {
                for (SortButton button : entry.getValue()) {
                    if (button.dragging) {
                        button.finishDrag(false);
                    }
                }
                iterator.remove();
                continue;
            }
            for (SortButton button : entry.getValue()) {
                if (button.widget == null) {
                    continue;
                }
                // 创造模式切标签页时也要跟着刷新显隐
                button.refreshVisible();
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
            // 切标签页后要等一个 tick 才刷新，按下时再确认一次，避免点到已隐藏的按钮
            button.refreshVisible();
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
        // 拖动结束会重新算偏移，所以允许 place() 再次把裁剪结果写回配置
        clampedStored = false;
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
        finishDrag(true);
    }

    /**
     * @param allowRun 松开时鼠标没动过就算"点了一下"，要执行整理；
     *                 界面在拖动途中被关掉时传 false，别在一张已经关掉的界面上整理。
     */
    private void finishDrag(boolean allowRun) {
        if (allowRun) {
            // 界面已经关掉时不要再照着当前鼠标位置挪一下，否则存的是"关掉之后"的坐标
            follow();
        }
        dragging = false;
        if (!moved) {
            if (allowRun) {
                run();
            }
            return;
        }
        int baseX = panelLeft() + ScreenBridge.imageWidth(screen);
        int baseY = baseY();
        int dx = widget.getX() - baseX;
        int dy = widget.getY() - baseY;
        storeOffset(dx, dy);
        TweakRoll.config().save();
        // 刚存的偏移就是当前显示的位置，别让 place() 认为还需要再纠正一次
        clampedStored = true;
        Log.info("整理按钮位置已保存：" + kindName() + " dx=" + dx + ", dy=" + dy);
    }

    /** 把偏移写进对应那颗按钮的配置字段（不落盘）。 */
    private void storeOffset(int dx, int dy) {
        Config config = TweakRoll.config();
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
    }

    // ---------------------------------------------------------------- 位置

    /**
     * 面板左边缘。
     *
     * <p>{@code ScreenEvents.AFTER_INIT} 是在 {@code Screen#init} 返回时触发的，而
     * {@code AbstractContainerScreen} 是在 {@code super.init()} <b>之后</b>才算
     * {@code leftPos/topPos}，所以挂按钮的那一刻它们还是 0。这时拿 0 当基准会把按钮
     * 摆到左上角，等下一个 tick 才跳回原位；更糟的是这时候拖动会把
     * 「相对 0 的偏移」写进配置（比如 dy=-15），下次打开落在面板外面被裁到屏幕顶，
     * 用户只能再拖一次——看起来就是「位置根本没保存」。
     *
     * <p>原版的算法就是居中，这里照着补一份，保证任何时刻基准都对。</p>
     */
    private int panelLeft() {
        int left = ScreenBridge.leftPos(screen);
        int width = ScreenBridge.imageWidth(screen);
        if (left == 0 && screen.width > width) {
            left = (screen.width - width) / 2;
            logGeometryFallback(left, panelTopRaw());
        }
        return left;
    }

    private int panelTop() {
        int top = ScreenBridge.topPos(screen);
        int height = ScreenBridge.imageHeight(screen);
        if (top == 0 && screen.height > height) {
            top = (screen.height - height) / 2;
            logGeometryFallback(panelLeftRaw(), top);
        }
        return top;
    }

    private int panelLeftRaw() {
        int left = ScreenBridge.leftPos(screen);
        int width = ScreenBridge.imageWidth(screen);
        return left == 0 && screen.width > width ? (screen.width - width) / 2 : left;
    }

    private int panelTopRaw() {
        int top = ScreenBridge.topPos(screen);
        int height = ScreenBridge.imageHeight(screen);
        return top == 0 && screen.height > height ? (screen.height - height) / 2 : top;
    }

    private void logGeometryFallback(int left, int top) {
        if (geometryLogged) {
            return;
        }
        geometryLogged = true;
        Log.info("界面几何还没算好，按居中补算：left=" + left + ", top=" + top);
    }

    /**
     * 这颗按钮的纵向基准。
     *
     * <p>「整理背包」那颗贴在<b>面板底部</b>，所以基准取底边；其余的都贴顶边。
     * 用同一个「顶边 + dy」去算的话，箱子（高 222）、熔炉（高 166）、工作台
     * 的 {@code topPos} 各不相同，同一个 dy 落到屏幕上就差几十像素 ——
     * 表现就是「同一个按钮，换个界面位置就变了」。</p>
     */
    private int baseY() {
        return kind == KIND_BAG
                ? panelTop() + ScreenBridge.imageHeight(screen)
                : panelTop();
    }

    private void place() {
        if (widget == null) {
            return;
        }
        Config config = TweakRoll.config();
        if (kind == KIND_BAG && !config.sortBagFromBottom) {
            // 老配置的 dy 是相对面板顶边存的：大箱子（高 222）和熔炉（高 166）算出来差几十像素。
            // 一次性重置成"贴面板底部"，从此按底边记，换任何容器都对得上。
            config.sortBagButtonDx = AUTO;
            config.sortBagButtonDy = AUTO;
            config.sortBagFromBottom = true;
            config.save();
            Log.info("背包整理按钮改为「相对面板底部」定位，位置已重置为自动贴底");
        }
        int baseX = panelLeft() + ScreenBridge.imageWidth(screen);
        int baseY = baseY();
        if (kind == KIND_BAG && config.sortBagButtonDx == AUTO) {
            setPos(baseX + 2, baseY - 20);
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
        if (setPos(baseX + dx, baseY + dy) && !dragging && !clampedStored) {
            // 存的位置落到屏幕外被裁掉了：把裁剪后的偏移写回去，
            // 否则每次打开都要"回到屏幕外 → 再被裁一次"，看着就像位置没保存住
            clampedStored = true;
            storeOffset(widget.getX() - baseX, widget.getY() - baseY);
            config.save();
            Log.info("整理按钮超出屏幕，已把裁剪后的位置写回配置：" + kindName()
                    + " dx=" + (widget.getX() - baseX) + ", dy=" + (widget.getY() - baseY));
        }
    }

    /** @return 是否发生了裁剪（位置和期望值不一致）。 */
    private boolean setPos(int x, int y) {
        if (widget == null) {
            return false;
        }
        int maxX = Math.max(0, screen.width - WIDTH);
        int maxY = Math.max(0, screen.height - HEIGHT);
        int clampedX = Math.max(0, Math.min(maxX, x));
        int clampedY = Math.max(0, Math.min(maxY, y));
        widget.setX(clampedX);
        widget.setY(clampedY);
        return clampedX != x || clampedY != y;
    }

    private String kindName() {
        return switch (kind) {
            case KIND_BAG -> "背包";
            case KIND_CREATIVE -> "创造";
            default -> "主";
        };
    }
}
