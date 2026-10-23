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
    public enum ScrollDirection {
        NORMAL("tweakroll.value.normal"),
        INVERTED("tweakroll.value.inverted");

        public final String key;

        ScrollDirection(String key) {
            this.key = key;
        }

        public ScrollDirection next() {
            return this == NORMAL ? INVERTED : NORMAL;
        }
    }

    public enum SearchOrder {
        FIRST_TO_LAST("tweakroll.value.first_to_last"),
        LAST_TO_FIRST("tweakroll.value.last_to_first");

        public final String key;

        SearchOrder(String key) {
            this.key = key;
        }

        public SearchOrder next() {
            return this == FIRST_TO_LAST ? LAST_TO_FIRST : FIRST_TO_LAST;
        }
    }

    public enum Scaling {
        ALWAYS_ONE("tweakroll.value.always_one"),
        PROPORTIONAL("tweakroll.value.proportional");

        public final String key;

        Scaling(String key) {
            this.key = key;
        }

        public Scaling next() {
            return this == ALWAYS_ONE ? PROPORTIONAL : ALWAYS_ONE;
        }
    }

    /** 滚轮整理：悬停格与另一侧背包之间逐个搬运。 */
    public boolean wheelTweak = true;
    /** 左键按住拖拽，把经过的同种物品收集到光标上。 */
    public boolean lmbCollect = true;
    /** 右键按住拖拽，往经过的格子各放一个。 */
    public boolean rmbPlace = true;
    /** 按住 Shift + 左键扫过格子，自动快速转移（物品栏 ↔ 背包）。 */
    public boolean shiftHoverMove = true;
    public ScrollDirection scrollDirection = ScrollDirection.NORMAL;
    public SearchOrder searchOrder = SearchOrder.FIRST_TO_LAST;
    public Scaling scaling = Scaling.ALWAYS_ONE;

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
        wheelTweak = readBoolean(properties, "wheelTweak", wheelTweak);
        lmbCollect = readBoolean(properties, "lmbCollect", lmbCollect);
        rmbPlace = readBoolean(properties, "rmbPlace", rmbPlace);
        shiftHoverMove = readBoolean(properties, "shiftHoverMove", shiftHoverMove);
        scrollDirection = readEnum(properties, "scrollDirection", ScrollDirection.class, scrollDirection);
        searchOrder = readEnum(properties, "searchOrder", SearchOrder.class, searchOrder);
        scaling = readEnum(properties, "scaling", Scaling.class, scaling);
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("wheelTweak", Boolean.toString(wheelTweak));
        properties.setProperty("lmbCollect", Boolean.toString(lmbCollect));
        properties.setProperty("rmbPlace", Boolean.toString(rmbPlace));
        properties.setProperty("shiftHoverMove", Boolean.toString(shiftHoverMove));
        properties.setProperty("scrollDirection", scrollDirection.name());
        properties.setProperty("searchOrder", searchOrder.name());
        properties.setProperty("scaling", scaling.name());
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

    private static <E extends Enum<E>> E readEnum(Properties properties, String key, Class<E> type, E fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
