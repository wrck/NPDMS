package cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query;

public record ExportTaskRetryUpdate(Long tenantId, Long taskId, Integer expectedVersion,
                                    String updater) {
}
