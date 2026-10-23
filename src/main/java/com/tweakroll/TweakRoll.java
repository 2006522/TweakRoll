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
                "key.tweakroll.config", InputConstants.Type.KEYBOARD, InputConstants.KEY_O, KeyMapping.Category.INVENTORY));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> container && wiredScreens.add(screen)) {
                InventoryTweaks.onScreenOpened(container);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKey.consumeClick()) {
                client.setScreenAndShow(new ConfigScreen(client.gui.screen()));
            }
            // 扫过转移用客户端 tick 轮询（20 次/秒）：
            // Fabric 的拖拽事件在容器界面里不可靠，逐帧事件也不保证一定会派发
            if (client.gui.screen() instanceof AbstractContainerScreen<?> container) {
                InventoryTweaks.tickSweep(container);
            }
        });

        Log.info("已加载，按 O 打开设置。");
    }
}
