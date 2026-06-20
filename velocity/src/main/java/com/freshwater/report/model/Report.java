package com.freshwater.report.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 一条举报记录。
 */
public final class Report {

    private int id;
    private UUID reporterUuid;
    private String reporterName;
    private UUID targetUuid;
    private String targetName;
    private String reason;
    private String server;
    private boolean nestedServer;
    private ReportStatus status;
    private UUID handlerUuid;
    private String handlerName;
    private Instant createdAt;
    private Instant updatedAt;

    public Report() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getReporterUuid() {
        return reporterUuid;
    }

    public void setReporterUuid(UUID reporterUuid) {
        this.reporterUuid = reporterUuid;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public boolean isNestedServer() {
        return nestedServer;
    }

    public void setNestedServer(boolean nestedServer) {
        this.nestedServer = nestedServer;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public UUID getHandlerUuid() {
        return handlerUuid;
    }

    public void setHandlerUuid(UUID handlerUuid) {
        this.handlerUuid = handlerUuid;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
