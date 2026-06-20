package com.freshwater.report.model;

/**
 * 举报状态。
 */
public enum ReportStatus {
    /** 待处理。 */
    OPEN,
    /** 已认领（处理中）。 */
    CLAIMED,
    /** 已关闭（处理完毕）。 */
    CLOSED;

    public static ReportStatus fromString(String value) {
        if (value == null) {
            return OPEN;
        }
        try {
            return ReportStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
