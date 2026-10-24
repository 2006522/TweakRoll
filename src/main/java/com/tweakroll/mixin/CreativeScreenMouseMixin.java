package com.tweakroll.mixin;

import com.tweakroll.SortButton;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 创造模式背包要单独挂一遍鼠标事件。
 *
 * <p>{@link CreativeModeInventoryScreen} 自己重写了
 * {@code mouseClicked} / {@code mouseDragged} / {@code mouseReleased}，
 * 父类（{@code AbstractContainerScreen}）上的注入在它不调 super 时就不会执行，
 * 所以「整理」按钮在那里既拖不动、点击也拦不住。</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMouseMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tweakroll$onMouseClicked(MouseButtonEvent event, boolean doubled,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!SortButton.onClick((Screen) (Object) this, event)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void tweakroll$onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!SortButton.onDrag((Screen) (Object) this, event, deltaX, deltaY)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void tweakroll$onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!SortButton.onRelease((Screen) (Object) this, event)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
