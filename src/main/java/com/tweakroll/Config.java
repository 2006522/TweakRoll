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
    /** 左键按住拖拽，把经过的同种物品收集到光标上。 */
    public boolean lmbCollect = true;
    /** 右键按住拖拽，往经过的格子各放一个。 */
    public boolean rmbPlace = true;
    /** 按住 Shift + 左键扫过格子，自动快速转移（物品栏 ↔ 背包）。 */
    public boolean shiftHoverMove = true;

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
        lmbCollect = readBoolean(properties, "lmbCollect", lmbCollect);
        rmbPlace = readBoolean(properties, "rmbPlace", rmbPlace);
        shiftHoverMove = readBoolean(properties, "shiftHoverMove", shiftHoverMove);
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("lmbCollect", Boolean.toString(lmbCollect));
        properties.setProperty("rmbPlace", Boolean.toString(rmbPlace));
        properties.setProperty("shiftHoverMove", Boolean.toString(shiftHoverMove));
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

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }
}
