package com.tweakroll.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shift + 点击往背包搬东西时，把原版的"从后往前填"改成"从前往后填"。
 *
 * <p>原版的 {@code quickMoveStack} 在"容器 → 玩家背包"这个方向上会传
 * {@code reverse = true}，也就是从背包<b>最后一格倒着</b>往起点填 —— 表现就是
 * 从箱子拿出来的东西先落在快捷栏最右边，再一路往左、然后钻到主背包右下角。</p>
 *
 * <p>这里统一改成 {@code false}：一律从 hotbar 最左边开始，往右填满，再填主背包
 * 左上角。和玩家自己往背包里摆东西的顺序一致。</p>
 *
 * <p>只影响传了 {@code true} 的调用。"背包 → 容器"本来就是 {@code false}，
 * 所以往箱子里放东西的顺序不变。</p>
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {

    /**
     * @param reverse 原版算出来的遍历方向
     * @return 恒为 {@code false}（从前往后）
     */
    @ModifyVariable(method = "moveItemStackTo", at = @At("HEAD"), argsOnly = true, index = 4)
    private boolean tweakroll$fillFromLeft(boolean reverse) {
        return false;
    }
}
