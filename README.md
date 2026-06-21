# FreshwaterReportV

一套安装在 **Velocity** 前端代理即全服通用的举报系统。支持 MySQL 存储、游戏内可点击通知、跨服/嵌套子服精确传送、被举报历史查询，并开放 **Java API** 与带 Bearer Token 鉴权的 **HTTP REST API**。

## 模块构成

| 模块 | 产物 | 安装位置 | 说明 |
| --- | --- | --- | --- |
| `velocity` | `FreshwaterReportV-Velocity-<version>.jar` | 前端 Velocity `plugins/` | 主举报插件，必装 |
| `velocity-companion` | `FreshwaterReportV-VelocityCompanion-<version>.jar` | 后端子 Velocity `plugins/` | 伴生插件，**Velocity 套 Velocity** 时用 |
| `waterfall-companion` | `FreshwaterReportV-Companion-<version>.jar` | 后端 Waterfall `plugins/` | 伴生插件，**Velocity 套 Waterfall** 时用 |
| `common` | （被上述模块打包） | - | 共享通道/协议常量 |

> 伴生插件仅在 **嵌套代理（前端 Velocity + 后端子代理）** 时需要，用于把 `/reports tp` 精确传送到子代理背后的真实子服、让举报记录服务器字段准确、并把主插件配置同步到子代理。请按后端子代理的类型选择对应的伴生插件：后端是 **Velocity** 用 `FreshwaterReportV-VelocityCompanion`，后端是 **Waterfall** 用 `FreshwaterReportV-Companion`。纯单层 Velocity 环境只需主插件。

## 构建

需要 JDK 17。在项目根目录执行：

```bash
./gradlew build
```

产物位于：

- `velocity/build/libs/FreshwaterReportV-Velocity-1.0.0.jar`
- `waterfall-companion/build/libs/FreshwaterReportV-Companion-1.0.0.jar`

## 安装与配置

1. 将 `FreshwaterReportV-Velocity-*.jar` 放入 Velocity 的 `plugins/` 并启动一次，生成 `plugins/freshwaterreportv/config.yml` 与 `messages.yml`。
2. 编辑 `config.yml` 填写 MySQL 连接信息（数据表会自动创建）。
3. 如为嵌套拓扑：把 `nested-proxy-mode` 设为 `true`，并将 `FreshwaterReportV-Companion-*.jar` 放入 Waterfall 的 `plugins/`。
4. 重启代理。

`config.yml` 关键项：`database`、`http-api`、`report-cooldown-seconds`、`nested-proxy-mode`、`allow-custom-reason`、`teleport-enabled`、`reasons`（举报原因列表）。所有提示语可在 `messages.yml` 自定义（MiniMessage 格式，支持颜色/点击/悬浮）。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/report <玩家> [原因id\|自定义原因...]` | 提交举报；不带原因时弹出可点击原因菜单 |
| `/reports list [页码] [状态]` | 分页查看举报（状态：OPEN/CLAIMED/CLOSED） |
| `/reports info <id>` | 查看详情与备注 |
| `/reports claim <id>` | 认领 |
| `/reports close <id>` | 关闭 |
| `/reports reopen <id>` | 重新打开 |
| `/reports note <id> <内容>` | 添加备注 |
| `/reports tp <id>` | 传送到被举报者所在（子）服 |
| `/reports delete <id>` | 删除 |
| `/reporthistory <玩家>` | 查询某玩家被举报历史 |

## 权限节点

| 节点 | 作用 |
| --- | --- |
| `freshwaterreport.report` | 提交举报（默认允许所有玩家，可显式设为 `false` 禁用） |
| `freshwaterreport.report.cooldownbypass` | 绕过举报冷却 |
| `freshwaterreport.report.custom` | 允许自定义原因（需开启 `allow-custom-reason`） |
| `freshwaterreport.notify` | 接收游戏内举报通知 |
| `freshwaterreport.reports.list` | 查看列表 |
| `freshwaterreport.reports.info` | 查看详情 |
| `freshwaterreport.reports.claim` | 认领 |
| `freshwaterreport.reports.close` | 关闭 |
| `freshwaterreport.reports.reopen` | 重新打开 |
| `freshwaterreport.reports.note` | 添加备注 |
| `freshwaterreport.reports.tp` | 跨服传送 |
| `freshwaterreport.reports.delete` | 删除 |
| `freshwaterreport.history` | 查询历史 |
| `freshwaterreport.admin` | 通配：上述全部 `reports.*` + `history` + `notify` |
| `freshwaterreport.*` | 全部权限 |

> 注意：Velocity 自身没有 OP/权限系统。`/report` 默认对所有玩家开放，无需权限插件即可使用；但 `/reports`、`/reporthistory` 等管理命令需要在 Velocity 端安装权限插件（如 LuckPerms-Velocity）并授予相应节点，否则仅控制台可用。

## Java API

其它 Velocity 插件可获取 `ReportService`：

```java
ReportService service = com.freshwater.report.FreshwaterReportV.getInstance().getReportService();
Report report = service.createReport(reporterUuid, "Reporter", targetUuid, "Target",
        "作弊", "lobby", false);
List<Report> open = service.listReports(ReportStatus.OPEN, 1, 20);
service.claimReport(report.getId(), handlerUuid, "Admin");
```

> 所有方法均为阻塞调用（访问数据库），请在异步线程使用；失败抛出 `ReportStorageException`。

## HTTP REST API

在 `config.yml` 开启 `http-api.enabled` 并设置 `token`。所有请求需携带请求头 `Authorization: Bearer <token>`，否则返回 401。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/reports?status=OPEN&page=1&size=20` | 分页列表 |
| GET | `/api/reports/{id}` | 详情（含备注） |
| POST | `/api/reports` | 创建：`{reporterName,targetName,reason,server?,reporterUuid?,targetUuid?,nestedServer?}` |
| PATCH | `/api/reports/{id}` | 改状态：`{status:"CLAIMED"|"CLOSED"|"OPEN",handlerUuid?,handlerName?}` |
| DELETE | `/api/reports/{id}` | 删除 |
| GET | `/api/reports/{id}/notes` | 备注列表 |
| POST | `/api/reports/{id}/notes` | 添加备注：`{content,authorName?,authorUuid?}` |

示例：

```bash
curl -H "Authorization: Bearer change-me-please" \
     http://127.0.0.1:8085/api/reports?status=OPEN
```

## 配置自动同步

伴生插件**无需单独配置**。Velocity 主插件是配置的唯一来源：当 Waterfall 有玩家连接时，伴生插件通过 `freshwater:report` 通道向 Velocity 请求最新的 `config.yml` 与 `messages.yml`，主插件回传后伴生插件将其镜像写入自己的数据目录（`plugins/FreshwaterReportCompanion/`）。

- 跨机器有效（走插件消息通道，无需共享文件系统）
- 带 30 秒请求冷却，避免频繁同步；主插件配置更新后，下一次有玩家连接即自动拉取
- 仅当内容有变化时才写盘并打印同步日志

## 嵌套代理下的精确传送原理

`/reports tp` 在嵌套模式下：Velocity 先把处理者连接到 Waterfall 入口，随后沿其连接下发 `TP_TO_PLAYER` 插件消息；Waterfall 伴生插件实时解析被举报者真实子服并执行 `connect()`，从而精确落点。未安装伴生插件时自动降级为仅到入口并给出提示。
