package com.freshwater.report.http;

import com.freshwater.report.model.Report;
import com.freshwater.report.model.ReportNote;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Report / ReportNote 与 JSON 的转换。
 */
final class ReportJson {

    private ReportJson() {
    }

    static JsonObject toJson(Report report) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", report.getId());
        obj.addProperty("reporterUuid", str(report.getReporterUuid()));
        obj.addProperty("reporterName", report.getReporterName());
        obj.addProperty("targetUuid", str(report.getTargetUuid()));
        obj.addProperty("targetName", report.getTargetName());
        obj.addProperty("reason", report.getReason());
        obj.addProperty("server", report.getServer());
        obj.addProperty("nestedServer", report.isNestedServer());
        obj.addProperty("status", report.getStatus().name());
        obj.addProperty("handlerUuid", str(report.getHandlerUuid()));
        obj.addProperty("handlerName", report.getHandlerName());
        obj.addProperty("createdAt", report.getCreatedAt() == null ? 0 : report.getCreatedAt().toEpochMilli());
        obj.addProperty("updatedAt", report.getUpdatedAt() == null ? 0 : report.getUpdatedAt().toEpochMilli());
        return obj;
    }

    static JsonObject toJson(ReportNote note) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", note.getId());
        obj.addProperty("reportId", note.getReportId());
        obj.addProperty("authorUuid", str(note.getAuthorUuid()));
        obj.addProperty("authorName", note.getAuthorName());
        obj.addProperty("content", note.getContent());
        obj.addProperty("createdAt", note.getCreatedAt() == null ? 0 : note.getCreatedAt().toEpochMilli());
        return obj;
    }

    static JsonArray toJsonArray(List<Report> reports) {
        JsonArray array = new JsonArray();
        for (Report report : reports) {
            array.add(toJson(report));
        }
        return array;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
