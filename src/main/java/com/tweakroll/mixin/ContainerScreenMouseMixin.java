package com.tweakroll.mixin;

import com.tweakroll.SortButton;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把「整理」按钮的按下 / 拖动 / 松开接到界面自己的鼠标方法上。
 *
 * <p>之前用 Fabric 的 {@code ScreenMouseEvents}，在创造模式背包
 * （{@code CreativeModeInventoryScreen}）里收不到事件，按钮就拖不动。
 * 直接注入 {@link AbstractContainerScreen} 的三个鼠标方法更稳，所有容器界面都覆盖。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMouseMixin {

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
