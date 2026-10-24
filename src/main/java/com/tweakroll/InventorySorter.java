package com.tweakroll;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * R 键一键整理：背包界面整理背包 27 格，箱子/木桶/潜影盒/发射器/漏斗界面整理容器那一块。
 *
 * <p>只整理"存储区"：玩家背包区或容器槽位，不碰快捷栏、盔甲、合成格、结果格。
 * 做法是先用本地模型算出完整的点击序列（合并堆叠 + 链式归位），再逐 tick 发出去；
 * 单人游戏则直接在服务端改数据，连点击都省了。</p>
 */
final class InventorySorter {
    /** InventoryMenu 里背包区的槽位范围（0~4 合成、5~8 盔甲、9~35 背包、36~44 快捷栏）。 */
    private static final int BAG_FIRST = 9;
    private static final int BAG_LAST = 35;
    private static final int BAG_SIZE = BAG_LAST - BAG_FIRST + 1;

    /** 每个客户端 tick 发几次点击（20 tick/秒 × 4 ≈ 80 次/秒）。 */
    private static final int CLICKS_PER_TICK = 4;

    private static final Deque<Integer> QUEUE = new ArrayDeque<>();
    private static AbstractContainerScreen<?> target;
    private static long lastSortMillis;

    private InventorySorter() {
    }

    /** 按下 R 时调用；返回 true 表示已经接管这次按键。 */
    static boolean onKeyPress(Screen screen, KeyEvent event) {
        if (!TweakRoll.config().sortInventory || !(screen instanceof AbstractContainerScreen<?> container)) {
            return false;
        }
        // 搜索框里 R 是普通字符，不能抢
        if (screen.getFocused() instanceof EditBox) {
            return false;
        }
        KeyMapping key = TweakRoll.sortKey();
        if (key == null || !key.matches(event)) {
            return false;
        }
        // 按住 R 时按键会重复触发，短时间内只认一次
        long now = System.currentTimeMillis();
        if (now - lastSortMillis < 250L) {
            return true;
        }
        lastSortMillis = now;
        return trigger(container);
    }

    /** 真正开始整理：按键和界面按钮都走这里。 */
    static boolean trigger(AbstractContainerScreen<?> container) {
        // 正在整理时再按一次就当取消
        if (!QUEUE.isEmpty()) {
            QUEUE.clear();
            target = null;
            Log.info("整理已取消");
            return true;
        }
        int[] slots = pickSlots(container.getMenu());
        if (slots.length == 0) {
            Log.info("这个界面没有可整理的容器");
            return true;
        }
        if (TweakRoll.config().instantSort && sortOnServer()) {
            return true;
        }
        return startClickSort(container, slots);
    }

    /** 这个界面有没有可整理的槽位（决定要不要显示按钮）。 */
    static boolean canSort(AbstractContainerScreen<?> screen) {
        return TweakRoll.config().sortInventory && pickSlots(screen.getMenu()).length > 0;
    }

    /**
     * 挑出要整理的槽位（菜单索引）。
     * 背包界面只取 9~35；箱子等容器界面取"不属于玩家背包"的槽位。
     */
    private static int[] pickSlots(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu) {
            int[] bag = new int[BAG_SIZE];
            for (int i = 0; i < BAG_SIZE; i++) {
                bag[i] = BAG_FIRST + i;
            }
            return bag;
        }
        if (!isStorageMenu(menu)) {
            return new int[0];
        }
        List<Integer> list = new ArrayList<>();
        for (Slot slot : menu.slots) {
            // 玩家背包/快捷栏的容器是 Inventory，箱子等是方块实体自己的容器
            if (!(slot.container instanceof Inventory)) {
                list.add(slot.index);
            }
        }
        int[] result = new int[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    /** 只处理存储类容器，避免碰到熔炉、合成台这些有结果格的界面。 */
    private static boolean isStorageMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu
                || menu instanceof ShulkerBoxMenu
                || menu instanceof DispenserMenu
                || menu instanceof HopperMenu;
    }

    /** 单人游戏专用：直接在集成服务器的容器里改内容，再整体同步一次。 */
    private static boolean sortOnServer() {
        Minecraft client = Minecraft.getInstance();
        if (!client.hasSingleplayerServer()) {
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
            AbstractContainerMenu menu = player.containerMenu;
            int[] slots = pickSlots(menu);
            if (slots.length == 0) {
                return;
            }
            ItemStack[] current = read(menu, slots);
            ItemStack[] desired = sortedCopy(current);
            int changed = 0;
            for (int i = 0; i < slots.length; i++) {
                if (!sameStack(current[i], desired[i])) {
                    menu.slots.get(slots[i]).set(desired[i]);
                    changed++;
                }
            }
            menu.broadcastChanges();
            // 只发增量客户端偶尔不刷新，补一次全量
            menu.broadcastFullState();
            Log.info("整理完成：改动 " + changed + " 格");
        });
        return true;
    }

    /** 多人服务器上只能模拟点击：把算好的点击序列排队，逐 tick 发出去。 */
    private static boolean startClickSort(AbstractContainerScreen<?> screen, int[] slots) {
        if (!ScreenBridge.isReady()) {
            return false;
        }
        ItemStack[] current = read(screen.getMenu(), slots);
        List<Integer> clicks = new ArrayList<>();
        mergeSameItems(current, slots, clicks);
        sortStacks(current, slots, clicks);

        if (clicks.isEmpty()) {
            Log.info("已经很整齐了");
            return true;
        }
        QUEUE.clear();
        QUEUE.addAll(clicks);
        target = screen;
        Log.info("开始整理，共 " + clicks.size() + " 次点击");
        return true;
    }

    /** 合并同种物品：把后面的堆叠补进前面的未满堆叠。源格子被掏空时少发一次点击。 */
    private static void mergeSameItems(ItemStack[] current, int[] slots, List<Integer> clicks) {
        for (int i = 0; i < current.length; i++) {
            if (current[i].isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < current.length; j++) {
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
                clicks.add(slots[j]);
                clicks.add(slots[i]);
                if (left > 0) {
                    clicks.add(slots[j]);
                }
            }
        }
    }

    /** 链式归位：拾起一次后沿环依次放好，长度 k 的环只要 k+1 次点击。 */
    private static void sortStacks(ItemStack[] current, int[] slots, List<Integer> clicks) {
        ItemStack[] desired = sortedCopy(current);
        int size = current.length;
        for (int start = 0; start < size; start++) {
            if (sameStack(current[start], desired[start])) {
                continue;
            }
            int from = -1;
            for (int k = 0; k < size; k++) {
                if (!sameStack(current[k], desired[k]) && sameStack(current[k], desired[start])) {
                    from = k;
                    break;
                }
            }
            if (from < 0) {
                continue;
            }
            clicks.add(slots[from]);
            ItemStack carried = current[from];
            current[from] = ItemStack.EMPTY;

            int guard = 0;
            while (!carried.isEmpty() && guard++ <= size) {
                int to = -1;
                for (int k = 0; k < size; k++) {
                    if (sameStack(desired[k], carried) && !sameStack(current[k], desired[k])) {
                        to = k;
                        break;
                    }
                }
                if (to < 0) {
                    clicks.add(slots[from]);
                    current[from] = carried;
                    break;
                }
                clicks.add(slots[to]);
                ItemStack prev = current[to];
                current[to] = carried;
                carried = prev;
            }
        }
    }

    /** 纯计算：合并同种堆叠 + 排序，返回目标排列（不产生点击）。 */
    private static ItemStack[] sortedCopy(ItemStack[] source) {
        ItemStack[] current = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            current[i] = source[i].copy();
        }
        for (int i = 0; i < current.length; i++) {
            if (current[i].isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < current.length; j++) {
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
        ItemStack[] result = new ItemStack[current.length];
        for (int i = 0; i < current.length; i++) {
            result[i] = i < list.size() ? list.get(i) : ItemStack.EMPTY;
        }
        return result;
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
            Log.info("整理完成");
        }
    }

    static boolean isSorting() {
        return !QUEUE.isEmpty();
    }

    private static ItemStack[] read(AbstractContainerMenu menu, int[] slots) {
        ItemStack[] current = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            current[i] = menu.slots.get(slots[i]).getItem().copy();
        }
        return current;
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
