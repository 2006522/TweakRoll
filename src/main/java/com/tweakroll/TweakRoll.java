package com.tweakroll;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class TweakRoll implements ClientModInitializer {
    public static final String MOD_ID = "tweakroll";

    private static final int KEY_O = 79;

    private static TweakRoll instance;

    private final Config config = new Config();
    private final Set<Screen> wiredScreens = Collections.newSetFromMap(new WeakHashMap<>());

    private KeyMapping configKey;

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

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || !wiredScreens.add(screen)) {
                return;
            }
            InventoryTweaks.onScreenOpened(container);
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
        });

        Log.info("已加载，按 O 打开设置。");
    }
}
