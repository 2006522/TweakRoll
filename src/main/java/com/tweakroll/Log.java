package com.tweakroll;

/** 极简日志，省掉 slf4j 依赖，方便脱离构建工具手工编译。 */
final class Log {
    private static final String PREFIX = "[TweakRoll] ";
    private static boolean warned;

    private Log() {
    }

    static void info(String message) {
        System.out.println(PREFIX + message);
    }

    static void warn(String message) {
        System.out.println(PREFIX + "警告: " + message);
    }

    static void error(String message, Throwable throwable) {
        if (warned) {
            return;
        }
        warned = true;
        System.out.println(PREFIX + "错误: " + message);
        throwable.printStackTrace(System.out);
    }
}
