package com.tweakroll;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 简单的 properties 配置，文件名 config/tweakroll.properties。 */
public final class Config {
    /** 按住 Shift + 左键扫过格子，自动快速转移（物品栏 ↔ 背包）。 */
    public boolean shiftHoverMove = true;
    /** R 键一键整理背包（合并堆叠 + 按物品排序）。 */
    public boolean sortInventory = true;
    /** 单人游戏时瞬间整理（不模拟点击，直接生效）。 */
    public boolean instantSort = true;
    /** 在背包/箱子界面显示「整理」按钮。 */
    public boolean sortButton = true;
    /** 整理按钮相对面板右边缘的水平偏移（可以拖动按钮来改）。 */
    public int sortButtonDx = -40;
    /** 整理按钮相对面板顶边的垂直偏移。 */
    public int sortButtonDy = -18;
    /** 容器界面里"整理背包"那颗按钮的位置（Integer.MIN_VALUE = 自动贴在面板底部）。 */
    public int sortBagButtonDx = Integer.MIN_VALUE;
    public int sortBagButtonDy = Integer.MIN_VALUE;
    /**
     * 创造模式「物品栏」页里整理按钮的位置。
     * 默认贴面板左侧外面：面板右边那两列标签页会盖住放在右侧的按钮。
     */
    public int sortCreativeDx = -42;
    public int sortCreativeDy = 20;

    private Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("tweakroll.properties");
    }

    public void load() {
        Path file = path();
        if (!Files.exists(file)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException exception) {
            Log.error("读取配置失败", exception);
            return;
        }
        shiftHoverMove = readBoolean(properties, "shiftHoverMove", shiftHoverMove);
        sortInventory = readBoolean(properties, "sortInventory", sortInventory);
        instantSort = readBoolean(properties, "instantSort", instantSort);
        sortButton = readBoolean(properties, "sortButton", sortButton);
        sortButtonDx = readInt(properties, "sortButtonDx", sortButtonDx);
        sortButtonDy = readInt(properties, "sortButtonDy", sortButtonDy);
        sortBagButtonDx = readInt(properties, "sortBagButtonDx", sortBagButtonDx);
        sortBagButtonDy = readInt(properties, "sortBagButtonDy", sortBagButtonDy);
        sortCreativeDx = readInt(properties, "sortCreativeDx", sortCreativeDx);
        sortCreativeDy = readInt(properties, "sortCreativeDy", sortCreativeDy);
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("shiftHoverMove", Boolean.toString(shiftHoverMove));
        properties.setProperty("sortInventory", Boolean.toString(sortInventory));
        properties.setProperty("instantSort", Boolean.toString(instantSort));
        properties.setProperty("sortButton", Boolean.toString(sortButton));
        properties.setProperty("sortButtonDx", Integer.toString(sortButtonDx));
        properties.setProperty("sortButtonDy", Integer.toString(sortButtonDy));
        properties.setProperty("sortBagButtonDx", Integer.toString(sortBagButtonDx));
        properties.setProperty("sortBagButtonDy", Integer.toString(sortBagButtonDy));
        properties.setProperty("sortCreativeDx", Integer.toString(sortCreativeDx));
        properties.setProperty("sortCreativeDy", Integer.toString(sortCreativeDy));
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "TweakRoll configuration");
            }
        } catch (IOException exception) {
            Log.error("保存配置失败", exception);
        }
    }

    private static int readInt(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }
}
