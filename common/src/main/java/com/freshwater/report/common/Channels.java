package com.freshwater.report.common;

/**
 * Velocity 与 Waterfall 伴生插件之间通信使用的插件消息通道。
 */
public final class Channels {

    /** 自定义跨代理通道名（namespace:path 格式，兼容 1.13+）。 */
    public static final String REPORT_NAMESPACE = "freshwater";
    public static final String REPORT_NAME = "report";
    public static final String REPORT = REPORT_NAMESPACE + ":" + REPORT_NAME;

    private Channels() {
    }
}
