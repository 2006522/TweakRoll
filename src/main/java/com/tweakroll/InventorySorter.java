package com.tweakroll;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * R 键一键整理背包：把玩家背包 27 格里的物品合并堆叠并按物品 ID 排序。
 *
 * <p>做法是"离线算好点击序列，再逐 tick 执行"：先用一份本地模型模拟
 * 「拾起 A → 放下 / 交换」的点击语义，把结果算好，然后把这些点击一个个发给服务端。
 * 只动背包区（菜单槽位 9~35），不碰快捷栏、盔甲和合成格。</p>
 */
final class InventorySorter {
    /** InventoryMenu 里背包区的槽位范围（含两端）。0~4 合成、5~8 盔甲、9~35 背包、36~44 快捷栏。 */
    private static final int BAG_FIRST = 9;
    private static final int BAG_LAST = 35;
    private static final int BAG_SIZE = BAG_LAST - BAG_FIRST + 1;

    /** 每个客户端 tick 发几次点击。4 次/秒×4 = 每秒 80 次，实测一次性发 4 个按键操作原版也能按序处理。 */
    private static final int CLICKS_PER_TICK = 4;

    private static final Deque<Integer> QUEUE = new ArrayDeque<>();
    private static AbstractContainerScreen<?> target;

    private InventorySorter() {
    }

    /** 按下 R（在背包界面里）时调用；返回 true 表示已经接管这次按键。 */
    static boolean onKeyPress(Screen screen, KeyEvent event) {
        if (!TweakRoll.config().sortInventory || !(screen instanceof InventoryScreen)) {
            return false;
        }
        // 创造模式背包有搜索框，正在输入时 R 就是普通字符，不能抢
        if (screen.getFocused() instanceof EditBox) {
            return false;
        }
        var key = TweakRoll.sortKey();
        if (key == null || !key.matches(event)) {
            return false;
        }
        // 正在整理时再按一次就当取消
        if (!QUEUE.isEmpty()) {
            QUEUE.clear();
            target = null;
            Log.info("整理已取消");
            return true;
        }
        // 按住 R 时按键会重复触发，50ms 内只认一次
        long now = System.currentTimeMillis();
        if (now - lastSortMillis < 250L) {
            return true;
        }
        lastSortMillis = now;
        if (TweakRoll.config().instantSort && sortOnServer()) {
            return true;
        }
        return startClickSort((AbstractContainerScreen<?>) screen);
    }

    /**
     * 单人游戏专用：直接在集成服务器的容器里改内容，再整体同步一次。
     * 没有任何点击，客户端看到的是一次刷新就整齐了。
     */
    private static boolean sortOnServer() {
        Minecraft client = Minecraft.getInstance();
        if (!client.hasSingleplayerServer() || client.player == null) {
            return false;
        }
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            return false;
        }
        String name = client.getUser().getName();
        server.executeIfPossible(() -> {
            ServerPlayer player = server.getPlayerList().getPlayerByName(name);
            if (player == null) {
                return;
            }
            InventoryMenu menu = player.inventoryMenu;
            if (menu.slots.size() <= BAG_LAST) {
                return;
            }
            ItemStack[] current = new ItemStack[BAG_SIZE];
            for (int i = 0; i < BAG_SIZE; i++) {
                current[i] = menu.slots.get(BAG_FIRST + i).getItem().copy();
            }
            ItemStack[] desired = sortedCopy(current);
            int changed = 0;
            for (int i = 0; i < BAG_SIZE; i++) {
                if (!sameStack(current[i], desired[i])) {
                    menu.slots.get(BAG_FIRST + i).set(desired[i]);
                    changed++;
                }
            }
            menu.broadcastChanges();
            // 客户端偶尔收不到增量同步，补一次全量
            menu.broadcastFullState();
            Log.info("整理完成：改动 " + changed + " 格");
        });
        return true;
    }

    /** 纯计算：合并同种堆叠 + 排序，返回目标排列（不产生点击）。 */
    private static ItemStack[] sortedCopy(ItemStack[] source) {
        ItemStack[] current = new ItemStack[BAG_SIZE];
        for (int i = 0; i < BAG_SIZE; i++) {
            current[i] = source[i].copy();
        }
        for (int i = 0; i < BAG_SIZE; i++) {
            if (current[i].isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < BAG_SIZE; j++) {
                if (current[j].isEmpty() || !ItemStack.isSameItemSameComponents(current[i], current[j])) {
                    continue;
                }
                int room = current[i].getMaxStackSize() - current[i].getCount();
                if (room <= 0) {
                    break;
                }
                int moved = Math.min(room, current[j].getCount());
                int left = current[j].getCount() - moved;
                current[i] = withCount(current[i], current[i].getCount() + moved);
                current[j] = left <= 0 ? ItemStack.EMPTY : withCount(current[j], left);
            }
        }
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : current) {
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        list.sort(Comparator
                .comparing((ItemStack stack) -> stack.getItem().getDescriptionId())
                .thenComparing(stack -> -stack.getCount()));
        ItemStack[] result = new ItemStack[BAG_SIZE];
        for (int i = 0; i < BAG_SIZE; i++) {
            result[i] = i < list.size() ? list.get(i) : ItemStack.EMPTY;
        }
        return result;
    }

    private static long lastSortMillis;

    private static boolean startClickSort(AbstractContainerScreen<?> screen) {
        if (!(screen.getMenu() instanceof InventoryMenu menu) || !ScreenBridge.isReady()) {
            return false;
        }
        if (menu.slots.size() <= BAG_LAST) {
            return false;
        }

        ItemStack[] current = new ItemStack[BAG_SIZE];
        for (int i = 0; i < BAG_SIZE; i++) {
            current[i] = menu.slots.get(BAG_FIRST + i).getItem().copy();
        }

        List<Integer> clicks = new ArrayList<>();
        mergeSameItems(current, clicks);
        sortStacks(current, clicks);

        if (clicks.isEmpty()) {
            Log.info("背包已经很整齐了");
            return true;
        }

        QUEUE.clear();
        QUEUE.addAll(clicks);
        target = screen;
        Log.info("开始整理背包，共 " + clicks.size() + " 次点击");
        return true;
    }

    /** 合并同种物品：把后面的堆叠补进前面的未满堆叠。 */
    private static void mergeSameItems(ItemStack[] current, List<Integer> clicks) {
        for (int i = 0; i < BAG_SIZE; i++) {
            if (current[i].isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < BAG_SIZE; j++) {
                if (current[j].isEmpty() || !ItemStack.isSameItemSameComponents(current[i], current[j])) {
                    continue;
                }
                int room = current[i].getMaxStackSize() - current[i].getCount();
                if (room <= 0) {
                    break;
                }
                int moved = Math.min(room, current[j].getCount());
                int left = current[j].getCount() - moved;
                current[i] = withCount(current[i], current[i].getCount() + moved);
                current[j] = left <= 0 ? ItemStack.EMPTY : withCount(current[j], left);
                // 拾起 j → 点到 i：源清空就不用再点回 j 了
                clicks.add(BAG_FIRST + j);
                clicks.add(BAG_FIRST + i);
                if (left > 0) {
                    clicks.add(BAG_FIRST + j);
                }
            }
        }
    }

    /**
     * 链式归位：一次「拾起」之后，把光标上的物品放进它该去的格子，
     * 顺手拿起那格原有的物品继续找下一个位置，直到回到空出来的那个洞。
     * 一个长度为 k 的环只需 k+1 次点击（旧做法要 3(k-1) 次）。
     */
    private static void sortStacks(ItemStack[] current, List<Integer> clicks) {
        List<ItemStack> desired = new ArrayList<>();
        for (ItemStack stack : current) {
            if (!stack.isEmpty()) {
                desired.add(stack);
            }
        }
        desired.sort(Comparator
                .comparing((ItemStack stack) -> stack.getItem().getDescriptionId())
                .thenComparing(stack -> -stack.getCount()));
        while (desired.size() < BAG_SIZE) {
            desired.add(ItemStack.EMPTY);
        }

        for (int start = 0; start < BAG_SIZE; start++) {
            if (sameStack(current[start], desired.get(start))) {
                continue;
            }
            int from = -1;
            for (int k = 0; k < BAG_SIZE; k++) {
                if (!sameStack(current[k], desired.get(k)) && sameStack(current[k], desired.get(start))) {
                    from = k;
                    break;
                }
            }
            if (from < 0) {
                continue;
            }
            clicks.add(BAG_FIRST + from);
            ItemStack carried = current[from];
            current[from] = ItemStack.EMPTY;

            int guard = 0;
            while (!carried.isEmpty() && guard++ <= BAG_SIZE) {
                int to = -1;
                for (int k = 0; k < BAG_SIZE; k++) {
                    if (sameStack(desired.get(k), carried) && !sameStack(current[k], desired.get(k))) {
                        to = k;
                        break;
                    }
                }
                if (to < 0) {
                    clicks.add(BAG_FIRST + from);
                    current[from] = carried;
                    break;
                }
                clicks.add(BAG_FIRST + to);
                ItemStack prev = current[to];
                current[to] = carried;
                carried = prev;
            }
        }
    }

    /** 每个客户端 tick 调一次，按节奏把队列里的点击发出去。 */
    static void tick(Minecraft client) {
        if (QUEUE.isEmpty()) {
            target = null;
            return;
        }
        if (target == null || client.gui.screen() != target) {
            QUEUE.clear();
            target = null;
            return;
        }
        for (int i = 0; i < CLICKS_PER_TICK && !QUEUE.isEmpty(); i++) {
            Integer index = QUEUE.poll();
            if (index == null) {
                break;
            }
            Slot slot = index < target.getMenu().slots.size() ? target.getMenu().slots.get(index) : null;
            if (slot != null) {
                ScreenBridge.click(target, slot, 0);
            }
        }
        if (QUEUE.isEmpty()) {
            target = null;
            Log.info("背包整理完成");
        }
    }

    static boolean isSorting() {
        return !QUEUE.isEmpty();
    }

    private static ItemStack withCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty();
        }
        return ItemStack.isSameItemSameComponents(a, b) && a.getCount() == b.getCount();
    }
}
