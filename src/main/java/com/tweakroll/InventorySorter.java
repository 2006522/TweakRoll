package com.tweakroll;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
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
 * <p>只整理"存储区"：玩家背包区或容器槽位，不碰快捷栏、盔甲、合成格、结果格。</p>
 *
 * <p>能直接改数据就直接改，不改数据才退回模拟点击：</p>
 * <ul>
 *   <li>创造模式「物品栏」页：改客户端的 {@code inventoryMenu}，由它的监听器同步给服务器。</li>
 *   <li>单人存档：改集成服务器里玩家真实菜单的槽位。</li>
 *   <li>联机服务器：客户端说了不算，只能模拟点击让服务器自己搬。</li>
 * </ul>
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
        return trigger(container, defaultSlots(container.getMenu()));
    }

    /** 真正开始整理：按键和界面按钮都走这里。 */
    static boolean trigger(AbstractContainerScreen<?> container, int[] slots) {
        // 正在整理时再按一次就当取消
        if (!QUEUE.isEmpty()) {
            QUEUE.clear();
            target = null;
            Log.info("整理已取消");
            return true;
        }
        if (slots.length == 0) {
            Log.info("这个界面没有可整理的格子");
            return true;
        }
        if (sortOnServer(container, slots)) {
            return true;
        }
        return startClickSort(container, slots);
    }

    /** R 键的默认目标：背包界面整理背包，容器界面整理容器。 */
    private static int[] defaultSlots(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu) {
            return bagSlots(menu);
        }
        int[] container = containerSlots(menu);
        return container.length > 0 ? container : bagSlots(menu);
    }

    /**
     * 容器自己那几格（箱子 / 木桶 / 潜影盒 / 发射器 / 漏斗）。
     * 玩家背包和快捷栏的底层容器是 {@link Inventory}，其余的就是容器自己的。
     *
     * <p>返回的是槽位在 {@code menu.slots} 里的<b>位置</b>，不是 {@code slot.index}。
     * 普通菜单里两者相同（{@code addSlot} 会把 index 设成列表长度），
     * 但创造模式「物品栏」页把 {@code SlotWrapper} 直接塞进列表、绕过了 addSlot，
     * 那些槽的 index 全是 0。</p>
     */
    static int[] containerSlots(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu || !isStorageMenu(menu)) {
            return new int[0];
        }
        List<Integer> list = new ArrayList<>();
        for (int position = 0; position < menu.slots.size(); position++) {
            if (!(menu.slots.get(position).container instanceof Inventory)) {
                list.add(position);
            }
        }
        return toArray(list);
    }

    /**
     * 玩家背包那 27 格（不含快捷栏）。
     * 背包界面固定是 9~35；容器界面里挑 containerSlot 落在 9~35 的玩家槽位。
     */
    static int[] bagSlots(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu) {
            int[] bag = new int[BAG_SIZE];
            for (int i = 0; i < BAG_SIZE; i++) {
                bag[i] = BAG_FIRST + i;
            }
            return bag;
        }
        List<Integer> list = new ArrayList<>();
        for (int position = 0; position < menu.slots.size(); position++) {
            Slot slot = menu.slots.get(position);
            if (slot.container instanceof Inventory
                    && slot.getContainerSlot() >= BAG_FIRST
                    && slot.getContainerSlot() <= BAG_LAST) {
                list.add(position);
            }
        }
        return toArray(list);
    }

    private static int[] toArray(List<Integer> list) {
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

    /**
     * 直接改数据、不模拟点击。
     *
     * <ol>
     *   <li><b>创造模式「物品栏」页</b>：见 {@link #sortCreative}。</li>
     *   <li><b>单人存档</b>：改集成服务器里玩家真实菜单的槽位，再整体广播一次。</li>
     *   <li><b>其它情况</b>（连别人的服务器）：返回 false，退回模拟点击。</li>
     * </ol>
     */
    private static boolean sortOnServer(AbstractContainerScreen<?> container, int[] slots) {
        if (container instanceof CreativeModeInventoryScreen creative) {
            return sortCreative(creative, slots);
        }
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
            if (slots.length == 0 || slots[slots.length - 1] >= menu.slots.size()) {
                Log.info("整理跳过：槽位与菜单不匹配（" + slots.length + " 格）");
                return;
            }
            Log.info("整理完成：改动 " + applySort(menu, slots) + " 格");
        });
        return true;
    }

    /** 把 {@code menu} 里这些槽位排好；返回真正改动的格数。 */
    private static int applySort(AbstractContainerMenu menu, int[] slots) {
        int changed = write(menu, slots);
        // 只发增量客户端偶尔不刷新，补一次全量
        menu.broadcastChanges();
        menu.broadcastFullState();
        return changed;
    }

    /** 只把算好的结果写进槽位，不广播；返回真正改动的格数。 */
    private static int write(AbstractContainerMenu menu, int[] slots) {
        ItemStack[] current = read(menu, slots);
        ItemStack[] desired = sortedCopy(current);
        int changed = 0;
        for (int i = 0; i < slots.length; i++) {
            if (!sameStack(current[i], desired[i])) {
                menu.slots.get(slots[i]).set(desired[i]);
                changed++;
            }
        }
        return changed;
    }

    /**
     * 创造模式「物品栏」页：改玩家真实的 {@code inventoryMenu}，靠它的监听器同步到服务器。
     *
     * <p>这个世界里 {@code inventoryMenu} 上挂着 {@code CreativeInventoryListener}
     * （打开创造界面时注册），{@code broadcastChanges()} 触发它就能把改动的槽位
     * 转成 {@code ServerboundSetCreativeModeSlotPacket}，所以不用一个一个点。
     * 原版自己的 {@code slotClicked} 整理这一页走的也是
     * {@code inventoryMenu.clicked(...)} + {@code broadcastChanges()}，同一条路。</p>
     *
     * <p>槽位编号：这个页面的槽位是 {@code InventoryMenu} 槽位包了一层 {@code SlotWrapper}，
     * 位置和编号跟 {@code inventoryMenu} 一一对应，可以直接复用。其它分类页
     * （建筑方块、搜索……）里是「能刷出来的物品」，跟背包没有任何对应关系，
     * 所以只有「物品栏」页才整理。</p>
     */
    private static boolean sortCreative(CreativeModeInventoryScreen screen, int[] slots) {
        if (!screen.isInventoryOpen()) {
            Log.info("创造模式只有「物品栏」页能整理");
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }
        AbstractContainerMenu menu = client.player.inventoryMenu;
        if (menu == null || slots.length == 0 || slots[slots.length - 1] >= menu.slots.size()) {
            return false;
        }
        int changed = write(menu, slots);
        if (changed == 0) {
            Log.info("已经很整齐了");
            return true;
        }
        // 只走 broadcastChanges：它会触发 CreativeInventoryListener 发包
        menu.broadcastChanges();
        Log.info("创造模式整理完成：改动 " + changed + " 格");
        return true;
    }

    /** 连别人的服务器时用：把算好的点击序列排队，逐 tick 发出去。 */
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
                // 用槽位在 slots 里的位置当索引：SlotWrapper（创造模式）的 index 字段是 0
                ScreenBridge.click(target, slot, index, 0);
            }
        }
        if (QUEUE.isEmpty()) {
            target = null;
            Log.info("整理完成");
        }
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
