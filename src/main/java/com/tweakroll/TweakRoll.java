package com.tweakroll;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class TweakRoll implements ClientModInitializer {
    public static final String MOD_ID = "tweakroll";

    private static final int KEY_O = 79;
    private static final int KEY_LEFT_ALT = 342;

    private static TweakRoll instance;

    private final Config config = new Config();
    private final Set<Screen> wiredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    private KeyMapping configKey;
    private KeyMapping toggleWheelKey;

    public static TweakRoll get() {
        return instance;
    }

    public static Config config() {
        return instance.config;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        ScreenBridge.init();
        config.load();
        config.save();

        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.tweakroll.config", InputConstants.Type.KEYBOARD, KEY_O, KeyMapping.Category.INVENTORY));
        toggleWheelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.tweakroll.toggle_wheel", InputConstants.Type.KEYBOARD, KEY_LEFT_ALT, KeyMapping.Category.INVENTORY));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !wiredScreens.add(screen)) {
                return;
            }
            InventoryTweaks.onScreenOpened(container);
            ScreenMouseEvents.allowMouseClick(screen).register(InventoryTweaks::onClick);
            ScreenMouseEvents.allowMouseDrag(screen).register(InventoryTweaks::onDrag);
            ScreenMouseEvents.allowMouseRelease(screen).register(InventoryTweaks::onRelease);
            ScreenMouseEvents.allowMouseScroll(screen).register(InventoryTweaks::onScroll);
            // Shift+左键扫过转移必须逐帧轮询：原版容器界面的 mouseDragged 会提前 return，
            // 拖拽事件到不了我们的处理器
            ScreenEvents.afterForeground(screen).register((s, extractor) -> {
                if (s instanceof AbstractContainerScreen<?> containerScreen) {
                    InventoryTweaks.tickSweep(containerScreen);
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKey.consumeClick()) {
                client.setScreenAndShow(new ConfigScreen(client.gui.screen()));
            }
            while (toggleWheelKey.consumeClick()) {
                if (client.gui.screen() instanceof AbstractContainerScreen<?> container) {
                    InventoryTweaks.toggleWheel(container);
                }
            }
        });

        Log.info("已加载，按 O 打开设置。");
    }
}
