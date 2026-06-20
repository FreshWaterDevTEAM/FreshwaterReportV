package com.freshwater.report.config;

/**
 * config.yml 中配置的预设举报原因。
 */
public final class ReportReason {

    private final String id;
    private final String display;
    private final String description;

    public ReportReason(String id, String display, String description) {
        this.id = id;
        this.display = display;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public String getDescription() {
        return description;
    }
}
