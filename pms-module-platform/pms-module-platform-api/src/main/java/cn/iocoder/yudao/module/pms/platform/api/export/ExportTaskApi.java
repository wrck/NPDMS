package cn.iocoder.yudao.module.pms.platform.api.export;

/** 统一异步导出任务公开契约。 */
public interface ExportTaskApi {

    ExportTaskFact request(ExportTaskRequestCommand command);

    ExportTaskFact getFact(ExportTaskFactQuery query);

    ExportTaskFact retry(ExportTaskRetryCommand command);
}
