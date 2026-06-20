package com.freshwater.report.service;

import com.freshwater.report.model.Report;
import com.freshwater.report.model.ReportNote;
import com.freshwater.report.model.ReportStatus;
import com.freshwater.report.storage.ReportRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ReportService} 的默认实现，委托给 {@link ReportRepository}。
 */
public final class ReportServiceImpl implements ReportService {

    private final ReportRepository repository;

    public ReportServiceImpl(ReportRepository repository) {
        this.repository = repository;
    }

    @Override
    public Report createReport(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName,
                               String reason, String server, boolean nestedServer) {
        Report report = new Report();
        report.setReporterUuid(reporterUuid);
        report.setReporterName(reporterName);
        report.setTargetUuid(targetUuid);
        report.setTargetName(targetName);
        report.setReason(reason);
        report.setServer(server);
        report.setNestedServer(nestedServer);
        report.setStatus(ReportStatus.OPEN);
        Instant now = Instant.now();
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        try {
            return repository.insert(report);
        } catch (SQLException e) {
            throw new ReportStorageException("创建举报失败", e);
        }
    }

    @Override
    public Optional<Report> getReport(int id) {
        try {
            return repository.findById(id);
        } catch (SQLException e) {
            throw new ReportStorageException("查询举报失败: id=" + id, e);
        }
    }

    @Override
    public List<Report> listReports(ReportStatus filter, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, pageSize);
        try {
            return repository.list(filter, (safePage - 1) * safeSize, safeSize);
        } catch (SQLException e) {
            throw new ReportStorageException("查询举报列表失败", e);
        }
    }

    @Override
    public int countReports(ReportStatus filter) {
        try {
            return repository.count(filter);
        } catch (SQLException e) {
            throw new ReportStorageException("统计举报数量失败", e);
        }
    }

    @Override
    public boolean claimReport(int id, UUID handlerUuid, String handlerName) {
        try {
            return repository.updateStatusAndHandler(id, ReportStatus.CLAIMED, handlerUuid, handlerName, Instant.now());
        } catch (SQLException e) {
            throw new ReportStorageException("认领举报失败: id=" + id, e);
        }
    }

    @Override
    public boolean closeReport(int id, UUID handlerUuid, String handlerName) {
        try {
            return repository.updateStatusAndHandler(id, ReportStatus.CLOSED, handlerUuid, handlerName, Instant.now());
        } catch (SQLException e) {
            throw new ReportStorageException("关闭举报失败: id=" + id, e);
        }
    }

    @Override
    public boolean reopenReport(int id) {
        try {
            return repository.updateStatusAndHandler(id, ReportStatus.OPEN, null, null, Instant.now());
        } catch (SQLException e) {
            throw new ReportStorageException("重开举报失败: id=" + id, e);
        }
    }

    @Override
    public boolean deleteReport(int id) {
        try {
            return repository.delete(id);
        } catch (SQLException e) {
            throw new ReportStorageException("删除举报失败: id=" + id, e);
        }
    }

    @Override
    public ReportNote addNote(int id, UUID authorUuid, String authorName, String content) {
        try {
            return repository.addNote(id, authorUuid, authorName, content);
        } catch (SQLException e) {
            throw new ReportStorageException("添加备注失败: id=" + id, e);
        }
    }

    @Override
    public List<ReportNote> getNotes(int id) {
        try {
            return repository.listNotes(id);
        } catch (SQLException e) {
            throw new ReportStorageException("查询备注失败: id=" + id, e);
        }
    }

    @Override
    public List<Report> getHistoryByUuid(UUID targetUuid, int limit) {
        try {
            return repository.listByTargetUuid(targetUuid, limit);
        } catch (SQLException e) {
            throw new ReportStorageException("查询历史失败", e);
        }
    }

    @Override
    public List<Report> getHistoryByName(String targetName, int limit) {
        try {
            return repository.listByTargetName(targetName, limit);
        } catch (SQLException e) {
            throw new ReportStorageException("查询历史失败", e);
        }
    }

    @Override
    public boolean updateReportServer(int id, String server, boolean nestedServer) {
        try {
            return repository.updateServer(id, server, nestedServer);
        } catch (SQLException e) {
            throw new ReportStorageException("更新服务器字段失败: id=" + id, e);
        }
    }
}
