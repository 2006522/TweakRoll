package com.tweakroll;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ConfigScreen extends Screen {
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("tweakroll.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config config = TweakRoll.config();
        int x = this.width / 2 - 100;

        addToggle(x, 40, "tweakroll.option.shift_hover_move",
                () -> config.shiftHoverMove,
                value -> config.shiftHoverMove = value);
        addToggle(x, 64, "tweakroll.option.sort_inventory",
                () -> config.sortInventory,
                value -> config.sortInventory = value);
        addToggle(x, 88, "tweakroll.option.instant_sort",
                () -> config.instantSort,
                value -> config.instantSort = value);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x, this.height - 32, 200, 20)
                .build());
    }

    private int addToggle(int x, int y, String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(
                        Component.translatable(key)
                                .append(": ")
                                .append(Component.translatable(getter.get() ? "tweakroll.value.on" : "tweakroll.value.off")),
                        button -> {
                            setter.accept(!getter.get());
                            TweakRoll.config().save();
                            // 重建界面以刷新按钮文字
                            if (this.minecraft != null) {
                                this.minecraft.setScreenAndShow(new ConfigScreen(this.parent));
                            }
                        })
                .bounds(x, y, 200, 20)
                .build());
        return y + 24;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor) {
        super.extractRenderState(extractor);
        extractor.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
