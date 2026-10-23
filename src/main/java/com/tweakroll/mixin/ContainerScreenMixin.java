package com.tweakroll.mixin;

import com.tweakroll.TweakRoll;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 扫过转移期间不渲染物品提示（tooltip）。
 *
 * <p>原版 {@code extractTooltip} 只要悬停格有物品就画提示，扫过时物品被移走又移到下一格，
 * 名字会一格一格闪。这里在扫过期间直接取消。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void tweakroll$hideTooltipWhileSweeping(GuiGraphicsExtractor extractor, CallbackInfo info) {
        if (TweakRoll.isSweeping()) {
            info.cancel();
        }
    }
}
