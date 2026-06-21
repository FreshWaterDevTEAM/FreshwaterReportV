package com.freshwater.report.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;

/**
 * 权限节点定义与判定。
 */
public final class Permissions {

    public static final String REPORT = "freshwaterreport.report";
    public static final String REPORT_COOLDOWN_BYPASS = "freshwaterreport.report.cooldownbypass";
    public static final String REPORT_CUSTOM = "freshwaterreport.report.custom";
    public static final String NOTIFY = "freshwaterreport.notify";

    public static final String REPORTS_LIST = "freshwaterreport.reports.list";
    public static final String REPORTS_INFO = "freshwaterreport.reports.info";
    public static final String REPORTS_CLAIM = "freshwaterreport.reports.claim";
    public static final String REPORTS_CLOSE = "freshwaterreport.reports.close";
    public static final String REPORTS_REOPEN = "freshwaterreport.reports.reopen";
    public static final String REPORTS_NOTE = "freshwaterreport.reports.note";
    public static final String REPORTS_TP = "freshwaterreport.reports.tp";
    public static final String REPORTS_DELETE = "freshwaterreport.reports.delete";
    public static final String HISTORY = "freshwaterreport.history";

    public static final String ADMIN = "freshwaterreport.admin";
    public static final String ALL = "freshwaterreport.*";

    private Permissions() {
    }

    /**
     * 判定来源是否拥有某节点。{@link #ALL} 覆盖全部；{@link #ADMIN} 覆盖管理类节点
     * （reports.* / history / notify）。
     */
    public static boolean has(CommandSource source, String node) {
        if (source.hasPermission(ALL)) {
            return true;
        }
        if (source.hasPermission(node)) {
            return true;
        }
        return isManagementNode(node) && source.hasPermission(ADMIN);
    }

    /**
     * 默认允许判定：未被权限插件显式拒绝（FALSE）即放行。
     * 用于面向所有玩家的功能（如 /report），使得未安装权限插件时玩家也能使用。
     */
    public static boolean hasAllowedByDefault(CommandSource source, String node) {
        if (source.hasPermission(ALL)) {
            return true;
        }
        return source.getPermissionValue(node) != Tristate.FALSE;
    }

    private static boolean isManagementNode(String node) {
        return node.startsWith("freshwaterreport.reports.")
                || node.equals(HISTORY)
                || node.equals(NOTIFY);
    }
}
