package com.freshwater.report.storage;

import com.freshwater.report.model.Report;
import com.freshwater.report.model.ReportNote;
import com.freshwater.report.model.ReportStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 举报数据访问层（手写 SQL）。所有方法均为阻塞调用，调用方需在异步线程执行。
 */
public final class ReportRepository {

    private final Database database;
    private final String reportsTable;
    private final String notesTable;

    public ReportRepository(Database database) {
        this.database = database;
        this.reportsTable = database.tablePrefix() + "reports";
        this.notesTable = database.tablePrefix() + "notes";
    }

    public void initSchema() throws SQLException {
        String createReports = "CREATE TABLE IF NOT EXISTS " + reportsTable + " ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "reporter_uuid VARCHAR(36) NOT NULL,"
                + "reporter_name VARCHAR(32) NOT NULL,"
                + "target_uuid VARCHAR(36) NOT NULL,"
                + "target_name VARCHAR(32) NOT NULL,"
                + "reason TEXT NOT NULL,"
                + "server VARCHAR(64),"
                + "nested_server TINYINT(1) NOT NULL DEFAULT 0,"
                + "status VARCHAR(16) NOT NULL DEFAULT 'OPEN',"
                + "handler_uuid VARCHAR(36),"
                + "handler_name VARCHAR(32),"
                + "created_at BIGINT NOT NULL,"
                + "updated_at BIGINT NOT NULL,"
                + "INDEX idx_status (status),"
                + "INDEX idx_target_uuid (target_uuid),"
                + "INDEX idx_target_name (target_name)"
                + ") DEFAULT CHARSET=utf8mb4";

        String createNotes = "CREATE TABLE IF NOT EXISTS " + notesTable + " ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "report_id INT NOT NULL,"
                + "author_uuid VARCHAR(36) NOT NULL,"
                + "author_name VARCHAR(32) NOT NULL,"
                + "content TEXT NOT NULL,"
                + "created_at BIGINT NOT NULL,"
                + "INDEX idx_report_id (report_id)"
                + ") DEFAULT CHARSET=utf8mb4";

        try (Connection conn = database.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate(createReports);
            st.executeUpdate(createNotes);
        }
    }

    public Report insert(Report report) throws SQLException {
        String sql = "INSERT INTO " + reportsTable + " (reporter_uuid, reporter_name, target_uuid, target_name,"
                + " reason, server, nested_server, status, handler_uuid, handler_name, created_at, updated_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, str(report.getReporterUuid()));
            ps.setString(2, report.getReporterName());
            ps.setString(3, str(report.getTargetUuid()));
            ps.setString(4, report.getTargetName());
            ps.setString(5, report.getReason());
            ps.setString(6, report.getServer());
            ps.setBoolean(7, report.isNestedServer());
            ps.setString(8, report.getStatus().name());
            ps.setString(9, str(report.getHandlerUuid()));
            ps.setString(10, report.getHandlerName());
            ps.setLong(11, report.getCreatedAt().toEpochMilli());
            ps.setLong(12, report.getUpdatedAt().toEpochMilli());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    report.setId(keys.getInt(1));
                }
            }
        }
        return report;
    }

    public Optional<Report> findById(int id) throws SQLException {
        String sql = "SELECT * FROM " + reportsTable + " WHERE id = ?";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapReport(rs)) : Optional.empty();
            }
        }
    }

    public List<Report> list(ReportStatus filter, int offset, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(reportsTable);
        if (filter != null) {
            sql.append(" WHERE status = ?");
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (filter != null) {
                ps.setString(idx++, filter.name());
            }
            ps.setInt(idx++, Math.max(1, limit));
            ps.setInt(idx, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                List<Report> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapReport(rs));
                }
                return out;
            }
        }
    }

    public int count(ReportStatus filter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(reportsTable);
        if (filter != null) {
            sql.append(" WHERE status = ?");
        }
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (filter != null) {
                ps.setString(1, filter.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public boolean updateStatusAndHandler(int id, ReportStatus status, UUID handlerUuid, String handlerName,
                                          Instant updatedAt) throws SQLException {
        String sql = "UPDATE " + reportsTable + " SET status = ?, handler_uuid = ?, handler_name = ?, updated_at = ?"
                + " WHERE id = ?";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, str(handlerUuid));
            ps.setString(3, handlerName);
            ps.setLong(4, updatedAt.toEpochMilli());
            ps.setInt(5, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateServer(int id, String server, boolean nested) throws SQLException {
        String sql = "UPDATE " + reportsTable + " SET server = ?, nested_server = ? WHERE id = ?";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, server);
            ps.setBoolean(2, nested);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection conn = database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + notesTable + " WHERE report_id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + reportsTable + " WHERE id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public ReportNote addNote(int reportId, UUID authorUuid, String authorName, String content) throws SQLException {
        String sql = "INSERT INTO " + notesTable + " (report_id, author_uuid, author_name, content, created_at)"
                + " VALUES (?,?,?,?,?)";
        Instant now = Instant.now();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, reportId);
            ps.setString(2, str(authorUuid));
            ps.setString(3, authorName);
            ps.setString(4, content);
            ps.setLong(5, now.toEpochMilli());
            ps.executeUpdate();
            ReportNote note = new ReportNote();
            note.setReportId(reportId);
            note.setAuthorUuid(authorUuid);
            note.setAuthorName(authorName);
            note.setContent(content);
            note.setCreatedAt(now);
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    note.setId(keys.getInt(1));
                }
            }
            return note;
        }
    }

    public List<ReportNote> listNotes(int reportId) throws SQLException {
        String sql = "SELECT * FROM " + notesTable + " WHERE report_id = ? ORDER BY id ASC";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ReportNote> out = new ArrayList<>();
                while (rs.next()) {
                    ReportNote note = new ReportNote();
                    note.setId(rs.getInt("id"));
                    note.setReportId(rs.getInt("report_id"));
                    note.setAuthorUuid(uuid(rs.getString("author_uuid")));
                    note.setAuthorName(rs.getString("author_name"));
                    note.setContent(rs.getString("content"));
                    note.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
                    out.add(note);
                }
                return out;
            }
        }
    }

    public List<Report> listByTargetUuid(UUID targetUuid, int limit) throws SQLException {
        String sql = "SELECT * FROM " + reportsTable + " WHERE target_uuid = ? ORDER BY id DESC LIMIT ?";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, str(targetUuid));
            ps.setInt(2, Math.max(1, limit));
            return readList(ps);
        }
    }

    public List<Report> listByTargetName(String name, int limit) throws SQLException {
        String sql = "SELECT * FROM " + reportsTable + " WHERE target_name = ? ORDER BY id DESC LIMIT ?";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, Math.max(1, limit));
            return readList(ps);
        }
    }

    private List<Report> readList(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Report> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapReport(rs));
            }
            return out;
        }
    }

    private Report mapReport(ResultSet rs) throws SQLException {
        Report r = new Report();
        r.setId(rs.getInt("id"));
        r.setReporterUuid(uuid(rs.getString("reporter_uuid")));
        r.setReporterName(rs.getString("reporter_name"));
        r.setTargetUuid(uuid(rs.getString("target_uuid")));
        r.setTargetName(rs.getString("target_name"));
        r.setReason(rs.getString("reason"));
        r.setServer(rs.getString("server"));
        r.setNestedServer(rs.getBoolean("nested_server"));
        r.setStatus(ReportStatus.fromString(rs.getString("status")));
        r.setHandlerUuid(uuid(rs.getString("handler_uuid")));
        r.setHandlerName(rs.getString("handler_name"));
        r.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
        r.setUpdatedAt(Instant.ofEpochMilli(rs.getLong("updated_at")));
        return r;
    }

    private static String str(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
