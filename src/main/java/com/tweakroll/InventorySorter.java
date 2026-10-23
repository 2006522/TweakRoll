package com.tweakroll;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
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

    /** 每次点击间隔的 tick 数，太大拖慢、太小怕服务端跟不上。 */
    private static final int CLICK_INTERVAL = 1;

    private static final Deque<Integer> QUEUE = new ArrayDeque<>();
    private static AbstractContainerScreen<?> target;
    private static int cooldown;

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
        return start((AbstractContainerScreen<?>) screen);
    }

    private static boolean start(AbstractContainerScreen<?> screen) {
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
        cooldown = CLICK_INTERVAL;
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
                // 拾起 j → 点到 i（并进去）→ 点回 j（把余数放回去）
                addTriple(clicks, j, i, j);
                current[i] = withCount(current[i], current[i].getCount() + moved);
                int left = current[j].getCount() - moved;
                current[j] = left <= 0 ? ItemStack.EMPTY : withCount(current[j], left);
            }
        }
    }

    /** 选择排序：把想要的物品换到第 i 格。 */
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

        for (int i = 0; i < BAG_SIZE; i++) {
            if (sameStack(current[i], desired.get(i))) {
                continue;
            }
            int j = -1;
            for (int k = i + 1; k < BAG_SIZE; k++) {
                if (sameStack(current[k], desired.get(i))) {
                    j = k;
                    break;
                }
            }
            if (j < 0) {
                continue;
            }
            // 拾起 j → 点到 i（换过来，原 i 的物品到了光标上）→ 点回 j（放好）
            addTriple(clicks, j, i, j);
            ItemStack tmp = current[i];
            current[i] = current[j];
            current[j] = tmp;
        }
    }

    private static void addTriple(List<Integer> clicks, int a, int b, int c) {
        clicks.add(BAG_FIRST + a);
        clicks.add(BAG_FIRST + b);
        clicks.add(BAG_FIRST + c);
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
        if (cooldown-- > 0) {
            return;
        }
        cooldown = CLICK_INTERVAL;

        Integer index = QUEUE.poll();
        if (index == null) {
            target = null;
            Log.info("背包整理完成");
            return;
        }
        Slot slot = index < target.getMenu().slots.size() ? target.getMenu().slots.get(index) : null;
        if (slot == null) {
            return;
        }
        ScreenBridge.click(target, slot, 0);
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
