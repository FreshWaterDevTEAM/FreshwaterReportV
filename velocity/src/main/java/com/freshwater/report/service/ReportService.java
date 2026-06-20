package com.freshwater.report.service;

import com.freshwater.report.model.Report;
import com.freshwater.report.model.ReportNote;
import com.freshwater.report.model.ReportStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 对外开放的 Java API。其它 Velocity 插件可通过
 * {@code proxy.getPluginManager().getPlugin("freshwaterreportv")} 拿到主插件实例后获取本服务，
 * 或调用 {@code FreshwaterReportV.getInstance().getReportService()}。
 *
 * <p>所有方法均为阻塞调用（会访问数据库），请勿在 Netty/主线程直接调用，建议放入异步线程。
 * 访问失败将抛出 {@link ReportStorageException}。</p>
 */
public interface ReportService {

    /**
     * 创建一条举报。
     *
     * @param server       被举报者所在服务器（可为 null）
     * @param nestedServer 该服务器是否为嵌套子代理入口（影响显示标注）
     */
    Report createReport(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName,
                        String reason, String server, boolean nestedServer);

    Optional<Report> getReport(int id);

    /**
     * 分页查询。
     *
     * @param filter   状态过滤，null 表示全部
     * @param page     从 1 开始
     * @param pageSize 每页条数
     */
    List<Report> listReports(ReportStatus filter, int page, int pageSize);

    int countReports(ReportStatus filter);

    boolean claimReport(int id, UUID handlerUuid, String handlerName);

    boolean closeReport(int id, UUID handlerUuid, String handlerName);

    boolean reopenReport(int id);

    boolean deleteReport(int id);

    ReportNote addNote(int id, UUID authorUuid, String authorName, String content);

    List<ReportNote> getNotes(int id);

    List<Report> getHistoryByUuid(UUID targetUuid, int limit);

    List<Report> getHistoryByName(String targetName, int limit);

    boolean updateReportServer(int id, String server, boolean nestedServer);
}
