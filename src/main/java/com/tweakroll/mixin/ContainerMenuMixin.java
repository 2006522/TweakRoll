package com.tweakroll.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift + 点击往背包搬东西时，<b>先填快捷栏、从左往右</b>，填满了再轮到主背包。
 *
 * <p>原版 {@code quickMoveStack} 在「容器 → 玩家背包」这条路径上传的是
 * {@code reverse = true}，而 {@code moveItemStackTo} 的槽位表里主背包在前、快捷栏在后，
 * 所以倒着遍历就是：快捷栏最右 → 往左 → 主背包右下 → 往左上。怎么做都不顺手。</p>
 *
 * <p>把 {@code reverse} 改成 {@code false} 也不对 —— 那会变成"主背包先填满，
 * 快捷栏最后"，更反直觉。所以这里直接接管这段遍历：</p>
 * <ol>
 *   <li>先把堆叠并进快捷栏里已有的同类堆，再并进主背包；</li>
 *   <li>还有剩的就找空格，同样是快捷栏优先、从左往右。</li>
 * </ol>
 *
 * <p>只接管 <b>倒序 + 刚好 36 格</b>（玩家背包的完整范围）这一次调用；
 * 别的调用（往箱子里放、快捷栏内部移动等）原样走原版。</p>
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {

    /** 快捷栏固定 9 格，位于玩家背包范围的末尾。 */
    private static final int HOTBAR_SIZE = 9;

    /** 一次搬运涉及的玩家背包格数。 */
    private static final int BACKPACK_SIZE = 36;

    /** 第一次接管时打一条日志，方便确认 mixin 真的挂上了。 */
    private static boolean logged;

    /** mixin 包访问不到主包的 Log（包内可见），这里用 JDK 自带的。 */
    private static final System.Logger LOGGER = System.getLogger("TweakRoll");

    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void tweakroll$fillHotbarFirst(ItemStack stack, int startIndex, int endIndex, boolean reverse,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!reverse || endIndex - startIndex != BACKPACK_SIZE) {
            return;
        }
        if (!logged) {
            logged = true;
            LOGGER.log(System.Logger.Level.INFO, "[TweakRoll] Shift 搬运改成「快捷栏优先、从左往右」");
        }
        cir.setReturnValue(moveIntoBackpack(stack, startIndex, endIndex));
    }

    /**
     * 按「快捷栏 → 主背包、各自从左往右」的顺序搬。
     *
     * @return 是否搬动了任何东西（和原版返回值语义一致）
     */
    private boolean moveIntoBackpack(ItemStack stack, int start, int end) {
        if (stack.isEmpty()) {
            return false;
        }
        int hotbarStart = end - HOTBAR_SIZE;

        boolean changed = false;
        // 第一轮：并进已有的同类堆
        changed |= mergeInto(stack, hotbarStart, end);
        changed |= mergeInto(stack, start, hotbarStart);

        // 第二轮：还有剩就找空格
        if (!stack.isEmpty()) {
            changed |= fillEmpty(stack, hotbarStart, end);
        }
        if (!stack.isEmpty()) {
            changed |= fillEmpty(stack, start, hotbarStart);
        }
        return changed;
    }

    /** 把 stack 并进 [from, to) 里同种物品的堆，从前往后。 */
    private boolean mergeInto(ItemStack stack, int from, int to) {
        boolean changed = false;
        for (int i = from; i < to && !stack.isEmpty(); i++) {
            Slot slot = this.slots.get(i);
            ItemStack inSlot = slot.getItem();
            if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(stack, inSlot)) {
                continue;
            }
            int max = Math.min(slot.getMaxStackSize(inSlot), inSlot.getMaxStackSize());
            int total = inSlot.getCount() + stack.getCount();
            if (total <= max) {
                inSlot.setCount(total);
                stack.setCount(0);
            } else {
                stack.shrink(max - inSlot.getCount());
                inSlot.setCount(max);
            }
            slot.setChanged();
            changed = true;
        }
        return changed;
    }

    /** 在 [from, to) 里找第一个能放的空格，放下 stack 的一部分。 */
    private boolean fillEmpty(ItemStack stack, int from, int to) {
        for (int i = from; i < to; i++) {
            Slot slot = this.slots.get(i);
            if (slot.hasItem() || !slot.mayPlace(stack)) {
                continue;
            }
            int max = slot.getMaxStackSize(stack);
            slot.setByPlayer(stack.split(Math.min(stack.getCount(), max)));
            slot.setChanged();
            return true;
        }
        return false;
    }
}
