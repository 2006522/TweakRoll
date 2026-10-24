package com.tweakroll.mixin;

import com.tweakroll.SortButton;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「整理」按钮的绘制时机：挂在 {@code extractRenderStateWithTooltipAndSubtitles} 的末尾。
 *
 * <p>之前画在 {@code AbstractContainerScreen.extractRenderState} 的 TAIL，
 * 但创造模式会在调完父类之后**自己再画面板、标签页、物品**，于是按钮被盖住了 ——
 * 点击能响应、屏幕上看不见。挂在这个更外层的钩子上，就不会被任何界面内容压住。</p>
 */
@Mixin(Screen.class)
public abstract class ScreenRenderMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void tweakroll$onRender(GuiGraphicsExtractor g, CallbackInfo ci) {
        SortButton.render((Screen) (Object) this, g);
    }
}
